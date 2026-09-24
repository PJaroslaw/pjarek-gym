package com.pjarek.gym

import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus

@Controller
class AccountController(private val jdbc: JdbcTemplate, private val encoder: PasswordEncoder, private val access: UserAccess) {
    @GetMapping("/login") fun login() = "login"

    @RequestMapping("/forbidden")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun forbidden() = "forbidden"

    @PostMapping("/settings/weight-unit")
    fun updateWeightUnit(@AuthenticationPrincipal user: UserDetails, @RequestParam unit: String): String {
        require(unit in listOf("KG", "LB"))
        jdbc.update("UPDATE app_user SET weight_unit=? WHERE id=?", unit, access.userId(user))
        return "redirect:/settings"
    }

    @PostMapping("/settings/theme-mode")
    fun updateThemeMode(@AuthenticationPrincipal user: UserDetails, @RequestParam mode: String): String {
        require(mode in listOf("LIGHT", "DARK", "SYSTEM"))
        jdbc.update("UPDATE app_user SET theme_mode=? WHERE id=?", mode, access.userId(user))
        return "redirect:/settings"
    }

    @PostMapping("/settings/password")
    fun changePassword(@AuthenticationPrincipal user: UserDetails, @RequestParam currentPassword: String, @RequestParam newPassword: String): String {
        val hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String::class.java, access.userId(user))!!
        require(encoder.matches(currentPassword, hash)) { "Current password is not correct" }
        require(newPassword.isNotEmpty()) { "Enter a new password." }
        jdbc.update("UPDATE app_user SET password_hash=?,must_change_password=FALSE WHERE username=?", encoder.encode(newPassword), user.username)
        return "redirect:/settings?passwordChanged"
    }

    @GetMapping("/change-password")
    fun requiredPasswordChange(@AuthenticationPrincipal user: UserDetails): String {
        val required = jdbc.queryForObject("SELECT must_change_password FROM app_user WHERE id=?", Boolean::class.java, access.userId(user))!!
        return if (required) "change-password" else "redirect:/"
    }

    @PostMapping("/change-password")
    fun saveRequiredPasswordChange(@AuthenticationPrincipal user: UserDetails, @RequestParam newPassword: String): String {
        require(newPassword.isNotEmpty()) { "Enter a new password." }
        val updated = jdbc.update(
            "UPDATE app_user SET password_hash=?,must_change_password=FALSE WHERE id=? AND must_change_password=TRUE",
            encoder.encode(newPassword), access.userId(user)
        )
        require(updated == 1) { "A password change is not currently required." }
        return "redirect:/"
    }
}
