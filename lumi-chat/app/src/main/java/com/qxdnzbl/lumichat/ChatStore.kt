package com.qxdnzbl.lumichat

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

fun loadMessages(context: Context): List<ChatMessage> {
    val raw = context.getSharedPreferences("lumi_chat", Context.MODE_PRIVATE)
        .getString("messages", null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                add(ChatMessage(item.getString("role"), item.getString("text")))
            }
        }
    }.getOrDefault(emptyList())
}

fun saveMessages(context: Context, messages: List<ChatMessage>) {
    val array = JSONArray().apply {
        messages.forEach { message ->
            put(JSONObject().apply {
                put("role", message.role)
                put("text", message.text)
            })
        }
    }
    context.getSharedPreferences("lumi_chat", Context.MODE_PRIVATE)
        .edit()
        .putString("messages", array.toString())
        .apply()
}
