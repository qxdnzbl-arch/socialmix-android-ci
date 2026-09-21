package com.suisuinian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val KP=Color(0xFFFF4169); private val KI=Color(0xFF29292D); private val KS=Color(0xFF909098); private val KB=Color(0xFFF7F7FA)
class KehuaNativeActivity:ComponentActivity(){override fun onCreate(s:Bundle?){super.onCreate(s);setContent{MaterialTheme{KehuaApp(KehuaProdApi(this))}}}}

@Composable private fun KehuaApp(api:KehuaProdApi){var ok by remember{mutableStateOf(api.isLoggedIn)};if(!ok)Auth(api){ok=true}else Shell(api){api.logout();ok=false}}
@Composable private fun Auth(api:KehuaProdApi,done:()->Unit){
 var reg by remember{mutableStateOf(false)};var recover by remember{mutableStateOf(false)}
 var a by remember{mutableStateOf("")};var p by remember{mutableStateOf("")};var n by remember{mutableStateOf("")};var code by remember{mutableStateOf("")}
 var e by remember{mutableStateOf("")};var busy by remember{mutableStateOf(false)};var recoveryToShow by remember{mutableStateOf<String?>(null)}
 val sc=rememberCoroutineScope()
 Column(Modifier.fillMaxSize().background(Color.White).padding(30.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
  Text("可话~!",color=KP,fontSize=48.sp,fontWeight=FontWeight.Black);Text("说  你  想  说  的  话",color=Color(0xFFB5B5BC),fontSize=13.sp);Spacer(Modifier.height(42.dp))
  if(reg&&!recover){OutlinedTextField(n,{n=it},label={Text("昵称")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("nickname"));Spacer(Modifier.height(9.dp))}
  OutlinedTextField(a,{a=it.lowercase()},label={Text("账号")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("login"));Spacer(Modifier.height(9.dp))
  if(recover){OutlinedTextField(code,{code=it.uppercase()},label={Text("恢复码")},singleLine=true,modifier=Modifier.fillMaxWidth().testTag("recovery-code"));Spacer(Modifier.height(9.dp))}
  OutlinedTextField(p,{p=it},label={Text(if(recover)"新密码" else "密码")},singleLine=true,visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth().testTag("password"))
  if(e.isNotBlank())Text(e,color=Color(0xFFB14B57),fontSize=13.sp,modifier=Modifier.padding(top=8.dp));Spacer(Modifier.height(14.dp))
  Button(onClick={
   if(!busy){busy=true;e="";sc.launch{
    val result=when{recover->api.recover(a,code,p);reg->api.register(a,p,n);else->api.login(a,p)}
    result.onSuccess{s->if(s.recoveryCode!=null)recoveryToShow=s.recoveryCode else done()}.onFailure{e=it.message?:"操作失败"};busy=false
   }}
  },colors=ButtonDefaults.buttonColors(containerColor=KP),shape=RoundedCornerShape(26.dp),modifier=Modifier.fillMaxWidth().height(52.dp).testTag("auth-submit")){Text(when{recover->"重置密码";reg->"注册并进入可话";else->"登录"})}
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
   TextButton({recover=false;reg=!reg;e=""}){Text(if(reg)"已有账号？登录" else "第一次来？注册",color=KS)}
   if(!reg)TextButton({recover=!recover;e=""}){Text(if(recover)"返回登录" else "忘记密码？",color=KS)}
  }
 }
 recoveryToShow?.let{rc->AlertDialog(onDismissRequest={},title={Text("请保存恢复码")},text={Text(rc+"\n\n忘记密码时需要它。生成新恢复码后旧码会失效。")},confirmButton={Button({recoveryToShow=null;done()},colors=ButtonDefaults.buttonColors(containerColor=KP)){Text("我已保存")}})}
}

@Composable private fun Shell(api:KehuaProdApi,logout:()->Unit){var tab by remember{mutableIntStateOf(0)};var peer by remember{mutableStateOf<NativePeer?>(null)};Scaffold(bottomBar={if(peer==null)NavigationBar(containerColor=Color.White){listOf("首页","消息","我").forEachIndexed{i,t->NavigationBarItem(tab==i,{tab=i},{Text(t)},colors=NavigationBarItemDefaults.colors(indicatorColor=Color(0xFFE9DDFC)))}}}){pad->Box(Modifier.fillMaxSize().padding(pad)){if(peer!=null)Chat(api,peer!!){peer=null}else when(tab){0->Home(api){peer=it};1->Threads(api){peer=it};else->Me(api,logout)}}}}

@Composable private fun Home(api:KehuaProdApi,open:(NativePeer)->Unit){
 var h by remember{mutableStateOf<NativeHome?>(null)};var d by remember{mutableStateOf("")};var priv by remember{mutableStateOf(false)};var err by remember{mutableStateOf("")};var selected by remember{mutableStateOf<NativeResonance?>(null)}
 val sc=rememberCoroutineScope();fun load(){sc.launch{api.home().onSuccess{h=it;err=""}.onFailure{err=it.message?:"加载失败"}}};LaunchedEffect(Unit){load()}
 LazyColumn(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFC8DAFF),Color(0xFFE7DDF7),Color(0xFFF3DCE8),KB))).padding(horizontal=20.dp).testTag("home-screen"),contentPadding=PaddingValues(top=80.dp,bottom=24.dp)){
  item{Text("此刻，说你想说的话～",fontSize=27.sp,fontWeight=FontWeight.Bold,color=KI);Spacer(Modifier.height(16.dp));Card(shape=RoundedCornerShape(25.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp)){OutlinedTextField(d,{if(it.length<=1200)d=it},placeholder={Text("我想说…")},modifier=Modifier.fillMaxWidth().height(150.dp).testTag("composer"));Row(verticalAlignment=Alignment.CenterVertically){Text("仅自己可见",color=KS,fontSize=12.sp);Switch(priv,{priv=it});Spacer(Modifier.weight(1f));Button({val x=d.trim();if(x.isNotEmpty())sc.launch{api.createPost(x,priv).onSuccess{d="";priv=false;load()}.onFailure{err=it.message?:"发布失败"}}},colors=ButtonDefaults.buttonColors(containerColor=KI),shape=RoundedCornerShape(22.dp),modifier=Modifier.testTag("publish")){Text("发表")}}}};if(err.isNotBlank())Text(err,color=Color.Red,fontSize=12.sp,modifier=Modifier.padding(top=8.dp));Spacer(Modifier.height(18.dp));Text("共鸣",color=KS,fontSize=13.sp)}
  val rs=h?.resonances.orEmpty();if(rs.isEmpty())item{Text("发出一句话后，共鸣会在这里到达。",color=KS,modifier=Modifier.padding(vertical=24.dp))}else items(rs,key={it.id}){r->Card(Modifier.fillMaxWidth().padding(vertical=5.dp).clickable{selected=r},shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp)){Text(r.content,color=KI,fontSize=16.sp);Text(if(r.status=="lit")"已点亮 · "+(r.nickname?:"可话er") else "先看内容，点亮后才看到对方",color=if(r.status=="lit")KP else KS,fontSize=12.sp,modifier=Modifier.padding(top=7.dp))}}}
  item{Spacer(Modifier.height(14.dp));Text("我说过的话",color=KS,fontSize=13.sp)}
  items(h?.posts.orEmpty(),key={it.id}){p->Card(Modifier.fillMaxWidth().padding(vertical=5.dp),shape=RoundedCornerShape(18.dp),colors=CardDefaults.cardColors(containerColor=Color.White)){Column(Modifier.padding(16.dp)){Text(p.content,color=KI);Text(if(p.isPrivate)"仅自己可见" else p.lightCount.toString()+" 次点亮",color=KS,fontSize=11.sp,modifier=Modifier.padding(top=7.dp))}}}
 }
 selected?.let{r->AlertDialog(onDismissRequest={selected=null},title={Text("共鸣")},text={Text(r.content)},dismissButton={TextButton({sc.launch{api.dismissResonance(r.id);selected=null;load()}}){Text("略过",color=KS)}},confirmButton={Button({sc.launch{api.openResonance(r.id);api.light(r.id).onSuccess{selected=null;open(it)}.onFailure{err=it.message?:"点亮失败";selected=null}}},colors=ButtonDefaults.buttonColors(containerColor=KP)){Text(if(r.status=="lit")"聊聊" else "点亮")}})}
}

