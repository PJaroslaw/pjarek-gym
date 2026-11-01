package com.pjarek.gym.model

data class InspectionPage(
    val account: AdminAccount,
    val routines: List<Map<String, Any?>>,
    val routineCount: Long,
    val routinesPage: Int,
    val hasPreviousRoutinePage: Boolean,
    val hasNextRoutinePage: Boolean,
    val workouts: List<Map<String, Any?>>,
    val sessionsPage: Int,
    val hasPreviousSessionPage: Boolean,
    val hasNextSessionPage: Boolean
)

data class InspectedRoutine(val account: AdminAccount, val routine: Map<String, Any?>,
                            val days: List<Map<String, Any?>>, val items: List<Map<String, Any?>>)

data class InspectedWorkout(val account: AdminAccount, val workout: Map<String, Any?>,
                            val exercises: List<Map<String, Any?>>, val sets: List<Map<String, Any?>>)
