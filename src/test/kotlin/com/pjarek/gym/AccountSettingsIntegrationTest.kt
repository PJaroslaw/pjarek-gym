package com.pjarek.gym

import org.hamcrest.Matchers.containsString
import org.hamcrest.Matchers.not
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class AccountSettingsIntegrationTest : IntegrationTestSupport() {
    @Test
    fun `temporary password forces an unrestricted-length password change after login`() {
        val temporaryPassword = "LongPassphrase".repeat(24)
        mockMvc.perform(post("/admin/users")
            .param("username", "temporary-password-test")
            .param("password", temporaryPassword)
            .param("role", "USER")
            .param("temporaryPassword", "true")
            .with(user("integration-admin").roles("ADMIN"))
            .with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/admin/users"))

        var userRow = jdbc.queryForMap("SELECT password_hash,must_change_password FROM app_user WHERE username='temporary-password-test'")
        assertEquals(true, userRow["must_change_password"])
        assertTrue(passwordEncoder.matches(temporaryPassword, userRow["password_hash"] as String))
        mockMvc.perform(get("/admin/users").with(user("integration-admin").roles("ADMIN")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Temporary password · change required")))
        val temporaryUserId = jdbc.queryForObject("SELECT id FROM app_user WHERE username='temporary-password-test'", Long::class.java)!!
        mockMvc.perform(get("/admin/users/$temporaryUserId").with(user("integration-admin").roles("ADMIN")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Password change required")))

        mockMvc.perform(post("/login").param("username", "temporary-password-test").param("password", temporaryPassword).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/change-password"))
        mockMvc.perform(get("/").with(user("temporary-password-test")))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/change-password"))
        mockMvc.perform(get("/change-password").with(user("temporary-password-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Set a new password")))

        mockMvc.perform(post("/change-password").param("newPassword", "")
            .header("Referer", "http://localhost/change-password")
            .with(user("temporary-password-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/change-password"))
        assertEquals(true, jdbc.queryForObject("SELECT must_change_password FROM app_user WHERE username='temporary-password-test'", Boolean::class.java))

        mockMvc.perform(post("/change-password").param("newPassword", "x").with(user("temporary-password-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/"))
        userRow = jdbc.queryForMap("SELECT password_hash,must_change_password FROM app_user WHERE username='temporary-password-test'")
        assertEquals(false, userRow["must_change_password"])
        assertTrue(passwordEncoder.matches("x", userRow["password_hash"] as String))
        mockMvc.perform(get("/").with(user("temporary-password-test"))).andExpect(status().isOk)
    }

    @Test
    fun `personal preferences and password controls live on the settings page`() {
        mockMvc.perform(get("/").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(not(containsString("Color mode"))))
            .andExpect(content().string(not(containsString("Weight display"))))
            .andExpect(content().string(not(containsString("Change password"))))

        mockMvc.perform(get("/settings").with(user("exercise-search-test")))
            .andExpect(status().isOk)
            .andExpect(content().string(containsString("Weight display")))
            .andExpect(content().string(containsString("Color mode")))
            .andExpect(content().string(containsString("Change password")))
            .andExpect(content().string(containsString("Settings")))

        mockMvc.perform(post("/settings/weight-unit").param("unit", "LB").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/settings"))
        assertEquals("LB", jdbc.queryForObject("SELECT weight_unit FROM app_user WHERE username='exercise-search-test'", String::class.java))

        mockMvc.perform(post("/settings/theme-mode").param("mode", "DARK").with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/settings"))
        assertEquals("DARK", jdbc.queryForObject("SELECT theme_mode FROM app_user WHERE username='exercise-search-test'", String::class.java))

        jdbc.update("UPDATE app_user SET password_hash=? WHERE username='exercise-search-test'", passwordEncoder.encode("old-pass"))
        mockMvc.perform(post("/settings/password").param("currentPassword", "old-pass").param("newPassword", "x")
            .with(user("exercise-search-test")).with(csrf()))
            .andExpect(status().is3xxRedirection)
            .andExpect(redirectedUrl("/settings?passwordChanged"))
        val newHash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE username='exercise-search-test'", String::class.java)!!
        assertTrue(passwordEncoder.matches("x", newHash))
    }
}
