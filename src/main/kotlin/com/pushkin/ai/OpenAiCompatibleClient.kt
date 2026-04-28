package com.pushkin.ai

import com.pushkin.i18n.PushkinBundle
import com.pushkin.settings.PushkinSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

class OpenAiCompatibleClient(
    private val settings: PushkinSettings = PushkinSettings.getInstance(),
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    // systemPrompt 由调用方按“项目提示词优先，否则全局提示词”规则解析后传入。
    fun generateCommitMessage(userContext: String, systemPrompt: String): String {
        val state = settings.state
        validateState(state)

        val requestBody = ChatRequest(
            model = state.model,
            temperature = state.temperature,
            messages = listOf(
                ChatMessage("system", systemPrompt),
                ChatMessage("user", userContext),
            ),
        )

        val endpoint = state.baseUrl.trimEnd('/') + "/v1/chat/completions"
        val request = HttpRequest.newBuilder(URI.create(endpoint))
            .timeout(Duration.ofSeconds(60))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer ${state.apiKey}")
            .POST(HttpRequest.BodyPublishers.ofString(json.encodeToString(requestBody)))
            .build()

        val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            error(PushkinBundle.message("client.error.requestFailed", response.statusCode(), response.body()))
        }

        val parsed = json.decodeFromString(ChatResponse.serializer(), response.body())
        val content = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (content.isBlank()) {
            error(PushkinBundle.message("client.error.emptyResponse"))
        }
        return content
    }

    private fun validateState(state: PushkinSettings.State) {
        require(state.baseUrl.isNotBlank()) { PushkinBundle.message("client.validate.baseUrl.blank") }
        require(state.baseUrl.startsWith("http://") || state.baseUrl.startsWith("https://")) {
            PushkinBundle.message("client.validate.baseUrl.invalid")
        }
        require(state.apiKey.isNotBlank()) { PushkinBundle.message("client.validate.apiKey.blank") }
        require(state.model.isNotBlank()) { PushkinBundle.message("client.validate.model.blank") }
    }

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double,
    )

    @Serializable
    private data class ChatMessage(
        val role: String,
        val content: String,
    )

    @Serializable
    private data class ChatResponse(
        val choices: List<Choice>,
    ) {
        @Serializable
        data class Choice(
            val message: ChoiceMessage,
        )

        @Serializable
        data class ChoiceMessage(
            val role: String,
            val content: String,
            @SerialName("reasoning_content")
            val reasoningContent: String? = null,
        )
    }
}
