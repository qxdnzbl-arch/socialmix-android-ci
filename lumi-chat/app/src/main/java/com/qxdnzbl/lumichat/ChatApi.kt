package com.qxdnzbl.lumichat

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ChatApi {
    const val MODEL = "deepseek-ai/DeepSeek-V4-Flash"
    private const val ENDPOINT = "https://api.siliconflow.cn/v1/chat/completions"
    private const val SYSTEM_PROMPT = "你是微光，一个高质量中文AI助手。默认直接、清楚、务实地回答，优先给可执行结论；必要时解释关键原因，不铺垫，不重复用户问题。"

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun stream(context: Context, messages: List<ChatMessage>): Flow<String> = flow {
        val apiKey = SecureConfigStore.loadApiKey(context)
        if (apiKey.isBlank()) error("还没连接 AI，请先在左上角菜单里设置 AI 连接")

        val payload = buildPayload(messages, maxTokens = 4096, stream = true)
        val request = buildRequest(apiKey, payload)
        val response = client.newCall(request).execute()
        try {
            if (!response.isSuccessful) {
                val body = response.body?.string().orEmpty()
                throwApiError(response.code, body)
            }

            val source = response.body?.source() ?: error("AI 没有返回内容")
            var received = false
            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") break

                val delta = runCatching {
                    JSONObject(data)
                        .optJSONArray("choices")
                        ?.optJSONObject(0)
                        ?.optJSONObject("delta")
                        ?.optString("content")
                        .orEmpty()
                }.getOrDefault("")

                if (delta.isNotEmpty()) {
                    received = true
                    emit(delta)
                }
            }
            if (!received) error("AI 没有返回内容")
        } finally {
            response.close()
        }
    }.flowOn(Dispatchers.IO)

    fun test(apiKey: String): Boolean {
        if (apiKey.isBlank()) return false
        return runCatching {
            val reply = requestOnce(
                apiKey.trim(),
                listOf(ChatMessage("user", "只回复：连接成功")),
                maxTokens = 16
            )
            reply.isNotBlank()
        }.getOrDefault(false)
    }

    private fun requestOnce(apiKey: String, messages: List<ChatMessage>, maxTokens: Int): String {
        val payload = buildPayload(messages, maxTokens = maxTokens, stream = false)
        val request = buildRequest(apiKey, payload)

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throwApiError(response.code, body)

            val text = JSONObject(body)
                .optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
                .orEmpty()
            if (text.isBlank()) error("AI 没有返回内容")
            return text
        }
    }

    private fun buildPayload(messages: List<ChatMessage>, maxTokens: Int, stream: Boolean): JSONObject =
        JSONObject().apply {
            put("model", MODEL)
            put("stream", stream)
            put("max_tokens", maxTokens)
            put("enable_thinking", false)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", SYSTEM_PROMPT)
                })
                messages.takeLast(40).forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.text)
                    })
                }
            })
        }

    private fun buildRequest(apiKey: String, payload: JSONObject): Request =
        Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

    private fun throwApiError(code: Int, body: String): Nothing {
        val remoteMessage = runCatching {
            val error = JSONObject(body).opt("error")
            when (error) {
                is JSONObject -> error.optString("message")
                is String -> error
                else -> ""
            }
        }.getOrDefault("")

        error(
            remoteMessage.takeIf { it.isNotBlank() }
                ?: when (code) {
                    401, 403 -> "AI 密钥无效，请重新设置"
                    429 -> "请求太频繁或余额不足，请稍后再试"
                    else -> "AI 连接失败（$code）"
                }
        )
    }
}