@Composable private fun Threads(api:KehuaProdApi,open:(NativePeer)->Unit){var ts by remember{mutableStateOf<List<NativeThread>>(emptyList())};var rq by remember{mutableStateOf<List<NativeFriendRequest>>(emptyList())};var err by remember{mutableStateOf("")};val sc=rememberCoroutineScope();fun load(){sc.launch{api.threads().onSuccess{ts=it}.onFailure{err=it.message?:"加载失败"};api.incomingRequests().onSuccess{rq=it}}};LaunchedEffect(Unit){while(true){load();delay(2500)}};Column(Modifier.fillMaxSize().background(Color.White).testTag("messages-screen")){Text("消息",fontSize=24.sp,fontWeight=FontWeight.Bold,modifier=Modifier.padding(20.dp));rq.forEach{x->Card(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=3.dp),colors=CardDefaults.cardColors(containerColor=Color(0xFFFFF2F6))){Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Text("收到新的好友申请",modifier=Modifier.weight(1f));TextButton({sc.launch{api.respondRequest(x.id,true);load()}}){Text("通过",color=KP)}}}};if(err.isNotBlank())Text(err,color=Color.Red,modifier=Modifier.padding(horizontal=20.dp));LazyColumn{items(ts,key={it.id}){t->Row(Modifier.fillMaxWidth().clickable{open(NativePeer(t.id,t.nickname,"",t.isFriend))}.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Avatar(t.nickname);Spacer(Modifier.width(12.dp));Column{Text(t.nickname,fontWeight=FontWeight.SemiBold);Text(t.lastMessage.ifBlank{"点开继续聊天"},color=KS,fontSize=13.sp)}};HorizontalDivider(color=Color(0xFFF0F0F3))}}}}

