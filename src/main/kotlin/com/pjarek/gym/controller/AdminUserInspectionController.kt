package com.pjarek.gym.controller

import com.pjarek.gym.service.AdminInspectionService
import java.util.UUID
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.server.ResponseStatusException

@Controller
class AdminUserInspectionController(private val adminInspectionService: AdminInspectionService) {
    @GetMapping("/admin/users/{id}/inspect")
    fun inspectUser(@PathVariable id: UUID, @RequestParam(defaultValue = "0") routinesPage: Int,
                    @RequestParam(defaultValue = "0") sessionsPage: Int,
                    @AuthenticationPrincipal user: UserDetails, model: Model): String {
        requireAdmin(user)
        val page = adminInspectionService.inspect(id, routinesPage, sessionsPage)
        model.addAttribute("account", page.account)
        model.addAttribute("routineCount", page.routineCount)
        model.addAttribute("routines", page.routines)
        model.addAttribute("routinesPage", page.routinesPage)
        model.addAttribute("hasPreviousRoutinePage", page.hasPreviousRoutinePage)
        model.addAttribute("hasNextRoutinePage", page.hasNextRoutinePage)
        model.addAttribute("workouts", page.workouts)
        model.addAttribute("sessionsPage", page.sessionsPage)
        model.addAttribute("hasPreviousSessionPage", page.hasPreviousSessionPage)
        model.addAttribute("hasNextSessionPage", page.hasNextSessionPage)
        return "admin-user-inspect"
    }

    @GetMapping("/admin/users/{id}/inspect/routines/{routineId}")
    fun inspectRoutine(@PathVariable id: UUID, @PathVariable routineId: UUID,
                       @AuthenticationPrincipal user: UserDetails, model: Model): String {
        requireAdmin(user)
        val detail = adminInspectionService.routine(id, routineId)
        model.addAttribute("account", detail.account)
        model.addAttribute("routine", detail.routine)
        model.addAttribute("days", detail.days)
        model.addAttribute("items", detail.items)
        return "admin-user-routine-inspect"
    }

    @GetMapping("/admin/users/{id}/inspect/workouts/{workoutId}")
    fun inspectWorkout(@PathVariable id: UUID, @PathVariable workoutId: UUID,
                       @AuthenticationPrincipal user: UserDetails, model: Model): String {
        requireAdmin(user)
        val detail = adminInspectionService.workout(id, workoutId)
        model.addAttribute("account", detail.account)
        model.addAttribute("workout", detail.workout)
        model.addAttribute("workoutExercises", detail.exercises)
        model.addAttribute("sets", detail.sets)
        return "admin-user-workout-inspect"
    }

    private fun requireAdmin(user: UserDetails) {
        if (user.authorities.none { it.authority == "ROLE_ADMIN" }) throw ResponseStatusException(HttpStatus.FORBIDDEN)
    }
}
