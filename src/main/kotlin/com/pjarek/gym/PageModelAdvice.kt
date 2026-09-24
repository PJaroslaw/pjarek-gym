package com.pjarek.gym

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ModelAttribute

@ControllerAdvice
class PageModelAdvice(private val jdbc: JdbcTemplate) {
    @ModelAttribute
    fun common(model: Model, @AuthenticationPrincipal user: UserDetails?) {
        if (user != null) {
            model.addAttribute("username", user.username)
            model.addAttribute("isAdmin", user.authorities.any { it.authority == "ROLE_ADMIN" })
            model.addAttribute("weightUnit", jdbc.queryForObject("SELECT weight_unit FROM app_user WHERE username=?", String::class.java, user.username)!!)
            model.addAttribute("themeMode", jdbc.queryForObject("SELECT theme_mode FROM app_user WHERE username=?", String::class.java, user.username)!!)
        }
    }
}
