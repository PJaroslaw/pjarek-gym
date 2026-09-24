package com.pjarek.gym

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class LongPasswordEncoderTest {
    private val encoder = LongPasswordEncoder()

    @Test
    fun `long passwords are stored without bcrypt length truncation`() {
        val first = "a".repeat(100) + "first"
        val second = "a".repeat(100) + "other"
        val hash = encoder.encode(first)

        assertTrue(encoder.matches(first, hash))
        assertTrue(!encoder.matches(second, hash))
    }
}
