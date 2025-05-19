package com.example.jumpgpt.di

import com.example.jumpgpt.data.remote.api.ChatApi
import com.example.jumpgpt.data.remote.api.TextToSpeechApi
import com.example.jumpgpt.domain.model.MessageRole
import com.example.jumpgpt.util.Constants
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideMessageRoleAdapter(): TypeAdapter<MessageRole> {
        return object : TypeAdapter<MessageRole>() {
            override fun write(out: JsonWriter, value: MessageRole?) {
                if (value == null) {
                    out.nullValue()
                } else {
                    out.value(value.name.lowercase())
                }
            }

            override fun read(reader: JsonReader): MessageRole? {
                val value = reader.nextString()
                return try {
                    MessageRole.valueOf(value.uppercase())
                } catch (e: IllegalArgumentException) {
                    null
                }
            }
        }
    }

    @Provides
    @Singleton
    fun provideGson(messageRoleAdapter: TypeAdapter<MessageRole>): Gson {
        return GsonBuilder()
            .registerTypeAdapter(MessageRole::class.java, messageRoleAdapter)
            .create()
    }
    
    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }

        return OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    
    @Provides
    @Singleton
    fun provideRetrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit {
        return Retrofit.Builder()
            .baseUrl(Constants.OPENAI_API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }
    
    @Provides
    @Singleton
    fun provideChatApi(retrofit: Retrofit): ChatApi {
        return retrofit.create(ChatApi::class.java)
    }
    
    @Provides
    @Singleton
    fun provideTextToSpeechApi(retrofit: Retrofit): TextToSpeechApi {
        return retrofit.create(TextToSpeechApi::class.java)
    }
} 