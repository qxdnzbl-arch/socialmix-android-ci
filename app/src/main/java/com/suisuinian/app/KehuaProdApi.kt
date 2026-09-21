package com.suisuinian.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val KEHUA_BASE = "https://kehua-public.onrender.com"

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

    suspend fun health():Result<String> = requestJson("GET","/health",auth=false).mapCatching{it.optString("database")}

    suspend fun register(login:String,password:String,nickname:String):Result<NativeSession> = runCatching {
        val j=requestJson("POST","/api/auth/register",JSONObject().put("username",login).put("password",password),false).getOrThrow()
        val t=j.str("token")
        token=t
        val initial=j.getJSONObject("user")
        val wanted=nickname.trim().ifBlank{initial.optString("nickname").ifBlank{login}}
        val prof=requestJson("PATCH","/api/me",JSONObject().put("nickname",wanted).put("bio",initial.optString("bio"))).getOrThrow()
        NativeSession(prof.strId("id"),prof.optString("nickname").ifBlank{wanted},t)
    }.onFailure{ token="" }

    suspend fun login(login:String,password:String):Result<NativeSession> = runCatching {
        val j=requestJson("POST","/api/auth/login",JSONObject().put("username",login).put("password",password),false).getOrThrow()
        val u=j.getJSONObject("user")
        val s=NativeSession(u.strId("id"),u.optString("nickname").ifBlank{login},j.str("token"))
        token=s.token
        s
    }
    fun logout(){ token="" }

    suspend fun home():Result<NativeHome> = runCatching {
        val postArray=requestArray("GET","/api/posts/mine").getOrThrow()
        val posts=postArray.mapObj { o ->
            NativePost(o.strId("id"),o.optString("body"),o.optString("created_at"),o.optInt("resonance_count"),o.optBoolean("is_private"))
        }
        val resonances=ArrayList<NativeResonance>()
        for(p in posts){
            if(p.isPrivate) continue
            val arr=requestArray("GET","/api/posts/${p.id}/resonances").getOrThrow()
            resonances += arr.mapObj { o ->
                val lit=o.optBoolean("lit")
                val u=o.optJSONObject("user")
                NativeResonance(o.strId("match_id"),o.optString("body"),if(lit)"lit" else "new",u?.optString("nickname")?.ifBlank{null},u?.let{it.strIdOrNull("id")})
            }
        }
        NativeHome(posts,resonances,resonances.count{it.status!="lit"})
    }

    suspend fun createPost(content:String,isPrivate:Boolean=false):Result<String> =
        requestJson("POST","/api/posts",JSONObject().put("body",content).put("is_private",isPrivate)).mapCatching{it.strId("id")}

    suspend fun openResonance(id:String):Result<Unit> = Result.success(Unit)
    suspend fun dismissResonance(id:String):Result<Unit> = requestJson("POST","/api/matches/$id/skip").map{Unit}

    suspend fun light(id:String):Result<NativePeer> =
        requestJson("POST","/api/matches/$id/light").mapCatching { j ->
            val u=j.getJSONObject("user")
            NativePeer(u.strId("id"),u.optString("nickname").ifBlank{"可话er"},u.optString("bio"),u.optString("friend_status")=="friends")
        }

    suspend fun threads():Result<List<NativeThread>> =
        requestArray("GET","/api/conversations").mapCatching { arr ->
            arr.mapObj { o ->
                val u=o.getJSONObject("other")
                NativeThread(u.strId("id"),u.optString("nickname").ifBlank{"可话er"},o.optString("last_message"),o.optString("updated_at"),u.optString("friend_status")=="friends")
            }
        }

    suspend fun chat(peerId:String):Result<Pair<NativePeer,List<NativeMessage>>> = runCatching {
        val p=requestJson("GET","/api/users/$peerId").getOrThrow()
        val cid=p.strId("conversation_id")
        val arr=requestArray("GET","/api/conversations/$cid/messages").getOrThrow()
        val peer=NativePeer(p.strId("id"),p.optString("nickname").ifBlank{"可话er"},p.optString("bio"),p.optString("friend_status")=="friends")
        peer to arr.mapObj { o -> NativeMessage(o.optLong("id"),o.strId("sender_id"),o.optString("body"),o.optString("created_at")) }
    }

    suspend fun sendMessage(peerId:String,body:String):Result<Long> = runCatching {
        val p=requestJson("GET","/api/users/$peerId").getOrThrow()
        val cid=p.strId("conversation_id")
        requestJson("POST","/api/conversations/$cid/messages",JSONObject().put("body",body)).getOrThrow().optLong("id")
    }

    suspend fun friendRequest(peerId:String):Result<String> =
        requestJson("POST","/api/users/$peerId/friend-request").mapCatching{it.optString("status").ifBlank{"sent"}}

    suspend fun incomingRequests():Result<List<NativeFriendRequest>> =
        requestArray("GET","/api/friend-requests").mapCatching { arr ->
            arr.mapObj { o -> NativeFriendRequest(o.strId("id"),o.getJSONObject("user").strId("id")) }
        }

    suspend fun respondRequest(id:String,accept:Boolean):Result<String> =
        if(accept) requestJson("POST","/api/friend-requests/$id/accept").map{"accepted"}
        else Result.failure(IllegalStateException("当前版本只支持通过好友申请"))

    suspend fun friends():Result<List<NativePeer>> =
        requestArray("GET","/api/friends").mapCatching { arr ->
            arr.mapObj { o -> NativePeer(o.strId("id"),o.optString("nickname").ifBlank{"可话er"},o.optString("bio"),true) }
        }

    suspend fun me():Result<NativeMe> = runCatching {
        val j=requestJson("GET","/api/me").getOrThrow()
        val fs=requestArray("GET","/api/friends").getOrThrow()
        NativeMe(j.strId("id"),j.optString("nickname").ifBlank{"可话er"},j.optString("bio"),j.optJSONArray("posts")?.length() ?: 0,fs.length())
    }

    suspend fun deleteAccount():Result<Unit> =
        requestJson("DELETE","/api/me").map { token=""; Unit }.onFailure{ token="" }

    private suspend fun requestJson(method:String,path:String,body:JSONObject?=null,auth:Boolean=true):Result<JSONObject> =
        requestRaw(method,path,body,auth).mapCatching{JSONObject(it.ifBlank{"{}"})}

    private suspend fun requestArray(method:String,path:String,body:JSONObject?=null,auth:Boolean=true):Result<JSONArray> =
        requestRaw(method,path,body,auth).mapCatching{JSONArray(it.ifBlank{"[]"})}

    private suspend fun requestRaw(method:String,path:String,body:JSONObject?=null,auth:Boolean=true):Result<String> = withContext(Dispatchers.IO){ runCatching {
        val c=(URL("$KEHUA_BASE$path").openConnection() as HttpURLConnection).apply{
            requestMethod=method
            connectTimeout=15000
            readTimeout=20000
            setRequestProperty("Accept","application/json")
            if(auth && token.isNotBlank()) setRequestProperty("Authorization","Bearer $token")
            if(body!=null){ doOutput=true; setRequestProperty("Content-Type","application/json") }
        }
        if(body!=null) c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
        val code=c.responseCode
        val raw=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        c.disconnect()
        if(code !in 200..299){
            if(code==401) token=""
            throw IllegalStateException(parseError(raw,"请求失败 $code"))
        }
        raw
    }}

    private fun JSONObject.str(k:String)=optString(k).ifBlank{throw IllegalStateException("数据缺少 $k")}
    private fun JSONObject.strId(k:String):String{ if(!has(k)||isNull(k))throw IllegalStateException("数据缺少 $k");return get(k).toString() }
    private fun JSONObject.strIdOrNull(k:String):String?=if(!has(k)||isNull(k))null else get(k).toString()
    private inline fun <T> JSONArray.mapObj(block:(JSONObject)->T):List<T>{ val out=ArrayList<T>();for(i in 0 until length())out+=block(getJSONObject(i));return out }
    private fun parseError(raw:String,fallback:String)=runCatching{
        val j=JSONObject(raw);val d=j.opt("detail")
        when(d){is String->d;else->j.optString("message").ifBlank{j.optString("error")}.ifBlank{fallback}}
    }.getOrDefault(fallback)
}