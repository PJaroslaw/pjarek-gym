package com.pjarek.gym.service

import com.pjarek.gym.config.AiRoutineProperties
import com.pjarek.gym.model.RoutineDraft
import com.pjarek.gym.model.RoutineDraftRequest
import com.pjarek.gym.repository.ExerciseRepository
import org.springframework.stereotype.Service

@Service
class RoutineBuilderService(
    private val aiRoutineProperties: AiRoutineProperties,
    private val routineDraftGenerator: RoutineDraftGenerator,
    private val exerciseRepository: ExerciseRepository,
    private val sourceFileProcessor: RoutineSourceFileProcessor
) {
    val enabled: Boolean get() = aiRoutineProperties.enabled

    fun create(request: RoutineDraftRequest, sourceFile: RoutineSourceFile?): RoutineDraft {
        check(enabled) { "AI routine building is not configured. Ask the app administrator to configure an AI provider." }
        validateRequest(request, sourceFile != null)
        validateSourceFile(sourceFile)
        val preparedSourceFile = sourceFile?.let(sourceFileProcessor::prepare)
        val requiresWeightedExercises = requestsWeightedExercisesOnly(request.equipment, request.goal, request.limitations)
        val equipmentPreference = if (requiresWeightedExercises) WEIGHTED_EXERCISES_PREFERENCE else request.equipment
        val catalog = catalogForEquipment(exerciseRepository.routineBuilderCatalog(), equipmentPreference)
        val constraints = RoutineDraftConstraints.forSession(
            request.sessionMinutes,
            if (sourceFile == null) request.daysPerWeek..request.daysPerWeek else 1..MAX_DAYS
        )
        var draft = routineDraftGenerator.create(request, catalog, preparedSourceFile)
        if (draft.name.isBlank()) draft.name = request.goal.trim().take(MAX_ROUTINE_NAME_LENGTH).ifBlank { "Imported routine" }
        draft.sessionMinutes = request.sessionMinutes
        draft.equipmentPreference = equipmentPreference
        return validateAndRepair(draft, catalog, constraints)
    }

    private fun validateAndRepair(
        initialDraft: RoutineDraft,
        catalog: List<Map<String, Any?>>,
        constraints: RoutineDraftConstraints
    ): RoutineDraft {
        var draft = initialDraft
        for (attempt in 0..MAX_AI_REPAIR_ATTEMPTS) {
            try {
                return validateDraft(draft, catalog, constraints, enforceWorkoutOrder = true)
            } catch (error: RoutineDraftValidationException) {
                if (attempt == MAX_AI_REPAIR_ATTEMPTS) {
                    throw IllegalStateException(
                        "The AI couldn't satisfy the routine requirements after $MAX_AI_REPAIR_ATTEMPTS repair attempts. ${error.issues.joinToString(" ")} Try again or adjust your request."
                    )
                }
                val sessionMinutes = draft.sessionMinutes
                val equipmentPreference = draft.equipmentPreference
                draft = routineDraftGenerator.repair(draft, error.issues, catalog, constraints)
                if (draft.name.isBlank()) draft.name = initialDraft.name
                draft.sessionMinutes = sessionMinutes
                draft.equipmentPreference = equipmentPreference
            }
        }
        error("Routine validation loop ended unexpectedly.")
    }

    fun refine(draft: RoutineDraft, instructions: String): RoutineDraft {
        check(enabled) { "AI routine building is not configured. Ask the app administrator to configure an AI provider." }
        val availableCatalog = exerciseRepository.routineBuilderCatalog()
        val currentCatalog = catalogForEquipment(availableCatalog, draft.equipmentPreference)
        validateDraft(draft, currentCatalog, RoutineDraftConstraints())
        val request = instructions.trim()
        require(request.isNotEmpty() && request.length <= MAX_INSTRUCTIONS_LENGTH) {
            "Enter a change request up to $MAX_INSTRUCTIONS_LENGTH characters."
        }
        val requiresWeightedExercises = requestsWeightedExercisesOnly(request)
        val equipmentPreference = if (requiresWeightedExercises) WEIGHTED_EXERCISES_PREFERENCE else draft.equipmentPreference
        val refinementCatalog = catalogForEquipment(availableCatalog, equipmentPreference)
        val sessionMinutes = draft.sessionMinutes?.takeIf { it in 15..180 } ?: DEFAULT_SESSION_MINUTES
        val constraints = RoutineDraftConstraints.forSession(sessionMinutes).copy(
            uniqueExercisesAcrossDays = requestsUniqueExercisesAcrossDays(request)
        )
        val revised = routineDraftGenerator.refine(draft, request, refinementCatalog, constraints)
        if (revised.name.isBlank()) revised.name = draft.name
        revised.sessionMinutes = sessionMinutes
        revised.equipmentPreference = equipmentPreference
        return validateAndRepair(revised, refinementCatalog, constraints)
    }

    fun validateDraft(draft: RoutineDraft): RoutineDraft {
        val catalog = exerciseRepository.routineBuilderCatalog()
        return validateDraft(draft, catalog, RoutineDraftConstraints(exercisesPerDay = 1..MAX_EXERCISES_PER_DAY))
    }

    fun exerciseDetails(draft: RoutineDraft): Map<Long, Map<String, Any?>> {
        val ids = draft.days.flatMap { day -> day.exercises.map { it.exerciseId } }.distinct()
        return exerciseRepository.findActive(ids).associateBy { (it["id"] as Number).toLong() }
    }

    private fun validateDraft(
        draft: RoutineDraft,
        catalogRows: List<Map<String, Any?>>,
        constraints: RoutineDraftConstraints,
        enforceWorkoutOrder: Boolean = false
    ): RoutineDraft {
        val issues = mutableListOf<String>()
        if (draft.name.isBlank() || draft.name.trim().length > MAX_ROUTINE_NAME_LENGTH) {
            issues += "Routine names must be 1–$MAX_ROUTINE_NAME_LENGTH characters."
        }
        if (draft.summary.length > MAX_SUMMARY_LENGTH) issues += "The routine summary is too long."
        if (draft.days.size !in constraints.days) {
            issues += "A routine needs between ${constraints.days.first} and ${constraints.days.last} days."
        }
        val catalog = catalogRows.associateBy { (it["id"] as Number).toLong() }
        draft.days.forEach { day ->
            if (day.name.isBlank() || day.name.trim().length > MAX_DAY_NAME_LENGTH) {
                issues += "Day names must be 1–$MAX_DAY_NAME_LENGTH characters."
            }
            if (day.exercises.size !in constraints.exercisesPerDay) {
                issues += "Each day needs between ${constraints.exercisesPerDay.first} and ${constraints.exercisesPerDay.last} exercises for the selected session length."
            }
            if (day.exercises.map { it.exerciseId }.distinct().size != day.exercises.size) {
                issues += "Do not repeat an exercise within the same training day."
            }
            day.exercises.forEach { exercise ->
                if (exercise.sets !in constraints.setsPerExercise || exercise.minReps !in 1..1000 || exercise.maxReps !in exercise.minReps..1000 || exercise.restSeconds !in 0..900) {
                    issues += "Use ${constraints.setsPerExercise.first}–${constraints.setsPerExercise.last} sets, a rep range from 1–1,000 with the minimum no greater than the maximum, and 0–900 seconds rest."
                }
                val knownExercise = catalog[exercise.exerciseId]
                if (knownExercise == null) {
                    issues += "Choose each exercise from the active exercise library."
                } else {
                    exercise.exerciseName = knownExercise["name"].toString()
                }
            }
        }
        if (constraints.uniqueExercisesAcrossDays) {
            val repeatedExercises = draft.days.flatMapIndexed { dayIndex, day ->
                day.exercises.map { exercise -> exercise.exerciseId to (dayIndex to day.name) }
            }.groupBy({ it.first }, { it.second })
            repeatedExercises.forEach { (exerciseId, appearances) ->
                if (appearances.map { it.first }.distinct().size > 1) {
                    val exerciseName = catalog[exerciseId]?.get("name")?.toString() ?: "Exercise $exerciseId"
                    val dayNames = appearances.map { it.second }.distinct().joinToString(", ")
                    issues += "$exerciseName is repeated across training days ($dayNames). Use a different exercise on each day."
                }
            }
        }
        if (issues.isNotEmpty()) throw RoutineDraftValidationException(issues.distinct())
        draft.name = draft.name.trim()
        draft.days.forEach { day ->
            day.name = day.name.trim()
            if (enforceWorkoutOrder) {
                day.exercises = day.exercises.sortedBy { exercise ->
                    workoutOrderRank(catalog.getValue(exercise.exerciseId))
                }.toMutableList()
            }
        }
        return draft
    }

    private fun workoutOrderRank(exercise: Map<String, Any?>): Int {
        val primaryMuscles = exercise["primary_muscles"].toString().lowercase()
        if ("abdominal" in primaryMuscles || "oblique" in primaryMuscles) return 3
        return when (exercise["mechanic"].toString().lowercase()) {
            "compound" -> 0
            "isolation" -> 2
            else -> 1
        }
    }

    private fun requestsUniqueExercisesAcrossDays(request: String): Boolean {
        val normalized = request.lowercase()
        val negation = Regex("\\b(no|not|don't|do not|never|avoid|without)\\b").containsMatchIn(normalized)
        val repetition = Regex("\\b(repeat\\w*|duplicat\\w*|reuse\\w*|same)\\b").containsMatchIn(normalized)
        val relation = Regex("\\b(between|across|different|separate|each|every)\\b|day.to.day").containsMatchIn(normalized)
        val trainingUnit = Regex("\\b(day|session|workout)s?\\b").containsMatchIn(normalized)
        return negation && repetition && relation && trainingUnit
    }

    private fun validateRequest(request: RoutineDraftRequest, hasSourceFile: Boolean) {
        require((request.goal.isNotBlank() || hasSourceFile) && request.goal.length <= MAX_TEXT_LENGTH) {
            "Describe your training goal in up to $MAX_TEXT_LENGTH characters."
        }
        require(request.goal.isBlank() || request.goal.trim().length >= 3) { "Describe your training goal in at least 3 characters." }
        require(request.daysPerWeek in 1..MAX_DAYS) { "Choose 1–$MAX_DAYS training days per week." }
        require(request.experience in EXPERIENCE_LEVELS) { "Choose a valid experience level." }
        require(request.sessionMinutes in 15..180) { "Choose a session length from 15 to 180 minutes." }
        require(request.equipment.length <= MAX_TEXT_LENGTH && request.limitations.length <= MAX_TEXT_LENGTH) {
            "Equipment and limitations must each be no longer than $MAX_TEXT_LENGTH characters."
        }
    }

    private fun validateSourceFile(sourceFile: RoutineSourceFile?) {
        if (sourceFile == null) return
        require(sourceFile.bytes.isNotEmpty() && sourceFile.bytes.size <= aiRoutineProperties.maxUploadBytes) {
            "Choose a file smaller than ${aiRoutineProperties.maxUploadBytes / (1024 * 1024)} MB."
        }
        require(sourceFile.contentType in ACCEPTED_FILE_TYPES) { "Choose a PDF, JPEG, PNG, or WebP routine image." }
    }

    private fun catalogForEquipment(
        catalog: List<Map<String, Any?>>,
        equipmentPreference: String
    ): List<Map<String, Any?>> {
        val preference = equipmentPreference.lowercase()
        if (preference.isBlank() || preference.contains("full gym") || preference.contains("all equipment")) return catalog
        if (requestsWeightedExercisesOnly(preference)) {
            return catalog.filter { exercise ->
                exercise["equipment"].toString().lowercase() in WEIGHTED_EQUIPMENT &&
                    exercise["category"].toString().lowercase() in WEIGHTED_CATEGORIES &&
                    !exercise["name"].toString().contains("bodyweight", ignoreCase = true)
            }
        }
        if (preference.contains("no equipment") || preference.contains("bodyweight")) {
            return catalog.filter { it["equipment"].toString() == "body only" }
        }
        if (preference.contains("home gym") && EQUIPMENT_TYPES.keys.none(preference::contains)) {
            return catalog.filter { it["equipment"].toString() in HOME_GYM_EQUIPMENT }
        }
        val selected = EQUIPMENT_TYPES.filter { (equipment, aliases) ->
            equipment in preference || aliases.any(preference::contains)
        }.keys
        if (selected.isEmpty()) return catalog
        return catalog.filter { it["equipment"].toString() == "body only" || it["equipment"].toString() in selected }
    }

    private fun requestsWeightedExercisesOnly(vararg requests: String): Boolean = requests.any { request ->
        val normalized = request.trim().lowercase()
        normalized in WEIGHT_ONLY_LABELS || FREE_WEIGHTS_ONLY_REQUEST.containsMatchIn(normalized)
    }

    companion object {
        const val MAX_INSTRUCTIONS_LENGTH = 2000
        private const val DEFAULT_SESSION_MINUTES = 60
        private const val MAX_ROUTINE_NAME_LENGTH = 160
        private const val MAX_SUMMARY_LENGTH = 1000
        private const val MAX_DAY_NAME_LENGTH = 120
        private const val MAX_TEXT_LENGTH = 1000
        private const val MAX_DAYS = 7
        private const val MAX_EXERCISES_PER_DAY = 20
        private const val MAX_AI_REPAIR_ATTEMPTS = 2
        private const val WEIGHTED_EXERCISES_PREFERENCE = "weighted exercises only"
        private val EXPERIENCE_LEVELS = setOf("beginner", "intermediate", "advanced")
        private val ACCEPTED_FILE_TYPES = setOf("application/pdf", "image/jpeg", "image/png", "image/webp")
        private val WEIGHT_ONLY_LABELS = setOf("weight", "weights", "free weight", "free weights")
        private val FREE_WEIGHTS_ONLY_REQUEST = Regex(
            "\\b(?:free\\s+weights?|weights?\\s+only|weighted\\s+exercises?|only\\b.{0,60}\\bweights?\\b|exercises?\\b.{0,30}\\bwith\\s+weights?)\\b"
        )
        private val WEIGHTED_EQUIPMENT = setOf("barbell", "dumbbell", "kettlebells", "e-z curl bar", "cable")
        private val WEIGHTED_CATEGORIES = setOf("strength", "powerlifting", "olympic weightlifting")
        private val EQUIPMENT_TYPES = mapOf(
            "dumbbell" to listOf("dumbbell"),
            "barbell" to listOf("barbell"),
            "kettlebells" to listOf("kettlebell"),
            "bands" to listOf("band", "resistance band"),
            "cable" to listOf("cable"),
            "machine" to listOf("machine"),
            "medicine ball" to listOf("medicine ball"),
            "exercise ball" to listOf("exercise ball", "stability ball"),
            "foam roll" to listOf("foam roll", "foam roller"),
            "e-z curl bar" to listOf("ez bar", "curl bar")
        )
        private val HOME_GYM_EQUIPMENT = setOf("body only", "dumbbell", "bands", "kettlebells")
    }
}

private class RoutineDraftValidationException(val issues: List<String>) : IllegalArgumentException(issues.joinToString("\n"))
