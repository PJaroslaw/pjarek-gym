package com.pjarek.gym.controller

import com.pjarek.gym.service.RoutineService
import com.pjarek.gym.service.UserAccess
import jakarta.servlet.http.HttpServletResponse
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
class RoutineController(private val service: RoutineService, private val access: UserAccess) {
    @GetMapping("/routines")
    fun routines(@AuthenticationPrincipal user: UserDetails, model: Model): String {
        model.addAttribute("routines", service.list(access.userId(user)))
        return "routines"
    }

    @PostMapping("/routines")
    fun createRoutine(@AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) name: String?, model: Model, response: HttpServletResponse): String {
        val owner = access.userId(user)
        val routineName = name?.trim().orEmpty()
        if (routineName.isBlank() || routineName.length > 160) {
            model.addAttribute("routines", service.list(owner))
            model.addAttribute("formError", if (routineName.isBlank()) "Enter a name for your routine." else "Routine names can be up to 160 characters.")
            model.addAttribute("routineName", name.orEmpty())
            response.status = HttpServletResponse.SC_BAD_REQUEST
            return "routines"
        }
        return "redirect:/routines/${service.create(owner, name)}"
    }

    @GetMapping("/routines/{id}")
    fun routine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, model: Model): String {
        val detail = service.detail(id, access.userId(user))
        model.addAttribute("routine", detail.routine)
        model.addAttribute("days", detail.days)
        model.addAttribute("items", detail.items)
        return "routine-detail"
    }

    @PostMapping("/routines/{id}/days")
    fun addDay(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam(defaultValue = "") name: String): String {
        service.addDay(id, access.userId(user), name)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/days/{dayId}/rename")
    fun renameDay(@PathVariable id: UUID, @PathVariable dayId: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam name: String): String {
        service.renameDay(id, dayId, access.userId(user), name)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/days/{dayId}/delete")
    fun deleteDay(@PathVariable id: UUID, @PathVariable dayId: UUID, @AuthenticationPrincipal user: UserDetails): String {
        service.deleteDay(id, dayId, access.userId(user))
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/rename")
    fun renameRoutine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam name: String): String {
        service.rename(id, access.userId(user), name)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/delete")
    fun deleteRoutine(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        service.delete(id, access.userId(user))
        return "redirect:/routines"
    }

    @PostMapping("/routines/{id}/items/{itemId}/delete")
    fun deleteRoutineItem(@PathVariable id: UUID, @PathVariable itemId: UUID, @AuthenticationPrincipal user: UserDetails): String {
        service.deleteItem(id, itemId, access.userId(user))
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/items/{itemId}/edit")
    fun editRoutineItem(@PathVariable id: UUID, @PathVariable itemId: UUID, @AuthenticationPrincipal user: UserDetails,
                        @RequestParam exerciseId: Long, @RequestParam sets: Int, @RequestParam minReps: Int,
                        @RequestParam maxReps: Int, @RequestParam rest: Int): String {
        service.editItem(id, itemId, access.userId(user), exerciseId, sets, minReps, maxReps, rest)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{id}/items")
    fun addRoutineItem(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails,
                       @RequestParam dayId: UUID, @RequestParam exerciseId: Long,
                       @RequestParam(defaultValue = "3") sets: Int, @RequestParam(defaultValue = "8") minReps: Int,
                       @RequestParam(defaultValue = "12") maxReps: Int, @RequestParam(defaultValue = "90") rest: Int): String {
        service.addItem(id, dayId, access.userId(user), exerciseId, sets, minReps, maxReps, rest)
        return "redirect:/routines/$id"
    }

    @PostMapping("/routines/{routineId}/days/{dayId}/start")
    fun startRoutine(@PathVariable routineId: UUID, @PathVariable dayId: UUID, @AuthenticationPrincipal user: UserDetails): String =
        "redirect:/workouts/${service.start(routineId, dayId, access.userId(user))}"
}
