package com.example.jumpgpt.data.remote.api

import com.example.jumpgpt.data.remote.model.ChatCompletionRequest
import com.example.jumpgpt.data.remote.model.ChatCompletionResponse
import com.example.jumpgpt.data.remote.model.TextToSpeechRequest
import com.example.jumpgpt.data.remote.model.SpeechToTextResponse
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.*

interface ChatApi {
    @POST("chat/completions")
    suspend fun getChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>

    @POST("chat/completions")
    @Streaming
    suspend fun getChatCompletionStream(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Response<ResponseBody>
    
    @POST("audio/speech")
    @Streaming
    suspend fun textToSpeech(
        @Header("Authorization") authorization: String,
        @Body request: TextToSpeechRequest
    ): Response<ResponseBody>

    @POST("audio/transcriptions")
    @Multipart
    suspend fun speechToText(
        @Header("Authorization") authorization: String,
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody = "whisper-1".toRequestBody(),
        @Part("language") language: RequestBody = "en".toRequestBody()
    ): Response<SpeechToTextResponse>

    @POST("chat/completions")
    suspend fun generateTitle(
        @Header("Authorization") authorization: String,
        @Body request: ChatCompletionRequest
    ): Response<ChatCompletionResponse>
} 