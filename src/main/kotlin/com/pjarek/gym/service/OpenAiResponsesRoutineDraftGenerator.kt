package com.pjarek.gym.service

import com.pjarek.gym.config.AiRoutineProperties
import com.pjarek.gym.model.RoutineDraft
import com.pjarek.gym.model.RoutineDraftDay
import com.pjarek.gym.model.RoutineDraftExercise
import com.pjarek.gym.model.RoutineDraftRequest
import java.net.http.HttpClient
import java.time.Duration
import java.util.Base64
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.client.JdkClientHttpRequestFactory
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.client.RestClientException
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

@Service
@ConditionalOnProperty(prefix = "app.ai", name = ["provider"], havingValue = "openai", matchIfMissing = true)
class OpenAiResponsesRoutineDraftGenerator(
    private val properties: AiRoutineProperties,
    private val mapper: JsonMapper
) : RoutineDraftGenerator {
    private val restClient by lazy {
        RestClient.builder()
            .baseUrl(baseUrl)
            .requestFactory(
                JdkClientHttpRequestFactory(
                    HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
                ).apply { setReadTimeout(Duration.ofSeconds(90)) }
            )
            .defaultHeader("Authorization", "Bearer ${properties.apiKey}")
            .build()
    }

    override fun create(
        request: RoutineDraftRequest,
        catalog: List<Map<String, Any?>>,
        sourceFile: RoutineSourceFile?
    ): RoutineDraft {
        val constraints = RoutineDraftConstraints.forSession(
            request.sessionMinutes,
            if (sourceFile == null) request.daysPerWeek..request.daysPerWeek else 1..7
        )
        val requestText = buildString {
            appendLine("Create a training routine from these user preferences:")
            appendLine("Goal: ${request.goal.ifBlank { "Preserve the intent of the attached routine" }}")
            appendLine("Days per week: ${request.daysPerWeek}")
            appendLine("Experience: ${request.experience}")
            appendLine("Session length in minutes: ${request.sessionMinutes}")
            appendLine("Include ${constraints.exercisesPerDay.first}–${constraints.exercisesPerDay.last} exercises per day so the plan fits the session length.")
            appendLine("Use 2–5 working sets per exercise.")
            appendLine("Available equipment: ${request.equipment.ifBlank { "Not specified" }}")
            if (request.equipment.isNotBlank()) appendLine("Only choose exercises that use the listed equipment or bodyweight.")
            appendLine("Limitations or preferences: ${request.limitations.ifBlank { "Not specified" }}")
            appendLine("Order exercises as they should be performed: priority compound movements first, then secondary compounds, isolation/accessory work, and core or conditioning last. Never sort exercises alphabetically.")
            if (sourceFile != null) appendLine("Use the attached routine as the source plan. Preserve its structure where it is legible and adapt it only to fit the active exercise catalog.")
            appendLine("Use only exercise IDs and names from this active catalog:")
            append(mapper.writeValueAsString(catalog))
        }
        return requestRoutine(requestText, sourceFile, catalog, constraints)
    }

    override fun refine(draft: RoutineDraft, instructions: String, catalog: List<Map<String, Any?>>): RoutineDraft = refine(
        draft,
        instructions,
        catalog,
        draft.sessionMinutes?.let { RoutineDraftConstraints.forSession(it) } ?: RoutineDraftConstraints()
    )

    override fun refine(
        draft: RoutineDraft,
        instructions: String,
        catalog: List<Map<String, Any?>>,
        constraints: RoutineDraftConstraints
    ): RoutineDraft {
        val requestText = buildString {
            appendLine("Revise this editable routine draft according to the user's request.")
            appendLine("Keep every part that the request does not ask to change.")
            appendLine("Apply the requested change explicitly. Do not return the draft unchanged when the request can be followed. If a requested change is impossible with this exercise catalog, say so in the summary and make the closest valid change.")
            if (constraints.uniqueExercisesAcrossDays) appendLine("Use each exercise on only one training day. Replace repeated exercises on other days with suitable alternatives from the catalog.")
            appendLine("Preserve a natural workout order: priority compound movements first, then secondary compounds, isolation/accessory work, and core or conditioning last. Never sort exercises alphabetically.")
            appendLine("User request: $instructions")
            appendLine("Current draft:")
            appendLine(mapper.writeValueAsString(draft))
            appendLine("Use only exercise IDs and names from this active catalog:")
            append(mapper.writeValueAsString(catalog))
        }
        return requestRoutine(requestText, null, catalog, constraints)
    }

    override fun repair(
        draft: RoutineDraft,
        issues: List<String>,
        catalog: List<Map<String, Any?>>,
        constraints: RoutineDraftConstraints
    ): RoutineDraft {
        val requestText = buildString {
            appendLine("Repair this routine draft using the automated validation report below.")
            appendLine("Fix every reported issue while preserving all valid choices, workout order, and the user's goal.")
            if (constraints.uniqueExercisesAcrossDays) appendLine("Do not use the same exercise on more than one training day.")
            appendLine("Keep a natural workout order and never alphabetize exercises.")
            appendLine("Validation report:")
            issues.forEach { appendLine("- $it") }
            appendLine("Current draft:")
            appendLine(mapper.writeValueAsString(draft))
            appendLine("Use only exercise IDs and names from this active catalog:")
            append(mapper.writeValueAsString(catalog))
        }
        return requestRoutine(requestText, null, catalog, constraints)
    }

    private fun requestRoutine(
        prompt: String,
        sourceFile: RoutineSourceFile?,
        catalog: List<Map<String, Any?>>,
        constraints: RoutineDraftConstraints
    ): RoutineDraft {
        check(properties.enabled) { "AI routine building is not configured. Set the provider settings to enable it." }
        val content = mutableListOf<Map<String, Any>>()
        content += mapOf("type" to "input_text", "text" to prompt)
        if (sourceFile != null) {
            sourceFile.extractedText?.let {
                content += mapOf("type" to "input_text", "text" to "Extracted text from the uploaded routine:\n$it")
            }
            val images = sourceFile.images.ifEmpty {
                if (sourceFile.contentType.startsWith("image/")) listOf(RoutineSourceImage(sourceFile.contentType, sourceFile.bytes)) else emptyList()
            }
            if (sourceFile.contentType == "application/pdf" && sourceFile.images.isEmpty()) {
                content += mapOf(
                    "type" to "input_file",
                    "filename" to sourceFile.fileName,
                    "file_data" to "data:application/pdf;base64,${Base64.getEncoder().encodeToString(sourceFile.bytes)}",
                    "detail" to "high"
                )
            }
            images.forEach { image ->
                val encoded = Base64.getEncoder().encodeToString(image.bytes)
                content += mapOf(
                    "type" to "input_image",
                    "image_url" to "data:${image.contentType};base64,$encoded",
                    "detail" to "high"
                )
            }
        }
        val response = try {
            restClient.post()
                .uri("$baseUrl/responses")
                .body(
                    mapOf(
                        "model" to properties.model.ifBlank { "gpt-4.1-mini" },
                        "store" to false,
                        "max_output_tokens" to 6000,
                        "instructions" to SYSTEM_INSTRUCTIONS,
                        "input" to listOf(mapOf("role" to "user", "content" to content)),
                        "text" to mapOf("format" to mapOf(
                            "type" to "json_schema",
                            "name" to "routine_draft",
                            "strict" to true,
                            "schema" to routineSchema(catalog, constraints)
                        ))
                    )
                )
                .retrieve()
                .body(JsonNode::class.java)
                ?: throw IllegalStateException("The AI provider returned an empty response.")
        } catch (error: RestClientException) {
            throw IllegalStateException("The AI provider could not create a routine. Check the AI provider settings and try again.")
        }
        val output = response.path("output").children().flatMap { message ->
            message.path("content").children().filter { it.path("type").asString() == "output_text" }
                .map { it.path("text").asString() }
        }.firstOrNull() ?: throw IllegalStateException("The AI provider did not return a routine. Try again with more detail.")
        val result = try {
            mapper.readTree(output)
        } catch (error: RuntimeException) {
            throw IllegalStateException("The AI provider returned an unreadable routine. Try again.")
        }
        val name = result.path("name").asString("").trim()
        val summary = result.path("summary").asString("").trim()
        val exercisesById = catalog.associateBy { (it["id"] as Number).toLong() }
        val days = result.path("days").children().map { day ->
            RoutineDraftDay(
                name = day.path("name").asString("").trim(),
                exercises = day.path("exercises").children().map { exercise ->
                    val id = exercise.path("exerciseId").longValue()
                    val knownExercise = exercisesById[id]
                    RoutineDraftExercise(
                        exerciseId = id,
                        exerciseName = knownExercise?.get("name")?.toString().orEmpty(),
                        sets = exercise.path("sets").intValue(),
                        minReps = exercise.path("minReps").intValue(),
                        maxReps = exercise.path("maxReps").intValue(),
                        restSeconds = exercise.path("restSeconds").intValue()
                    )
                }.toMutableList()
            )
        }.toMutableList()
        return RoutineDraft(name, summary, days)
    }

    private fun JsonNode.children(): List<JsonNode> = asSequence().toList()

    private val baseUrl get() = properties.baseUrl.ifBlank { "https://api.openai.com/v1" }.trimEnd('/')

    private fun routineSchema(catalog: List<Map<String, Any?>>, constraints: RoutineDraftConstraints): Map<String, Any> {
        val exerciseIds = catalog.map { (it["id"] as Number).toLong() }
        return mapOf(
            "type" to "object",
            "additionalProperties" to false,
            "properties" to mapOf(
                "name" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 160),
                "summary" to mapOf("type" to "string", "maxLength" to 1000),
                "days" to mapOf(
                    "type" to "array",
                    "minItems" to constraints.days.first,
                    "maxItems" to constraints.days.last,
                    "items" to mapOf(
                        "type" to "object",
                        "additionalProperties" to false,
                        "properties" to mapOf(
                            "name" to mapOf("type" to "string", "minLength" to 1, "maxLength" to 120),
                            "exercises" to mapOf(
                                "type" to "array",
                                "minItems" to constraints.exercisesPerDay.first,
                                "maxItems" to constraints.exercisesPerDay.last,
                                "items" to mapOf(
                                    "type" to "object",
                                    "additionalProperties" to false,
                                    "properties" to mapOf(
                                        "exerciseId" to mapOf("type" to "integer", "enum" to exerciseIds),
                                        "sets" to mapOf(
                                            "type" to "integer",
                                            "minimum" to constraints.setsPerExercise.first,
                                            "maximum" to constraints.setsPerExercise.last
                                        ),
                                        "minReps" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 1000),
                                        "maxReps" to mapOf("type" to "integer", "minimum" to 1, "maximum" to 1000),
                                        "restSeconds" to mapOf("type" to "integer", "minimum" to 0, "maximum" to 900)
                                    ),
                                    "required" to listOf("exerciseId", "sets", "minReps", "maxReps", "restSeconds")
                                )
                            )
                        ),
                        "required" to listOf("name", "exercises")
                    )
                )
            ),
            "required" to listOf("name", "summary", "days")
        )
    }

    companion object {
        private val SYSTEM_INSTRUCTIONS = """
            You create practical, balanced training routine drafts. Return a complete draft that matches the requested structure.
            Always give the routine a concise, non-empty name no longer than 160 characters.
            Pick exercise IDs only from the supplied active exercise catalog and use those exercises' exact names.
            Do not give medical diagnoses or claim a plan treats an injury. Respect limitations by avoiding affected movements and mention uncertainty in the summary.
            Create the required number of training days and exercises in each day, following the user's session length.
            Use the requested number of sets, rep values from 1 to 1000 with minimum no greater than maximum, and 0 to 900 seconds rest.
            Order priority compound movements first, then secondary compounds, isolation/accessory work, and core or conditioning last. Never alphabetize exercises.
            Never repeat the same exercise within a day. When equipment preferences are provided, never use unavailable equipment.
            If an attached file is unclear, do not invent missing details; make a reasonable conservative draft and explain assumptions in the summary.
        """.trimIndent()
    }
}
