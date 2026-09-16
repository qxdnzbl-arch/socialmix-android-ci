package com.suisuinian.app

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KehuaBg = Color(0xFFF7F7F7)
private val KehuaSurface = Color(0xFFFFFFFF)
private val KehuaInk = Color(0xFF29292D)
private val KehuaSub = Color(0xFF8A8A91)
private val KehuaLine = Color(0xFFECECF0)
private val KehuaPink = Color(0xFFFF4169)
private val KehuaBlue = Color(0xFF4C8DFF)
private val KehuaDanger = Color(0xFFD75C67)

class KehuaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val api = remember { KehuaApi(this@KehuaActivity) }
                Surface(Modifier.fillMaxSize(), color = KehuaBg) {
                    KehuaRoot(api)
                }
            }
        }
    }
}

@Composable
private fun KehuaRoot(api: KehuaApi) {
    var loggedIn by remember { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(Unit) { loggedIn = runCatching { api.restoreSession() }.getOrDefault(false) }
    when (loggedIn) {
        null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = KehuaPink, strokeWidth = 2.dp)
        }
        false -> KehuaAuth(api) { loggedIn = true }
        true -> KehuaShell(api) { api.logout(); loggedIn = false }
    }
}

@Composable
private fun KehuaAuth(api: KehuaApi, onDone: () -> Unit) {
    var registerMode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf(if (api.isFirebaseConfigured()) "" else "正式账号服务等待 Firebase 项目配置") }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(KehuaSurface), contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().widthIn(max = 520.dp).padding(horizontal = 30.dp)) {
            Text("可话·重生", color = KehuaInk, fontSize = 31.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text("说想说的话，找到真正的共鸣。", color = KehuaSub, fontSize = 14.sp)
            Spacer(Modifier.height(34.dp))
            Row(Modifier.fillMaxWidth()) {
                AuthMode("第一次来", registerMode) { registerMode = true; status = "" }
                Spacer(Modifier.width(22.dp))
                AuthMode("回来看看", !registerMode) { registerMode = false; status = "" }
            }
            Spacer(Modifier.height(24.dp))
            KehuaField("邮箱", email, { email = it }, KeyboardType.Email)
            Spacer(Modifier.height(12.dp))
            KehuaField("密码", password, { password = it }, KeyboardType.Password, true)
            if (!registerMode) {
                Text(
                    "忘记密码？",
                    color = KehuaSub,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.End).clickable(enabled = !busy) {
                        scope.launch {
                            val result = api.sendPasswordReset(email)
                            status = result ?: "如果这个邮箱注册过，重置密码邮件已经发送。"
                        }
                    }.padding(vertical = 10.dp)
                )
            } else Spacer(Modifier.height(18.dp))
            if (status.isNotBlank()) {
                Text(status, color = if (status.contains("发送") || status.contains("验证")) KehuaSub else KehuaDanger, fontSize = 13.sp, lineHeight = 19.sp)
                Spacer(Modifier.height(14.dp))
            }
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true; status = ""
                    scope.launch {
                        val result = if (registerMode) api.signUp(email, password) else api.signIn(email, password)
                        busy = false
                        when {
                            result.success && !result.needsVerification -> onDone()
                            result.success -> { status = result.message; registerMode = false; password = "" }
                            else -> status = result.message
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(26.dp),
                enabled = !busy,
                colors = ButtonDefaults.buttonColors(containerColor = KehuaPink)
            ) { Text(if (busy) "处理中…" else if (registerMode) "进去" else "登录", fontSize = 16.sp) }
            Spacer(Modifier.height(18.dp))
            Text("没有公开广场、热度榜和关注数。你说的话只会去寻找共鸣。", color = KehuaSub, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AuthMode(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.clickable { onClick() }.padding(vertical = 4.dp)) {
        Text(label, color = if (selected) KehuaInk else KehuaSub, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        Spacer(Modifier.height(5.dp))
        Box(Modifier.height(2.dp).width(28.dp).background(if (selected) KehuaPink else Color.Transparent))
    }
}

@Composable
private fun KehuaField(label: String, value: String, onChange: (String) -> Unit, type: KeyboardType, password: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = type),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = KehuaPink,
            focusedLabelColor = KehuaPink,
            cursorColor = KehuaPink,
            unfocusedBorderColor = KehuaLine
        )
    )
}

