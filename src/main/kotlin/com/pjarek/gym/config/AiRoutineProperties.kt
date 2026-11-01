package com.pjarek.gym.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties("app.ai")
data class AiRoutineProperties(
    val provider: String = "openai",
    val apiKey: String = "",
    val baseUrl: String = "https://api.openai.com/v1",
    val model: String = "gpt-4.1-mini",
    val maxUploadBytes: Long = 10 * 1024 * 1024
) {
    val enabled: Boolean get() = provider.equals("ollama", ignoreCase = true) || apiKey.isNotBlank()
}
