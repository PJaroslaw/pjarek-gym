package com.pjarek.gym

import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class WorkoutController(private val service: WorkoutService, private val access: UserAccess) {
    @PostMapping("/workouts/ad-hoc")
    fun startAdHoc(@AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) exerciseId: Long?): String {
        val (workout, item) = service.startAdHoc(access.userId(user), exerciseId)
        return if (item == null) "redirect:/workouts/$workout" else "redirect:/workouts/$workout?exercise=$item"
    }

    @GetMapping("/workouts/{id}")
    fun workout(@PathVariable id: UUID, @RequestParam(required = false) exercise: UUID?,
                @RequestParam(required = false) restSeconds: Int?, @RequestParam(required = false) restStartedAt: Long?,
                @AuthenticationPrincipal user: UserDetails, model: Model): String {
        val page = service.page(id, access.userId(user), exercise, restSeconds, restStartedAt)
        model.addAttribute("restRemainingMillis", page.restRemainingMillis)
        model.addAttribute("workout", page.workout)
        model.addAttribute("workoutElapsedSeconds", page.workout["displayed_elapsed_seconds"])
        if (page.workout["status"] == "COMPLETED") {
            model.addAttribute("sessionExercises", page.items)
            model.addAttribute("sessionVolume", page.sessionVolume)
            model.addAttribute("sessionSets", page.sessionSets)
        }
        model.addAttribute("currentItem", page.currentItem)
        model.addAttribute("exerciseNumber", page.exerciseNumber)
        model.addAttribute("exerciseCount", page.items.size)
        model.addAttribute("previousExerciseId", page.previousExerciseId)
        model.addAttribute("nextExerciseId", page.nextExerciseId)
        model.addAttribute("exercises", page.exercises)
        model.addAttribute("sets", page.sets)
        model.addAttribute("previousSets", page.previousSets)
        model.addAttribute("weightStep", page.weightStep)
        model.addAttribute("defaultReps", page.defaultReps)
        model.addAttribute("defaultWeight", page.defaultWeight)
        return "workout"
    }

    @PostMapping("/workouts/{id}/exercises")
    fun addWorkoutExercise(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam exerciseId: Long): String =
        "redirect:/workouts/$id?exercise=${service.addExercise(id, access.userId(user), exerciseId)}"

    @PostMapping("/workouts/{id}/sets")
    fun logSet(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam itemId: UUID,
               @RequestParam reps: Int, @RequestParam weight: BigDecimal): String {
        val logged = service.logSet(id, access.userId(user), itemId, reps, weight)
        return "redirect:/workouts/$id?exercise=${logged.itemId}&restSeconds=${logged.restSeconds}&restStartedAt=${logged.restStartedAt}"
    }

    @PostMapping("/workouts/{id}/sets/{setId}/edit")
    fun editWorkoutSet(@PathVariable id: UUID, @PathVariable setId: UUID, @AuthenticationPrincipal user: UserDetails,
                       @RequestParam reps: Int, @RequestParam weight: BigDecimal): String =
        "redirect:/workouts/$id?exercise=${service.editSet(id, access.userId(user), setId, reps, weight)}"

    @PostMapping("/workouts/{id}/sets/{setId}/delete")
    fun deleteWorkoutSet(@PathVariable id: UUID, @PathVariable setId: UUID, @AuthenticationPrincipal user: UserDetails): String =
        "redirect:/workouts/$id?exercise=${service.deleteSet(id, access.userId(user), setId)}"

    @PostMapping("/workouts/{id}/finish")
    fun finishWorkout(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails): String {
        service.finish(id, access.userId(user))
        return "redirect:/"
    }

    @PostMapping("/workouts/{id}/pause")
    fun pause(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) exercise: UUID?): String {
        service.pause(id, access.userId(user))
        return workoutRedirect(id, exercise)
    }

    @PostMapping("/workouts/{id}/resume")
    fun resume(@PathVariable id: UUID, @AuthenticationPrincipal user: UserDetails, @RequestParam(required = false) exercise: UUID?): String {
        service.resume(id, access.userId(user))
        return workoutRedirect(id, exercise)
    }

    @PostMapping("/workouts/{id}/delete")
    fun deleteWorkout(@PathVariable id: UUID, @RequestParam returnTo: String,
                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) day: LocalDate?,
                      @AuthenticationPrincipal user: UserDetails): String {
        val destination = when (returnTo) {
            "overview" -> "redirect:/"
            "history" -> if (day == null) "redirect:/history" else "redirect:/history?day=$day"
            else -> throw IllegalArgumentException("Unknown workout deletion destination")
        }
        service.delete(id, access.userId(user))
        return destination
    }

    private fun workoutRedirect(id: UUID, exercise: UUID?): String =
        if (exercise == null) "redirect:/workouts/$id" else "redirect:/workouts/$id?exercise=$exercise"
}
