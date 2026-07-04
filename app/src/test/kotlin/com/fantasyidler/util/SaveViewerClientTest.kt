package com.fantasyidler.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveViewerClientTest {

    @Test
    fun `parseViewerUrl extracts id from full HTTPS URL`() {
        val result = SaveViewerClient.parseViewerUrl(
            "https://if-viewer.elpatron.me/v/AbCdEf1234567890/",
        )
        assertTrue(result.isSuccess)
        val target = result.getOrThrow()
        assertEquals("https://if-viewer.elpatron.me", target.baseUrl)
        assertEquals("AbCdEf1234567890", target.viewerId)
        assertEquals("https://if-viewer.elpatron.me/v/AbCdEf1234567890/", target.viewerUrl)
    }

    @Test
    fun `parseViewerUrl adds HTTPS scheme when missing`() {
        val result = SaveViewerClient.parseViewerUrl(
            "if-viewer.elpatron.me/v/AbCdEf1234567890",
        )
        assertTrue(result.isSuccess)
        assertEquals("https://if-viewer.elpatron.me", result.getOrThrow().baseUrl)
    }

    @Test
    fun `parseViewerUrl accepts custom port`() {
        val result = SaveViewerClient.parseViewerUrl(
            "http://127.0.0.1:5000/v/AbCdEf1234567890/",
        )
        assertTrue(result.isSuccess)
        assertEquals("http://127.0.0.1:5000", result.getOrThrow().baseUrl)
    }

    @Test
    fun `parseViewerUrl rejects empty input`() {
        assertTrue(SaveViewerClient.parseViewerUrl("").isFailure)
        assertTrue(SaveViewerClient.parseViewerUrl("   ").isFailure)
    }

    @Test
    fun `parseViewerUrl rejects URL without viewer path`() {
        assertTrue(SaveViewerClient.parseViewerUrl("https://example.com/").isFailure)
    }

    @Test
    fun `parseViewerUrl rejects viewer id that is too short`() {
        assertFalse(SaveViewerClient.parseViewerUrl("https://example.com/v/tooshort/").isSuccess)
    }
}
