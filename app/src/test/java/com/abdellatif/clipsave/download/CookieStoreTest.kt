package com.abdellatif.clipsave.download

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CookieStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun readsStandardAndHttpOnlyNetscapeCookies() {
        val file = temporaryFolder.newFile("cookies.txt").apply {
            writeText(
                """
                # Netscape HTTP Cookie File
                .example.com	TRUE	/	TRUE	1893456000	session	abc
                #HttpOnly_.youtube.com	TRUE	/	TRUE	1893456000	SID	secret
                www.example.com	FALSE	/watch	FALSE	0	preference	dark
                """.trimIndent()
            )
        }

        val status = CookieStore.inspect(file)

        assertTrue(status.configured)
        assertEquals(3, status.cookieCount)
        assertEquals(3, status.domainCount)
        assertTrue(status.sizeBytes > 0)
    }

    @Test
    fun ignoresCommentsAndMalformedRows() {
        val file = temporaryFolder.newFile("cookies.txt").apply {
            writeText(
                """
                # Netscape HTTP Cookie File
                not-a-cookie
                example.com	MAYBE	/	TRUE	soon	name	value
                """.trimIndent()
            )
        }

        val status = CookieStore.inspect(file)

        assertFalse(status.configured)
        assertEquals(0, status.cookieCount)
        assertEquals(0, status.domainCount)
    }

    @Test
    fun missingFileIsNotConfigured() {
        val status = CookieStore.inspect(File(temporaryFolder.root, "missing.txt"))

        assertFalse(status.configured)
        assertEquals(0, status.sizeBytes)
    }
}
