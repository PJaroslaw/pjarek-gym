package com.pjarek.gym.model

import java.math.BigDecimal
import java.util.UUID

data class WorkoutPage(
    val workout: Map<String, Any?>,
    val restRemainingMillis: Long,
    val items: List<Map<String, Any?>>,
    val sessionVolume: BigDecimal?,
    val sessionSets: List<Map<String, Any?>>,
    val currentItem: Map<String, Any?>?,
    val exerciseNumber: Int,
    val previousExerciseId: UUID?,
    val nextExerciseId: UUID?,
    val exercises: List<Map<String, Any?>>,
    val sets: List<Map<String, Any?>>,
    val previousSets: List<Map<String, Any?>>,
    val weightStep: String,
    val defaultReps: Any,
    val defaultWeight: BigDecimal
)

data class LoggedSet(val itemId: UUID, val restSeconds: Int, val restStartedAt: Long)
