package com.kamboji.quiver.screenshots.search

import com.kamboji.quiver.screenshots.data.db.ScreenshotText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotSearchTest {

    private fun row(id: Long, text: String, at: Long = id) =
        ScreenshotText(mediaId = id, uri = "content://$id", fileName = "s$id.png", text = text, capturedAt = at)

    @Test
    fun `tokenize lowercases, splits and drops blanks`() {
        assertEquals(listOf("wifi", "password"), ScreenshotSearch.tokenize("  WiFi   Password "))
        assertTrue(ScreenshotSearch.tokenize("   ").isEmpty())
    }

    @Test
    fun `likeArg uses the longest token wrapped in wildcards`() {
        assertEquals("%password%", ScreenshotSearch.likeArg("wifi password"))
        assertEquals("%", ScreenshotSearch.likeArg("   "))
    }

    @Test
    fun `rank scores by distinct token hits`() {
        val rows = listOf(
            row(1, "Cafe WiFi\nNetwork: Cafe5G\nPassword: bean-4417"),
            row(2, "Meeting notes: discuss the budget"),
            row(3, "Home wifi password is hunter2"),
        )
        val hits = ScreenshotSearch.rank("wifi password", rows)
        // rows 1 and 3 both contain both tokens; row 2 matches neither.
        assertEquals(2, hits.size)
        assertTrue(hits.all { it.score == 2 })
        assertEquals(setOf(1L, 3L), hits.map { it.row.mediaId }.toSet())
    }

    @Test
    fun `rank breaks ties by recency`() {
        val rows = listOf(
            row(1, "otp is 1234", at = 100),
            row(2, "otp is 5678", at = 200),
        )
        val hits = ScreenshotSearch.rank("otp", rows)
        assertEquals(2L, hits.first().row.mediaId) // newer first
    }

    @Test
    fun `partial matches still rank, higher score wins`() {
        val rows = listOf(
            row(1, "wifi only here"),
            row(2, "wifi and password both here"),
        )
        val hits = ScreenshotSearch.rank("wifi password", rows)
        assertEquals(2L, hits.first().row.mediaId) // 2 tokens beats 1
        assertEquals(2, hits.size)
    }

    @Test
    fun `empty query returns nothing`() {
        assertTrue(ScreenshotSearch.rank("   ", listOf(row(1, "anything"))).isEmpty())
    }

    @Test
    fun `snippet centers on the match with ellipses`() {
        val text = "The quick brown fox jumps over the lazy dog and then the wifi password is shown at the very end here"
        val snip = ScreenshotSearch.snippet(text, listOf("password"))
        assertTrue("password" in snip)
        assertTrue(snip.startsWith("…"))
    }

    @Test
    fun `snippet from the start has no leading ellipsis`() {
        val snip = ScreenshotSearch.snippet("Password: abc123 shown right at the start", listOf("password"))
        assertTrue(snip.startsWith("Password"))
    }
}
