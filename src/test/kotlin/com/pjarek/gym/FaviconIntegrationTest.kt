package com.pjarek.gym

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class FaviconIntegrationTest : IntegrationTestSupport() {
    @Test
    fun `favicon is available without signing in and is an ico file`() {
        val response = mockMvc.perform(get("/favicon.ico"))
            .andExpect(status().isOk)
            .andReturn()
            .response
        val bytes = response.contentAsByteArray

        assertTrue(bytes.isNotEmpty())
        assertArrayEquals(byteArrayOf(0, 0, 1, 0), bytes.copyOfRange(0, 4))
    }
}
