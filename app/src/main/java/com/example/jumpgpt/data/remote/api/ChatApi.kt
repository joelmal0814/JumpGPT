package com.example.jumpgpt.data.remote.api

import com.example.jumpgpt.data.remote.model.ChatCompletionRequest
import com.example.jumpgpt.data.remote.model.ChatCompletionResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface ChatApi {
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>
} 