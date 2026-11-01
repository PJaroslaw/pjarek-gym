package com.pjarek.gym.controller

import com.pjarek.gym.service.AccountService
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class PageModelAdvice(private val accountService: AccountService) {
    @ModelAttribute
    fun common(model: Model, @AuthenticationPrincipal user: UserDetails?) {
        if (user != null) {
            model.addAttribute("username", user.username)
            model.addAttribute("isAdmin", user.authorities.any { it.authority == "ROLE_ADMIN" })
            val account = accountService.account(user.username)
            model.addAttribute("weightUnit", account.weightUnit)
            model.addAttribute("themeMode", account.themeMode)
        }
    }
}
