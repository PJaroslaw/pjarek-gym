package com.pjarek.gym.controller

import com.pjarek.gym.config.AiRoutineProperties
import com.pjarek.gym.model.RoutineDraft
import com.pjarek.gym.model.RoutineDraftDay
import com.pjarek.gym.model.RoutineDraftExercise
import com.pjarek.gym.model.RoutineDraftRequest
import com.pjarek.gym.service.RoutineBuilderService
import com.pjarek.gym.service.RoutineDraftSession
import com.pjarek.gym.service.RoutineService
import com.pjarek.gym.service.RoutineSourceFile
import com.pjarek.gym.service.UserAccess
import jakarta.servlet.http.HttpSession
import java.util.UUID
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.multipart.MultipartFile
import org.slf4j.LoggerFactory

@Controller
class RoutineBuilderController(
    private val routineBuilderService: RoutineBuilderService,
    private val routineService: RoutineService,
    private val userAccess: UserAccess,
    private val aiRoutineProperties: AiRoutineProperties
) {
    @GetMapping("/routine-builder")
    fun page(@AuthenticationPrincipal user: UserDetails, session: HttpSession, model: Model): String = render(
        userAccess.userId(user), session, model
    )

    @PostMapping("/routine-builder/generate")
    fun generate(
        @AuthenticationPrincipal user: UserDetails,
        session: HttpSession,
        model: Model,
        @RequestParam(defaultValue = "") goal: String,
        @RequestParam(defaultValue = "3") daysPerWeek: Int,
        @RequestParam(defaultValue = "intermediate") experience: String,
        @RequestParam(defaultValue = "60") sessionMinutes: Int,
        @RequestParam(defaultValue = "") equipment: String,
        @RequestParam(defaultValue = "") limitations: String,
        @RequestParam(required = false) sourceFile: MultipartFile?
    ): String {
        val owner = userAccess.userId(user)
        try {
            val draft = routineBuilderService.create(
                RoutineDraftRequest(goal.trim(), daysPerWeek, experience, sessionMinutes, equipment.trim(), limitations.trim()),
                sourceFile?.let(::toRoutineSourceFile)
            )
            session.setAttribute(DRAFT_SESSION_KEY, RoutineDraftSession(owner, draft))
        } catch (error: IllegalArgumentException) {
            model.addAttribute("builderError", error.message ?: "Check the routine details and try again.")
        } catch (error: IllegalStateException) {
            model.addAttribute("builderError", error.message ?: "Routine generation failed. Try again.")
        }
        return render(owner, session, model)
    }

    @PostMapping("/routine-builder/refine")
    fun refine(
        @AuthenticationPrincipal user: UserDetails,
        session: HttpSession,
        model: Model,
        @RequestParam params: MultiValueMap<String, String>,
        @RequestParam(defaultValue = "") changeRequest: String
    ): String {
        val owner = userAccess.userId(user)
        try {
            require(currentDraft(session, owner) != null) { "Start a routine draft before asking for changes." }
            val draft = draftFrom(params)
            session.setAttribute(DRAFT_SESSION_KEY, RoutineDraftSession(owner, draft))
            val revised = routineBuilderService.refine(draft, changeRequest)
            session.setAttribute(DRAFT_SESSION_KEY, RoutineDraftSession(owner, revised))
            if (draft.name == revised.name && draft.days == revised.days) {
                model.addAttribute("builderNotice", "The AI returned the routine unchanged. Try asking for one specific change, such as replacing a named exercise.")
            } else {
                model.addAttribute("builderNotice", "Draft updated. Review the changes below before saving.")
            }
        } catch (error: IllegalArgumentException) {
            model.addAttribute("builderError", error.message ?: "Check the routine draft and try again.")
        } catch (error: IllegalStateException) {
            model.addAttribute("builderError", error.message ?: "Routine refinement failed. Try again.")
        } catch (error: Exception) {
            logger.error("Unexpected error while refining a routine draft", error)
            model.addAttribute("builderError", "Routine refinement failed unexpectedly. Your submitted draft is still available; try again.")
        }
        return render(owner, session, model)
    }

    @PostMapping("/routine-builder/save")
    fun save(
        @AuthenticationPrincipal user: UserDetails,
        session: HttpSession,
        model: Model,
        @RequestParam params: MultiValueMap<String, String>
    ): String {
        val owner = userAccess.userId(user)
        return try {
            require(currentDraft(session, owner) != null) { "Start a routine draft before saving it." }
            val routineId = routineService.saveDraft(owner, routineBuilderService.validateDraft(draftFrom(params)))
            session.removeAttribute(DRAFT_SESSION_KEY)
            "redirect:/routines/$routineId"
        } catch (error: IllegalArgumentException) {
            model.addAttribute("builderError", error.message ?: "Check the routine draft and try again.")
            render(owner, session, model)
        } catch (error: IllegalStateException) {
            model.addAttribute("builderError", error.message ?: "The routine draft is no longer available.")
            render(owner, session, model)
        }
    }

    @PostMapping("/routine-builder/cancel")
    fun cancel(@AuthenticationPrincipal user: UserDetails, session: HttpSession): String {
        val owner = userAccess.userId(user)
        if (currentDraft(session, owner) != null) session.removeAttribute(DRAFT_SESSION_KEY)
        return "redirect:/routines"
    }

    private fun render(owner: UUID, session: HttpSession, model: Model): String {
        val draft = if (!model.containsAttribute("draft")) currentDraft(session, owner) else model.getAttribute("draft") as? RoutineDraft
        if (!model.containsAttribute("draft")) model.addAttribute("draft", draft)
        model.addAttribute("exerciseDetails", draft?.let(routineBuilderService::exerciseDetails) ?: emptyMap<Long, Map<String, Any?>>())
        model.addAttribute("aiEnabled", aiRoutineProperties.enabled)
        return "routine-builder"
    }

    private fun currentDraft(session: HttpSession, owner: UUID): RoutineDraft? {
        val stored = session.getAttribute(DRAFT_SESSION_KEY) as? RoutineDraftSession ?: return null
        if (stored.owner != owner) {
            session.removeAttribute(DRAFT_SESSION_KEY)
            return null
        }
        return stored.draft
    }

    private fun draftFrom(params: MultiValueMap<String, String>): RoutineDraft {
        val values = params.toSingleValueMap()
        val dayIndexes = values.keys.mapNotNull { DAY_NAME.matchEntire(it)?.groupValues?.get(1)?.toIntOrNull() }.distinct().sorted()
        val days = dayIndexes.map { dayIndex ->
            val exerciseIndexes = values.keys.mapNotNull {
                EXERCISE_ID.matchEntire(it)?.takeIf { match -> match.groupValues[1].toIntOrNull() == dayIndex }
                    ?.groupValues?.get(2)?.toIntOrNull()
            }.distinct().sorted()
            val exercises = exerciseIndexes.map { exerciseIndex ->
                fun value(field: String): String = values["days[$dayIndex].exercises[$exerciseIndex].$field"]
                    ?: throw IllegalArgumentException("Complete every exercise in the draft.")
                RoutineDraftExercise(
                    exerciseId = value("exerciseId").toLongOrNull() ?: 0,
                    exerciseName = value("exerciseName"),
                    sets = value("sets").toIntOrNull() ?: 0,
                    minReps = value("minReps").toIntOrNull() ?: 0,
                    maxReps = value("maxReps").toIntOrNull() ?: 0,
                    restSeconds = value("restSeconds").toIntOrNull() ?: 0
                )
            }.toMutableList()
            RoutineDraftDay(
                name = values["days[$dayIndex].name"] ?: "",
                exercises = exercises
            )
        }.toMutableList()
        return RoutineDraft(
            name = values["name"] ?: "",
            summary = values["summary"] ?: "",
            days = days,
            sessionMinutes = values["sessionMinutes"]?.toIntOrNull(),
            equipmentPreference = values["equipmentPreference"] ?: ""
        )
    }

    private fun toRoutineSourceFile(file: MultipartFile): RoutineSourceFile? {
        if (file.isEmpty) return null
        require(file.size <= aiRoutineProperties.maxUploadBytes) {
            "Choose a file smaller than ${aiRoutineProperties.maxUploadBytes / (1024 * 1024)} MB."
        }
        val bytes = file.bytes
        val (contentType, extension) = when {
            bytes.startsWith(PDF_SIGNATURE) -> "application/pdf" to "pdf"
            bytes.startsWith(PNG_SIGNATURE) -> "image/png" to "png"
            bytes.startsWith(JPEG_SIGNATURE) -> "image/jpeg" to "jpg"
            bytes.size >= 12 && bytes.startsWith(WEBP_RIFF) && bytes.copyOfRange(8, 12).contentEquals(WEBP_MARKER) -> "image/webp" to "webp"
            else -> throw IllegalArgumentException("Choose a PDF, JPEG, PNG, or WebP routine image.")
        }
        return RoutineSourceFile("routine.$extension", contentType, bytes)
    }

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean = size >= prefix.size && copyOfRange(0, prefix.size).contentEquals(prefix)

    companion object {
        private val logger = LoggerFactory.getLogger(RoutineBuilderController::class.java)
        private const val DRAFT_SESSION_KEY = "routine-builder-draft"
        private val DAY_NAME = Regex("days\\[(\\d+)]\\.name")
        private val EXERCISE_ID = Regex("days\\[(\\d+)]\\.exercises\\[(\\d+)]\\.exerciseId")
        private val PDF_SIGNATURE = "%PDF-".toByteArray(Charsets.US_ASCII)
        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a)
        private val JPEG_SIGNATURE = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
        private val WEBP_RIFF = "RIFF".toByteArray(Charsets.US_ASCII)
        private val WEBP_MARKER = "WEBP".toByteArray(Charsets.US_ASCII)
    }
}
