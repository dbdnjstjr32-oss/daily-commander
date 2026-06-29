package com.dc.aurora.network

import com.dc.aurora.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

object AnthropicClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    // CJU 게이트웨이 사용 여부 (키가 있으면 무료 CJU 우선)
    private val useCju get() = BuildConfig.CJU_API_KEY.isNotBlank()
    private val apiKey get() = if (useCju) BuildConfig.CJU_API_KEY else BuildConfig.ANTHROPIC_API_KEY
    private val apiUrl get() = if (useCju)
        "https://factchat-cloud.mindlogic.ai/v1/gateway/claude/v1/messages"
    else
        "https://api.anthropic.com/v1/messages"

    data class Message(val role: String, val content: String)

    private data class ApiRequest(
        val model: String,
        val max_tokens: Int,
        val system: String,
        val messages: List<Message>,
    )

    suspend fun call(
        model: String,
        system: String,
        messages: List<Message>,
        maxTokens: Int = 1024,
    ): String = withContext(Dispatchers.IO) {
        val body = gson.toJson(ApiRequest(model, maxTokens, system, messages))
        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("x-api-key", apiKey)
            .addHeader("anthropic-version", "2023-06-01")
            .addHeader("content-type", "application/json")
            .post(body.toRequestBody("application/json".toMediaType()))
            .build()

        val response = client.newCall(request).execute()
        val text = response.body?.string() ?: error("빈 응답")
        if (!response.isSuccessful) error("API 오류 ${response.code}: $text")

        gson.fromJson(text, JsonObject::class.java)
            .getAsJsonArray("content")
            ?.get(0)?.asJsonObject
            ?.get("text")?.asString
            ?: error("응답에 텍스트 없음: $text")
    }

    // claude-sonnet-4-6 — 메인 채팅
    const val SONNET = "claude-sonnet-4-6"
    // claude-haiku-4-5-20251001 — 빠른 파싱
    const val HAIKU = "claude-haiku-4-5-20251001"
}
