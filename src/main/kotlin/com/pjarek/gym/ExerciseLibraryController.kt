package com.pjarek.gym

import jakarta.servlet.http.HttpServletResponse
import java.nio.file.Files
import java.nio.file.Path
import org.springframework.beans.factory.annotation.Value
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody

@Controller
class ExerciseLibraryController(private val jdbc: JdbcTemplate, private val exerciseSearch: ExerciseSearchService, @Value("\${app.exercise-image-dir}") private val imageDir: String) {
    @GetMapping("/exercises")
    fun exercises(@RequestParam(defaultValue="") q: String, @RequestHeader(name="HX-Request", required=false) hx: String?, model: Model): String {
        require(q.length <= 100) { "Search terms can be up to 100 characters." }
        val term = "%${q.trim()}%"
        model.addAttribute("q", q)
        model.addAttribute("exercises", jdbc.queryForList("SELECT id,name,category,equipment,primary_muscles,source_id FROM exercise WHERE active=TRUE AND (name ILIKE ? OR primary_muscles ILIKE ? OR equipment ILIKE ?) ORDER BY name LIMIT 200", term, term, term))
        return if (hx != null) "fragments/exercises :: rows" else "exercises"
    }

    @ResponseBody
    @GetMapping("/api/exercises/search")
    fun searchExercises(@RequestParam(defaultValue = "") q: String) = exerciseSearch.search(q)

    @GetMapping("/exercise/{id}")
    fun exercise(@PathVariable id: Long, model: Model): String {
        model.addAttribute("exercise", jdbc.queryForMap("SELECT * FROM exercise WHERE id=?", id))
        return "exercise-detail"
    }

    @GetMapping("/exercise-images/{sourceId}/{index}")
    fun image(@PathVariable sourceId: String, @PathVariable index: Int, response: HttpServletResponse) {
        require(sourceId.matches(Regex("[A-Za-z0-9_-]+")) && index in 0..3)
        val base = Path.of(imageDir).toAbsolutePath().normalize()
        Files.createDirectories(base)
        val path = jdbc.query("SELECT image_paths FROM exercise WHERE source_id=?", { rs, _ -> rs.getString(1) }, sourceId).firstOrNull()?.split(',')?.getOrNull(index)
        if (path == null || !path.matches(Regex("[A-Za-z0-9_-]+/[0-3]\\.jpg"))) { response.sendError(404); return }
        val currentImage = base.resolve("current/exercises/$path").normalize()
        val legacyImage = base.resolve("exercises/$path").normalize()
        if (!currentImage.startsWith(base) || !legacyImage.startsWith(base)) { response.sendError(404); return }
        val image = currentImage.takeIf { Files.isRegularFile(it) } ?: legacyImage
        if (!Files.isRegularFile(image)) { response.sendError(404); return }
        response.contentType = "image/jpeg"
        Files.newInputStream(image).use { it.copyTo(response.outputStream) }
    }

}
