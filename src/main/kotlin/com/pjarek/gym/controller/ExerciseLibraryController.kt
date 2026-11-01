package com.pjarek.gym.controller

import com.pjarek.gym.service.ExerciseLibraryService
import com.pjarek.gym.service.ExerciseSearchService
import jakarta.servlet.http.HttpServletResponse
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class ExerciseLibraryController(private val library: ExerciseLibraryService, private val exerciseSearch: ExerciseSearchService, @Value("\${app.exercise-image-dir}") private val imageDir: String) {
    @GetMapping("/exercises")
    fun exercises(@RequestParam(defaultValue="") q: String, @RequestHeader(name="HX-Request", required=false) hx: String?, model: Model): String {
        model.addAttribute("q", q)
        model.addAttribute("exercises", exerciseSearch.searchLibrary(q))
        return if (hx != null) "fragments/exercises :: rows" else "exercises"
    }

    @ResponseBody
    @GetMapping("/api/exercises/search")
    fun searchExercises(@RequestParam(defaultValue = "") q: String) = exerciseSearch.search(q)

    @GetMapping("/exercise/{id}")
    fun exercise(@PathVariable id: Long, model: Model): String {
        model.addAttribute("exercise", library.exercise(id))
        return "exercise-detail"
    }

    @GetMapping("/exercise-images/{sourceId}/{index}")
    fun image(@PathVariable sourceId: String, @PathVariable index: Int, response: HttpServletResponse) {
        val base = Path.of(imageDir).toAbsolutePath().normalize()
        Files.createDirectories(base)
        val path = library.imagePath(sourceId, index)
        if (path == null) { response.sendError(404); return }
        val currentImage = base.resolve("current/exercises/$path").normalize()
        val legacyImage = base.resolve("exercises/$path").normalize()
        if (!currentImage.startsWith(base) || !legacyImage.startsWith(base)) { response.sendError(404); return }
        val image = currentImage.takeIf { Files.isRegularFile(it) } ?: legacyImage
        if (!Files.isRegularFile(image)) { response.sendError(404); return }
        response.contentType = "image/jpeg"
        Files.newInputStream(image).use { it.copyTo(response.outputStream) }
    }

}
