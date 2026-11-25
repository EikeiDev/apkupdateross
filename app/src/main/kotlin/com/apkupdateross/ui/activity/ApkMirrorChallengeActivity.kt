package com.apkupdateross.ui.activity

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.apkupdateross.R
import com.apkupdateross.util.ApkMirrorChallengeCoordinator
import com.apkupdateross.util.extractCookieValue
import com.apkupdateross.util.hasFreshClearanceCookie
import com.apkupdateross.util.isApkMirrorUrl
import com.apkupdateross.util.isCloudflareChallengePage
import com.apkupdateross.util.isLikelyApkMirrorDownloadUrl
import com.apkupdateross.util.mergeCookieHeaders
import org.koin.android.ext.android.inject

class ApkMirrorChallengeActivity : Activity() {
    private val coordinator: ApkMirrorChallengeCoordinator by inject()

    private lateinit var browser: WebView
    private lateinit var progress: ProgressBar
    private lateinit var status: TextView
    private var requestId: Long = -1
    private var challengeUrl: String = ""
    private var referer: String = ""
    private var initialClearance: String? = null
    private var completed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestId = intent.getLongExtra(EXTRA_REQUEST_ID, -1)
        challengeUrl = intent.getStringExtra(EXTRA_CHALLENGE_URL).orEmpty()
        referer = intent.getStringExtra(EXTRA_REFERER).orEmpty()
        if (requestId < 0 || !isApkMirrorUrl(challengeUrl)) {
            finish()
            return
        }