@Composable
private fun KehuaShell(api: KehuaApi, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var resonancePost by remember { mutableStateOf<KehuaPost?>(null) }
    var chat by remember { mutableStateOf<KehuaConversation?>(null) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().fillMaxWidth().widthIn(max = 760.dp)) {
            if (resonancePost != null) {
                ResonanceScreen(api, resonancePost!!, onClose = { resonancePost = null }, onChat = { c -> chat = c; resonancePost = null })
            } else if (chat != null) {
                ChatScreen(api, chat!!, onBack = { chat = null })
            } else {
                Scaffold(
                    containerColor = KehuaBg,
                    bottomBar = { KehuaBottomBar(tab) { tab = it } }
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> HomeScreen(api) { resonancePost = it }
                            1 -> MessagesScreen(api) { chat = it }
                            else -> MeScreen(api, onLogout)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KehuaBottomBar(tab: Int, onTab: (Int) -> Unit) {
    NavigationBar(containerColor = KehuaSurface, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
        listOf(
            Triple("首页", Icons.Outlined.Home, 0),
            Triple("消息", Icons.Outlined.ChatBubbleOutline, 1),
            Triple("我", Icons.Outlined.PersonOutline, 2)
        ).forEach { (label, icon, index) ->
            NavigationBarItem(
                selected = tab == index,
                onClick = { onTab(index) },
                icon = { Icon(icon, label, tint = if (tab == index) KehuaPink else KehuaSub) },
                label = { Text(label, color = if (tab == index) KehuaPink else KehuaSub, fontSize = 11.sp) }
            )
        }
    }
}

@Composable
private fun HomeScreen(api: KehuaApi, onResonance: (KehuaPost) -> Unit) {
    var posts by remember { mutableStateOf<List<KehuaPost>>(emptyList()) }
    var body by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Uri?>(null) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image = it }

    LaunchedEffect(refresh) {
        runCatching { api.myPosts() }.onSuccess { posts = it }.onFailure { status = it.message.orEmpty() }
    }

    LazyColumn(Modifier.fillMaxSize().background(KehuaBg)) {
        item {
            Column(Modifier.fillMaxWidth().background(KehuaSurface).padding(horizontal = 20.dp, vertical = 22.dp)) {
                Text(todayLabel(), color = KehuaSub, fontSize = 12.sp)
                Spacer(Modifier.height(8.dp))
                Text("此刻，说你想说的话～", color = KehuaInk, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(24.dp))
                Column(Modifier.fillMaxWidth().background(KehuaSurface)) {
                    BasicTextField(
                        value = body,
                        onValueChange = { if (it.length <= 1200) body = it },
                        textStyle = TextStyle(color = KehuaInk, fontSize = 17.sp, lineHeight = 26.sp),
                        modifier = Modifier.fillMaxWidth().height(126.dp),
                        decorationBox = { inner -> if (body.isEmpty()) Text("写下你真实的想法和感受", color = KehuaSub, fontSize = 17.sp) else inner() }
                    )
                    if (image != null) {
                        AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
                        Text("移除图片", color = KehuaSub, fontSize = 12.sp, modifier = Modifier.clickable { image = null }.padding(vertical = 8.dp))
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { picker.launch("image/*") }) { Icon(Icons.Outlined.AddPhotoAlternate, "添加图片", tint = KehuaSub) }
                        Text("${body.length}/1200", color = KehuaSub, fontSize = 11.sp, modifier = Modifier.weight(1f))
                        Text(
                            if (busy) "发布中…" else "发布",
                            color = if (busy || body.isBlank()) KehuaSub else KehuaPink,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clickable(enabled = !busy && body.isNotBlank()) {
                                busy = true; status = ""
                                scope.launch {
                                    runCatching {
                                        val path = image?.let { api.uploadImage(it, "posts") }
                                        api.publish(body, path)
                                    }.onSuccess { (_, count) ->
                                        status = if (count > 0) "共鸣已到达。请签收～" else "已经说出去了，正在替你寻找共鸣…"
                                        body = ""; image = null; refresh++
                                    }.onFailure { status = it.message.orEmpty() }
                                    busy = false
                                }
                            }.padding(horizontal = 8.dp, vertical = 10.dp)
                        )
                    }
                }
                if (status.isNotBlank()) Text(status, color = KehuaSub, fontSize = 12.sp, modifier = Modifier.padding(top = 10.dp))
            }
        }
        items(posts, key = { it.id }) { post ->
            PostCard(api, post, onResonance)
            Spacer(Modifier.height(8.dp))
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun PostCard(api: KehuaApi, post: KehuaPost, onResonance: (KehuaPost) -> Unit) {
    Column(Modifier.fillMaxWidth().background(KehuaSurface).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Text(post.body, color = KehuaInk, fontSize = 16.sp, lineHeight = 25.sp)
        if (!post.imagePath.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp)); SignedImage(api, post.imagePath, Modifier.fillMaxWidth().height(210.dp))
        }
        Spacer(Modifier.height(13.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(shortTime(post.createdAt), color = KehuaSub, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (post.resonanceCount > 0) {
                Text("共鸣已到达。请签收～", color = KehuaPink, fontSize = 13.sp, modifier = Modifier.clickable { onResonance(post) }.padding(8.dp))
            } else Text("正在寻找共鸣…", color = KehuaSub, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ResonanceScreen(api: KehuaApi, post: KehuaPost, onClose: () -> Unit, onChat: (KehuaConversation) -> Unit) {
    var rows by remember { mutableStateOf<List<KehuaResonance>>(emptyList()) }
    var index by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(post.id) {
        runCatching { api.resonances(post.id) }.onSuccess { rows = it }.onFailure { status = it.message.orEmpty() }
    }
    val current = rows.getOrNull(index)
    Column(Modifier.fillMaxSize().background(KehuaSurface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Text("×", fontSize = 28.sp, color = KehuaInk) }
            Spacer(Modifier.weight(1f))
            Text(if (rows.isEmpty()) "" else "${index + 1} / ${rows.size}", color = KehuaSub, fontSize = 12.sp)
            Spacer(Modifier.width(14.dp))
        }
        Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 28.dp), contentAlignment = Alignment.Center) {
            when {
                status.isNotBlank() -> Text(status, color = KehuaSub)
                current == null -> Text("还在寻找共鸣", color = KehuaSub)
                else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(current.body, color = KehuaInk, fontSize = 22.sp, lineHeight = 34.sp, textAlign = TextAlign.Center)
                    if (!current.imagePath.isNullOrBlank()) {
                        Spacer(Modifier.height(18.dp)); SignedImage(api, current.imagePath, Modifier.fillMaxWidth().height(230.dp))
                    }
                    if (current.isLit && !current.targetUserId.isNullOrBlank()) {
                        Spacer(Modifier.height(28.dp))
                        KehuaAvatar(api, current.nickname ?: "TA", current.avatarPath, 54.dp)
                        Spacer(Modifier.height(8.dp))
                        Text(current.nickname ?: "TA", color = KehuaInk, fontWeight = FontWeight.SemiBold)
                        if (!current.bio.isNullOrBlank()) Text(current.bio, color = KehuaSub, fontSize = 12.sp)
                    }
                }
            }
        }
        if (current != null) {
            Row(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = { if (index < rows.lastIndex) index++ else onClose() }, modifier = Modifier.weight(1f).height(50.dp)) { Text("略过", color = KehuaSub) }
                Button(
                    onClick = {
                        if (busy) return@Button
                        if (current.isLit && !current.conversationId.isNullOrBlank() && !current.targetUserId.isNullOrBlank()) {
                            onChat(KehuaConversation(current.conversationId, current.targetUserId, current.nickname ?: "TA", current.bio.orEmpty(), current.avatarSeed.orEmpty(), current.avatarPath, null, null))
                        } else {
                            busy = true
                            scope.launch {
                                runCatching { api.light(current.matchId) }.onSuccess { lit ->
                                    rows = rows.toMutableList().also { list ->
                                        list[index] = current.copy(
                                            isLit = true,
                                            targetUserId = lit.targetUserId,
                                            nickname = lit.nickname,
                                            bio = lit.bio,
                                            avatarSeed = lit.avatarSeed,
                                            avatarPath = lit.avatarPath,
                                            conversationId = lit.conversationId
                                        )
                                    }
                                }.onFailure { status = it.message.orEmpty() }
                                busy = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f).height(50.dp),
                    shape = RoundedCornerShape(25.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = KehuaPink)
                ) { Text(if (busy) "点亮中…" else if (current.isLit) "去说句话" else "点亮") }
            }
        }
    }
}

@Composable
private fun MessagesScreen(api: KehuaApi, onChat: (KehuaConversation) -> Unit) {
    var conversations by remember { mutableStateOf<List<KehuaConversation>>(emptyList()) }
    var requests by remember { mutableStateOf<List<KehuaFriendRequest>>(emptyList()) }
    var friends by remember { mutableStateOf<List<KehuaFriend>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        runCatching {
            Triple(api.conversations(), api.incomingFriendRequests(), api.friends())
        }.onSuccess { (c, r, f) -> conversations = c; requests = r; friends = f; status = "" }
            .onFailure { status = it.message.orEmpty() }
    }
    LaunchedEffect(Unit) {
        while (isActive) { delay(20_000); refresh++ }
    }

    LazyColumn(Modifier.fillMaxSize().background(KehuaBg)) {
        item {
            Column(Modifier.fillMaxWidth().background(KehuaSurface).padding(horizontal = 20.dp, vertical = 20.dp)) {
                Text("消息", color = KehuaInk, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                if (friends.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        items(friends, key = { it.id }) { f ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(62.dp)) {
                                KehuaAvatar(api, f.nickname, f.avatarPath, 48.dp)
                                Spacer(Modifier.height(5.dp))
                                Text(f.nickname, color = KehuaInk, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
        if (requests.isNotEmpty()) {
            item { SectionLabel("好友请求") }
            items(requests, key = { it.id }) { r ->
                Row(Modifier.fillMaxWidth().background(KehuaSurface).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    KehuaAvatar(api, r.nickname, r.avatarPath, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(r.nickname, color = KehuaInk, fontWeight = FontWeight.SemiBold)
                        Text("想和你成为好友", color = KehuaSub, fontSize = 12.sp)
                    }
                    Button(
                        onClick = { scope.launch { runCatching { api.acceptFriendRequest(r.id) }.onSuccess { refresh++ }.onFailure { status = it.message.orEmpty() } } },
                        colors = ButtonDefaults.buttonColors(containerColor = KehuaPink),
                        shape = RoundedCornerShape(18.dp)
                    ) { Text("接受", fontSize = 12.sp) }
                }
                HorizontalDivider(color = KehuaLine)
            }
        }
        item { SectionLabel("对话") }
        if (conversations.isEmpty()) {
            item { Text(if (status.isBlank()) "还没有消息。\n从一次共鸣开始。" else status, color = KehuaSub, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(KehuaSurface).padding(42.dp), textAlign = TextAlign.Center) }
        } else {
            items(conversations, key = { it.id }) { c ->
                Row(Modifier.fillMaxWidth().background(KehuaSurface).clickable { onChat(c) }.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                    KehuaAvatar(api, c.nickname, c.avatarPath, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(c.nickname, color = KehuaInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text(c.lastMessage ?: "从一次共鸣开始。", color = KehuaSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(shortTime(c.lastAt.orEmpty()), color = KehuaSub, fontSize = 10.sp)
                }
                HorizontalDivider(color = KehuaLine)
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun ChatScreen(api: KehuaApi, conversation: KehuaConversation, onBack: () -> Unit) {
    var messages by remember { mutableStateOf<List<KehuaMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()

    LaunchedEffect(conversation.id) {
        while (isActive) {
            runCatching { api.messages(conversation.id) }.onSuccess { messages = it; status = "" }.onFailure { status = it.message.orEmpty() }
            delay(2200)
        }
    }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) state.animateScrollToItem(messages.lastIndex) }

    Column(Modifier.fillMaxSize().background(KehuaSurface)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = KehuaInk) }
            Column(Modifier.weight(1f)) {
                Text(conversation.nickname, color = KehuaInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text(conversation.bio.ifBlank { "只有你们看得见这里" }, color = KehuaSub, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "更多", tint = KehuaSub) }
        }
        HorizontalDivider(color = KehuaLine)
        LazyColumn(Modifier.weight(1f).fillMaxWidth().background(KehuaBg).padding(horizontal = 14.dp), state = state, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item { Spacer(Modifier.height(8.dp)) }
            items(messages, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.senderId == api.userId()) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (m.senderId == api.userId()) KehuaBlue else KehuaSurface)
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    ) { Text(m.body, color = if (m.senderId == api.userId()) Color.White else KehuaInk, fontSize = 15.sp, lineHeight = 21.sp) }
                }
            }
            item { if (status.isNotBlank()) Text(status, color = KehuaSub, fontSize = 11.sp); Spacer(Modifier.height(8.dp)) }
        }
        Row(Modifier.fillMaxWidth().background(KehuaSurface).padding(10.dp).navigationBarsPadding(), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = input,
                onValueChange = { if (it.length <= 2000) input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("说点什么", color = KehuaSub) },
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = KehuaLine, unfocusedBorderColor = KehuaLine, cursorColor = KehuaPink)
            )
            IconButton(enabled = !busy && input.isNotBlank(), onClick = {
                busy = true
                val sending = input; input = ""
                scope.launch {
                    runCatching { api.sendMessage(conversation.id, sending) }
                        .onSuccess { runCatching { api.messages(conversation.id) }.onSuccess { messages = it } }
                        .onFailure { status = it.message.orEmpty(); input = sending }
                    busy = false
                }
            }) { Icon(Icons.Outlined.Send, "发送", tint = if (input.isBlank()) KehuaSub else KehuaPink) }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text(conversation.nickname) },
            text = { Text("你可以屏蔽或举报这个人。") },
            confirmButton = {
                TextButton(onClick = { scope.launch { runCatching { api.block(conversation.otherUserId) }; menu = false; onBack() } }) { Text("屏蔽", color = KehuaDanger) }
            },
            dismissButton = {
                TextButton(onClick = { scope.launch { runCatching { api.report(conversation.otherUserId, null, "聊天内容不适") }; menu = false } }) { Text("举报", color = KehuaSub) }
            }
        )
    }
}

@Composable
private fun MeScreen(api: KehuaApi, onLogout: () -> Unit) {
    var profile by remember { mutableStateOf<KehuaProfile?>(null) }
    var nickname by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var avatarUri by remember { mutableStateOf<Uri?>(null) }
    var posts by remember { mutableStateOf<List<KehuaPost>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatarUri = it }

    LaunchedEffect(refresh) {
        runCatching { api.ensureProfile() }.onSuccess { p -> profile = p; nickname = p.nickname; bio = p.bio }
            .onFailure { status = it.message.orEmpty() }
        runCatching { api.myPosts() }.onSuccess { posts = it }
    }

    LazyColumn(Modifier.fillMaxSize().background(KehuaBg)) {
        item {
            Column(Modifier.fillMaxWidth().background(KehuaSurface).padding(horizontal = 20.dp, vertical = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("我", color = KehuaInk, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(20.dp))
                Box(Modifier.clickable { picker.launch("image/*") }) {
                    if (avatarUri != null) AsyncImage(model = avatarUri, contentDescription = null, modifier = Modifier.size(76.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                    else KehuaAvatar(api, nickname.ifBlank { "我" }, profile?.avatarPath, 76.dp)
                }
                Spacer(Modifier.height(12.dp))
                BasicTextField(value = nickname, onValueChange = { if (it.length <= 20) nickname = it }, textStyle = TextStyle(color = KehuaInk, fontSize = 20.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.SemiBold), modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                BasicTextField(value = bio, onValueChange = { if (it.length <= 120) bio = it }, textStyle = TextStyle(color = KehuaSub, fontSize = 13.sp, textAlign = TextAlign.Center), modifier = Modifier.fillMaxWidth(), decorationBox = { inner -> if (bio.isBlank()) Text("写一句关于自己", color = KehuaSub, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) else inner() })
                Spacer(Modifier.height(15.dp))
                Text(if (busy) "保存中…" else "保存", color = KehuaPink, fontSize = 14.sp, modifier = Modifier.clickable(enabled = !busy) {
                    busy = true; status = ""
                    scope.launch {
                        runCatching {
                            val path = avatarUri?.let { api.uploadImage(it, "avatars") } ?: profile?.avatarPath
                            api.saveProfile(nickname, bio, path)
                        }.onSuccess { avatarUri = null; status = "保存好了"; refresh++ }.onFailure { status = it.message.orEmpty() }
                        busy = false
                    }
                }.padding(8.dp))
                if (status.isNotBlank()) Text(status, color = KehuaSub, fontSize = 11.sp)
            }
        }
        item { SectionLabel("我说过的") }
        items(posts, key = { it.id }) { post ->
            Column(Modifier.fillMaxWidth().background(KehuaSurface).padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(post.body, color = KehuaInk, fontSize = 15.sp, lineHeight = 23.sp)
                if (!post.imagePath.isNullOrBlank()) { Spacer(Modifier.height(10.dp)); SignedImage(api, post.imagePath, Modifier.fillMaxWidth().height(180.dp)) }
                Spacer(Modifier.height(8.dp)); Text(shortTime(post.createdAt), color = KehuaSub, fontSize = 10.sp)
            }
            HorizontalDivider(color = KehuaLine)
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("退出当前账号", color = KehuaDanger, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().background(KehuaSurface).clickable { onLogout() }.padding(20.dp))
            Text("这是独立重建版本，不代表原开发团队。", color = KehuaSub, fontSize = 10.sp, modifier = Modifier.fillMaxWidth().padding(20.dp), textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SignedImage(api: KehuaApi, path: String?, modifier: Modifier) {
    var url by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) { url = runCatching { api.signedUrl(path) }.getOrNull() }
    if (url != null) AsyncImage(model = url, contentDescription = null, modifier = modifier.clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop)
}

@Composable
private fun KehuaAvatar(api: KehuaApi, name: String, path: String?, size: androidx.compose.ui.unit.Dp) {
    var url by remember(path) { mutableStateOf<String?>(null) }
    LaunchedEffect(path) { url = runCatching { api.signedUrl(path) }.getOrNull() }
    if (url != null) {
        AsyncImage(model = url, contentDescription = name, modifier = Modifier.size(size).clip(CircleShape), contentScale = ContentScale.Crop)
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFF0EEF3)), contentAlignment = Alignment.Center) {
            Text(name.take(1).ifBlank { "·" }, color = KehuaSub, fontSize = (size.value * .36f).sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = KehuaSub, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().background(KehuaBg).padding(horizontal = 18.dp, vertical = 9.dp))
}

private fun todayLabel(): String {
    val d = LocalDate.now()
    val weekday = arrayOf("一", "二", "三", "四", "五", "六", "日")[d.dayOfWeek.value - 1]
    return "${d.monthValue}月${d.dayOfMonth}日 · 星期$weekday"
}

private fun shortTime(value: String): String {
    if (value.isBlank()) return ""
    return try {
        val instant = Instant.parse(value)
        val local = instant.atZone(ZoneId.systemDefault())
        val today = LocalDate.now()
        if (local.toLocalDate() == today) local.format(DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA))
        else local.format(DateTimeFormatter.ofPattern("M月d日", Locale.CHINA))
    } catch (_: Exception) { value.take(10) }
}
