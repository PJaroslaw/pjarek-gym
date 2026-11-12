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

class OpenAiResponsesRoutineDraftGeneratorTest {
    private val mapper = JsonMapper.builder().build()

    @Test
    fun `routine image is sent as inline multimodal input without storing the API response`() {
        val request = requestCaptureServer()
        val imageBytes = byteArrayOf(0x89.toByte(), 0x50, 0x4e, 0x47)
        val catalog = listOf(mapOf("id" to 42L, "name" to "Cable Row", "equipment" to "cable", "primary_muscles" to "back"))
        val generator = generator(request.first)

        val draft = generator.create(
            RoutineDraftRequest("Build strength", 3, "beginner", 45, "dumbbells", "Avoid overhead pressing"),
            catalog,
            RoutineSourceFile("routine.png", "image/png", imageBytes)
        )

        assertEquals("Three day strength", draft.name)
        assertEquals(42L, draft.days.single().exercises.single().exerciseId)
        val body = mapper.readTree(request.second.get())
        assertFalse(body.path("store").booleanValue())
        val inputContent = body.path("input").path(0).path("content")
        assertTrue(inputContent.path(1).path("type").asString() == "input_image")
        assertEquals(
            "data:image/png;base64,${Base64.getEncoder().encodeToString(imageBytes)}",
            inputContent.path(1).path("image_url").asString()
        )
        request.third.stop(0)
    }

    @Test
    fun `routine pdf is sent as inline file input`() {
        val request = requestCaptureServer()
        val pdfBytes = "%PDF-1.7".toByteArray(StandardCharsets.US_ASCII)
        val generator = generator(request.first)

        generator.create(
            RoutineDraftRequest("", 3, "intermediate", 60, "", ""),
            listOf(mapOf("id" to 42L, "name" to "Cable Row")),
            RoutineSourceFile("routine.pdf", "application/pdf", pdfBytes)
        )

        val body = mapper.readTree(request.second.get())
        val file = body.path("input").path(0).path("content").path(1)
        assertEquals("input_file", file.path("type").asString())
        assertEquals("routine.pdf", file.path("filename").asString())
        assertEquals(
            "data:application/pdf;base64,${Base64.getEncoder().encodeToString(pdfBytes)}",
            file.path("file_data").asString()
        )
        request.third.stop(0)
    }

    private fun generator(baseUrl: String): OpenAiResponsesRoutineDraftGenerator = OpenAiResponsesRoutineDraftGenerator(
        AiRoutineProperties(apiKey = "test-api-key", baseUrl = baseUrl),
        mapper
    )

    private fun requestCaptureServer(): Triple<String, AtomicReference<String>, HttpServer> {
        val capturedBody = AtomicReference("")
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/responses") { exchange ->
            assertEquals("Bearer test-api-key", exchange.requestHeaders.getFirst("Authorization"))
            capturedBody.set(exchange.requestBody.use { it.readBytes().toString(StandardCharsets.UTF_8) })
            val responseBody = mapper.writeValueAsString(
                mapOf(
                    "output" to listOf(
                        mapOf(
                            "type" to "message",
                            "content" to listOf(
                                mapOf(
                                    "type" to "output_text",
                                    "text" to """{"name":"Three day strength","summary":"Balanced plan","days":[{"name":"Full body","exercises":[{"exerciseId":42,"sets":3,"minReps":6,"maxReps":8,"restSeconds":90}]}]}"""
                                )
                            )
                        )
                    )
                )
            ).toByteArray(StandardCharsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBody.size.toLong())
            exchange.responseBody.use { it.write(responseBody) }
        }
        server.start()
        return Triple("http://127.0.0.1:${server.address.port}/v1", capturedBody, server)
    }
}
