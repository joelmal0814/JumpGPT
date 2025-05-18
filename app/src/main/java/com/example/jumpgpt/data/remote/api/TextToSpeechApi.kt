package com.example.jumpgpt.data.remote.api

import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Streaming

interface TextToSpeechApi {
    @Streaming
    @POST("audio/speech")
    suspend fun textToSpeech(
        @Header("Authorization") authorization: String,
        @Body request: TextToSpeechRequest
    ): Response<ResponseBody>
}

data class TextToSpeechRequest(
    val model: String = "tts-1",
    val input: String,
    val voice: String = "alloy",
    val response_format: String = "mp3"
) 