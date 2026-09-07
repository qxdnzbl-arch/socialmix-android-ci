package com.qxdnzbl.lumichat

import android.content.Context
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

    fun send(context: Context, messages: List<ChatMessage>): String {
        val apiKey = SecureConfigStore.loadApiKey(context)
        if (apiKey.isBlank()) error("还没连接 AI，请先在左上角菜单里设置 AI 连接")
        return request(apiKey, messages, maxTokens = 4096)
    }

    fun test(apiKey: String): Boolean {
        if (apiKey.isBlank()) return false
        return runCatching {
            val reply = request(
                apiKey.trim(),
                listOf(ChatMessage("user", "只回复：连接成功")),
                maxTokens = 16
            )
            reply.isNotBlank()
        }.getOrDefault(false)
    }

    private fun request(apiKey: String, messages: List<ChatMessage>, maxTokens: Int): String {
        val payload = JSONObject().apply {
            put("model", MODEL)
            put("stream", false)
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

        val request = Request.Builder()
            .url(ENDPOINT)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
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
                        ?: when (response.code) {
                            401, 403 -> "AI 密钥无效，请重新设置"
                            429 -> "请求太频繁或余额不足，请稍后再试"
                            else -> "AI 连接失败（${response.code}）"
                        }
                )
            }

            val root = JSONObject(body)
            val choices = root.optJSONArray("choices")
            val message = choices?.optJSONObject(0)?.optJSONObject("message")
            val text = message?.optString("content").orEmpty()
            if (text.isBlank()) error("AI 没有返回内容")
            return text
        }
    }
}
