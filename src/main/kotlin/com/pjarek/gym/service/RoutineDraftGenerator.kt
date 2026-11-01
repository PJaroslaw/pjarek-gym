package com.pjarek.gym.service

import com.pjarek.gym.model.RoutineDraft
import com.pjarek.gym.model.RoutineDraftRequest
import java.util.UUID

data class RoutineSourceImage(val contentType: String, val bytes: ByteArray)

data class RoutineSourceFile(
    val fileName: String,
    val contentType: String,
    val bytes: ByteArray,
    val extractedText: String? = null,
    val images: List<RoutineSourceImage> = emptyList()
)

data class RoutineDraftSession(val owner: UUID, val draft: RoutineDraft)

data class RoutineDraftConstraints(
    val days: IntRange = 1..7,
    val exercisesPerDay: IntRange = 1..20,
    val setsPerExercise: IntRange = 1..20,
    val uniqueExercisesAcrossDays: Boolean = false
) {
    companion object {
        fun forSession(sessionMinutes: Int, days: IntRange = 1..7): RoutineDraftConstraints {
            val minimumExercises = ((sessionMinutes + 19) / 20).coerceAtLeast(1)
            val maximumExercises = ((sessionMinutes + 11) / 12).coerceIn(minimumExercises, 10)
            return RoutineDraftConstraints(days, minimumExercises..maximumExercises, 2..5)
        }
    }
}

interface RoutineDraftGenerator {
    fun create(request: RoutineDraftRequest, catalog: List<Map<String, Any?>>, sourceFile: RoutineSourceFile? = null): RoutineDraft
    fun refine(draft: RoutineDraft, instructions: String, catalog: List<Map<String, Any?>>): RoutineDraft
    fun refine(
        draft: RoutineDraft,
        instructions: String,
        catalog: List<Map<String, Any?>>,
        constraints: RoutineDraftConstraints
    ): RoutineDraft = refine(draft, instructions, catalog)
    fun repair(
        draft: RoutineDraft,
        issues: List<String>,
        catalog: List<Map<String, Any?>>,
        constraints: RoutineDraftConstraints
    ): RoutineDraft = refine(draft, issues.joinToString("\n"), catalog)
}
