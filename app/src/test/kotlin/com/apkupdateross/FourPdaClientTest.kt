package com.apkupdateross

import com.apkupdateross.data.fourpda.FourPdaBuildPreference
import com.apkupdateross.data.fourpda.FourPdaCrawlerAttachment
import com.apkupdateross.data.fourpda.FourPdaCrawlerReleaseLink
import com.apkupdateross.data.ui.ReleaseType
import com.apkupdateross.repository.FourPdaRepository
import com.apkupdateross.repository.buildFourPdaDownloadChoices
import com.apkupdateross.repository.cleanFourPdaPostNotes
import com.apkupdateross.repository.detectFourPdaModFromPostNotes
import com.apkupdateross.repository.detectFourPdaReleaseType
import com.apkupdateross.repository.extractCookiePairsFromSetCookie
import com.apkupdateross.repository.extractFourPdaFileVersion
import com.apkupdateross.repository.isFourPdaTopicHeader
import com.apkupdateross.repository.isFourPdaUpdate
import com.apkupdateross.repository.normalizeCrawlerBaseUrl
import com.apkupdateross.repository.parseFourPdaAuthChallenge
import com.apkupdateross.repository.parseFourPdaAuthErrors
import com.apkupdateross.repository.parseFourPdaPostHtml
import com.apkupdateross.repository.parseFourPdaTopicId
import com.apkupdateross.repository.resolveAttachmentMetadata
import com.apkupdateross.repository.selectFourPdaCrawlerAttachment
import com.apkupdateross.util.extractFourPdaMemberId
import com.apkupdateross.util.hasValidFourPdaSession
import com.apkupdateross.util.isFourPdaUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FourPdaClientTest {

    @Test
    fun parsesNativeAuthFormAndErrors() {
        val html = """
            <html><body>
              <form id="auth" method="POST" action="https://4pda.to/forum/index.php?act=auth">
                <div class="captcha">
                  <input name="captcha-time" type="hidden" value="1795728750" />
                  <input name="captcha-sig" type="hidden" value="2dd67432d1a725155c8ff7682aa7b617" />
                  <img src="https://4pda.to/s/captcha.gif" />
                </div>
              </form>
            </body></html>
        """.trimIndent()
        val challenge = parseFourPdaAuthChallenge(html)
        assertNotNull(challenge)
        assertEquals("1795728750", challenge?.captchaTime)
        assertEquals("2dd67432d1a725155c8ff7682aa7b617", challenge?.captchaSig)
        assertEquals("https://4pda.to/s/captcha.gif", challenge?.captchaImageUrl)

        val errHtml = """
            <html><body>
              <ul class="errors-list"><li>Неверный код безопасности</li></ul>
            </body></html>
        """.trimIndent()
        assertEquals("Неверный код безопасности", parseFourPdaAuthErrors(errHtml))

        val cookies = extractCookiePairsFromSetCookie(
            listOf(
                "member_id=7835913; path=/; domain=.4pda.to; HttpOnly",
                "pass_hash=abcdef0123456789; path=/; domain=.4pda.to; HttpOnly"
            )
        )
        assertEquals("7835913", extractFourPdaMemberId(cookies))
        assertTrue(hasValidFourPdaSession(cookies))
        assertFalse(hasValidFourPdaSession("member_id=0; pass_hash=deleted"))
    }

    @Test
    fun selectsAttachmentsByPreferenceFilterAndAbi() {
        val attachments = listOf(
            FourPdaCrawlerAttachment(
                attachId = 10,
                fileName = "App_2.0_mod_arm64-v8a.apk",
                downloadUrl = "https://4pda.to/forum/dl/post/10/App_2.0_mod_arm64-v8a.apk",
                postId = 9001L,
                version = "2.0",
                isMod = true,
                abi = "arm64-v8a"
            ),
            FourPdaCrawlerAttachment(
                attachId = 9,
                fileName = "App_2.0_arm64-v8a.apk",
                downloadUrl = "https://4pda.to/forum/dl/post/9/App_2.0_arm64-v8a.apk",
                postId = 9000L,
                version = "2.0",
                isMod = false,
                abi = "arm64-v8a"
            ),
            FourPdaCrawlerAttachment(
                attachId = 8,
                fileName = "App_2.0_armeabi-v7a.apk",
                downloadUrl = "https://4pda.to/forum/dl/post/8/App_2.0_armeabi-v7a.apk",
                postId = 9000L,
                version = "2.0",
                isMod = false,
                abi = "armeabi-v7a"
            )
        )

        val orig = selectFourPdaCrawlerAttachment(attachments, listOf("arm64-v8a"), FourPdaBuildPreference.ORIGINALS)
        assertEquals("App_2.0_arm64-v8a.apk", orig?.fileName)

        // In ASK mode, Updates card also prefers Original first so users see the official version
        val askPrimary = selectFourPdaCrawlerAttachment(attachments, listOf("arm64-v8a"), FourPdaBuildPreference.ASK)
        assertEquals("App_2.0_arm64-v8a.apk", askPrimary?.fileName)

        val mod = selectFourPdaCrawlerAttachment(attachments, listOf("arm64-v8a"), FourPdaBuildPreference.MODS)
        assertEquals("App_2.0_mod_arm64-v8a.apk", mod?.fileName)

        val custom = selectFourPdaCrawlerAttachment(
            attachments,
            listOf("arm64-v8a"),
            FourPdaBuildPreference.ORIGINALS,
            fileFilter = "mod"
        )
        assertEquals("App_2.0_mod_arm64-v8a.apk", custom?.fileName)

        val choices = buildFourPdaDownloadChoices(attachments, listOf("arm64-v8a"), "com.example.app", topicId = 12345L)
        assertEquals(3, choices.size)
        // Strictly sorted from newest (highest attachId) to oldest
        assertEquals("App_2.0_mod_arm64-v8a.apk", choices[0].label)
        assertEquals(9001L, choices[0].postId)
        assertEquals("https://4pda.to/forum/index.php?showtopic=12345&view=findpost&p=9001", choices[0].postUrl)
        assertEquals("App_2.0_arm64-v8a.apk", choices[1].label)
        assertEquals("App_2.0_armeabi-v7a.apk", choices[2].label)

        // Newest uploads always appear first regardless of ABI or original/mod split, capped at 30
        val manyOriginalsAndMods = (1..35).map { idx ->
            FourPdaCrawlerAttachment(
                attachId = 100L - idx,
                fileName = "DeepSeek_2.${40 - idx}.0.apk",
                downloadUrl = "https://4pda.to/forum/dl/post/${100 - idx}/DeepSeek.apk",
                postId = 1000L - idx,
                version = "2.${40 - idx}.0",
                isMod = false
            )
        } + listOf(
            FourPdaCrawlerAttachment(
                attachId = 105L,
                fileName = "DeepSeek_2.39.0_mod.apk",
                downloadUrl = "https://4pda.to/forum/dl/post/105/DeepSeek_mod.apk",
                postId = 1005L,
                version = "2.39.0",
                isMod = true
            )
        )
        val mixedChoices = buildFourPdaDownloadChoices(manyOriginalsAndMods, listOf("arm64-v8a"), "com.deepseek.chat", topicId = 1100303L)
        assertEquals(30, mixedChoices.size)
        assertEquals("DeepSeek_2.39.0_mod.apk", mixedChoices[0].label)
        assertEquals("DeepSeek_2.39.0.apk", mixedChoices[1].label)
        assertEquals("DeepSeek_2.38.0.apk", mixedChoices[2].label)
    }

    @Test
    fun cleansWholePostWithoutAttachmentsAndDetectsTopicHeader() {
        val postHtml = """
            <html><body>
              <div class="post_body" id="post-main-131280476">
                Claude<br/>Последнее обновление программы в шапке: 26.11.2026
              </div>
              <div class="post_body" id="post-main-146224894">
                <b>Тип:</b> Новая версия<br/>
                <b>Версия:</b> 1.261125.19<br/>
                <b>Краткое описание:</b> от 27/11/2026 (Android 8.0+)<br/>
                Список изменений:<br/>
                <ul><li>Squashed some bugs and improved the overall experience.</li></ul>
                <b>universal:</b> <a class="attach-file" href="https://4pda.to/forum/dl/post/36638845/Claude.apks">Claude.apks</a> ( 42.44 МБ )<br/>
                <b>Примечание:</b><br/>
                Ставить через SAI (см. <a href="https://4pda.to">инструкцию</a>).
              </div>
            </body></html>
        """.trimIndent()

        // Extracts ONLY post-main-146224894 without attachments, never falls back to header
        val parsed = parseFourPdaPostHtml(postHtml, 146224894L)
        assertNotNull(parsed)
        assertTrue(parsed!!.contains("Тип: Новая версия"))
        assertTrue(parsed.contains("• Squashed some bugs"))
        assertTrue(parsed.contains("Ставить через SAI (см. инструкцию)."))
        assertFalse(parsed.contains("42.44 МБ"))
        assertFalse(parsed.contains("Claude.apks"))
        assertFalse(parsed.contains("universal:"))

        // Missing post returns null instead of falling back to the topic header!
        assertNull(parseFourPdaPostHtml(postHtml, 999999999L))

        assertTrue(isFourPdaTopicHeader("Claude\nПоследнее обновление программы в шапке:\n26.11.2026"))
        assertFalse(isFourPdaTopicHeader(parsed))

        // Legacy broken-line crawler cache also gets joined cleanly while preserving full post text
        val legacyBroken = """
            Тип:
            Модификация
            Автор мода:
            Balatan
            Особенности мода:
            • Разблокирован Premium
            arm64-v8a:
            ( 84.10 МБ )
        """.trimIndent()
        assertEquals(
            "Тип: Модификация\nАвтор мода: Balatan\nОсобенности мода:\n• Разблокирован Premium",
            cleanFourPdaPostNotes(legacyBroken)
        )

        // Post 'Тип:' classification: Новая версия / Beta -> Original (false), Mod -> MOD (true)
        assertEquals(false, detectFourPdaModFromPostNotes("Тип: Новая версия\nВерсия: 1.0"))
        assertEquals(false, detectFourPdaModFromPostNotes("Тип: Beta\nВерсия: 1.0"))
        assertEquals(false, detectFourPdaModFromPostNotes("Тип: Бета\nВерсия: 1.0"))
        assertEquals(false, detectFourPdaModFromPostNotes("Тип: Альфа\nВерсия: 1.0"))
        assertEquals(true, detectFourPdaModFromPostNotes("Тип: Модификация\nВерсия: 1.0"))
        assertEquals(true, detectFourPdaModFromPostNotes("Тип: Репак / Клон\nВерсия: 1.0"))
        assertEquals(true, detectFourPdaModFromPostNotes("TrueZaller v26.46.7_Morphe (Premium) Unlocked (MicroG)"))
        assertEquals(true, detectFourPdaModFromPostNotes("Попробуйте unlock версию"))
        assertEquals(true, detectFourPdaModFromPostNotes("Тип: Новая версия\nВерсия: 26.41.5\nКраткое описание: Premium by youarefinished"))
        assertNull(detectFourPdaModFromPostNotes("Просто текст без строки типа"))

        // Release type detection from 'Тип: Beta' / 'Бета' / release links
        assertEquals(ReleaseType.Beta, detectFourPdaReleaseType(version = "12.46.1", postNotes = "Тип: Beta\nВерсия: 12.46.1"))
        assertEquals(ReleaseType.Beta, detectFourPdaReleaseType(version = "12.46.1", postNotes = "Тип: Бета\nВерсия: 12.46.1"))
        assertEquals(ReleaseType.Alpha, detectFourPdaReleaseType(version = "12.46.1", postNotes = "Тип: Альфа\nВерсия: 12.46.1"))
        assertEquals(ReleaseType.Beta, detectFourPdaReleaseType(version = "12.46.1", releaseLabel = "Бета от 26/11/2026"))
        assertEquals(ReleaseType.Stable, detectFourPdaReleaseType(version = "12.46.1", postNotes = "Тип: Новая версия\nВерсия: 12.46.1"))

        // Re-extracts proper version from `_v2.10.0(302)` where v1 crawler returned `10.0`
        assertEquals("2.10.0", extractFourPdaFileVersion("DeepSeek_v2.10.0(302)_arm64-v8a.apk"))

        // Cross-referencing releaseLinks and cached post notes marks untagged mod APKs as MOD
        val untaggedAtt = FourPdaCrawlerAttachment(
            attachId = 55L,
            fileName = "DeepSeek_v2.10.0(302)_arm64-v8a.apk",
            downloadUrl = "https://4pda.to/forum/dl/post/55/DeepSeek_v2.10.0.apk",
            postId = 777L,
            version = "10.0",
            isMod = false
        )
        val fromHeader = resolveAttachmentMetadata(
            attachments = listOf(untaggedAtt),
            releaseLinks = listOf(FourPdaCrawlerReleaseLink(postId = 777L, version = "2.10.0 Mod", label = "Mod by Author", isMod = true))
        )
        assertTrue(fromHeader[0].isMod)
        assertEquals("2.10.0", fromHeader[0].version)

        val fromPostCache = resolveAttachmentMetadata(
            attachments = listOf(untaggedAtt),
            releaseLinks = emptyList(),
            postNotesCache = mapOf(777L to "Тип: Модификация\nУдалена реклама")
        )
        assertTrue(fromPostCache[0].isMod)
        assertEquals("2.10.0", fromPostCache[0].version)
    }

    @Test
    fun validatesTopicIdsUrlsAndVersions() {
        assertEquals("https://4pda-api2.notdev.top", FourPdaRepository.DEFAULT_SERVER_URL)
        assertEquals(1035213L, parseFourPdaTopicId("https://4pda.to/forum/index.php?showtopic=1035213&view=findpost&p=1"))
        assertEquals(1035213L, parseFourPdaTopicId("1035213"))
        assertNull(parseFourPdaTopicId("abc"))

        assertEquals("http://4pda-api2.notdev.top:45187", normalizeCrawlerBaseUrl("http://4pda-api2.notdev.top:45187/"))
        assertEquals("https://pda.example.com", normalizeCrawlerBaseUrl("pda.example.com/"))

        assertTrue(isFourPdaUrl("https://4pda.to/forum/dl/post/1/app.apk"))
        assertFalse(isFourPdaUrl("http://4pda.to/forum/dl/post/1/app.apk"))
        assertTrue(isFourPdaUpdate("2.1.0", "2.0.5"))
        assertFalse(isFourPdaUpdate("2.0.0", "2.0.5"))
    }
}
