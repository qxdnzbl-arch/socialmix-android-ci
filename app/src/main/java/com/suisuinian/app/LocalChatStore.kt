package com.suisuinian.app

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Durable per-account cache for chat UI state.
 *
 * Auth tokens stay in SharedPreferences. User-visible chat data lives here so process death,
 * slow network, or a temporary Supabase outage does not turn the UI into a blank loading page.
 */
class LocalChatStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    "socialmix_chat_cache.db",
    null,
    2
) {
    private val appContext = context.applicationContext

    private fun currentOwner(): String = appContext
        .getSharedPreferences("socialmix_live_session", Context.MODE_PRIVATE)
        .getString("user_id", "")
        .orEmpty()

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            create table profile_cache(
              owner_id text primary key,
              profile_id text not null,
              username text not null,
              display_name text not null,
              updated_at integer not null
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            create table friend_cache(
              owner_id text not null,
              id text not null,
              username text not null,
              display_name text not null,
              updated_at integer not null,
              primary key(owner_id, id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            create table conversation_cache(
              owner_id text not null,
              conversation_id text not null,
              friend_id text not null,
              friend_username text not null,
              friend_display_name text not null,
              last_message text not null default '',
              last_message_type text not null default '',
              last_message_at text not null default '',
              unread_count integer not null default 0,
              updated_at integer not null,
              primary key(owner_id, conversation_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            create table message_cache(
              owner_id text not null,
              local_id text not null,
              server_id text,
              conversation_id text not null,
              sender_id text not null,
              client_message_id text not null,
              content text not null default '',
              created_at text not null,
              delivery_state text not null,
              error_message text not null default '',
              updated_at integer not null,
              primary key(owner_id, local_id),
              unique(owner_id, conversation_id, sender_id, client_message_id)
            )
            """.trimIndent()
        )
        db.execSQL("create unique index message_server_id_idx on message_cache(owner_id, server_id) where server_id is not null")
        db.execSQL("create index message_conversation_created_idx on message_cache(owner_id, conversation_id, created_at, local_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("drop table if exists message_cache")
        db.execSQL("drop table if exists conversation_cache")
        db.execSQL("drop table if exists friend_cache")
        db.execSQL("drop table if exists profile_cache")
        onCreate(db)
    }

    // Convenience overloads use the session belonging to this Context. This also keeps
    // instrumentation tests with isolated Context/SharedPreferences truly isolated.
    fun clearAll() = clearOwner(currentOwner())
    fun saveProfile(profile: LiveProfile) = saveProfile(currentOwner(), profile)
    fun profile(): LiveProfile? = profile(currentOwner())
    fun replaceFriends(items: List<LiveProfile>) = replaceFriends(currentOwner(), items)
    fun friends(): List<LiveProfile> = friends(currentOwner())
    fun replaceConversations(items: List<ConversationSummary>) = replaceConversations(currentOwner(), items)
    fun saveConversation(item: ConversationSummary) = saveConversation(currentOwner(), item)
    fun conversations(): List<ConversationSummary> = conversations(currentOwner())
    fun conversationIdForFriend(friendId: String): String? = conversationIdForFriend(currentOwner(), friendId)
    fun messages(conversationId: String, limit: Int = 250): List<LiveMessage> = messages(currentOwner(), conversationId, limit)
    fun saveOptimistic(conversationId: String, message: LiveMessage) = saveOptimistic(currentOwner(), conversationId, message)
    fun upsertServerMessage(conversationId: String, message: LiveMessage) = upsertServerMessage(currentOwner(), conversationId, message)
    fun markSending(conversationId: String, senderId: String, clientMessageId: String) =
        markSending(currentOwner(), conversationId, senderId, clientMessageId)
    fun markFailed(conversationId: String, senderId: String, clientMessageId: String, error: String) =
        markFailed(currentOwner(), conversationId, senderId, clientMessageId, error)
    fun markInterruptedSendsFailed() = markInterruptedSendsFailed(currentOwner())

    @Synchronized
    fun clearOwner(ownerId: String) {
        if (ownerId.isBlank()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("message_cache", "owner_id=?", arrayOf(ownerId))
            db.delete("conversation_cache", "owner_id=?", arrayOf(ownerId))
            db.delete("friend_cache", "owner_id=?", arrayOf(ownerId))
            db.delete("profile_cache", "owner_id=?", arrayOf(ownerId))
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun saveProfile(ownerId: String, profile: LiveProfile) {
        if (ownerId.isBlank()) return
        val v = ContentValues().apply {
            put("owner_id", ownerId)
            put("profile_id", profile.id)
            put("username", profile.username)
            put("display_name", profile.displayName)
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.insertWithOnConflict("profile_cache", null, v, SQLiteDatabase.CONFLICT_REPLACE)
    }

    @Synchronized
    fun profile(ownerId: String): LiveProfile? {
        if (ownerId.isBlank()) return null
        return readableDatabase.rawQuery(
            "select profile_id, username, display_name from profile_cache where owner_id=? limit 1",
            arrayOf(ownerId)
        ).use { c ->
            if (!c.moveToFirst()) null else LiveProfile(c.string("profile_id"), c.string("username"), c.string("display_name"))
        }
    }

    @Synchronized
    fun replaceFriends(ownerId: String, items: List<LiveProfile>) {
        if (ownerId.isBlank()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("friend_cache", "owner_id=?", arrayOf(ownerId))
            val now = System.currentTimeMillis()
            items.forEach { friend ->
                val v = ContentValues().apply {
                    put("owner_id", ownerId)
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
    fun friends(ownerId: String): List<LiveProfile> {
        if (ownerId.isBlank()) return emptyList()
        return readableDatabase.rawQuery(
            "select id, username, display_name from friend_cache where owner_id=? order by display_name collate nocase, username collate nocase",
            arrayOf(ownerId)
        ).use { c ->
            buildList {
                while (c.moveToNext()) add(LiveProfile(c.string("id"), c.string("username"), c.string("display_name")))
            }
        }
    }

    @Synchronized
    fun replaceConversations(ownerId: String, items: List<ConversationSummary>) {
        if (ownerId.isBlank()) return
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("conversation_cache", "owner_id=?", arrayOf(ownerId))
            items.forEach { saveConversation(db, ownerId, it) }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    @Synchronized
    fun saveConversation(ownerId: String, item: ConversationSummary) {
        if (ownerId.isBlank()) return
        saveConversation(writableDatabase, ownerId, item)
    }

    private fun saveConversation(db: SQLiteDatabase, ownerId: String, item: ConversationSummary) {
        val v = ContentValues().apply {
            put("owner_id", ownerId)
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
    fun conversations(ownerId: String): List<ConversationSummary> {
        if (ownerId.isBlank()) return emptyList()
        return readableDatabase.rawQuery(
            """
            select conversation_id, friend_id, friend_username, friend_display_name,
                   last_message, last_message_type, last_message_at, unread_count
            from conversation_cache
            where owner_id=?
            order by case when last_message_at='' then 1 else 0 end, last_message_at desc, updated_at desc
            """.trimIndent(),
            arrayOf(ownerId)
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
    }

    @Synchronized
    fun conversationIdForFriend(ownerId: String, friendId: String): String? {
        if (ownerId.isBlank() || friendId.isBlank()) return null
        return readableDatabase.rawQuery(
            "select conversation_id from conversation_cache where owner_id=? and friend_id=? limit 1",
            arrayOf(ownerId, friendId)
        ).use { c -> if (c.moveToFirst()) c.string("conversation_id") else null }
    }

    @Synchronized
    fun messages(ownerId: String, conversationId: String, limit: Int = 250): List<LiveMessage> {
        if (ownerId.isBlank() || conversationId.isBlank()) return emptyList()
        return readableDatabase.rawQuery(
            """
            select local_id, server_id, sender_id, client_message_id, content, created_at,
                   delivery_state, error_message
            from (
              select * from message_cache
              where owner_id=? and conversation_id=?
              order by created_at desc, local_id desc
              limit ?
            ) recent
            order by created_at asc, local_id asc
            """.trimIndent(),
            arrayOf(ownerId, conversationId, limit.toString())
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
    fun saveOptimistic(ownerId: String, conversationId: String, message: LiveMessage) {
        if (ownerId.isBlank()) return
        val clientId = message.clientMessageId.ifBlank { message.id.removePrefix("local:") }
        val v = ContentValues().apply {
            put("owner_id", ownerId)
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
    fun upsertServerMessage(ownerId: String, conversationId: String, message: LiveMessage) {
        if (ownerId.isBlank()) return
        val db = writableDatabase
        val clientId = message.clientMessageId.ifBlank { message.id }
        val existingLocalId = db.rawQuery(
            "select local_id from message_cache where owner_id=? and conversation_id=? and sender_id=? and client_message_id=? limit 1",
            arrayOf(ownerId, conversationId, message.senderId, clientId)
        ).use { c -> if (c.moveToFirst()) c.string("local_id") else null }

        val localId = existingLocalId ?: message.id
        val v = ContentValues().apply {
            put("owner_id", ownerId)
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
    fun markSending(ownerId: String, conversationId: String, senderId: String, clientMessageId: String) {
        if (ownerId.isBlank()) return
        val v = ContentValues().apply {
            put("delivery_state", MessageDelivery.SENDING.name)
            put("error_message", "")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update(
            "message_cache",
            v,
            "owner_id=? and conversation_id=? and sender_id=? and client_message_id=?",
            arrayOf(ownerId, conversationId, senderId, clientMessageId)
        )
    }

    @Synchronized
    fun markFailed(ownerId: String, conversationId: String, senderId: String, clientMessageId: String, error: String) {
        if (ownerId.isBlank()) return
        val v = ContentValues().apply {
            put("delivery_state", MessageDelivery.FAILED.name)
            put("error_message", error.take(160))
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update(
            "message_cache",
            v,
            "owner_id=? and conversation_id=? and sender_id=? and client_message_id=?",
            arrayOf(ownerId, conversationId, senderId, clientMessageId)
        )
    }

    @Synchronized
    fun markInterruptedSendsFailed(ownerId: String) {
        if (ownerId.isBlank()) return
        val v = ContentValues().apply {
            put("delivery_state", MessageDelivery.FAILED.name)
            put("error_message", "发送被中断，点此重试")
            put("updated_at", System.currentTimeMillis())
        }
        writableDatabase.update(
            "message_cache",
            v,
            "owner_id=? and delivery_state=?",
            arrayOf(ownerId, MessageDelivery.SENDING.name)
        )
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
