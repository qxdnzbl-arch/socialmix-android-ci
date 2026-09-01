package com.suisuinian.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Small durable cache for chat UI state.
 *
 * Auth tokens stay in SharedPreferences; user-visible chat data lives here so process death,
 * slow network, or a temporary Supabase outage does not turn the UI into a blank loading page.
 */
class LocalChatStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "socialmix_chat_cache.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table profile_cache(
              id text primary key,
              username text not null,
              display_name text not null,
              updated_at integer not null
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            create table friend_cache(
              id text primary key,
              username text not null,
              display_name text not null,
              updated_at integer not null
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            create table conversation_cache(
              conversation_id text primary key,
              friend_id text not null,
              friend_username text not null,
              friend_display_name text not null,
              last_message text not null default '',
              last_message_type text not null default '',
              last_message_at text not null default '',
              unread_count integer not null default 0,
              updated_at integer not null
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            create table message_cache(
              local_id text primary key,
              server_id text,
              conversation_id text not null,
              sender_id text not null,
              client_message_id text not null,
              content text not null default '',
              created_at text not null,
              delivery_state text not null,
              error_message text not null default '',
              updated_at integer not null,
              unique(conversation_id, sender_id, client_message_id)
            )
            """.trimIndent()
        )
        db.execSQL("create unique index message_server_id_idx on message_cache(server_id) where server_id is not null")
        db.execSQL("create index message_conversation_created_idx on message_cache(conversation_id, created_at, local_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    @Synchronized
    fun clearAll() {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("message_cache", null, null)
            writableDatabase.delete("conversation_cache", null, null)
            writableDatabase.delete("friend_cache", null, null)
            writableDatabase.delete("profile_cache", null, null)
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    @Synchronized
    fun saveProfile(profile: LiveProfile) {
        val v = ContentValues().apply {
            put("id", profile.id)
            put("username", profile.username)
            put("display_name", profile.displayName)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("profile_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun profile(): LiveProfile? = readableDatabase.rawQuery(
        "select id, username, display_name from profile_cache order by updated_at desc limit 1",
        null
    ).use { c ->
        if (!c.moveToFirst()) null else LiveProfile(c.string("id"), c.string("username"), c.string("display_name"))
    }

    @Synchronized
    fun replaceFriends(items: List<LiveProfile>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("friend_cache", null, null)
            val now = System.currentTimeMillis()
            items.forEach { friend ->
                val v = ContentValues().apply {
                    put("id", friend.id)
                    put("username", friend.username)
                    put("display_name", friend.displayName)
                    put("updated_at", now)
                }
                db.insertWithOnConflict("friend_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun friends(): List<LiveProfile> = readableDatabase.rawQuery(
        "select id, username, display_name from friend_cache order by display_name collate nocase, username collate nocase",
        null
    ).use { c ->
        buildList {
            while (c.moveToNext()) add(LiveProfile(c.string("id"), c.string("username"), c.string("display_name")))
        }
    }

    @Synchronized
    fun replaceConversations(items: List<ConversationSummary>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("conversation_cache", null, null)
            items.forEach { saveConversation(db, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun saveConversation(item: ConversationSummary) = saveConversation(writableDatabase, item)

    private fun saveConversation(db: SQLiteDatabase, item: ConversationSummary) {
        val v = ContentValues().apply {
            put("conversation_id", item.conversationId)
            put("friend_id", item.friend.id)
            put("friend_username", item.friend.username)
            put("friend_display_name", item.friend.displayName)
            put("last_message", item.lastMessage)
            put("last_message_type", item.lastMessageType)
            put("last_message_at", item.lastMessageAt)
            put("unread_count", item.unreadCount)
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("conversation_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun conversations(): List<ConversationSummary> = readableDatabase.rawQuery(
        """
        select conversation_id, friend_id, friend_username, friend_display_name,
               last_message, last_message_type, last_message_at, unread_count
        from conversation_cache
        order by case when last_message_at='' then 1 else 0 end, last_message_at desc, updated_at desc
        """.trimIndent(),
        null
    ).use { c ->
        buildList {
            while (c.moveToNext()) {
                add(
                    ConversationSummary(
                        conversationId = c.string("conversation_id"),
                        friend = LiveProfile(c.string("friend_id"), c.string("friend_username"), c.string("friend_display_name")),
                        lastMessage = c.string("last_message"),
                        lastMessageType = c.string("last_message_type"),
                        lastMessageAt = c.string("last_message_at"),
                        unreadCount = c.long("unread_count")
                    )
                )
            }
        }
    }

    @Synchronized
    fun conversationIdForFriend(friendId: String): String? = readableDatabase.rawQuery(
        "select conversation_id from conversation_cache where friend_id=? limit 1",
        arrayOf(friendId)
    ).use { c -> if (c.moveToFirst()) c.string("conversation_id") else null }

    @Synchronized
    fun messages(conversationId: String, limit: Int = 250): List<LiveMessage> {
        if (conversationId.isBlank()) return emptyList()
        return readableDatabase.rawQuery(
            """
            select local_id, server_id, sender_id, client_message_id, content, created_at,
                   delivery_state, error_message
            from (
              select * from message_cache
              where conversation_id=?
              order by created_at desc, local_id desc
              limit ?
            ) recent
            order by created_at asc, local_id asc
            """.trimIndent(),
            arrayOf(conversationId, limit.toString())
        ).use { c ->
            buildList {
                while (c.moveToNext()) {
                    val state = runCatching { MessageDelivery.valueOf(c.string("delivery_state")) }.getOrDefault(MessageDelivery.SENT)
                    add(
                        LiveMessage(
                            id = c.string("server_id").ifBlank { c.string("local_id") },
                            senderId = c.string("sender_id"),
                            content = c.string("content"),
                            createdAt = c.string("created_at"),
                            clientMessageId = c.string("client_message_id"),
                            delivery = state,
                            errorMessage = c.string("error_message")
                        )
                    )
                }
            }
        }
    }

    @Synchronized
    fun saveOptimistic(conversationId: String, message: LiveMessage) {
        val clientId = message.clientMessageId.ifBlank { message.id.removePrefix("local:") }
        val v = ContentValues().apply {
            put("local_id", "local:$clientId")
            putNull("server_id")
            put("conversation_id", conversationId)
            put("sender_id", message.senderId)
            put("client_message_id", clientId)
            put("content", message.content)
            put("created_at", message.createdAt)
            put("delivery_state", MessageDelivery.SENDING.name)
            put("error_message", "")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("message_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun upsertServerMessage(conversationId: String, message: LiveMessage) {
        val db = writableDatabase
        val clientId = message.clientMessageId.ifBlank { message.id }
        val existingLocalId = db.rawQuery(
            "select local_id from message_cache where conversation_id=? and sender_id=? and client_message_id=? limit 1",
            arrayOf(conversationId, message.senderId, clientId)
        ).use { c -> if (c.moveToFirst()) c.string("local_id") else null }

        val localId = existingLocalId ?: message.id
        val v = ContentValues().apply {
            put("local_id", localId)
            put("server_id", message.id)
            put("conversation_id", conversationId)
            put("sender_id", message.senderId)
            put("client_message_id", clientId)
            put("content", message.content)
            put("created_at", message.createdAt)
            put("delivery_state", MessageDelivery.SENT.name)
            put("error_message", "")
            put("updated_at", System.currentTimeMillis())
        }
        db.insertWithOnConflict("message_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun markSending(conversationId: String, senderId: String, clientMessageId: String) {
        val v = ContentValues().apply {
            put("delivery_state", MessageDelivery.SENDING.name)
            put("error_message", "")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update(
            "message_cache",
            v,
            "conversation_id=? and sender_id=? and client_message_id=?",
            arrayOf(conversationId, senderId, clientMessageId)
        )
    }

    @Synchronized
    fun markFailed(conversationId: String, senderId: String, clientMessageId: String, error: String) {
        val v = ContentValues().apply {
            put("delivery_state", MessageDelivery.FAILED.name)
            put("error_message", error.take(160))
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update(
            "message_cache",
            v,
            "conversation_id=? and sender_id=? and client_message_id=?",
            arrayOf(conversationId, senderId, clientMessageId)
        )
    }

    @Synchronized
    fun markInterruptedSendsFailed() {
        val v = ContentValues().apply {
            put("delivery_state", MessageDelivery.FAILED.name)
            put("error_message", "发送被中断，点此重试")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update("message_cache", v, "delivery_state=?", arrayOf(MessageDelivery.SENDING.name))
    }

    private fun Cursor.string(column: String): String {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) "" else getString(index).orEmpty()
    }

    private fun Cursor.long(column: String): Long {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) 0L else getLong(index)
    }
}
