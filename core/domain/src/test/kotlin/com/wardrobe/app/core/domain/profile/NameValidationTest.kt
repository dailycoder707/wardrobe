package com.wardrobe.app.core.domain.profile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NameValidationTest {
    @Test
    fun `a plain name is valid and unchanged`() {
        val result = validateName("Palak")

        assertEquals(NameValidationResult.Valid("Palak"), result)
    }

    @Test
    fun `leading and trailing whitespace is trimmed`() {
        val result = validateName("   Palak   ")

        assertEquals(NameValidationResult.Valid("Palak"), result)
    }

    @Test
    fun `an empty string is rejected as blank`() {
        val result = validateName("")

        assertEquals(NameValidationResult.Blank, result)
    }

    @Test
    fun `whitespace-only input is rejected as blank after trimming`() {
        val result = validateName("     ")

        assertEquals(NameValidationResult.Blank, result)
    }

    @Test
    fun `a name at exactly the maximum length is valid`() {
        val name = "A".repeat(MAX_DISPLAY_NAME_LENGTH)

        val result = validateName(name)

        assertEquals(NameValidationResult.Valid(name), result)
    }

    @Test
    fun `a name one character over the maximum length is rejected`() {
        val name = "A".repeat(MAX_DISPLAY_NAME_LENGTH + 1)

        val result = validateName(name)

        assertEquals(NameValidationResult.TooLong(MAX_DISPLAY_NAME_LENGTH), result)
    }

    @Test
    fun `Unicode names are preserved exactly, not transliterated`() {
        val unicodeName = "Élodie 🌺 李雷"

        val result = validateName(unicodeName)

        assertEquals(NameValidationResult.Valid(unicodeName), result)
    }

    @Test
    fun `a Unicode name over the length limit is still rejected by code-unit length`() {
        val longUnicodeName = "李".repeat(MAX_DISPLAY_NAME_LENGTH + 1)

        val result = validateName(longUnicodeName)

        assertTrue(result is NameValidationResult.TooLong)
    }

    @Test
    fun `errorMessageOrNull is null for a valid result`() {
        assertEquals(null, NameValidationResult.Valid("Palak").errorMessageOrNull())
    }

    @Test
    fun `errorMessageOrNull describes a blank name`() {
        assertEquals("Name can't be empty.", NameValidationResult.Blank.errorMessageOrNull())
    }

    @Test
    fun `errorMessageOrNull describes a too-long name with the actual limit`() {
        assertEquals(
            "Name must be $MAX_DISPLAY_NAME_LENGTH characters or fewer.",
            NameValidationResult.TooLong(MAX_DISPLAY_NAME_LENGTH).errorMessageOrNull(),
        )
    }
}
