package com.suisuinian.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val KEHUA_BASE = "https://nvwdtfnhsyfdopaxdylx.supabase.co"
private const val KEHUA_KEY = "sb_publishable_S4IE-ziO7WQ_JAK9tuQGgQ_cszwKBWB"

data class NativeSession(val id:String,val nickname:String,val token:String)
data class NativePost(val id:String,val content:String,val createdAt:String,val lightCount:Int,val isPrivate:Boolean)
data class NativeResonance(val id:String,val content:String,val status:String,val nickname:String?,val authorId:String?)
data class NativeHome(val posts:List<NativePost>,val resonances:List<NativeResonance>,val newCount:Int)
data class NativePeer(val id:String,val nickname:String,val bio:String,val isFriend:Boolean=false)
data class NativeThread(val id:String,val nickname:String,val lastMessage:String,val lastAt:String,val isFriend:Boolean)
data class NativeMessage(val id:Long,val fromId:String,val body:String,val createdAt:String)
data class NativeFriendRequest(val id:String,val fromUser:String)
data class NativeMe(val id:String,val nickname:String,val bio:String,val postCount:Int,val friendCount:Int)

class KehuaProdApi(context: Context, private val prefSuffix:String="") {
    private val prefs=context.applicationContext.getSharedPreferences("kehua_native_prod$prefSuffix",Context.MODE_PRIVATE)
    var token:String
        get()=prefs.getString("token","") ?: ""
        private set(v){ prefs.edit().putString("token",v).apply() }
    val isLoggedIn:Boolean get()=token.isNotBlank()

    suspend fun register(login:String,password:String,nickname:String):Result<NativeSession> = runRpc("kehua_prod_register", mapOf("p_phone" to login,"p_password" to password,"p_nickname" to nickname)).mapCatching { j ->
        val s=session(j); token=s.token; s
    }
    suspend fun login(login:String,password:String):Result<NativeSession> = runRpc("kehua_prod_login", mapOf("p_phone" to login,"p_password" to password)).mapCatching { j ->
        val s=session(j); token=s.token; s
    }
    fun logout(){ token="" }

