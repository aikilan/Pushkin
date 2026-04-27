package com.commitai.ai

import com.commitai.settings.CommitAiSettings
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
    private val settings: CommitAiSettings = CommitAiSettings.getInstance(),
) {
    private val httpClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    fun generateCommitMessage(userContext: String): String {
        val state = settings.state
        validateState(state)

        val requestBody = ChatRequest(
            model = state.model,
            temperature = state.temperature,
            messages = listOf(
                ChatMessage("system", state.promptTemplate),
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
            error("请求失败(${response.statusCode()}): ${response.body()}")
        }

        val parsed = json.decodeFromString(ChatResponse.serializer(), response.body())
        val content = parsed.choices.firstOrNull()?.message?.content?.trim().orEmpty()
        if (content.isBlank()) {
            error("AI 返回内容为空，请检查模型或提示词配置")
        }
        return content
    }

    private fun validateState(state: CommitAiSettings.State) {
        require(state.baseUrl.isNotBlank()) { "请先在设置中配置 Base URL" }
        require(state.baseUrl.startsWith("http://") || state.baseUrl.startsWith("https://")) {
            "Base URL 必须以 http:// 或 https:// 开头"
        }
        require(state.apiKey.isNotBlank()) { "请先在设置中配置 API Key" }
        require(state.model.isNotBlank()) { "请先在设置中配置 Model" }
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
