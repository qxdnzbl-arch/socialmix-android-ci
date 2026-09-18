package com.suisuinian.app

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val KEHUA_SUPABASE_URL = "https://nvwdtfnhsyfdopaxdylx.supabase.co"
private const val KEHUA_SUPABASE_KEY = "sb_publishable_S4IE-ziO7WQ_JAK9tuQGgQ_cszwKBWB"
private val KEHUA_JSON = "application/json; charset=utf-8".toMediaType()

data class KehuaAuthResult(
    val success: Boolean,
    val needsVerification: Boolean = false,
    val message: String = ""
)

data class KehuaProfile(
    val id: String,
    val nickname: String,
    val bio: String,
    val avatarSeed: String,
    val avatarPath: String?
)

data class KehuaPost(
    val id: String,
    val body: String,
    val imagePath: String?,
    val createdAt: String,
    val resonanceCount: Long = 0
)

data class KehuaResonance(
    val matchId: String,
    val targetPostId: String,
    val targetUserId: String?,
    val body: String,
    val imagePath: String?,
    val createdAt: String,
    val score: Double,
    val isLit: Boolean,
    val nickname: String?,
    val bio: String?,
    val avatarSeed: String?,
    val avatarPath: String?,
    val conversationId: String?
)

data class KehuaConversation(
    val id: String,
    val otherUserId: String,
    val nickname: String,
    val bio: String,
    val avatarSeed: String,
    val avatarPath: String?,
    val lastMessage: String?,
    val lastAt: String?
)

data class KehuaFriendRequest(
    val id: String,
    val senderId: String,
    val nickname: String,
    val bio: String,
    val avatarSeed: String,
    val avatarPath: String?,
    val createdAt: String
)

data class KehuaFriend(
    val id: String,
    val nickname: String,
    val bio: String,
    val avatarSeed: String,
    val avatarPath: String?,
    val latestPostId: String?,
    val latestPostBody: String?,
    val latestPostImagePath: String?,
    val latestPostAt: String?
)

data class KehuaMessage(
    val id: Long,
    val senderId: String,
    val body: String,
    val createdAt: String
)

