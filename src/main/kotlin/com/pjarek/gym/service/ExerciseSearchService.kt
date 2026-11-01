package com.pjarek.gym.service

import com.pjarek.gym.repository.ExerciseRepository
import java.util.Locale
import org.springframework.stereotype.Service

@Service
class ExerciseSearchService(private val exerciseRepository: ExerciseRepository) {
    fun search(query: String): List<Map<String, Any?>> {
        val term = query.trim()
        if (term.length < MIN_QUERY_LENGTH) return emptyList()
        require(term.length <= MAX_QUERY_LENGTH) { "Search terms can be up to $MAX_QUERY_LENGTH characters." }
        val terms = tokenize(term)
        if (terms.isEmpty()) return emptyList()
        return exerciseRepository.searchPicker(normalize(term), terms)
    }

    fun searchLibrary(query: String): List<Map<String, Any?>> {
        require(query.length <= MAX_QUERY_LENGTH) { "Search terms can be up to $MAX_QUERY_LENGTH characters." }
        val term = query.trim()
        if (term.isEmpty()) {
            return exerciseRepository.library()
        }
        val terms = tokenize(term)
        if (terms.isEmpty()) return emptyList()
        return exerciseRepository.searchLibrary(normalize(term), terms)
    }

    private fun tokenize(query: String): List<String> =
        normalize(query).split(WORD_SEPARATOR).filter(String::isNotEmpty).distinct()

    private fun normalize(query: String): String =
        query.lowercase(Locale.ROOT).replace(NON_WORD_CHARACTERS, " ").trim().replace(MULTIPLE_SPACES, " ")

    companion object {
        private const val MIN_QUERY_LENGTH = 2
        private const val MAX_QUERY_LENGTH = 100
        private val WORD_SEPARATOR = Regex("\\s+")
        private val NON_WORD_CHARACTERS = Regex("[^\\p{L}\\p{N}]+")
        private val MULTIPLE_SPACES = Regex("\\s+")
    }
}
