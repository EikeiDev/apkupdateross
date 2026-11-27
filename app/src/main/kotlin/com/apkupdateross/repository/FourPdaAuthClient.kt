package com.apkupdateross.repository

import com.apkupdateross.data.fourpda.FourPdaAuthChallenge
import com.apkupdateross.data.fourpda.FourPdaLoginResult
import com.apkupdateross.util.AppUserAgent
import com.apkupdateross.util.FourPdaSession
import com.apkupdateross.util.extractFourPdaMemberId
import com.apkupdateross.util.hasValidFourPdaSession
import com.apkupdateross.util.mergeCookieHeaders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.Charset

class FourPdaAuthClient(
    client: OkHttpClient,
    private val session: FourPdaSession
) {
    private val authClient = client.newBuilder()
        .cache(null)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    suspend fun loadChallenge(): Result<FourPdaAuthChallenge> = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(AUTH_URL)
                .header("User-Agent", AppUserAgent.value)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
                .header("Referer", FORUM_REFERER)
                .get()
                .build()

            val (html, cookies) = authClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("4PDA auth page returned HTTP ${response.code}")
                }
                val bodyBytes = response.body?.bytes()
                    ?: throw IllegalStateException("Empty auth page response")
                val charset = response.body?.contentType()?.charset(WINDOWS_1251) ?: WINDOWS_1251
                val setCookies = extractCookiePairsFromSetCookie(response.headers("Set-Cookie"))
                String(bodyBytes, charset) to setCookies
            }

            val parsed = parseFourPdaAuthChallenge(html)
                ?: throw IllegalStateException("Could not parse 4PDA login captcha")

            val imgRequest = Request.Builder()
                .url(parsed.captchaImageUrl)
                .header("User-Agent", AppUserAgent.value)
                .header("Referer", AUTH_URL)
                .apply { if (cookies.isNotBlank()) header("Cookie", cookies) }
                .get()
                .build()

            val imageBytes = authClient.newCall(imgRequest).execute().use { imgResponse ->
                if (!imgResponse.isSuccessful) {
                    throw IllegalStateException("Could not load captcha image (HTTP ${imgResponse.code})")
                }
                imgResponse.body?.bytes() ?: throw IllegalStateException("Empty captcha image")
            }

            parsed.copy(
                captchaImageBytes = imageBytes,
                sessionCookies = cookies
            )
        }
    }

    suspend fun submitLogin(
        challenge: FourPdaAuthChallenge,
        login: String,
        password: String,
        captchaAnswer: String
    ): FourPdaLoginResult = withContext(Dispatchers.IO) {
        val cleanLogin = login.trim()
        val cleanCaptcha = captchaAnswer.trim()
        if (cleanLogin.isEmpty() || password.isEmpty() || cleanCaptcha.isEmpty()) {
            return@withContext FourPdaLoginResult.Failure("Fill in login, password, and captcha")
        }

        val formEncoded = buildString {
            append("return=").append(encode1251("https://4pda.to/"))
            append("&login=").append(encode1251(cleanLogin))
            append("&password=").append(encode1251(password))
            append("&remember=1")
            append("&captcha-time=").append(encode1251(challenge.captchaTime))
            append("&captcha-sig=").append(encode1251(challenge.captchaSig))
            append("&captcha=").append(encode1251(cleanCaptcha))
        }

        val request = Request.Builder()
            .url(AUTH_URL)
            .header("User-Agent", AppUserAgent.value)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.5")
            .header("Origin", "https://4pda.to")
            .header("Referer", AUTH_URL)
            .apply {
                if (challenge.sessionCookies.isNotBlank()) {
                    header("Cookie", challenge.sessionCookies)
                }
            }
            .post(formEncoded.toRequestBody(FORM_MEDIA_TYPE))
            .build()

        runCatching {
            authClient.newCall(request).execute().use { response ->
                val returnedCookies = extractCookiePairsFromSetCookie(response.headers("Set-Cookie"))
                val merged = mergeCookieHeaders(challenge.sessionCookies, returnedCookies)
                if (hasValidFourPdaSession(merged)) {
                    val memberId = extractFourPdaMemberId(merged).orEmpty()
                    session.saveSession(merged, memberId)
                    return@use FourPdaLoginResult.Success(memberId)
                }

                val bodyBytes = response.body?.bytes() ?: ByteArray(0)
                val charset = response.body?.contentType()?.charset(WINDOWS_1251) ?: WINDOWS_1251
                val html = String(bodyBytes, charset)
                val errorText = parseFourPdaAuthErrors(html)
                    .ifBlank { "Sign-in failed (HTTP ${response.code})" }
                val nextChallenge = loadChallenge().getOrNull()
                FourPdaLoginResult.Failure(errorText, nextChallenge)
            }
        }.getOrElse { error ->
            FourPdaLoginResult.Failure(
                message = error.message ?: "Network error",
                refreshedChallenge = loadChallenge().getOrNull()
            )
        }
    }

    private fun encode1251(value: String): String =
        URLEncoder.encode(value, WINDOWS_1251.name())

    companion object {
        private const val AUTH_URL = "https://4pda.to/forum/index.php?act=auth"
        private const val FORUM_REFERER = "https://4pda.to/forum/"
        private val WINDOWS_1251: Charset = Charset.forName("windows-1251")
        private val FORM_MEDIA_TYPE = "application/x-www-form-urlencoded; charset=windows-1251".toMediaType()
    }
}

internal fun parseFourPdaAuthChallenge(html: String): FourPdaAuthChallenge? {
    val doc = Jsoup.parse(html, "https://4pda.to/forum/")
    val form = doc.selectFirst("form#auth") ?: doc
    val time = form.selectFirst("input[name=captcha-time]")?.attr("value")?.trim().orEmpty()
    val sig = form.selectFirst("input[name=captcha-sig]")?.attr("value")?.trim().orEmpty()
    val imgEl = form.selectFirst("div.captcha img[src]")
    val rawSrc = imgEl?.absUrl("src")?.ifBlank { imgEl.attr("src") }?.trim().orEmpty()
    if (time.isEmpty() || sig.isEmpty() || rawSrc.isEmpty()) return null

    val normalizedUrl = when {
        rawSrc.startsWith("https://", ignoreCase = true) -> rawSrc
        rawSrc.startsWith("http://4pda.to/", ignoreCase = true) -> "https://" + rawSrc.removePrefix("http://")
        rawSrc.startsWith("//") -> "https:$rawSrc"
        rawSrc.startsWith("/") -> "https://4pda.to$rawSrc"
        else -> rawSrc
    }

    return FourPdaAuthChallenge(
        captchaTime = time,
        captchaSig = sig,
        captchaImageUrl = normalizedUrl
    )
}

internal fun parseFourPdaAuthErrors(html: String): String {
    val doc = Jsoup.parse(html, "https://4pda.to/forum/")
    val errors = doc.select("ul.errors-list li, div.errorwrap p, div.error-box, .errors-list")
        .map { it.text().trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    return errors.joinToString("; ")
}

internal fun extractCookiePairsFromSetCookie(setCookieHeaders: List<String>): String {
    val map = LinkedHashMap<String, String>()
    for (header in setCookieHeaders) {
        val pair = header.substringBefore(';').trim()
        val eq = pair.indexOf('=')
        if (eq <= 0) continue
        val name = pair.substring(0, eq).trim()
        val value = pair.substring(eq + 1).trim()
        if (name.isNotEmpty()) {
            map[name] = value
        }
    }
    return map.entries.joinToString("; ") { "${it.key}=${it.value}" }
}
