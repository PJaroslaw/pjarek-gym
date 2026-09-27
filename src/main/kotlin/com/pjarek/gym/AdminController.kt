package com.pjarek.gym

import java.util.UUID
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class AdminController(private val jdbc: JdbcTemplate, private val encoder: PasswordEncoder, private val access: UserAccess) {
    @GetMapping("/admin/users")
    fun adminUsers(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        model.addAttribute("users", jdbc.queryForList("SELECT id,username,role,enabled,must_change_password,created_at FROM app_user ORDER BY created_at"))
        return "admin-users"
    }

    @GetMapping("/admin/users/{id}")
    fun adminUserEdit(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, model: Model): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        model.addAttribute("account", findAccount(id))
        return "admin-user-edit"
    }

    @PostMapping("/admin/users/{id}/delete")
    fun deleteUser(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        if (id == access.userId(user)) throw AccessDeniedException("Administrators cannot delete their own account")
        val deleted = jdbc.update("DELETE FROM app_user WHERE id=?", id)
        require(deleted == 1) { "User not found" }
        return "redirect:/admin/users"
    }

    @PostMapping("/admin/users")
    fun createUser(
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam username: String,
        @RequestParam password: String,
        @RequestParam role: String,
        @RequestParam temporaryPassword: Boolean
    ): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        require(username.matches(Regex("[A-Za-z0-9_.-]{3,80}")) && password.isNotEmpty() && role in listOf("USER","ADMIN")) { "Enter a valid username, a non-empty password, and a valid role." }
        jdbc.update("INSERT INTO app_user(username,password_hash,role,must_change_password) VALUES (?,?,?,?)", username, encoder.encode(password), role, temporaryPassword)
        return "redirect:/admin/users"
    }

    @PostMapping("/admin/users/{id}/enabled")
    fun toggleUser(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam enabled: Boolean): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        val targetUsername = jdbc.queryForObject("SELECT username FROM app_user WHERE id=?", String::class.java, id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
        if (targetUsername == user.username) throw AccessDeniedException("Administrators cannot disable their own account")
        require(jdbc.update("UPDATE app_user SET enabled=? WHERE id=?", enabled, id) == 1) { "User not found" }
        return "redirect:/admin/users/$id"
    }

    @PostMapping("/admin/users/{id}/password")
    fun adminResetPassword(
        @PathVariable id: UUID,
        @AuthenticationPrincipal user: UserDetails,
        @RequestParam password: String,
        @RequestParam temporaryPassword: Boolean
    ): String {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
        require(password.isNotEmpty()) { "Enter a password." }
        require(jdbc.update("UPDATE app_user SET password_hash=?,must_change_password=? WHERE id=?", encoder.encode(password), temporaryPassword, id) == 1) { "User not found" }
        return "redirect:/admin/users/$id"
    }

    private fun findAccount(id: UUID): Map<String, Any?> =
        jdbc.queryForList("SELECT id,username,role,enabled,must_change_password FROM app_user WHERE id=?", id)
            .firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND)
}