    suspend fun home():Result<NativeHome> = authed("kehua_prod_home").mapCatching { j ->
        NativeHome(
            posts=jsonArray(j,"my_posts").mapObj { o -> NativePost(o.str("id"),o.str("content"),o.str("created_at"),o.optInt("light_count"),o.optBoolean("is_private")) },
            resonances=jsonArray(j,"resonances").mapObj { o -> NativeResonance(o.str("id"),o.str("content"),o.str("status"),(if(o.isNull("nickname")) null else o.optString("nickname").ifBlank{null}),(if(o.isNull("author_id")) null else o.optString("author_id").ifBlank{null})) },
            newCount=j.optInt("new_count")
        )
    }
    suspend fun createPost(content:String,isPrivate:Boolean=false):Result<String> = authed("kehua_prod_create_post",mapOf("p_content" to content,"p_media_data" to null,"p_private" to isPrivate)).mapCatching{it.str("id")}
    suspend fun openResonance(id:String):Result<Unit> = authed("kehua_prod_open_resonance",mapOf("p_resonance_id" to id)).map{Unit}
    suspend fun dismissResonance(id:String):Result<Unit> = authed("kehua_prod_dismiss_resonance",mapOf("p_resonance_id" to id)).map{Unit}
    suspend fun light(id:String):Result<NativePeer> = authed("kehua_prod_light",mapOf("p_post_id" to id)).mapCatching { j ->
        val u=j.getJSONObject("user"); NativePeer(u.str("id"),u.str("nickname"),u.optString("bio"))
    }
    suspend fun threads():Result<List<NativeThread>> = authed("kehua_prod_threads").mapCatching{j -> jsonArray(j,"items").mapObj{o->NativeThread(o.str("id"),o.str("nickname"),o.optString("last_message"),o.optString("last_at"),o.optBoolean("is_friend"))}}
    suspend fun chat(peerId:String):Result<Pair<NativePeer,List<NativeMessage>>> = authed("kehua_prod_chat",mapOf("p_other" to peerId)).mapCatching { j ->
        val p=j.getJSONObject("peer");
        NativePeer(p.str("id"),p.str("nickname"),p.optString("bio"),j.optBoolean("is_friend")) to jsonArray(j,"items").mapObj{o->NativeMessage(o.optLong("id"),o.str("from_id"),o.str("body"),o.str("created_at"))}
    }
    suspend fun sendMessage(peerId:String,body:String):Result<Long> = authed("kehua_prod_send_message",mapOf("p_to" to peerId,"p_body" to body)).mapCatching{it.optLong("id")}
    suspend fun friendRequest(peerId:String):Result<String> = authed("kehua_prod_friend_request",mapOf("p_other" to peerId)).mapCatching{it.str("status")}
    suspend fun incomingRequests():Result<List<NativeFriendRequest>> = authed("kehua_prod_incoming_friend_requests").mapCatching{j->jsonArray(j,"items").mapObj{o->NativeFriendRequest(o.str("id"),o.str("from_user"))}}
    suspend fun respondRequest(id:String,accept:Boolean):Result<String> = authed("kehua_prod_respond_friend_request",mapOf("p_request" to id,"p_accept" to accept)).mapCatching{it.str("status")}
    suspend fun friends():Result<List<NativePeer>> = authed("kehua_prod_friends").mapCatching{j->jsonArray(j,"items").mapObj{o->NativePeer(o.str("id"),o.str("nickname"),o.optString("bio"),true)}}
    suspend fun me():Result<NativeMe> = authed("kehua_prod_me").mapCatching { j -> val u=j.getJSONObject("user"); NativeMe(u.str("id"),u.str("nickname"),u.optString("bio"),u.optInt("post_count"),u.optInt("friend_count")) }
    suspend fun deleteAccount():Result<Unit> = authed("kehua_prod_delete_account").map { token=""; Unit }

    private suspend fun authed(name:String,args:Map<String,Any?> = emptyMap())=runRpc(name,args+mapOf("p_token" to token)).onFailure{ if(it.message=="登录已失效") token="" }
    private suspend fun runRpc(name:String,args:Map<String,Any?>):Result<JSONObject> = withContext(Dispatchers.IO){ runCatching {
        val c=(URL("$KEHUA_BASE/rest/v1/rpc/$name").openConnection() as HttpURLConnection).apply{
            requestMethod="POST"; connectTimeout=8000; readTimeout=12000; doOutput=true
            setRequestProperty("apikey",KEHUA_KEY); setRequestProperty("Content-Type","application/json")
        }
        val body=JSONObject(); args.forEach{(k,v)->body.put(k,v ?: JSONObject.NULL)}
        c.outputStream.use{it.write(body.toString().toByteArray())}
        val code=c.responseCode; val raw=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty(); c.disconnect()
        if(code !in 200..299) throw IllegalStateException(parseError(raw,"请求失败 $code"))
        val j=JSONObject(raw.ifBlan{{"{}"}); if(j.optBoolean("ok",true).not()) throw IllegalStateException(j.optString("error","操作失败")); j
    }}
    private fun session(j:JSONObject):NativeSession{ val u=j.getJSONObject("user"); return NativeSession(u.str("id"),u.str("nickname"),j.str("token")) }
    private fun jsonArray(j:JSONObject,key:String)=j.optJSONArray(key) ?: JSONArray()
    private fun JSONObject.str(k:String)=optString(k).ifBlank{throw IllegalStateException("数据缺少 $k")}
    private inline fun <T> JSONArray.mapObj(block:(JSONObject)->T):List<T>{ val out=ArrayList<T>(); for(i in 0 until length()) out+=block(getJSONObject(i)); return out }
    private fun parseError(raw:String,fallback:String)=runCatching{JSONObject(raw).optString("message").ifBlank{JSONObject(raw).optString("error")}.ifBlan{fallback}}.getOrDefault(fallback)
}
