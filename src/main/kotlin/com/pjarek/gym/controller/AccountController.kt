package com.pjarek.gym.controller

import com.pjarek.gym.service.AccountService
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus

@Controller
class AccountController(private val accountService: AccountService) {
    @GetMapping("/login") fun login() = "login"

    @RequestMapping("/forbidden")
    @ResponseStatus(HttpStatus.FORBIDDEN)
    fun forbidden() = "forbidden"

    @PostMapping("/settings/weight-unit")
    fun updateWeightUnit(@AuthenticationPrincipal user: UserDetails, @RequestParam unit: String): String {
        accountService.updateWeightUnit(user.username, unit)
        return "redirect:/settings"
    }

    @PostMapping("/settings/theme-mode")
    fun updateThemeMode(@AuthenticationPrincipal user: UserDetails, @RequestParam mode: String): String {
        accountService.updateThemeMode(user.username, mode)
        return "redirect:/settings"
    }

    @PostMapping("/settings/password")
    fun changePassword(@AuthenticationPrincipal user: UserDetails, @RequestParam currentPassword: String, @RequestParam newPassword: String): String {
        accountService.changePassword(user.username, currentPassword, newPassword)
        return "redirect:/settings?passwordChanged"
    }

    @GetMapping("/change-password")
    fun requiredPasswordChange(@AuthenticationPrincipal user: UserDetails): String =
        if (accountService.account(user.username).mustChangePassword) "change-password" else "redirect:/"

    @PostMapping("/change-password")
    fun saveRequiredPasswordChange(@AuthenticationPrincipal user: UserDetails, @RequestParam newPassword: String): String {
        accountService.saveRequiredPassword(user.username, newPassword)
        return "redirect:/"
    }
}