@Composable private fun Chat(api:KehuaProdApi,start:NativePeer,back:()->Unit){
 var peer by remember{mutableStateOf(start)};var ms by remember{mutableStateOf<List<NativeMessage>>(emptyList())};var input by remember{mutableStateOf("")};var err by remember{mutableStateOf("")}
 var safety by remember{mutableStateOf(false)};var report by remember{mutableStateOf(false)};var reason by remember{mutableStateOf("")}
 val sc=rememberCoroutineScope();fun load(){sc.launch{api.chat(peer.id).onSuccess{peer=it.first;ms=it.second}.onFailure{err=it.message?:"加载失败"}}};LaunchedEffect(peer.id){while(true){load();delay(1800)}}
 Column(Modifier.fillMaxSize().background(KB).testTag("chat-screen")){
  Row(Modifier.fillMaxWidth().background(Color.White).padding(8.dp),verticalAlignment=Alignment.CenterVertically){TextButton(back){Text("‹ 返回",color=KI)};Text(peer.nickname,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));TextButton({sc.launch{api.friendRequest(peer.id).onFailure{err=it.message?:"申请失败"};load()}}){Text(if(peer.isFriend)"已是好友" else "加好友",color=KP)};TextButton({safety=true}){Text("•••",color=KS)}}
  if(err.isNotBlank())Text(err,color=Color.Red,fontSize=12.sp,modifier=Modifier.padding(10.dp))
  LazyColumn(Modifier.weight(1f).padding(12.dp)){items(ms,key={it.id}){m->Text(m.body,modifier=Modifier.fillMaxWidth().padding(8.dp),color=KI)}}
  Row(Modifier.fillMaxWidth().background(Color.White).padding(10.dp)){OutlinedTextField(input,{input=it},singleLine=true,modifier=Modifier.weight(1f).testTag("chat-input"));Spacer(Modifier.width(8.dp));Button({val s=input.trim();if(s.isNotEmpty()){input="";sc.launch{api.sendMessage(peer.id,s).onSuccess{load()}.onFailure{err=it.message?:"发送失败"}}}},colors=ButtonDefaults.buttonColors(containerColor=KP),modifier=Modifier.testTag("chat-send")){Text("发送")}}
 }
 if(safety)AlertDialog(onDismissRequest={safety=false},title={Text("这段关系")},text={Column{TextButton({safety=false;report=true}){Text("举报",color=KI)};TextButton({sc.launch{api.block(peer.id).onSuccess{safety=false;back()}.onFailure{err=it.message?:"屏蔽失败";safety=false}}}){Text("屏蔽这个人",color=Color(0xFFB14B57))}}},confirmButton={TextButton({safety=false}){Text("取消")}})
 if(report)AlertDialog(onDismissRequest={report=false},title={Text("举报")},text={OutlinedTextField(reason,{reason=it.take(500)},label={Text("原因")})},dismissButton={TextButton({report=false}){Text("取消")}},confirmButton={Button({val q=reason.trim();if(q.length>=2)sc.launch{api.report(peer.id,q).onSuccess{report=false;reason=""}.onFailure{err=it.message?:"举报失败"}}},colors=ButtonDefaults.buttonColors(containerColor=KP)){Text("提交")}})
}

