package com.pjarek.gym.controller

import com.pjarek.gym.service.AccountService
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class AdminController(private val accountService: AccountService) {
    @GetMapping("/admin/users")
    fun adminUsers(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        requireAdmin(user)
        model.addAttribute("users", accountService.allAccounts())
        return "admin-users"
    }

    @GetMapping("/admin/users/{id}")
    fun adminUserEdit(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, model: Model): String {
        requireAdmin(user)
        model.addAttribute("account", accountService.adminAccount(id))
        return "admin-user-edit"
    }

    @PostMapping("/admin/users/{id}/delete")
    fun deleteUser(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        requireAdmin(user)
        accountService.delete(id, user.username)
        return "redirect:/admin/users"
    }

    @PostMapping("/admin/users")
    fun createUser(@AuthenticationPrincipal user: UserDetails, @RequestParam username: String,
                   @RequestParam password: String, @RequestParam role: String, @RequestParam temporaryPassword: Boolean): String {
        requireAdmin(user)
        accountService.create(username, password, role, temporaryPassword)
        return "redirect:/admin/users"
    }

    @PostMapping("/admin/users/{id}/enabled")
    fun toggleUser(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam enabled: Boolean): String {
        requireAdmin(user)
        accountService.setEnabled(id, user.username, enabled)
        return "redirect:/admin/users/$id"
    }

    @PostMapping("/admin/users/{id}/password")
    fun adminResetPassword(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails,
                           @RequestParam password: String, @RequestParam temporaryPassword: Boolean): String {
        requireAdmin(user)
        accountService.resetPassword(id, password, temporaryPassword)
        return "redirect:/admin/users/$id"
    }

    private fun requireAdmin(user: UserDetails) {
        require(user.authorities.any { it.authority == "ROLE_ADMIN" })
    }
}
