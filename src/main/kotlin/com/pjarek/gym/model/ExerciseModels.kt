package com.pjarek.gym.model

data class CatalogExercise(
    val sourceId: String, val name: String, val category: String, val equipment: String,
    val difficulty: String, val forceType: String, val mechanic: String,
    val primaryMuscles: String, val secondaryMuscles: String, val instructions: String, val imagePaths: String
)
