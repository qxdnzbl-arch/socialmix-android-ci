package com.suisuinian.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val KEHUA_BASE = "https://kehua-public.onrender.com"

data class NativeSession(val id:String,val nickname:String,val token:String,val recoveryCode:String?=null)
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

    suspend fun health():Result<String> = requestObject("GET","/health",auth=false).mapCatching {
        if(!it.optBoolean("ok")) throw IllegalStateException("后端不可用")
        it.optString("database")
    }

    suspend fun register(login:String,password:String,nickname:String):Result<NativeSession> =
        requestObject("POST","/api/auth/register",JSONObject().put("username",login).put("password",password),auth=false).mapCatching { j ->
            val s=session(j);token=s.token
            if(nickname.isNotBlank() && nickname != s.nickname){
                requestObject("PATCH","/api/me",JSONObject().put("nickname",nickname).put("bio","").put("avatar_data",JSONObject.NULL)).getOrThrow()
                val me=requestObject("GET","/api/me").getOrThrow()
                NativeSession(s.id,me.optString("nickname",nickname),s.token,s.recoveryCode)
            } else s
        }

    suspend fun login(login:String,password:String):Result<NativeSession> =
        requestObject("POST","/api/auth/login",JSONObject().put("username",login).put("password",password),auth=false).mapCatching { j ->
            val s=session(j); token=s.token; s
        }

    fun logout(){ token="" }

    suspend fun logoutRemote():Result<Unit> {
        if(token.isBlank()) return Result.success(Unit)
        return requestObject("POST","/api/auth/logout").map{token="";Unit}.onFailure{token=""}
    }

    suspend fun recover(login:String,recoveryCode:String,newPassword:String):Result<NativeSession> =
        requestObject("POST","/api/auth/recover",JSONObject().put("username",login).put("recovery_code",recoveryCode).put("new_password",newPassword),auth=false).mapCatching { j ->
            val s=session(j);token=s.token;s
        }

    suspend fun rotateRecovery():Result<String> =
        requestObject("POST","/api/me/recovery").mapCatching{it.reqString("recovery_code")}

    suspend fun home():Result<NativeHome> = withContext(Dispatchers.IO){runCatching{
        val posts=requestArrayRaw("GET","/api/posts/mine").getOrThrow().mapObj { o ->
            NativePost(o.reqString("id"),o.reqString("body"),o.optString("created_at"),o.optInt("resonance_count"),o.optBoolean("is_private"))
        }
        val resonances=ArrayList<NativeResonance>()
        for(p in posts){
            val arr=requestArrayRaw("GET","/api/posts/${p.id}/resonances").getOrThrow()
            resonances += arr.mapObj { o ->
                val lit=o.optBoolean("lit")
                val u=o.optJSONObject("user")
                NativeResonance(
                    o.reqString("match_id"),
                    o.reqString("body"),
                    if(lit)"lit" else "new",
                    if(lit) u?.optString("nickname")?.ifBlank{null} else null,
                    null
                )
            }
        }
        NativeHome(posts,resonances,resonances.count{it.status=="new"})
    }}

    suspend fun createPost(content:String,isPrivate:Boolean=false):Result<String> =
        requestObject("POST","/api/posts",JSONObject().put("body",content).put("image_data",JSONObject.NULL).put("is_private",isPrivate)).mapCatching{it.reqString("id")}

    suspend fun openResonance(id:String):Result<Unit> = Result.success(Unit)

    suspend fun dismissResonance(id:String):Result<Unit> =
        requestObject("POST","/api/matches/$id/skip").map{Unit}

    suspend fun light(id:String):Result<NativePeer> =
        requestObject("POST","/api/matches/$id/light").mapCatching { j ->
            val u=j.getJSONObject("user")
            NativePeer(u.reqString("id"),u.reqString("nickname"),u.optString("bio"),u.optString("friend_status")=="friends")
        }

    suspend fun threads():Result<List<NativeThread>> =
        requestArrayRaw("GET","/api/conversations").mapCatching{arr ->
            arr.mapObj{o->
                val other=o.getJSONObject("other")
                NativeThread(other.reqString("id"),other.reqString("nickname"),o.optString("last_message"),o.optString("updated_at"),other.optString("friend_status")=="friends")
            }
        }

    suspend fun chat(peerId:String):Result<Pair<NativePeer,List<NativeMessage>>> = withContext(Dispatchers.IO){runCatching{
        val p=requestObject("GET","/api/users/$peerId").getOrThrow()
        val cid=p.reqString("conversation_id")
        val ms=requestArrayRaw("GET","/api/conversations/$cid/messages").getOrThrow().mapObj{o->
            NativeMessage(o.optLong("id"),o.reqString("sender_id"),o.reqString("body"),o.optString("created_at"))
        }
        NativePeer(p.reqString("id"),p.reqString("nickname"),p.optString("bio"),p.optString("friend_status")=="friends") to ms
    }}

    suspend fun sendMessage(peerId:String,body:String):Result<Long> = withContext(Dispatchers.IO){runCatching{
        val p=requestObject("GET","/api/users/$peerId").getOrThrow()
        requestObject("POST","/api/conversations/${p.reqString("conversation_id")}/messages",JSONObject().put("body",body)).getOrThrow().optLong("id")
    }}

    suspend fun friendRequest(peerId:String):Result<String> =
        requestObject("POST","/api/users/$peerId/friend-request").mapCatching{it.reqString("status")}

    suspend fun incomingRequests():Result<List<NativeFriendRequest>> =
        requestArrayRaw("GET","/api/friend-requests").mapCatching{arr->
            arr.mapObj{o->NativeFriendRequest(o.reqString("id"),o.getJSONObject("user").reqString("id"))}
        }

    suspend fun respondRequest(id:String,accept:Boolean):Result<String> {
        if(!accept) return Result.failure(IllegalStateException("当前后端暂不支持拒绝好友申请"))
        return requestObject("POST","/api/friend-requests/$id/accept").map{"accepted"}
    }

    suspend fun friends():Result<List<NativePeer>> =
        requestArrayRaw("GET","/api/friends").mapCatching{arr->
            arr.mapObj{o->NativePeer(o.reqString("id"),o.reqString("nickname"),o.optString("bio"),true)}
        }

    suspend fun me():Result<NativeMe> = withContext(Dispatchers.IO){runCatching{
        val u=requestObject("GET","/api/me").getOrThrow()
        val fs=requestArrayRaw("GET","/api/friends").getOrThrow()
        NativeMe(u.reqString("id"),u.reqString("nickname"),u.optString("bio"),u.optJSONArray("posts")?.length()?:0,fs.length())
    }}

    suspend fun updateProfile(nickname:String,bio:String):Result<Unit> =
        requestObject("PATCH","/api/me",JSONObject().put("nickname",nickname).put("bio",bio).put("avatar_data",JSONObject.NULL)).map{Unit}

    suspend fun block(peerId:String):Result<Unit> =
        requestObject("POST","/api/users/$peerId/block").map{Unit}

    suspend fun report(peerId:String,reason:String):Result<Unit> =
        requestObject("POST","/api/users/$peerId/report",JSONObject().put("reason",reason)).map{Unit}

    suspend fun deleteAccount():Result<Unit> =
        requestObject("DELETE","/api/me").map{token="";Unit}

    private fun session(j:JSONObject):NativeSession{
        val u=j.getJSONObject("user")
        return NativeSession(u.reqString("id"),u.reqString("nickname"),j.reqString("token"),j.optString("recovery_code").ifBlank{null})
    }

    private suspend fun requestObject(method:String,path:String,body:JSONObject?=null,auth:Boolean=true):Result<JSONObject> =
        withContext(Dispatchers.IO){runCatching{
            val raw=http(method,path,body,auth)
            JSONObject(raw.ifBlank{"{}"})
        }}

    private suspend fun requestArrayRaw(method:String,path:String,body:JSONObject?=null,auth:Boolean=true):Result<JSONArray> =
        withContext(Dispatchers.IO){runCatching{
            val raw=http(method,path,body,auth)
            JSONArray(raw.ifBlank{"[]"})
        }}

    private fun http(method:String,path:String,body:JSONObject?,auth:Boolean):String{
        val c=(URL(KEHUA_BASE+path).openConnection() as HttpURLConnection).apply{
            requestMethod=method;connectTimeout=45000;readTimeout=90000
            setRequestProperty("Accept","application/json")
            if(auth && token.isNotBlank())setRequestProperty("Authorization","Bearer $token")
            if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json")}
        }
        if(body!=null)c.outputStream.use{it.write(body.toString().toByteArray())}
        val code=c.responseCode
        val raw=(if(code in 200..299)c.inputStream else c.errorStream)?.bufferedReader()?.use{it.readText()}.orEmpty()
        c.disconnect()
        if(code !in 200..299)throw IllegalStateException(parseError(raw,"请求失败 $code"))
        return raw
    }

    private fun JSONObject.reqString(k:String)=when(val v=opt(k)){
        null,JSONObject.NULL -> throw IllegalStateException("数据缺少 $k")
        else -> v.toString().ifBlank{throw IllegalStateException("数据缺少 $k")}
    }

    private inline fun <T> JSONArray.mapObj(block:(JSONObject)->T):List<T>{
        val out=ArrayList<T>();for(i in 0 until length())out+=block(getJSONObject(i));return out
    }

    private fun parseError(raw:String,fallback:String)=runCatching{
        val j=JSONObject(raw)
        when(val d=j.opt("detail")){
            is String -> d
            else -> j.optString("message").ifBlank{j.optString("error")}.ifBlank{fallback}
        }
    }.getOrDefault(fallback)
}