class KehuaApi(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("kehua_prod_session", Context.MODE_PRIVATE)
    private val sessionLock = Any()
    private val http = OkHttpClient.Builder()
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .callTimeout(14, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    @Volatile private var firebase: FirebaseAuth? = null

    fun isFirebaseConfigured(): Boolean =
        appContext.getString(R.string.firebase_api_key).isNotBlank() &&
            appContext.getString(R.string.firebase_app_id).isNotBlank() &&
            appContext.getString(R.string.firebase_project_id).isNotBlank()

    fun userId(): String = prefs.getString("user_id", "") ?: ""
    private fun accessToken(): String = prefs.getString("access_token", "") ?: ""
    private fun refreshToken(): String = prefs.getString("refresh_token", "") ?: ""

    suspend fun restoreSession(): Boolean = withContext(Dispatchers.IO) {
        val auth = runCatching { firebaseAuth() }.getOrNull() ?: return@withContext false
        val current = auth.currentUser ?: return@withContext false
        if (!current.isEmailVerified) return@withContext false
        ensureSession()
    }

    suspend fun signUp(email: String, password: String): KehuaAuthResult = withContext(Dispatchers.IO) {
        if (!isFirebaseConfigured()) return@withContext KehuaAuthResult(false, message = "登录服务还没完成配置")
        if (!validEmail(email) || password.length !in 6..72) {
            return@withContext KehuaAuthResult(false, message = "请填写有效邮箱，密码至少 6 位")
        }
        try {
            val auth = firebaseAuth()
            val result = auth.createUserWithEmailAndPassword(email.trim().lowercase(), password).awaitTask()
            val user = result.user ?: return@withContext KehuaAuthResult(false, message = "注册失败")
            user.sendEmailVerification().awaitTask()
            auth.signOut()
            KehuaAuthResult(true, needsVerification = true, message = "确认邮件已经发送。验证邮箱后回来登录。")
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
            if (code.contains("EMAIL_EXISTS") || code.contains("EMAIL_ALREADY_IN_USE")) {
                KehuaAuthResult(false, message = "这个邮箱已经有账号了，直接登录即可")
            } else {
                KehuaAuthResult(false, message = authError(e))
            }
        }
    }

    suspend fun signIn(email: String, password: String): KehuaAuthResult = withContext(Dispatchers.IO) {
        if (!isFirebaseConfigured()) return@withContext KehuaAuthResult(false, message = "登录服务还没完成配置")
        if (!validEmail(email) || password.length !in 6..72) {
            return@withContext KehuaAuthResult(false, message = "请填写有效邮箱，密码至少 6 位")
        }
        val normalized = email.trim().lowercase()
        val auth = firebaseAuth()
        try {
            var user = try {
                auth.signInWithEmailAndPassword(normalized, password).awaitTask().user
            } catch (firebaseError: Exception) {
                if (!legacySupabasePasswordWorks(normalized, password)) throw firebaseError
                val created = auth.createUserWithEmailAndPassword(normalized, password).awaitTask().user
                created?.sendEmailVerification()?.awaitTask()
                auth.signOut()
                return@withContext KehuaAuthResult(
                    success = true,
                    needsVerification = true,
                    message = "为了保护原账号，确认邮件已经发送。验证后即可继续使用原来的内容。"
                )
            }

            if (user == null) return@withContext KehuaAuthResult(false, message = "登录失败")
            user.reload().awaitTask()
            user = auth.currentUser
            if (user == null) return@withContext KehuaAuthResult(false, message = "登录失败")
            if (!user.isEmailVerified) {
                runCatching { user.sendEmailVerification().awaitTask() }
                auth.signOut()
                return@withContext KehuaAuthResult(
                    success = true,
                    needsVerification = true,
                    message = "这个邮箱还没完成验证。验证邮件已经重新发送。"
                )
            }

            val firebaseToken = user.getIdToken(true).awaitTask().token.orEmpty()
            if (firebaseToken.isBlank()) throw IllegalStateException("身份验证失败")
            bridgeIdentity(firebaseToken, password)
            val session = callSupabaseAuth(
                "/auth/v1/token?grant_type=password",
                JSONObject().put("email", normalized).put("password", password)
            )
            if (session.optString("access_token").isBlank()) throw IllegalStateException("登录失败")
            saveSession(session)
            KehuaAuthResult(true)
        } catch (e: Exception) {
            auth.signOut()
            KehuaAuthResult(false, message = authError(e))
        }
    }

    suspend fun sendPasswordReset(email: String): String? = withContext(Dispatchers.IO) {
        if (!isFirebaseConfigured()) return@withContext "登录服务还没完成配置"
        if (!validEmail(email)) return@withContext "先填写你注册时使用的邮箱"
        try {
            firebaseAuth().sendPasswordResetEmail(email.trim().lowercase()).awaitTask()
            null
        } catch (e: Exception) {
            val code = (e as? FirebaseAuthException)?.errorCode.orEmpty()
            if (code.contains("TOO_MANY")) "发送太频繁了，请稍后再试" else null
        }
    }

    fun logout() {
        runCatching { firebaseAuth().signOut() }
        prefs.edit().clear().apply()
    }

    suspend fun ensureProfile(): KehuaProfile = withContext(Dispatchers.IO) {
        requireSession()
        val arr = rpcArray("kehua_ensure_profile", JSONObject())
        profileFrom(arr.optJSONObject(0)) ?: throw IllegalStateException("账号资料暂不可用")
    }

    suspend fun myPosts(): List<KehuaPost> = withContext(Dispatchers.IO) {
        requireSession()
        val arr = rpcArray("kehua_my_posts", JSONObject())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(KehuaPost(
                    id = o.optString("id"),
                    body = o.optString("body"),
                    imagePath = o.optNullable("image_path"),
                    createdAt = o.optString("created_at"),
                    resonanceCount = o.optLong("resonance_count", 0)
                ))
            }
        }
    }

    suspend fun publish(body: String, imagePath: String?): Pair<String, Int> = withContext(Dispatchers.IO) {
        requireSession()
        val clean = body.trim()
        if (clean.isBlank() || clean.length > 1200) throw IllegalStateException("写点想说的话再发布")
        val payload = JSONObject().put("p_body", clean)
        if (imagePath == null) payload.put("p_image_path", JSONObject.NULL) else payload.put("p_image_path", imagePath)
        val arr = rpcArray("kehua_publish", payload)
        val o = arr.optJSONObject(0) ?: throw IllegalStateException("发布失败")
        o.optString("post_id") to o.optInt("match_count", 0)
    }

    suspend fun resonances(postId: String): List<KehuaResonance> = withContext(Dispatchers.IO) {
        requireSession()
        val arr = rpcArray("kehua_get_resonances", JSONObject().put("p_post_id", postId))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(KehuaResonance(
                    matchId = o.optString("match_id"),
                    targetPostId = o.optString("target_post_id"),
                    targetUserId = o.optNullable("target_user_id"),
                    body = o.optString("body"),
                    imagePath = o.optNullable("image_path"),
                    createdAt = o.optString("created_at"),
                    score = o.optDouble("score", 0.0),
                    isLit = o.optBoolean("is_lit", false),
                    nickname = o.optNullable("nickname"),
                    bio = o.optNullable("bio"),
                    avatarSeed = o.optNullable("avatar_seed"),
                    avatarPath = o.optNullable("avatar_path"),
                    conversationId = o.optNullable("conversation_id")
                ))
            }
        }
    }

    suspend fun light(matchId: String): KehuaResonance = withContext(Dispatchers.IO) {
        requireSession()
        val arr = rpcArray("kehua_light", JSONObject().put("p_match_id", matchId))
        val o = arr.optJSONObject(0) ?: throw IllegalStateException("点亮失败")
        KehuaResonance(
            matchId = matchId,
            targetPostId = "",
            targetUserId = o.optString("target_user_id"),
            body = "",
            imagePath = null,
            createdAt = "",
            score = 0.0,
            isLit = true,
            nickname = o.optNullable("nickname"),
            bio = o.optNullable("bio"),
            avatarSeed = o.optNullable("avatar_seed"),
            avatarPath = o.optNullable("avatar_path"),
            conversationId = o.optNullable("conversation_id")
        )
    }

    suspend fun conversations(): List<KehuaConversation> = withContext(Dispatchers.IO) {
        requireSession()
        val arr = rpcArray("kehua_conversation_list", JSONObject())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(KehuaConversation(
                    id = o.optString("id"),
                    otherUserId = o.optString("other_user_id"),
                    nickname = o.optString("nickname").ifBlank { "TA" },
                    bio = o.optString("bio"),
                    avatarSeed = o.optString("avatar_seed"),
                    avatarPath = o.optNullable("avatar_path"),
                    lastMessage = o.optNullable("last_message"),
                    lastAt = o.optNullable("last_at")
                ))
            }
        }
    }

    suspend fun incomingFriendRequests(): List<KehuaFriendRequest> = withContext(Dispatchers.IO) {
        requireSession()
        val arr = rpcArray("kehua_incoming_friend_requests", JSONObject())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(KehuaFriendRequest(
                    id = o.optString("id"),
                    senderId = o.optString("sender_id"),
                    nickname = o.optString("nickname").ifBlank { "TA" },
                    bio = o.optString("bio"),
                    avatarSeed = o.optString("avatar_seed"),
                    avatarPath = o.optNullable("avatar_path"),
                    createdAt = o.optString("created_at")
                ))
            }
        }
    }

    suspend fun friends(): List<KehuaFriend> = withContext(Dispatchers.IO) {
        requireSession()
        val arr = rpcArray("kehua_friends_latest", JSONObject())
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(KehuaFriend(
                    id = o.optString("friend_id"),
                    nickname = o.optString("nickname").ifBlank { "TA" },
                    bio = o.optString("bio"),
                    avatarSeed = o.optString("avatar_seed"),
                    avatarPath = o.optNullable("avatar_path"),
                    latestPostId = o.optNullable("latest_post_id"),
                    latestPostBody = o.optNullable("latest_post_body"),
                    latestPostImagePath = o.optNullable("latest_post_image_path"),
                    latestPostAt = o.optNullable("latest_post_at")
                ))
            }
        }
    }

    suspend fun messages(conversationId: String): List<KehuaMessage> = withContext(Dispatchers.IO) {
        requireSession()
        val arr = rpcArray("kehua_message_list", JSONObject().put("p_conversation_id", conversationId))
        buildList {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                add(KehuaMessage(
                    id = o.optLong("id"),
                    senderId = o.optString("sender_id"),
                    body = o.optString("body"),
                    createdAt = o.optString("created_at")
                ))
            }
        }
    }

    suspend fun sendMessage(conversationId: String, body: String) = withContext(Dispatchers.IO) {
        requireSession()
        val clean = body.trim()
        if (clean.isBlank() || clean.length > 2000) throw IllegalStateException("消息不能为空")
        rpcRaw("kehua_send_message", JSONObject().put("p_conversation_id", conversationId).put("p_body", clean))
        Unit
    }

    suspend fun acceptFriendRequest(requestId: String) = withContext(Dispatchers.IO) {
        requireSession()
        rpcRaw("kehua_accept_friend_request", JSONObject().put("p_request", requestId))
        Unit
    }

    suspend fun friendStatus(targetId: String): String = withContext(Dispatchers.IO) {
        requireSession()
        rpcRaw("kehua_friend_status", JSONObject().put("p_target", targetId)).trim().trim('"')
    }

    suspend fun friendRequest(targetId: String): String = withContext(Dispatchers.IO) {
        requireSession()
        rpcRaw("kehua_friend_request", JSONObject().put("p_target", targetId)).trim().trim('"')
    }

    suspend fun block(targetId: String) = withContext(Dispatchers.IO) {
        requireSession()
        rpcRaw("kehua_block", JSONObject().put("p_target_user_id", targetId))
        Unit
    }

    suspend fun report(targetUserId: String?, targetPostId: String?, reason: String) = withContext(Dispatchers.IO) {
        requireSession()
        val payload = JSONObject()
            .put("p_target_user_id", targetUserId ?: JSONObject.NULL)
            .put("p_target_post_id", targetPostId ?: JSONObject.NULL)
            .put("p_reason", reason.trim())
        rpcRaw("kehua_report", payload)
        Unit
    }

    suspend fun saveProfile(nickname: String, bio: String, avatarPath: String?) = withContext(Dispatchers.IO) {
        requireSession()
        val body = JSONObject()
            .put("nickname", nickname.trim().ifBlank { "可话用户" })
            .put("bio", bio.trim())
            .put("avatar_path", avatarPath ?: JSONObject.NULL)
            .put("updated_at", java.time.Instant.now().toString())
        restPatch("/rest/v1/kehua_profiles?id=eq.${userId()}", body)
    }

    suspend fun uploadImage(uri: Uri, kind: String): String = withContext(Dispatchers.IO) {
        requireSession()
        val resolver = appContext.contentResolver
        val mime = resolver.getType(uri) ?: "image/jpeg"
        if (mime !in setOf("image/jpeg", "image/png", "image/webp")) throw IllegalStateException("只支持 JPG、PNG、WebP 图片")
        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: throw IllegalStateException("图片读取失败")
        if (bytes.size > 3 * 1024 * 1024) throw IllegalStateException("图片不能超过 3MB")
        val ext = when (mime) { "image/png" -> "png"; "image/webp" -> "webp"; else -> "jpg" }
        val path = "${userId()}/${kind}/${System.currentTimeMillis()}-${UUID.randomUUID()}.$ext"
        storageUpload(path, bytes, mime)
        path
    }

    suspend fun signedUrl(path: String?): String? = withContext(Dispatchers.IO) {
        if (path.isNullOrBlank()) return@withContext null
        requireSession()
        val text = authenticatedRequest(
            Request.Builder()
                .url("$KEHUA_SUPABASE_URL/storage/v1/object/sign/kehua-media/$path")
                .post(JSONObject().put("expiresIn", 3600).toString().toRequestBody(KEHUA_JSON))
        )
        val url = JSONObject(text).optString("signedURL")
        when {
            url.isBlank() -> null
            url.startsWith("http") -> url
            else -> KEHUA_SUPABASE_URL + url
        }
    }

    private fun firebaseAuth(): FirebaseAuth {
        firebase?.let { return it }
        val apiKey = appContext.getString(R.string.firebase_api_key)
        val appId = appContext.getString(R.string.firebase_app_id)
        val projectId = appContext.getString(R.string.firebase_project_id)
        if (apiKey.isBlank() || appId.isBlank() || projectId.isBlank()) throw IllegalStateException("identity_not_configured")
        val options = FirebaseOptions.Builder()
            .setApiKey(apiKey)
            .setApplicationId(appId)
            .setProjectId(projectId)
            .build()
        val app = FirebaseApp.getApps(appContext).firstOrNull { it.name == "kehua-prod" }
            ?: FirebaseApp.initializeApp(appContext, options, "kehua-prod")
        return FirebaseAuth.getInstance(app).also {
            it.setLanguageCode("zh-CN")
            firebase = it
        }
    }

    private suspend fun bridgeIdentity(firebaseToken: String, password: String) {
        val request = Request.Builder()
            .url("$KEHUA_SUPABASE_URL/functions/v1/kehua-auth-bridge")
            .header("apikey", KEHUA_SUPABASE_KEY)
            .header("Authorization", "Bearer $firebaseToken")
            .header("Content-Type", "application/json")
            .post(JSONObject().put("password", password).toString().toRequestBody(KEHUA_JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val code = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                throw IllegalStateException(if (code.isBlank()) "账号同步失败" else code)
            }
        }
    }

    private fun legacySupabasePasswordWorks(email: String, password: String): Boolean {
        return try {
            val json = callSupabaseAuth(
                "/auth/v1/token?grant_type=password",
                JSONObject().put("email", email).put("password", password)
            )
            json.optString("access_token").isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun callSupabaseAuth(path: String, body: JSONObject): JSONObject {
        val request = Request.Builder()
            .url(KEHUA_SUPABASE_URL + path)
            .header("apikey", KEHUA_SUPABASE_KEY)
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(KEHUA_JSON))
            .build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = if (text.isBlank()) JSONObject() else JSONObject(text)
            if (!response.isSuccessful) throw IllegalStateException(
                json.optString("msg").ifBlank { json.optString("message") }.ifBlank { "登录失败" }
            )
            return json
        }
    }

    private fun saveSession(json: JSONObject) {
        val user = json.optJSONObject("user")
        prefs.edit()
            .putString("access_token", json.optString("access_token"))
            .putString("refresh_token", json.optString("refresh_token"))
            .putString("user_id", user?.optString("id").orEmpty())
            .apply()
    }

    private suspend fun requireSession() {
        if (!ensureSession()) throw IllegalStateException("登录已过期，请重新登录")
    }

    private suspend fun ensureSession(): Boolean = withContext(Dispatchers.IO) {
        if (userId().isBlank()) return@withContext false
        val token = accessToken()
        if (token.isBlank() || jwtExpiresSoon(token)) refreshSessionBlocking() else true
    }

    private fun jwtExpiresSoon(token: String): Boolean = try {
        val parts = token.split('.')
        if (parts.size < 2) true else {
            val decoded = String(Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING), Charsets.UTF_8)
            JSONObject(decoded).optLong("exp", 0L) <= System.currentTimeMillis() / 1000L + 90L
        }
    } catch (_: Exception) { true }

    private fun refreshSessionBlocking(): Boolean = synchronized(sessionLock) {
        val refresh = refreshToken()
        if (refresh.isBlank()) return@synchronized false
        try {
            val json = callSupabaseAuth(
                "/auth/v1/token?grant_type=refresh_token",
                JSONObject().put("refresh_token", refresh)
            )
            if (json.optString("access_token").isBlank()) return@synchronized false
            saveSession(json)
            true
        } catch (_: Exception) {
            prefs.edit().clear().apply()
            false
        }
    }

    private fun rpcArray(name: String, body: JSONObject): JSONArray {
        val text = rpcRaw(name, body)
        return if (text.isBlank() || text == "null") JSONArray() else JSONArray(text)
    }

    private fun rpcRaw(name: String, body: JSONObject): String = authenticatedRequest(
        Request.Builder()
            .url("$KEHUA_SUPABASE_URL/rest/v1/rpc/$name")
            .post(body.toString().toRequestBody(KEHUA_JSON))
    )

    private fun restPatch(path: String, body: JSONObject) {
        authenticatedRequest(
            Request.Builder()
                .url(KEHUA_SUPABASE_URL + path)
                .header("Prefer", "return=minimal")
                .patch(body.toString().toRequestBody(KEHUA_JSON))
        )
    }

    private fun storageUpload(path: String, bytes: ByteArray, mime: String) {
        authenticatedRequest(
            Request.Builder()
                .url("$KEHUA_SUPABASE_URL/storage/v1/object/kehua-media/$path")
                .header("x-upsert", "false")
                .post(bytes.toRequestBody(mime.toMediaType()))
        )
    }

    private fun authenticatedRequest(base: Request.Builder): String {
        var attempt = 0
        while (true) {
            val request = base
                .header("apikey", KEHUA_SUPABASE_KEY)
                .header("Authorization", "Bearer ${accessToken()}")
                .build()
            val response = http.newCall(request).execute()
            val code = response.code
            val text = response.use { it.body?.string().orEmpty() }
            if (code in 200..299) return text
            if (attempt == 0 && code == 401 && refreshSessionBlocking()) {
                attempt++
                continue
            }
            val message = runCatching {
                val o = JSONObject(text)
                o.optString("message").ifBlank { o.optString("error") }.ifBlank { o.optString("msg") }
            }.getOrDefault("")
            throw IllegalStateException(message.ifBlank { if (code == 401) "登录已过期，请重新登录" else "请求失败 $code" })
        }
    }

    private fun profileFrom(o: JSONObject?): KehuaProfile? {
        if (o == null) return null
        val id = o.optString("id")
        if (id.isBlank()) return null
        return KehuaProfile(
            id = id,
            nickname = o.optString("nickname").ifBlank { "可话用户" },
            bio = o.optString("bio"),
            avatarSeed = o.optString("avatar_seed"),
            avatarPath = o.optNullable("avatar_path")
        )
    }

    private fun JSONObject.optNullable(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

    private fun validEmail(email: String): Boolean = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(email.trim())

    private fun authError(e: Exception): String {
        val code = (e as? FirebaseAuthException)?.errorCode.orEmpty().lowercase()
        val raw = e.message.orEmpty().lowercase()
        return when {
            code.contains("invalid_email") || raw.contains("badly formatted") -> "邮箱格式不正确"
            code.contains("wrong_password") || code.contains("invalid_credential") || raw.contains("credential") -> "邮箱或密码不对"
            code.contains("too_many") || raw.contains("too many") -> "操作太频繁了，请稍后再试"
            code.contains("network") || raw.contains("network") || raw.contains("timeout") -> "网络连接失败，请稍后再试"
            raw.contains("identity_not_configured") -> "登录服务还没完成配置"
            raw.contains("email_conflict") -> "这个邮箱对应的账号存在冲突"
            else -> "暂时无法完成，请稍后再试"
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (!cont.isActive) return@addOnCompleteListener
            if (task.isSuccessful) cont.resume(task.result)
            else cont.resumeWithException(task.exception ?: IllegalStateException("操作失败"))
        }
    }
}
