package com.pjarek.gym

import org.springframework.stereotype.Service

@Service
class ExerciseLibraryService(private val repository: ExerciseRepository) {
    fun exercise(id: Long): Map<String, Any?> = repository.find(id)

    fun imagePath(sourceId: String, index: Int): String? {
        require(sourceId.matches(Regex("[A-Za-z0-9_-]+")) && index in 0..3)
        return repository.imagePaths(sourceId)?.split(',')?.getOrNull(index)
            ?.takeIf { it.matches(Regex("[A-Za-z0-9_-]+/[0-3]\\.jpg")) }
    }
}
