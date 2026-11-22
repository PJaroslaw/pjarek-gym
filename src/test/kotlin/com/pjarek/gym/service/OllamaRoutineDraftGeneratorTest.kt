package com.pjarek.gym.service

import com.pjarek.gym.config.AiRoutineProperties
import com.pjarek.gym.model.RoutineDraftRequest
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

class OllamaRoutineDraftGeneratorTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `sends image and structured output request to ollama without requiring an api key`() {
        val capturedBody = AtomicReference("")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/chat") { exchange ->
            capturedBody.set(exchange.requestBody.use { it.readBytes().toString(StandardCharsets.UTF_8) })
            val response = mapper.writeValueAsBytes(mapOf("message" to mapOf("content" to routineJson())))
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, response.size.toLong())
            exchange.responseBody.use { it.write(response) }
        }
        server.start()
        try {
            val imageBytes = byteArrayOf(1, 2, 3)
            val generator = OllamaRoutineDraftGenerator(
                AiRoutineProperties(provider = "ollama", baseUrl = "http://127.0.0.1:${server.address.port}", model = "llama3.2-vision"),
                mapper
            )
            val draft = generator.create(
                RoutineDraftRequest("Build strength", 3, "beginner", 45, "dumbbells", ""),
                listOf(mapOf("id" to 42L, "name" to "Cable Row")),
                RoutineSourceFile("routine.png", "image/png", imageBytes)
            )

            val body = mapper.readTree(capturedBody.get())
            assertEquals("llama3.2-vision", body.path("model").asString())
            assertFalse(body.path("stream").booleanValue())
            assertEquals("object", body.path("format").path("type").asString())
            assertEquals(Base64.getEncoder().encodeToString(imageBytes), body.path("messages").path(1).path("images").path(0).asString())
            assertEquals(42L, draft.days.single().exercises.single().exerciseId)
            assertTrue(body.path("messages").path(0).path("content").asString().contains("untrusted routine content"))
        } finally {
            server.stop(0)
        }
    }

    private fun routineJson() = """{"name":"Three day strength","summary":"Balanced plan","days":[{"name":"Full body","exercises":[{"exerciseId":42,"sets":3,"minReps":6,"maxReps":8,"restSeconds":90}]}]}"""
}
