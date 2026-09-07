package com.apkupdateross.ui.activity

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.apkupdateross.R
import com.apkupdateross.data.ui.AppInstallProgress
import com.apkupdateross.data.ui.AppInstallStatus
import com.apkupdateross.data.ui.AppUpdate
import com.apkupdateross.data.ui.Link
import com.apkupdateross.prefs.Prefs
import com.apkupdateross.util.DownloadStorage
import com.apkupdateross.util.InstallLog
import com.apkupdateross.util.SessionInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipFile

class UptodownDownloadActivity : ComponentActivity(), KoinComponent {

    private val client: OkHttpClient by inject()
    private val installer: SessionInstaller by inject()
    private val prefs: Prefs by inject()
    private val downloadStorage: DownloadStorage by inject()
    private val installLog: InstallLog by inject()

    private lateinit var request: BrowserRequest
    private var webView: WebView? = null
    private var progressBar: ProgressBar? = null
    private var statusText: TextView? = null
    private var downloadJob: Job? = null
    private var currentCall: Call? = null
    private var suppressFailureResult = false
    private var autoClickAttempted = false
    private var downloadStarted = false
    private var browserFallbackLoaded = false

    enum class Mode {
        Install,
        Save
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val parsedRequest = BrowserRequest.from(intent)
        if (parsedRequest == null) {
            finish()
            return
        }
        request = parsedRequest

        val browser = createLayout()
        webView = browser
        configureWebView(browser)
        startQuietDownloadOrLoadBrowser(browser)

        onBackPressedDispatcher.addCallback(this) {
            val view = webView
            if (view?.canGoBack() == true) {
                view.goBack()
            } else {
                finish()
            }
        }
    }

    override fun onDestroy() {
        suppressFailureResult = true
        downloadJob?.cancel()
        currentCall?.cancel()
        webView?.destroy()
        webView = null
        super.onDestroy()
    }