        configureWindow()
        browser = createContent()
        configureBrowser(intent.getStringExtra(EXTRA_USER_AGENT).orEmpty())
        prepareCookiesAndLoad(intent.getStringExtra(EXTRA_COOKIES).orEmpty())
    }

    override fun onStart() {
        super.onStart()
        val metrics = resources.displayMetrics
        val isTv = resources.configuration.uiMode and Configuration.UI_MODE_TYPE_MASK ==
            Configuration.UI_MODE_TYPE_TELEVISION
        val widthFraction = if (isTv) 0.86f else 0.94f
        val heightFraction = if (isTv) 0.86f else 0.9f
        window.setLayout(
            (metrics.widthPixels * widthFraction).toInt(),
            (metrics.heightPixels * heightFraction).toInt()
        )
    }

    override fun onDestroy() {
        if (::browser.isInitialized) {
            browser.stopLoading()
            (browser.parent as? ViewGroup)?.removeView(browser)
            browser.removeAllViews()
            browser.destroy()
        }
        if (isFinishing && !completed && requestId >= 0) {
            coordinator.cancel(requestId)
        }
        super.onDestroy()
    }

    private fun configureWindow() {
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.68f }
        window.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun createContent(): WebView {
        val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
            Configuration.UI_MODE_NIGHT_YES
        val containerColor = if (night) Color.rgb(16, 21, 18) else Color.rgb(247, 250, 246)
        val foreground = if (night) Color.rgb(235, 241, 237) else Color.rgb(28, 34, 31)
        val secondary = if (night) Color.rgb(184, 194, 188) else Color.rgb(78, 88, 82)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(containerColor)
                cornerRadius = dp(18).toFloat()
            }
            clipToOutline = true
        }
        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(20), dp(14), dp(12), dp(12))
        }
        val labels = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        val title = TextView(this).apply {
            text = getString(R.string.apkmirror_verification_title)
            setTextColor(foreground)
            textSize = 20f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        status = TextView(this).apply {
            text = getString(R.string.apkmirror_verification_message)
            setTextColor(secondary)
            textSize = 14f
            setPadding(0, dp(3), 0, 0)
        }
        labels.addView(title)
        labels.addView(status)
        header.addView(labels, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        header.addView(ImageButton(this).apply {
            setImageResource(R.drawable.ic_close)
            setColorFilter(foreground)
            setBackgroundResource(R.drawable.tv_view_button_background)
            setPadding(dp(14), dp(14), dp(14), dp(14))
            contentDescription = getString(R.string.apkmirror_verification_close)
            isFocusable = true
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(dp(52), dp(52)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
        }
        val webView = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            isFocusable = true
            isFocusableInTouchMode = true
        }
        root.addView(header)
        root.addView(progress, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)))
        root.addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(FrameLayout(this).apply { addView(root) })
        return webView
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureBrowser(sessionUserAgent: String) {
        val defaultUserAgent = android.webkit.WebSettings.getDefaultUserAgent(this)
        val browserUserAgent = sessionUserAgent
            .takeIf { it.contains("Mozilla/", ignoreCase = true) && !it.contains("APKUpdater", ignoreCase = true) }
            ?: defaultUserAgent

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAcceptThirdPartyCookies(browser, true)
            }
        }
        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            userAgentString = browserUserAgent
            loadsImagesAutomatically = true
            allowFileAccess = false
            allowContentAccess = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            browser.settings.safeBrowsingEnabled = true
        }
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) View.INVISIBLE else View.VISIBLE
            }
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
                handleNavigation(request?.url?.toString())

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                maybeCompleteAfterClearance(url)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                inspectDownloadRequest(request?.url?.toString())
                return super.shouldInterceptRequest(view, request)
            }

            override fun onRenderProcessGone(
                view: WebView?,
                detail: RenderProcessGoneDetail?
            ): Boolean {
                if (!completed) {
                    completed = true
                    coordinator.cancel(requestId)
                    finish()
                }
                return true
            }
        }
        browser.setDownloadListener(DownloadListener { url, _, _, _, _ ->
            completeVerification(url, requireFreshClearance = false)
        })
    }

    private fun prepareCookiesAndLoad(sessionCookies: String) {
        val cookieManager = CookieManager.getInstance()
        initialClearance = extractCookieValue(collectCookies(challengeUrl), "cf_clearance")
        sessionCookies.split(';')
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("cf_clearance=", ignoreCase = true) }
            .forEach { cookieManager.setCookie(APKMIRROR_BASE_URL, it) }

        val expirePatterns = listOf(
            APKMIRROR_BASE_URL to "cf_clearance=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/; Domain=.apkmirror.com; Secure",
            APKMIRROR_BASE_URL to "cf_clearance=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/; Domain=www.apkmirror.com; Secure",
            APKMIRROR_BASE_URL to "cf_clearance=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/; Secure",
            "https://apkmirror.com" to "cf_clearance=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/; Domain=.apkmirror.com; Secure",
            "https://apkmirror.com" to "cf_clearance=; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Max-Age=0; Path=/; Secure"
        )
        expirePatterns.dropLast(1).forEach { (url, cookie) ->
            cookieManager.setCookie(url, cookie)
        }
        val (lastUrl, lastCookie) = expirePatterns.last()
        cookieManager.setCookie(lastUrl, lastCookie) {
            cookieManager.flush()
            val remaining = extractCookieValue(collectCookies(challengeUrl), "cf_clearance")
            if (remaining != null) {
                initialClearance = remaining
            }
            val headers = if (referer.isNotBlank()) mapOf("Referer" to referer) else emptyMap()
            browser.loadUrl(challengeUrl, headers)
            browser.requestFocus()
        }
    }

    private fun handlePotentialDownload(url: String?): Boolean {
        if (url.isNullOrBlank() || !isLikelyApkMirrorDownloadUrl(url)) return false
        val cookies = collectCookies(url)
        if (!hasFreshClearanceCookie(cookies, initialClearance)) return false
        completeVerification(url)
        return true
    }

    private fun handleNavigation(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        if (handlePotentialDownload(url)) return true
        return !isApkMirrorUrl(url)
    }

    private fun inspectDownloadRequest(url: String?) {
        if (url.isNullOrBlank() || !isLikelyApkMirrorDownloadUrl(url)) return
        browser.post { handlePotentialDownload(url) }
    }

    private fun maybeCompleteAfterClearance(url: String?) {
        if (completed) return
        val cookies = collectCookies(url)
        if (!hasFreshClearanceCookie(cookies, initialClearance)) return
        if (isCloudflareChallengePage(url, browser.title)) return
        browser.evaluateJavascript(
            """
            (function() {
                var hasChallenge = !!(
                    document.getElementById('challenge-form') ||
                    document.getElementById('challenge-running') ||
                    document.getElementById('turnstile-wrapper') ||
                    document.querySelector('.cf-turnstile, iframe[src*="challenges.cloudflare.com"]')
                );
                return hasChallenge ? 'CHALLENGE' : (document.title || '');
            })();
            """.trimIndent()
        ) { raw ->
            if (completed || isFinishing || isDestroyed) return@evaluateJavascript
            val cleaned = raw.orEmpty().trim().removeSurrounding("\"")
            if (cleaned == "CHALLENGE" || isCloudflareChallengePage(url, cleaned)) return@evaluateJavascript
            val latestCookies = collectCookies(url)
            if (hasFreshClearanceCookie(latestCookies, initialClearance)) {
                completeVerification(url?.takeIf(::isLikelyApkMirrorDownloadUrl))
            }
        }
    }

    private fun completeVerification(resolvedUrl: String?, requireFreshClearance: Boolean = true) {
        if (completed) return
        val cookies = collectCookies(resolvedUrl)
        if (requireFreshClearance && !hasFreshClearanceCookie(cookies, initialClearance)) return
        completed = true
        CookieManager.getInstance().flush()
        status.text = getString(R.string.apkmirror_verification_complete)
        coordinator.complete(
            requestId = requestId,
            cookies = cookies,
            userAgent = browser.settings.userAgentString,
            resolvedUrl = resolvedUrl?.takeIf(::isApkMirrorUrl)
        )
        browser.postDelayed({ finish() }, 300)
    }

    private fun collectCookies(url: String?): String = mergeCookieHeaders(
        CookieManager.getInstance().getCookie(APKMIRROR_BASE_URL),
        CookieManager.getInstance().getCookie("https://apkmirror.com"),
        url?.let { CookieManager.getInstance().getCookie(it) },
        browser.url?.let { CookieManager.getInstance().getCookie(it) }
    )

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_CHALLENGE_URL = "challenge_url"
        private const val EXTRA_REFERER = "referer"
        private const val EXTRA_USER_AGENT = "user_agent"
        private const val EXTRA_COOKIES = "cookies"
        private const val APKMIRROR_BASE_URL = "https://www.apkmirror.com"

        fun intent(
            context: Context,
            requestId: Long,
            challengeUrl: String,
            referer: String,
            userAgent: String,
            cookies: String
        ): Intent = Intent(context, ApkMirrorChallengeActivity::class.java).apply {
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_CHALLENGE_URL, challengeUrl)
            putExtra(EXTRA_REFERER, referer)
            putExtra(EXTRA_USER_AGENT, userAgent)
            putExtra(EXTRA_COOKIES, cookies)
        }
    }
}
