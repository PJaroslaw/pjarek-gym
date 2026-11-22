package com.pjarek.gym.model

data class RoutineDraft(
    var name: String = "",
    var summary: String = "",
    var days: MutableList<RoutineDraftDay> = mutableListOf(),
    var sessionMinutes: Int? = null,
    var equipmentPreference: String = ""
)

data class RoutineDraftDay(
    var name: String = "",
    var exercises: MutableList<RoutineDraftExercise> = mutableListOf()
)

data class RoutineDraftExercise(
    var exerciseId: Long = 0,
    var exerciseName: String = "",
    var sets: Int = 3,
    var minReps: Int = 8,
    var maxReps: Int = 12,
    var restSeconds: Int = 90
)

data class RoutineDraftRequest(
    val goal: String,
    val daysPerWeek: Int,
    val experience: String,
    val sessionMinutes: Int,
    val equipment: String,
    val limitations: String
)
