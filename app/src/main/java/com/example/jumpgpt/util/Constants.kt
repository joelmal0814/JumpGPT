package com.example.jumpgpt.util

import com.example.jumpgpt.BuildConfig

object Constants {
    val OPENAI_API_KEY = BuildConfig.OPENAI_API_KEY.let { "Bearer $it" }
        ?: throw IllegalStateException("OpenAI API key not found in local.properties. Please add OPENAI_API_KEY=your_key to local.properties")
    
    const val OPENAI_API_BASE_URL = "https://api.openai.com/v1/"
    const val DEFAULT_MODEL = "gpt-3.5-turbo"
    const val DEFAULT_TEMPERATURE = 0.7
    const val DEFAULT_MAX_TOKENS = 1000
} 