    private fun createLayout(): WebView {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(9, 14, 12))
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(16.dp, 12.dp, 12.dp, 8.dp)
        }

        val textColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        header.addView(
            textColumn,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        textColumn.addView(TextView(this).apply {
            setText(R.string.uptodown_download_title)
            setTextColor(Color.WHITE)
            textSize = 20f
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        })

        statusText = TextView(this).apply {
            setText(R.string.uptodown_download_hint)
            setTextColor(Color.rgb(190, 203, 197))
            textSize = 13f
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }
        textColumn.addView(statusText)

        header.addView(TextView(this).apply {
            text = "X"
            setTextColor(Color.rgb(219, 230, 225))
            textSize = 18f
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }, LinearLayout.LayoutParams(44.dp, 44.dp))

        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progress = 0
            isIndeterminate = false
        }

        val browser = WebView(this).apply {
            setBackgroundColor(Color.rgb(9, 14, 12))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        root.addView(header)
        root.addView(
            progressBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 3.dp)
        )
        root.addView(browser)
        setContentView(root)
        ViewCompat.requestApplyInsets(root)

        return browser
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView(browser: WebView) {
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
        }

        browser.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            userAgentString = USER_AGENT
            cacheMode = WebSettings.LOAD_DEFAULT
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            }
        }

        browser.addJavascriptInterface(DownloadBridge(), DOWNLOAD_BRIDGE_NAME)

        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (downloadJob?.isActive == true) return
                progressBar?.isIndeterminate = false
                progressBar?.progress = newProgress
            }
        }

        browser.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                view?.let { prepareDownloadPage(it) }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString().orEmpty()
                return maybeStartDownload(url, null, null, null)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                return maybeStartDownload(url.orEmpty(), null, null, null)
            }
        }

        browser.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            maybeStartDownload(url, userAgent, contentDisposition, mimeType)
        }
    }

    private fun startQuietDownloadOrLoadBrowser(browser: WebView) {
        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                withContext(Dispatchers.Main) {
                    statusText?.setText(R.string.uptodown_download_preparing)
                    progressBar?.isIndeterminate = true
                    browser.visibility = View.GONE
                }

                val url = resolveQuietDownloadUrl(request.pageUrl)
                val extension = resolveExtension(url, null, null)
                val file = downloadToCache(url, null, extension)
                validateDownloadedFile(file, extension)

                if (request.mode == Mode.Save) {
                    saveDownloadedFile(file, extension, url, null, null)
                } else {
                    installDownloadedFile(file, extension)
                }
            }.onFailure {
                currentCall?.cancel()
                currentCall = null
                if (suppressFailureResult || it is CancellationException) return@onFailure

                Log.i("UptodownDownloadActivity", "Quiet Uptodown download unavailable; falling back to WebView.", it)
                withContext(Dispatchers.Main) {
                    loadBrowserFallback(browser)
                }
            }
        }
    }

    private fun loadBrowserFallback(browser: WebView) {
        if (browserFallbackLoaded || isFinishing || isDestroyed) return
        browserFallbackLoaded = true
        downloadJob = null
        downloadStarted = false
        autoClickAttempted = false
        statusText?.setText(R.string.uptodown_download_hint)
        progressBar?.isIndeterminate = false
        progressBar?.progress = 0
        browser.visibility = View.VISIBLE
        browser.loadUrl(request.pageUrl)
    }

    private fun prepareDownloadPage(browser: WebView) {
        installDownloadCapture(browser)
        browser.evaluateJavascript(
            """
                (function() {
                    document.querySelectorAll('#detail-download-button-know-native, [data-url*="uptodown-app-store"]').forEach(function(el) {
                        el.style.display = 'none';
                    });
                    var button = document.getElementById('detail-download-button');
                    if (!button) return false;
                    button.scrollIntoView({ block: 'center' });
                    return true;
                })();
            """.trimIndent(),
            null
        )

        if (autoClickAttempted) return
        autoClickAttempted = true
        browser.postDelayed({
            if (downloadJob?.isActive == true || browser.url.orEmpty().isBlank()) return@postDelayed
            browser.evaluateJavascript(
                """
                    (function() {
                        var button = document.getElementById('detail-download-button');
                        if (!button) return false;
                        button.click();
                        return true;
                    })();
                """.trimIndent(),
                null
            )
        }, AUTO_CLICK_DELAY_MS)
    }

    private fun installDownloadCapture(browser: WebView) {
        browser.evaluateJavascript(
            """
                (function() {
                    if (window.__apkUpdaterUptodownCaptureInstalled) return true;
                    window.__apkUpdaterUptodownCaptureInstalled = true;

                    function isTrustedUptodownDownload(url) {
                        return /^https?:\/\/dw\.uptodown\.(com|net)\//i.test(url)
                            && (/\/dwn\//i.test(url) || /\.(apk|xapk|apks)([?#]|${'$'})/i.test(url));
                    }

                    function report(url) {
                        try {
                            if (url && isTrustedUptodownDownload(String(url)) && window.$DOWNLOAD_BRIDGE_NAME) {
                                window.$DOWNLOAD_BRIDGE_NAME.onDownloadUrl(String(url));
                            }
                        } catch (ignored) {}
                    }

                    var nativeClick = HTMLAnchorElement.prototype.click;
                    HTMLAnchorElement.prototype.click = function() {
                        try {
                            report(this.href);
                        } catch (ignored) {}
                        return nativeClick.apply(this, arguments);
                    };

                    document.addEventListener('click', function(event) {
                        try {
                            var target = event.target;
                            var anchor = target && target.closest ? target.closest('a[href]') : null;
                            if (anchor) report(anchor.href);
                        } catch (ignored) {}
                    }, true);

                    return true;
                })();
            """.trimIndent(),
            null
        )
    }

    private fun maybeStartDownload(
        rawUrl: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ): Boolean {
        val url = rawUrl.trim()
        if (url.isBlank() || !url.isUptodownDownloadUrl()) return false
        if (downloadStarted || downloadJob?.isActive == true) return true
        downloadStarted = true

        downloadJob = lifecycleScope.launch(Dispatchers.IO) {
            runCatching {
                withContext(Dispatchers.Main) {
                    statusText?.setText(R.string.uptodown_download_downloading)
                    progressBar?.isIndeterminate = true
                    webView?.visibility = View.GONE
                }

                val extension = resolveExtension(url, contentDisposition, mimeType)
                val file = downloadToCache(url, userAgent, extension)
                validateDownloadedFile(file, extension)

                if (request.mode == Mode.Save) {
                    saveDownloadedFile(file, extension, url, contentDisposition, mimeType)
                } else {
                    installDownloadedFile(file, extension)
                }
            }.onFailure {
                currentCall?.cancel()
                if (suppressFailureResult || it is CancellationException) return@onFailure

                Log.e("UptodownDownloadActivity", "Uptodown download failed.", it)
                if (request.mode == Mode.Install) {
                    installLog.emitStatus(AppInstallStatus(false, request.installId, true))
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        this@UptodownDownloadActivity,
                        getString(R.string.uptodown_download_failed),
                        Toast.LENGTH_LONG
                    ).show()
                    finish()
                }
            }
        }
        return true
    }

    private inner class DownloadBridge {
        @JavascriptInterface
        fun onDownloadUrl(url: String) {
            runOnUiThread {
                maybeStartDownload(url, null, null, null)
            }
        }
    }

    private fun resolveQuietDownloadUrl(pageUrl: String): String {
        val doc = requestDocument(pageUrl)
        val button = doc.selectFirst("#detail-download-button")
            ?: throw IOException("Uptodown download button not found")

        button.attr("data-url-ext")
            .takeIf { it.isNotBlank() }
            ?.let { url ->
                return url.takeIf { it.startsWith("http", ignoreCase = true) }
                    ?: throw IOException(getString(R.string.uptodown_download_invalid_url))
            }

        button.attr("data-url")
            .takeIf { it.isNotBlank() }
            ?.let { tokenizedPath ->
                return "$UPTODOWN_DOWNLOAD_BASE$tokenizedPath"
            }

        val appId = button.attr("data-app-id").takeIf { it.isNotBlank() }
            ?: throw IOException("Uptodown app id not found")
        val fileId = button.attr("data-file-id").takeIf { it.isNotBlank() }
            ?: throw IOException("Uptodown file id not found")
        val onlyXapk = button.attr("data-only-xapk").takeIf { it.isNotBlank() } ?: "0"

        return requestQuietAjaxDownloadUrl(pageUrl, appId, fileId, onlyXapk)
    }

    private fun requestDocument(url: String): Document {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Referer", "https://en.uptodown.com")
            .get()
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Uptodown HTTP ${response.code}")
            }
            Jsoup.parse(response.body?.string().orEmpty(), url)
        }
    }

    private fun requestQuietAjaxDownloadUrl(
        pageUrl: String,
        appId: String,
        fileId: String,
        onlyXapk: String
    ): String {
        val page = Uri.parse(pageUrl)
        val origin = "${page.scheme ?: "https"}://${page.host ?: throw IOException("Invalid Uptodown host")}"
        val body = JSONObject()
            .put("token", "")
            .put("onlyXapk", onlyXapk)
            .toString()
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$origin/ajax/app/$appId/file/$fileId/download-url")
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json, text/plain, */*")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("Origin", origin)
            .header("Referer", pageUrl)
            .post(body)
            .build()

        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Uptodown quiet download requires browser verification")
            }
            val payload = response.body?.string().orEmpty()
            val downloadUrl = JSONObject(payload)
                .optJSONObject("data")
                ?.optString("downloadURL")
                ?.takeIf { it.isNotBlank() }
                ?: throw IOException("Uptodown quiet download URL not found")
            "$UPTODOWN_DOWNLOAD_BASE$downloadUrl"
        }
    }

    private suspend fun downloadToCache(url: String, userAgent: String?, extension: String): File {
        val file = File(cacheDir, "uptodown_${request.installId}_${UUID.randomUUID()}.$extension")
        val cookies = listOfNotNull(
            CookieManager.getInstance().getCookie(url),
            CookieManager.getInstance().getCookie(request.pageUrl)
        ).filter { it.isNotBlank() }.distinct().joinToString("; ")

        val builder = Request.Builder()
            .url(url)
            .header("User-Agent", userAgent?.takeIf { it.isNotBlank() } ?: USER_AGENT)
            .header("Accept", "*/*")
            .header("Referer", request.pageUrl)

        if (cookies.isNotBlank()) {
            builder.header("Cookie", cookies)
        }

        val call = client.newCall(builder.build())
        currentCall = call
        val response = call.execute()
        return response.use {
            if (!it.isSuccessful) {
                throw IOException("Uptodown HTTP ${it.code}")
            }
            val finalUrl = it.request.url.toString()
            if (!finalUrl.isUptodownDownloadUrl()) {
                throw IOException(getString(R.string.uptodown_download_invalid_url))
            }
            if (finalUrl.isUptodownStoreInstaller() && !request.packageName.contains("uptodown", ignoreCase = true)) {
                throw IOException(getString(R.string.uptodown_download_invalid_url))
            }
            val body = it.body ?: throw IOException("Empty Uptodown response")
            val total = body.contentLength().takeIf { length -> length > 0L } ?: 0L
            file.outputStream().use { output ->
                body.byteStream().use { input ->
                    copyToFile(input, output, total)
                }
            }
            if (file.length() == 0L) {
                throw IOException("Empty Uptodown file")
            }
            installLog.emitProgress(AppInstallProgress(request.installId, file.length(), total))
            file
        }
    }

    private suspend fun copyToFile(input: InputStream, output: OutputStream, total: Long): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var copied = 0L
        var lastUiProgress = -1

        while (true) {
            currentCoroutineContext().ensureActive()
            val read = input.read(buffer)
            if (read < 0) break

            output.write(buffer, 0, read)
            copied += read
            installLog.emitProgress(AppInstallProgress(request.installId, copied, total))

            if (total > 0L) {
                val uiProgress = ((copied * 100L) / total).toInt().coerceIn(0, 100)
                if (uiProgress != lastUiProgress) {
                    lastUiProgress = uiProgress
                    withContext(Dispatchers.Main) {
                        progressBar?.isIndeterminate = false
                        progressBar?.progress = uiProgress
                    }
                }
            }
        }
        return copied
    }

    private fun validateDownloadedFile(file: File, extension: String) {
        val expectedPackageName = request.expectedPackageName?.takeIf { it.isNotBlank() } ?: request.packageName
        request.sha256?.takeIf { it.isNotBlank() }?.let { expected ->
            val actual = file.sha256()
            if (!actual.equals(expected, ignoreCase = true)) {
                throw IOException("Uptodown SHA-256 mismatch")
            }
        }

        if (extension.isSplitPackage()) {
            validateSplitPackage(file, expectedPackageName)
        } else {
            installer.validateApkFile(file, expectedPackageName, null, 0L)
        }
    }

    private fun validateSplitPackage(file: File, expectedPackageName: String) {
        ZipFile(file).use { zip ->
            val apks = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".apk", ignoreCase = true) }
                .sortedWith(compareBy({ !it.name.substringAfterLast('/').equals("base.apk", ignoreCase = true) }, { it.name.length }))
                .toList()
            if (apks.isEmpty()) {
                throw IOException("Uptodown split package has no APK entries")
            }

            var lastError: Throwable? = null
            for (entry in apks) {
                val tempApk = File(cacheDir, "uptodown_${UUID.randomUUID()}.apk")
                try {
                    zip.getInputStream(entry).use { input ->
                        tempApk.outputStream().use { output -> input.copyTo(output) }
                    }
                    runCatching {
                        installer.validateApkFile(tempApk, expectedPackageName, null, 0L)
                    }.onSuccess {
                        return
                    }.onFailure {
                        lastError = it
                    }
                } finally {
                    tempApk.delete()
                }
            }
            throw IOException(lastError?.message ?: "Uptodown split package mismatch")
        }
    }

    private suspend fun saveDownloadedFile(
        file: File,
        extension: String,
        url: String,
        contentDisposition: String?,
        mimeType: String?
    ) {
        withContext(Dispatchers.Main) {
            statusText?.setText(R.string.uptodown_download_saving)
            progressBar?.isIndeterminate = true
        }

        val fileName = request.suggestedFileName
            ?.sanitizeFileName()
            ?.ensureExtension(extension)
            ?: URLUtil.guessFileName(url, contentDisposition, mimeType)
                .sanitizeFileName()
                .ensureExtension(extension)
        val ok = downloadStorage.save(
            fileName,
            extension.mimeType(),
            file.inputStream(),
            request.installId,
            installLog,
            file.length()
        )
        file.delete()

        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@UptodownDownloadActivity,
                if (ok) getString(R.string.uptodown_download_saved) else getString(R.string.uptodown_download_failed),
                Toast.LENGTH_LONG
            ).show()
            finish()
        }
    }

    private suspend fun installDownloadedFile(file: File, extension: String) {
        withContext(Dispatchers.Main) {
            statusText?.setText(R.string.uptodown_download_installing)
            progressBar?.isIndeterminate = true
        }

        when (prefs.installMode.get()) {
            1 -> {
                val success = if (extension.isSplitPackage()) {
                    installer.rootInstallXapk(file)
                } else {
                    installer.rootInstall(file)
                }
                installLog.emitStatus(AppInstallStatus(success, request.installId, true))
                withContext(Dispatchers.Main) { finish() }
            }
            2 -> {
                if (!installer.isShizukuAvailable()) {
                    throw IOException(getString(R.string.shizuku_not_running))
                }
                file.inputStream().use { input ->
                    if (extension.isSplitPackage()) {
                        installer.shizukuInstallXapk(request.installId, request.packageName, input, file.length())
                    } else {
                        installer.shizukuInstall(request.installId, request.packageName, input, file.length())
                    }
                }
                file.delete()
                installLog.emitStatus(AppInstallStatus(true, request.installId, true))
                withContext(Dispatchers.Main) { finish() }
            }
            else -> {
                if (!installer.checkPermission()) {
                    file.delete()
                    withContext(Dispatchers.Main) { finish() }
                    return
                }
                file.inputStream().use { input ->
                    if (extension.isSplitPackage()) {
                        installer.installXapk(request.installId, request.packageName, input, file.length())
                    } else {
                        installer.install(request.installId, request.packageName, input, file.length())
                    }
                }
                file.delete()
                withContext(Dispatchers.Main) { finish() }
            }
        }
    }

    private fun resolveExtension(url: String, contentDisposition: String?, mimeType: String?): String {
        val text = "$url $contentDisposition $mimeType".lowercase(Locale.ROOT)
        return when {
            request.isXapk || ".xapk" in text || "xapk" in text -> "xapk"
            ".apks" in text || "apks" in text -> "apks"
            else -> "apk"
        }
    }

    private fun String.isSplitPackage(): Boolean =
        equals("xapk", ignoreCase = true) || equals("apks", ignoreCase = true)

    private fun String.mimeType(): String = when (lowercase(Locale.ROOT)) {
        "xapk" -> "application/vnd.android.xapk"
        "apks" -> "application/vnd.android.apks"
        else -> "application/vnd.android.package-archive"
    }

    private fun String.isUptodownDownloadUrl(): Boolean {
        val parsed = runCatching { Uri.parse(this) }.getOrNull() ?: return false
        val scheme = parsed.scheme?.lowercase(Locale.ROOT)
        if (scheme != "https" && scheme != "http") return false
        val host = parsed.host?.lowercase(Locale.ROOT) ?: return false
        val path = parsed.encodedPath?.lowercase(Locale.ROOT).orEmpty()
        val trustedHost = host == "dw.uptodown.com" || host == "dw.uptodown.net"
        return trustedHost && (path.startsWith("/dwn/") || path.endsWith(".apk") || path.endsWith(".xapk") || path.endsWith(".apks"))
    }

    private fun String.isUptodownStoreInstaller(): Boolean =
        runCatching { Uri.parse(this).lastPathSegment.orEmpty() }
            .getOrDefault("")
            .startsWith("uptodown-app-store-", ignoreCase = true)

    private fun String.sanitizeFileName(): String =
        replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
            .trim('_')
            .ifBlank { "uptodown_download" }

    private fun String.ensureExtension(extension: String): String {
        val cleanExtension = extension.lowercase(Locale.ROOT)
        val lower = lowercase(Locale.ROOT)
        if (lower.endsWith(".$cleanExtension")) return this
        val base = when {
            lower.endsWith(".apk") || lower.endsWith(".xapk") || lower.endsWith(".apks") -> substringBeforeLast('.')
            else -> this
        }
        return "$base.$cleanExtension"
    }

    private fun File.sha256(): String {
        val digest = MessageDigest.getInstance("SHA-256")
        inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private data class BrowserRequest(
        val installId: Int,
        val packageName: String,
        val name: String,
        val version: String,
        val pageUrl: String,
        val mode: Mode,
        val expectedPackageName: String?,
        val sha256: String?,
        val suggestedFileName: String?,
        val isXapk: Boolean
    ) {
        companion object {
            fun from(intent: Intent): BrowserRequest? {
                val mode = runCatching {
                    Mode.valueOf(intent.getStringExtra(EXTRA_MODE).orEmpty())
                }.getOrDefault(Mode.Install)
                val installId = intent.getIntExtra(EXTRA_INSTALL_ID, 0)
                val packageName = intent.getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf { it.isNotBlank() }
                    ?: return null
                val pageUrl = intent.getStringExtra(EXTRA_PAGE_URL)?.takeIf { it.isNotBlank() }
                    ?: return null

                return BrowserRequest(
                    installId = installId,
                    packageName = packageName,
                    name = intent.getStringExtra(EXTRA_NAME).orEmpty(),
                    version = intent.getStringExtra(EXTRA_VERSION).orEmpty(),
                    pageUrl = pageUrl,
                    mode = mode,
                    expectedPackageName = intent.getStringExtra(EXTRA_EXPECTED_PACKAGE),
                    sha256 = intent.getStringExtra(EXTRA_SHA256),
                    suggestedFileName = intent.getStringExtra(EXTRA_FILE_NAME),
                    isXapk = intent.getBooleanExtra(EXTRA_IS_XAPK, false)
                )
            }
        }
    }

    companion object {
        private const val USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/121 Mobile Safari/537.36"
        private const val UPTODOWN_DOWNLOAD_BASE = "https://dw.uptodown.com/dwn/"
        private const val DOWNLOAD_BRIDGE_NAME = "APKUpdaterOSSUptodown"
        private const val AUTO_CLICK_DELAY_MS = 650L
        private const val EXTRA_INSTALL_ID = "install_id"
        private const val EXTRA_PACKAGE_NAME = "package_name"
        private const val EXTRA_NAME = "name"
        private const val EXTRA_VERSION = "version"
        private const val EXTRA_PAGE_URL = "page_url"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_EXPECTED_PACKAGE = "expected_package"
        private const val EXTRA_SHA256 = "sha256"
        private const val EXTRA_FILE_NAME = "file_name"
        private const val EXTRA_IS_XAPK = "is_xapk"

        fun intent(context: Context, update: AppUpdate, mode: Mode): Intent {
            val link = update.link as Link.BrowserDownload
            return Intent(context, UptodownDownloadActivity::class.java).apply {
                putExtra(EXTRA_INSTALL_ID, update.id)
                putExtra(EXTRA_PACKAGE_NAME, update.packageName)
                putExtra(EXTRA_NAME, update.name)
                putExtra(EXTRA_VERSION, update.version)
                putExtra(EXTRA_PAGE_URL, link.pageUrl)
                putExtra(EXTRA_MODE, mode.name)
                putExtra(EXTRA_EXPECTED_PACKAGE, link.expectedPackageName)
                putExtra(EXTRA_SHA256, link.sha256)
                putExtra(EXTRA_FILE_NAME, link.suggestedFileName)
                putExtra(EXTRA_IS_XAPK, link.isXapk)
            }
        }
    }
}