@Composable private fun Me(api:KehuaProdApi,logout:()->Unit){
 var m by remember{mutableStateOf<NativeMe?>(null)};var nick by remember{mutableStateOf("")};var bio by remember{mutableStateOf("")};var err by remember{mutableStateOf("")}
 var recovery by remember{mutableStateOf<String?>(null)};var deleteConfirm by remember{mutableStateOf(false)}
 val sc=rememberCoroutineScope()
 fun load(){sc.launch{api.me().onSuccess{m=it;nick=it.nickname;bio=it.bio;err=""}.onFailure{err=it.message?:"加载失败"}}}
 LaunchedEffect(Unit){load()}
 Column(Modifier.fillMaxSize().background(Color.White).padding(22.dp).testTag("me-screen")){
  Spacer(Modifier.height(42.dp));Text("我",fontSize=24.sp,fontWeight=FontWeight.Bold);Spacer(Modifier.height(18.dp))
  Row(verticalAlignment=Alignment.CenterVertically){Avatar(m?.nickname?:"可");Spacer(Modifier.width(14.dp));Text(m?.nickname?:"加载中…",fontSize=22.sp,fontWeight=FontWeight.Bold)}
  Spacer(Modifier.height(20.dp));OutlinedTextField(nick,{nick=it.take(24)},label={Text("昵称")},singleLine=true,modifier=Modifier.fillMaxWidth());Spacer(Modifier.height(10.dp));OutlinedTextField(bio,{bio=it.take(120)},label={Text("简介")},modifier=Modifier.fillMaxWidth())
  Button({sc.launch{api.updateProfile(nick,bio).onSuccess{load()}.onFailure{err=it.message?:"保存失败"}}},colors=ButtonDefaults.buttonColors(containerColor=KP),modifier=Modifier.fillMaxWidth().padding(top=12.dp)){Text("保存资料")}
  Text("记录 "+(m?.postCount?:0)+" 条    好友 "+(m?.friendCount?:0)+" 位",fontSize=16.sp,color=KI,modifier=Modifier.padding(top=22.dp))
  if(err.isNotBlank())Text(err,color=Color.Red,modifier=Modifier.padding(top=8.dp))
  Spacer(Modifier.weight(1f))
  TextButton({sc.launch{api.rotateRecovery().onSuccess{recovery=it}.onFailure{err=it.message?:"生成失败"}}},modifier=Modifier.fillMaxWidth()){Text("重新生成恢复码",color=KI)}
  OutlinedButton({sc.launch{api.logoutRemote().onSuccess{logout()}.onFailure{api.logout();logout()}}},Modifier.fillMaxWidth()){Text("退出登录",color=KI)}
  TextButton({deleteConfirm=true},modifier=Modifier.fillMaxWidth()){Text("永久注销账号",color=Color(0xFFB14B57))}
 }
 recovery?.let{rc->AlertDialog(onDismissRequest={recovery=null},title={Text("新的恢复码")},text={Text(rc+"\n\n旧恢复码已经失效，请保存这一份。")},confirmButton={Button({recovery=null},colors=ButtonDefaults.buttonColors(containerColor=KP)){Text("我已保存")}})}
 if(deleteConfirm)AlertDialog(onDismissRequest={deleteConfirm=false},title={Text("永久注销账号？")},text={Text("你的内容、聊天和好友关系将永久删除。")},dismissButton={TextButton({deleteConfirm=false}){Text("取消")}},confirmButton={Button({sc.launch{api.deleteAccount().onSuccess{deleteConfirm=false;logout()}.onFailure{err=it.message?:"注销失败";deleteConfirm=false}}},colors=ButtonDefaults.buttonColors(containerColor=Color(0xFFB14B57))){Text("确认注销")}})
}

@Composable private fun Avatar(name:String){Surface(shape=CircleShape,color=Color(0xFFE3D9F5),modifier=Modifier.size(50.dp)){Box(contentAlignment=Alignment.Center){Text(name.take(1).ifBlank{"可"},fontWeight=FontWeight.Bold,color=Color(0xFF6D5F79))}}}