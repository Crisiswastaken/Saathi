package com.sohanreddy.sevak.network

import com.sohanreddy.sevak.Constants
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST
import java.util.concurrent.TimeUnit

data class GroqMessagePartText(
    val type: String = "text",
    val text: String
)

data class GroqImageUrl(val url: String)

data class GroqMessagePartImage(
    val type: String = "image_url",
    val image_url: GroqImageUrl
)

data class GroqRequestMessage(
    val role: String,
    val content: Any
)

data class GroqToolFunction(
    val name: String,
    val description: String,
    val parameters: Any
)

data class GroqTool(
    val type: String = "function",
    val function: GroqToolFunction
)

data class GroqToolCallFunction(
    val name: String,
    val arguments: String
)

data class GroqToolCall(
    val id: String? = null,
    val type: String? = null,
    val function: GroqToolCallFunction? = null
)

data class GroqRequest(
    val model: String = "llama-3.3-70b-versatile",
    val messages: List<GroqRequestMessage>,
    val temperature: Double = 0.4,
    val max_tokens: Int = 300,
    val tools: List<GroqTool>? = null,
    val tool_choice: Any? = null
)

data class GroqResponseMessage(
    val role: String? = null,
    val content: String? = null,
    val tool_calls: List<GroqToolCall>? = null
)

data class GroqChoice(val message: GroqResponseMessage)
data class GroqResponse(val choices: List<GroqChoice>)

fun groqTextMessage(role: String, text: String): GroqRequestMessage {
    return GroqRequestMessage(role = role, content = text)
}

fun groqVisionMessage(role: String, text: String, imageDataUrl: String): GroqRequestMessage {
    return GroqRequestMessage(
        role = role,
        content = listOf(
            GroqMessagePartText(text = text),
            GroqMessagePartImage(image_url = GroqImageUrl(imageDataUrl))
        )
    )
}

interface GroqApiService {
    @POST("openai/v1/chat/completions")
    suspend fun chat(
        @Header("Authorization") auth: String,
        @Body request: GroqRequest
    ): GroqResponse
}

object GroqApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY })
        .build()

    val service: GroqApiService = Retrofit.Builder()
        .baseUrl("https://api.groq.com/")
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(GroqApiService::class.java)

    suspend fun chat(request: GroqRequest): GroqResponse {
        return service.chat("Bearer ${Constants.GROQ_API_KEY}", request)
    }
}
