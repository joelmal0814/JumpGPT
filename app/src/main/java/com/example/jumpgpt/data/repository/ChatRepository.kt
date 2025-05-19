package com.example.jumpgpt.data.repository

import android.content.Context
import android.util.Log
import com.example.jumpgpt.data.local.dao.ConversationDao
import com.example.jumpgpt.data.mapper.toDomain
import com.example.jumpgpt.data.mapper.toEntity
import com.example.jumpgpt.data.remote.api.ChatApi
import com.example.jumpgpt.data.remote.model.ChatCompletionRequest
import com.example.jumpgpt.data.remote.model.ChatMessage
import com.example.jumpgpt.data.remote.model.ChatDelta
import com.example.jumpgpt.data.remote.model.TextToSpeechRequest
import com.example.jumpgpt.domain.model.Conversation
import com.example.jumpgpt.domain.model.Message
import com.example.jumpgpt.domain.model.MessageRole
import com.example.jumpgpt.util.Constants
import com.example.jumpgpt.util.TimeUtil
import com.google.gson.Gson
import com.google.gson.JsonObject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import okhttp3.MultipartBody

private const val TAG = "TokenStreaming"
private const val AUDIO_CACHE_DIR = "tts_cache"

@Singleton
class ChatRepository @Inject constructor(
    private val chatApi: ChatApi,
    private val conversationDao: ConversationDao,
    private val gson: Gson,
    @ApplicationContext private val context: Context
) {
    private val audioCacheDir: File by lazy {
        File(context.cacheDir, AUDIO_CACHE_DIR).also { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }
    }

    fun getAllConversations(): Flow<List<Conversation>> {
        return conversationDao.getAllConversations().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    suspend fun getConversationById(id: String): Conversation? {
        return conversationDao.getConversationById(id)?.toDomain()
    }

    suspend fun createConversation(messages: List<Message>): String {
        val conversationId = UUID.randomUUID().toString()
        val title = if (messages.isNotEmpty()) {
            generateTitle(messages)
        } else {
            "New Conversation"
        }
        val lastMessage = messages.lastOrNull { !it.isThinking }?.content
        
        val conversation = Conversation(
            id = conversationId,
            title = title,
            lastMessage = lastMessage,
            lastUpdated = TimeUtil.now(),
            messages = messages
        )
        
        conversationDao.insertConversation(conversation.toEntity())
        return conversationId
    }

    suspend fun updateConversation(conversation: Conversation) {
        // If this is the first message exchange, generate a title
        val updatedConversation = if (conversation.messages.size >= 2 && conversation.title == "New Conversation") {
            conversation.copy(
                title = generateTitle(conversation.messages)
            )
        } else {
            conversation
        }
        
        conversationDao.updateConversation(updatedConversation.toEntity())
    }

    suspend fun deleteConversation(id: String) {
        conversationDao.deleteConversation(id)
    }

    private suspend fun generateTitle(messages: List<Message>): String {
        try {
            val prompt = "Generate a short, descriptive title (max 50 characters) for this conversation based on the following messages. Do not use any quotation marks in the title:\n\n" +
                messages.joinToString("\n") { "${it.role}: ${it.content}" }

            val request = ChatCompletionRequest(
                messages = listOf(
                    ChatMessage(
                        role = MessageRole.SYSTEM,
                        content = "You are a helpful assistant that generates short, descriptive titles for conversations. Keep titles under 50 characters and do not use any quotation marks."
                    ),
                    ChatMessage(
                        role = MessageRole.USER,
                        content = prompt
                    )
                ),
                model = Constants.DEFAULT_MODEL,
                temperature = 0.7,
                stream = false
            )

            val response = chatApi.generateTitle(
                authorization = Constants.OPENAI_API_KEY,
                request = request
            )

            if (!response.isSuccessful) {
                throw Exception("Failed to generate title: ${response.errorBody()?.string()}")
            }

            return response.body()?.choices?.firstOrNull()?.message?.content?.trim()?.replace("\"", "")
                ?: "New Conversation"

        } catch (e: Exception) {
            Log.e(TAG, "Error generating title", e)
            return "New Conversation"
        }
    }

    suspend fun sendMessage(messages: List<Message>): Flow<Message> = flow {
        try {
            // Find the AI message ID from the input messages
            val aiMessageId = messages.find { it.role == MessageRole.ASSISTANT && it.isThinking }?.id
                ?: throw IllegalStateException("No assistant message found")

            // Construct the request
            val request = ChatCompletionRequest(
                model = Constants.DEFAULT_MODEL,
                messages = messages.map { ChatMessage.fromDomain(it) },
                temperature = Constants.DEFAULT_TEMPERATURE,
                maxTokens = Constants.DEFAULT_MAX_TOKENS,
                stream = false
            )

            // Make the API call
            val response = chatApi.getChatCompletion(
                authorization = Constants.OPENAI_API_KEY,
                request = request
            )

            if (!response.isSuccessful) {
                throw Exception("API call failed with code ${response.code()}: ${response.errorBody()?.string()}")
            }

            val responseBody = response.body() ?: throw Exception("Empty response from API")
            val content = responseBody.choices.firstOrNull()?.message?.content
                ?: throw Exception("No content in response")

            // Emit the complete message
            emit(Message(
                id = aiMessageId,
                content = content,
                role = MessageRole.ASSISTANT,
                timestamp = TimeUtil.now(),
                isStreaming = false
            ))
        } catch (e: Exception) {
            Log.e("ChatRepository", "Error in sendMessage", e)
            emit(Message(
                id = "error_${UUID.randomUUID()}",
                content = "Error: ${e.message}",
                role = MessageRole.ASSISTANT,
                timestamp = TimeUtil.now(),
                isError = true
            ))
        }
    }

    private suspend fun parseStreamingResponse(
        responseBody: ResponseBody,
        startTime: Long,
        onDelta: suspend (ChatDelta) -> Unit
    ) {
        responseBody.byteStream().bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                if (line.startsWith("data: ")) {
                    val json = line.substring(6).trim()
                    if (json == "[DONE]") {
                        Log.d(TAG, "Repository: [DONE] signal received at ${System.currentTimeMillis() - startTime}ms")
                        break
                    }

                    try {
                        val jsonObject = gson.fromJson(json, JsonObject::class.java)
                        val choices = jsonObject.getAsJsonArray("choices")
                        if (choices != null && choices.size() > 0) {
                            val delta = choices[0].asJsonObject.get("delta")?.asJsonObject
                            if (delta != null) {
                                val chatDelta = gson.fromJson(delta, ChatDelta::class.java)
                                if (chatDelta.content != null) {
                                    Log.d(TAG, "Repository: Token '${chatDelta.content}' received at ${System.currentTimeMillis() - startTime}ms")
                                }
                                onDelta(chatDelta)
                                // Add a tiny delay to ensure UI can keep up
                                delay(16) // One frame at 60fps
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Repository: Error parsing JSON at ${System.currentTimeMillis() - startTime}ms: $json", e)
                        continue
                    }
                }
            }
        }
    }

    suspend fun textToSpeech(text: String, messageId: String): File {
        // Check if cached file exists
        val cachedFile = File(audioCacheDir, "$messageId.mp3")
        if (cachedFile.exists()) {
            return cachedFile
        }

        val request = TextToSpeechRequest(input = text)
        val response = chatApi.textToSpeech(
            authorization = Constants.OPENAI_API_KEY,
            request = request
        )

        if (!response.isSuccessful) {
            throw Exception("TTS API call failed with code ${response.code()}: ${response.errorBody()?.string()}")
        }

        // Write the audio data to the cache file
        response.body()?.byteStream()?.use { input ->
            cachedFile.outputStream().use { output ->
                input.copyTo(output)
            }
        } ?: throw Exception("Empty response from TTS API")

        return cachedFile
    }

    private fun cleanupOldCacheFiles() {
        try {
            val maxCacheAge = 24 * 60 * 60 * 1000L // 24 hours in milliseconds
            val now = System.currentTimeMillis()
            
            audioCacheDir.listFiles()?.forEach { file ->
                if (now - file.lastModified() > maxCacheAge) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up cache files", e)
        }
    }

    suspend fun speechToText(audioFile: File): String {
        if (!audioFile.exists()) {
            throw Exception("Audio file does not exist")
        }
        
        if (audioFile.length() == 0L) {
            throw Exception("Audio file is empty")
        }

        try {
            val requestFile = RequestBody.create(
                "audio/mp3".toMediaType(),
                audioFile
            )

            val filePart = MultipartBody.Part.createFormData(
                "file",
                audioFile.name,
                requestFile
            )

            val response = chatApi.speechToText(
                authorization = Constants.OPENAI_API_KEY,
                file = filePart
            )

            if (!response.isSuccessful) {
                throw Exception("Speech to text API call failed with code ${response.code()}: ${response.errorBody()?.string()}")
            }

            return response.body()?.text ?: throw Exception("Empty response from speech to text API")
        } catch (e: Exception) {
            Log.e(TAG, "Error in speechToText", e)
            throw Exception("Failed to convert speech to text: ${e.localizedMessage}")
        } finally {
            // Clean up the audio file after we're done with it
            try {
                if (audioFile.exists()) {
                    audioFile.delete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error deleting audio file", e)
            }
        }
    }

    init {
        cleanupOldCacheFiles()
    }
} 