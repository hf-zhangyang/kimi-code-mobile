package com.kimicode.pocket

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PairingValidatorTest {
    @Test
    fun acceptsOfficialStyleHttpsUrl() {
        val url = "https://remote.example.com/connect?device=abc#/session"
        assertEquals(url, PairingValidator.normalize(url))
        assertEquals("remote.example.com", PairingValidator.displayHost(url))
    }

    @Test
    fun trimsSurroundingWhitespace() {
        assertEquals(
            "https://example.com/pair",
            PairingValidator.normalize("  https://example.com/pair\n")
        )
    }

    @Test
    fun rejectsInsecureOrInvalidValues() {
        assertNull(PairingValidator.normalize(null))
        assertNull(PairingValidator.normalize(""))
        assertNull(PairingValidator.normalize("http://example.com/pair"))
        assertNull(PairingValidator.normalize("javascript:alert(1)"))
        assertNull(PairingValidator.normalize("https://user:password@example.com/pair"))
        assertNull(PairingValidator.normalize("not a url"))
    }

    @Test
    fun rejectsUnreasonablyLongInput() {
        assertNull(PairingValidator.normalize("https://example.com/" + "a".repeat(9000)))
    }
}
