package com.qxdnzbl.lumichat

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ChatApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun send(messages: List<ChatMessage>): String {
        if (BuildConfig.API_BASE_URL.contains("replace-me")) {
            error("AI 连接还没完成配置")
        }

        val payload = JSONObject().apply {
            put("messages", JSONArray().apply {
                messages.takeLast(40).forEach { message ->
                    put(JSONObject().apply {
                        put("role", message.role)
                        put("content", message.text)
                    })
                }
            })
        }

        val request = Request.Builder()
            .url(BuildConfig.API_BASE_URL.trimEnd('/') + "/chat")
            .header("Authorization", "Bearer ${BuildConfig.APP_ACCESS_TOKEN}")
            .post(payload.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(body).optString("error") }.getOrNull()
                error(message?.takeIf { it.isNotBlank() } ?: "连接失败（${response.code}）")
            }
            val text = JSONObject(body).optString("text")
            if (text.isBlank()) error("AI 没有返回内容")
            return text
        }
    }
}
