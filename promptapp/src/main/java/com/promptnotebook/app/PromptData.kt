package com.promptnotebook.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class PromptItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val category: String = "",
    val tags: List<String> = emptyList(),
    val content: String = "",
    val note: String = "",
    val favorite: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

object PromptStore {
    private const val PREFS = "prompt_notebook"
    private const val ITEMS = "items"
    private const val DRAFT = "draft"

    fun load(context: Context): List<PromptItem> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ITEMS, null)
            ?: return emptyList()
        return runCatching { decode(raw) }.getOrElse { emptyList() }
    }

    fun save(context: Context, items: List<PromptItem>) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(ITEMS, encode(items)).apply()
    }

    fun saveDraft(context: Context, item: PromptItem?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            if (item == null) remove(DRAFT) else putString(DRAFT, encode(listOf(item)))
        }.apply()
    }

    fun loadDraft(context: Context): PromptItem? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(DRAFT, null)
            ?: return null
        return runCatching { decode(raw).firstOrNull() }.getOrNull()
    }

    fun encode(items: List<PromptItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(JSONObject().apply {
                put("id", item.id)
                put("title", item.title)
                put("category", item.category)
                put("tags", JSONArray(item.tags))
                put("content", item.content)
                put("note", item.note)
                put("favorite", item.favorite)
                put("createdAt", item.createdAt)
                put("updatedAt", item.updatedAt)
            })
        }
        return arr.toString(2)
    }

    fun decode(raw: String): List<PromptItem> {
        val arr = JSONArray(raw)
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val ta = o.optJSONArray("tags") ?: JSONArray()
                val tags = buildList {
                    for (j in 0 until ta.length()) add(ta.optString(j))
                }.filter { it.isNotBlank() }
                add(
                    PromptItem(
                        id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                        title = o.optString("title"),
                        category = o.optString("category"),
                        tags = tags,
                        content = o.optString("content"),
                        note = o.optString("note"),
                        favorite = o.optBoolean("favorite", false),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }
}

fun parseTags(raw: String): List<String> =
    raw.split(',', '，', ';', '；')
        .map { it.trim().removePrefix("#") }
        .filter { it.isNotBlank() }
        .distinct()
        .take(20)
