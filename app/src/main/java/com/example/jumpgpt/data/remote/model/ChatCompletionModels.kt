package com.example.jumpgpt.data.remote.model

import com.example.jumpgpt.domain.model.MessageRole
import com.google.gson.annotations.SerializedName

data class ChatCompletionRequest(
    val messages: List<ChatMessage>,
    val model: String = "gpt-3.5-turbo",
    val temperature: Double = 0.7,
    val stream: Boolean = true,
    @SerializedName("max_tokens")
    val maxTokens: Int? = null,
    val n: Int = 1,
    val stop: List<String>? = null,
    @SerializedName("presence_penalty")
    val presencePenalty: Double? = null,
    @SerializedName("frequency_penalty")
    val frequencyPenalty: Double? = null
)

data class ChatCompletionResponse(
    val id: String,
    val choices: List<ChatChoice>,
    val created: Long,
    val model: String,
    val usage: ChatUsage?,
    @SerializedName("object")
    val objectType: String = "chat.completion"
)

data class ChatMessage(
    @SerializedName("role")
    val role: MessageRole,
    val content: String,
    val name: String? = null
) {
    companion object {
        fun fromDomain(message: com.example.jumpgpt.domain.model.Message): ChatMessage {
            return ChatMessage(
                role = message.role,
                content = message.content
            )
        }
    }
}

data class ChatChoice(
    val index: Int,
    val message: ChatMessage?,
    val delta: ChatDelta?,
    @SerializedName("finish_reason")
    val finishReason: String?
)

data class ChatDelta(
    val role: MessageRole? = null,
    val content: String? = null
)

data class ChatUsage(
    @SerializedName("prompt_tokens")
    val promptTokens: Int,
    @SerializedName("completion_tokens")
    val completionTokens: Int,
    @SerializedName("total_tokens")
    val totalTokens: Int
) 