package com.pjarek.gym.controller

import com.pjarek.gym.service.DashboardService
import com.pjarek.gym.service.UserAccess
import java.time.LocalDate
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class DashboardController(private val dashboardService: DashboardService, private val userAccess: UserAccess) {
    @GetMapping("/")
    fun home(@AuthenticationPrincipal user: UserDetails, model: Model,
             @RequestParam(required = false) month: String?,
             @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) day: LocalDate?): String {
        val page = dashboardService.overview(userAccess.userId(user), month, day)
        model.addAttribute("calendar", page.calendar)
        model.addAttribute("username", user.username)
        model.addAttribute("routines", page.routines)
        model.addAttribute("routineDays", page.routineDays)
        model.addAttribute("active", page.active)
        model.addAttribute("recent", page.recent)
        model.addAttribute("stats", page.stats)
        model.addAttribute("volumeTrend", page.volumeTrend)
        return "home"
    }

    @GetMapping("/settings")
    fun settings(): String = "settings"

    @GetMapping("/history")
    fun history(@AuthenticationPrincipal user: UserDetails, model: Model,
                @RequestParam(required = false) month: String?,
                @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) day: LocalDate?): String {
        val page = dashboardService.history(userAccess.userId(user), month, day)
        model.addAttribute("calendar", page.calendar)
        model.addAttribute("history", page.history)
        model.addAttribute("records", page.records)
        return "history"
    }
}
