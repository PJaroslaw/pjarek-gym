package com.pjarek.gym.service

import com.pjarek.gym.repository.ExerciseRepository
import org.springframework.stereotype.Service

@Service
class ExerciseLibraryService(private val exerciseRepository: ExerciseRepository) {
    fun exercise(id: Long): Map<String, Any?> = exerciseRepository.find(id)

    fun imagePath(sourceId: String, index: Int): String? {
        require(sourceId.matches(Regex("[A-Za-z0-9_-]+")) && index in 0..3)
        return exerciseRepository.imagePaths(sourceId)?.split(',')?.getOrNull(index)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+/[0-3]\\.jpg")) }
    }
}
