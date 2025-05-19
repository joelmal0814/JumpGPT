package com.example.jumpgpt.data.remote.model

import com.google.gson.annotations.SerializedName

data class TextToSpeechRequest(
    val model: String = "tts-1",
    val input: String,
    val voice: String = "alloy",
    @SerializedName("response_format")
    val responseFormat: String = "mp3",
    val speed: Float = 1.0f
) 