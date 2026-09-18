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
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.ui.graphics.Brush
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
    var showForm by remember { mutableStateOf(false) }
    var registerMode by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    if (!showForm) {
        Box(Modifier.fillMaxSize().background(Color.White)) {
            Column(
                Modifier.fillMaxWidth().align(Alignment.TopCenter).padding(top = 205.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("可话~!", color = KehuaPink, fontSize = 48.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(8.dp))
                Text("说  你  想  说  的  话", color = Color(0xFFB7B7BD), fontSize = 14.sp)
            }
            Button(
                onClick = { showForm = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 126.dp).fillMaxWidth(.58f).height(54.dp),
                shape = RoundedCornerShape(27.dp),
                colors = ButtonDefaults.buttonColors(containerColor = KehuaPink)
            ) { Text("手机号登录", fontSize = 17.sp, color = Color.White) }
        }
        return
    }

    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxWidth().widthIn(max = 520.dp).align(Alignment.Center).padding(horizontal = 30.dp)
        ) {
            Text("可话~!", color = KehuaPink, fontSize = 34.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(8.dp))
            Text("说你想说的话", color = Color(0xFF9A9AA1), fontSize = 14.sp)
            Spacer(Modifier.height(30.dp))
            Row(Modifier.fillMaxWidth()) {
                AuthMode("回来看看", !registerMode) { registerMode = false; status = "" }
                Spacer(Modifier.width(24.dp))
                AuthMode("第一次来", registerMode) { registerMode = true; status = "" }
            }
            Spacer(Modifier.height(22.dp))
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
            ) { Text(if (busy) "处理中…" else if (registerMode) "注册" else "登录", fontSize = 16.sp) }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { showForm = false }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                Text("返回", color = KehuaSub, fontSize = 13.sp)
            }
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
    var publishOpen by remember { mutableStateOf(false) }
    var friendsOpen by remember { mutableStateOf(false) }
    var homeRefresh by remember { mutableIntStateOf(0) }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxHeight().fillMaxWidth().widthIn(max = 760.dp)) {
            when {
                publishOpen -> PublishScreen(api, onBack = { publishOpen = false }, onPublished = { publishOpen = false; homeRefresh++ })
                friendsOpen -> FriendsScreen(api, onBack = { friendsOpen = false })
                resonancePost != null -> ResonanceScreen(api, resonancePost!!, onClose = { resonancePost = null }, onChat = { conv -> chat = conv; resonancePost = null })
                chat != null -> ChatScreen(api, chat!!, onBack = { chat = null })
                else -> Scaffold(
                    containerColor = KehuaBg,
                    bottomBar = { KehuaBottomBar(tab) { tab = it } }
                ) { pad ->
                    Box(Modifier.padding(pad)) {
                        when (tab) {
                            0 -> HomeScreen(api, refreshToken = homeRefresh, onCompose = { publishOpen = true }, onResonance = { resonancePost = it })
                            1 -> MessagesScreen(api, onChat = { chat = it }, onFriends = { friendsOpen = true })
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
    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.ChatBubbleOutline, Icons.Outlined.PersonOutline)
    Column(Modifier.fillMaxWidth().background(Color.White).navigationBarsPadding()) {
        HorizontalDivider(color = Color(0xFFF0F0F2), thickness = 0.5.dp)
        Row(Modifier.fillMaxWidth().height(70.dp), verticalAlignment = Alignment.CenterVertically) {
            icons.forEachIndexed { index, icon ->
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onTab(index) }, contentAlignment = Alignment.Center) {
                    if (tab == index) Box(
                        Modifier.width(58.dp).height(38.dp).clip(RoundedCornerShape(20.dp)).background(Color(0xFFE9DDFC)),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, tint = KehuaInk, modifier = Modifier.size(27.dp)) }
                    else Icon(icon, null, tint = Color(0xFFB8B8BD), modifier = Modifier.size(27.dp))
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    api: KehuaApi,
    refreshToken: Int,
    onCompose: () -> Unit,
    onResonance: (KehuaPost) -> Unit
) {
    var posts by remember { mutableStateOf<List<KehuaPost>>(emptyList()) }
    var status by remember { mutableStateOf("") }

    LaunchedEffect(refreshToken) {
        runCatching { api.myPosts() }.onSuccess { posts = it; status = "" }.onFailure { status = it.message.orEmpty() }
    }

    LazyColumn(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFFC8DAFF), Color(0xFFE7DDF7), Color(0xFFF2D9E5), Color(0xFFF7F7F9)))
        )
    ) {
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 165.dp, bottom = 24.dp)) {
                Text(todayLabel(), color = Color(0xFF55555C), fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text("此刻，说你想说的话～", color = Color(0xFF252529), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().height(348.dp).clip(RoundedCornerShape(26.dp)).background(Color.White).clickable { onCompose() }
                ) {
                    Text("我想说...", color = Color(0xFFD0D0D5), fontSize = 18.sp, modifier = Modifier.padding(24.dp))
                }
                if (status.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(status, color = Color(0xFF8E8E95), fontSize = 12.sp)
                }
            }
        }

        items(posts, key = { it.id }) { post ->
            Box(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Color.White).padding(18.dp)) {
                    Text(post.body, color = KehuaInk, fontSize = 15.sp, lineHeight = 23.sp)
                    if (!post.imagePath.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        SignedImage(api, post.imagePath, Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp)))
                    }
                    Spacer(Modifier.height(11.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(shortTime(post.createdAt), color = Color(0xFFB5B5BB), fontSize = 10.sp, modifier = Modifier.weight(1f))
                        if (post.resonanceCount > 0) Text("共鸣已到达。请签收～", color = KehuaPink, fontSize = 12.sp, modifier = Modifier.clickable { onResonance(post) }.padding(7.dp))
                        else Text("正在寻找共鸣…", color = Color(0xFFB5B5BB), fontSize = 11.sp)
                    }
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun PublishScreen(api: KehuaApi, onBack: () -> Unit, onPublished: () -> Unit) {
    var body by remember { mutableStateOf("") }
    var image by remember { mutableStateOf<Uri?>(null) }
    var visibility by remember { mutableStateOf("他人可见") }
    var showVisibility by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { image = it }

    Box(Modifier.fillMaxSize().background(Color(0xFFF6F6F9))) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().height(56.dp).background(Color.White).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Text("⌄", color = KehuaInk, fontSize = 30.sp) }
                Text("说点什么", color = KehuaInk, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Text(
                    if (busy) "发布中…" else "发布",
                    color = if (body.isBlank() || busy) Color(0xFFC7C7CD) else KehuaPink,
                    fontSize = 15.sp,
                    modifier = Modifier.clickable(enabled = body.isNotBlank() && !busy) {
                        busy = true; status = ""
                        scope.launch {
                            runCatching {
                                val path = image?.let { api.uploadImage(it, "posts") }
                                api.publish(body, path)
                            }.onSuccess { onPublished() }.onFailure { status = it.message.orEmpty() }
                            busy = false
                        }
                    }.padding(12.dp)
                )
            }
            HorizontalDivider(color = KehuaLine)
            Column(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 20.dp, vertical = 18.dp)) {
                BasicTextField(
                    value = body,
                    onValueChange = { if (it.length <= 1200) body = it },
                    modifier = Modifier.fillMaxWidth().height(250.dp),
                    textStyle = TextStyle(color = KehuaInk, fontSize = 18.sp, lineHeight = 27.sp),
                    decorationBox = { inner -> if (body.isBlank()) Text("这一刻你在想什么？", color = Color(0xFFC5C5CB), fontSize = 18.sp) else inner() }
                )
                if (image != null) {
                    AsyncImage(model = image, contentDescription = null, modifier = Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(14.dp)), contentScale = ContentScale.Crop)
                    Spacer(Modifier.height(8.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("图片", color = KehuaSub, fontSize = 13.sp, modifier = Modifier.clickable { picker.launch("image/*") }.padding(vertical = 10.dp))
                    Spacer(Modifier.width(22.dp))
                    if (image != null) Text("移除", color = KehuaSub, fontSize = 13.sp, modifier = Modifier.clickable { image = null }.padding(vertical = 10.dp))
                    Spacer(Modifier.weight(1f))
                    Text(body.length.toString() + "/1200", color = Color(0xFFBDBDC3), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(56.dp).background(Color.White).clickable { showVisibility = true }.padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("谁可以看", color = KehuaInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(visibility, color = KehuaSub, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFC9C9CF))
            }
            if (status.isNotBlank()) Text(status, color = KehuaDanger, fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        }

        if (showVisibility) {
            Box(Modifier.fillMaxSize().background(Color(0x33000000)).clickable { showVisibility = false })
            Column(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color(0xFFF5F5F7)).navigationBarsPadding()
            ) {
                listOf("他人可见", "仅自己可见").forEach { option ->
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).background(Color.White).clickable { visibility = option; showVisibility = false }.padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(option, color = KehuaInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        if (visibility == option) Box(Modifier.size(9.dp).clip(CircleShape).background(KehuaPink))
                    }
                    HorizontalDivider(color = KehuaLine)
                }
                Spacer(Modifier.height(8.dp))
                Text("取消", color = KehuaInk, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().background(Color.White).clickable { showVisibility = false }.padding(18.dp))
            }
        }
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
        runCatching { Triple(api.conversations(), api.incomingFriendRequests(), api.friends()) }
            .onSuccess { triple -> conversations = triple.first; requests = triple.second; friends = triple.third; status = "" }
            .onFailure { status = it.message.orEmpty() }
    }
    LaunchedEffect(Unit) { while (isActive) { delay(20_000); refresh++ } }

    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF7F7F9))) {
        item {
            Row(Modifier.fillMaxWidth().height(78.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("消息", color = Color(0xFF222226), fontSize = 27.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Icon(Icons.Outlined.Search, "搜索", tint = Color(0xFF8F8F96), modifier = Modifier.size(28.dp))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().height(72.dp).background(Color.White).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFEFF4)), contentAlignment = Alignment.Center) { Text("友", color = KehuaPink, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("我的好友", color = KehuaInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(if (friends.isEmpty()) "还没有好友" else "已认识 " + friends.size + " 位", color = Color(0xFFA7A7AD), fontSize = 12.sp)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFB6B6BC), modifier = Modifier.size(22.dp))
            }
            HorizontalDivider(color = Color(0xFFEFEFF2))
            Row(Modifier.fillMaxWidth().height(72.dp).background(Color.White).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(42.dp).clip(CircleShape).background(Color(0xFFFFF5E7)), contentAlignment = Alignment.Center) { Text("☀", color = Color(0xFFFFB92E), fontSize = 19.sp) }
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Text("点亮我的", color = KehuaInk, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text("收到的点亮会出现在这里", color = Color(0xFFA7A7AD), fontSize = 12.sp)
                }
                Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFB6B6BC), modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(10.dp))
        }
        if (requests.isNotEmpty()) {
            item { SectionLabel("好友请求") }
            items(requests, key = { it.id }) { req ->
                Row(Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    KehuaAvatar(api, req.nickname, req.avatarPath, 48.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) { Text(req.nickname, color = KehuaInk, fontSize = 15.sp); Text("想和你成为好友", color = Color(0xFFA5A5AB), fontSize = 12.sp) }
                    Button(onClick = { scope.launch { runCatching { api.acceptFriendRequest(req.id) }.onSuccess { refresh++ }.onFailure { status = it.message.orEmpty() } } }, colors = ButtonDefaults.buttonColors(containerColor = KehuaPink), shape = RoundedCornerShape(18.dp)) { Text("接受", fontSize = 12.sp) }
                }
                HorizontalDivider(color = Color(0xFFEFEFF2), modifier = Modifier.padding(start = 78.dp))
            }
        }
        if (conversations.isEmpty()) item {
            Text(if (status.isBlank()) "还没有消息" else status, color = Color(0xFF9D9DA4), fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Color.White).padding(32.dp), textAlign = TextAlign.Center)
        } else items(conversations, key = { it.id }) { conv ->
            Row(Modifier.fillMaxWidth().background(Color.White).clickable { onChat(conv) }.padding(horizontal = 18.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                KehuaAvatar(api, conv.nickname, conv.avatarPath, 50.dp)
                Spacer(Modifier.width(13.dp))
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth()) { Text(conv.nickname, color = KehuaInk, fontSize = 16.sp, modifier = Modifier.weight(1f)); Text(shortTime(conv.lastAt.orEmpty()), color = Color(0xFFAFAFB5), fontSize = 10.sp) }
                    Spacer(Modifier.height(5.dp))
                    Text(conv.lastMessage ?: "从一次共鸣开始。", color = Color(0xFFA2A2A8), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(color = Color(0xFFEFEFF2), modifier = Modifier.padding(start = 81.dp))
        }
        item { Spacer(Modifier.height(28.dp)) }
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

    val bg = Color(0xFF101010)
    val card = Color(0xFF202020)
    val ink = Color(0xFFF1F1F1)
    val sub = Color(0xFF818181)

    Column(Modifier.fillMaxSize().background(bg)) {
        Row(Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = ink) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(conversation.nickname, color = ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(conversation.bio.ifBlank { "只有你们看得见这里" }, color = sub, fontSize = 10.sp, maxLines = 1)
            }
            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreHoriz, "更多", tint = sub) }
        }
        HorizontalDivider(color = Color(0xFF252525))
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            state = state,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item { Spacer(Modifier.height(12.dp)) }
            items(messages, key = { it.id }) { m ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.senderId == api.userId()) Arrangement.End else Arrangement.Start) {
                    Box(
                        Modifier.widthIn(max = 300.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (m.senderId == api.userId()) KehuaPink else card)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) { Text(m.body, color = ink, fontSize = 14.sp, lineHeight = 20.sp) }
                }
            }
            item {
                if (status.isNotBlank()) Text(status, color = sub, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF171717)).padding(10.dp).navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { if (it.length <= 2000) input = it },
                modifier = Modifier.weight(1f).clip(RoundedCornerShape(22.dp)).background(Color(0xFF272727)).padding(horizontal = 15.dp, vertical = 12.dp),
                textStyle = TextStyle(color = ink, fontSize = 14.sp),
                decorationBox = { inner -> if (input.isBlank()) Text("说点什么", color = sub, fontSize = 14.sp) else inner() }
            )
            Spacer(Modifier.width(7.dp))
            IconButton(enabled = !busy && input.isNotBlank(), onClick = {
                busy = true
                val sending = input
                input = ""
                scope.launch {
                    runCatching { api.sendMessage(conversation.id, sending) }
                        .onSuccess { runCatching { api.messages(conversation.id) }.onSuccess { messages = it } }
                        .onFailure { status = it.message.orEmpty(); input = sending }
                    busy = false
                }
            }) { Icon(Icons.Outlined.Send, "发送", tint = if (input.isBlank()) sub else KehuaPink) }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text(conversation.nickname) },
            text = { Text("你可以屏蔽或举报这个人。") },
            confirmButton = { TextButton(onClick = { scope.launch { runCatching { api.block(conversation.otherUserId) }; menu = false; onBack() } }) { Text("屏蔽", color = KehuaDanger) } },
            dismissButton = { TextButton(onClick = { scope.launch { runCatching { api.report(conversation.otherUserId, null, "聊天内容不适") }; menu = false } }) { Text("举报", color = KehuaSub) } }
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
    var friends by remember { mutableStateOf<List<KehuaFriend>>(emptyList()) }
    var status by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var settingsOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { avatarUri = it }

    LaunchedEffect(refresh) {
        runCatching { api.ensureProfile() }.onSuccess { p -> profile = p; nickname = p.nickname; bio = p.bio }.onFailure { status = it.message.orEmpty() }
        runCatching { api.myPosts() }.onSuccess { posts = it }
        runCatching { api.friends() }.onSuccess { friends = it }
    }

    if (settingsOpen) {
        KehuaSettings(onBack = { settingsOpen = false }, onLogout = onLogout)
        return
    }

    LazyColumn(Modifier.fillMaxSize().background(Color(0xFFF6F6F9))) {
        item {
            Box(
                Modifier.fillMaxWidth().height(250.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFFB59684), Color(0xFFD2C0B2), Color(0xFFE8DFD8))))
            ) {
                IconButton(onClick = { settingsOpen = true }, modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)) {
                    Icon(Icons.Outlined.Settings, "设置", tint = Color.White, modifier = Modifier.size(28.dp))
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.clickable { picker.launch("image/*") }) {
                        if (avatarUri != null) AsyncImage(model = avatarUri, contentDescription = null, modifier = Modifier.size(76.dp).clip(CircleShape), contentScale = ContentScale.Crop)
                        else KehuaAvatar(api, nickname.ifBlank { "我" }, profile?.avatarPath, 76.dp)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BasicTextField(
                                value = nickname,
                                onValueChange = { if (it.length <= 20) nickname = it },
                                textStyle = TextStyle(color = KehuaInk, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(7.dp))
                            Box(Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFE8EF)).padding(horizontal = 7.dp, vertical = 3.dp)) {
                                Text("VIP", color = KehuaPink, fontSize = 10.sp)
                            }
                        }
                        Spacer(Modifier.height(7.dp))
                        BasicTextField(
                            value = bio,
                            onValueChange = { if (it.length <= 120) bio = it },
                            textStyle = TextStyle(color = Color(0xFF929299), fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth(),
                            decorationBox = { inner -> if (bio.isBlank()) Text("写一句关于自己", color = Color(0xFFAAAAAF), fontSize = 12.sp) else inner() }
                        )
                    }
                }
                Text(
                    if (busy) "保存中…" else "保存资料",
                    color = KehuaPink,
                    fontSize = 12.sp,
                    modifier = Modifier.align(Alignment.End).clickable(enabled = !busy) {
                        busy = true
                        scope.launch {
                            runCatching {
                                val path = avatarUri?.let { api.uploadImage(it, "avatars") } ?: profile?.avatarPath
                                api.saveProfile(nickname, bio, path)
                            }.onSuccess { avatarUri = null; status = "保存好了"; refresh++ }.onFailure { status = it.message.orEmpty() }
                            busy = false
                        }
                    }.padding(8.dp)
                )
                if (status.isNotBlank()) Text(status, color = Color(0xFF9C9CA2), fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f).height(78.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(15.dp)) {
                        Text(friends.size.toString() + "位", color = KehuaInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("好友", color = Color(0xFF97979E), fontSize = 12.sp)
                    }
                    Column(Modifier.weight(1f).height(78.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(15.dp)) {
                        Text(posts.sumOf { it.resonanceCount }.toString() + "条", color = KehuaInk, fontSize = 25.sp, fontWeight = FontWeight.Bold)
                        Text("点亮", color = Color(0xFF97979E), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().height(58.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("查看 VIP 特权", color = KehuaInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                    Text("♕", color = KehuaPink, fontSize = 20.sp)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFCECED4), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)) {
                    Text("相册", color = KehuaInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(12.dp))
                    val imagePosts = posts.filter { !it.imagePath.isNullOrBlank() }.take(4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(4) { index ->
                            Box(
                                Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(8.dp))
                                    .background(listOf(Color(0xFF754139), Color(0xFFC39B5E), Color(0xFF7B5638), Color(0xFF8B827D))[index]),
                                contentAlignment = Alignment.Center
                            ) {
                                val post = imagePosts.getOrNull(index)
                                if (post != null) SignedImage(api, post.imagePath, Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)))
                                else if (index == 3 && posts.size > 4) Text("+" + (posts.size - 3).toString(), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
        items(posts, key = { it.id }) { post ->
            Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)) {
                    Text(post.body, color = KehuaInk, fontSize = 14.sp, lineHeight = 21.sp)
                    if (!post.imagePath.isNullOrBlank()) {
                        Spacer(Modifier.height(10.dp))
                        SignedImage(api, post.imagePath, Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(12.dp)))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(shortTime(post.createdAt), color = Color(0xFFBDBDC3), fontSize = 10.sp)
                }
            }
        }
        item { Spacer(Modifier.height(28.dp)) }
    }
}

@Composable
private fun KehuaSettings(onBack: () -> Unit, onLogout: () -> Unit) {
    val groups = listOf(
        listOf("个人信息", "账号与安全", "表情整理"),
        listOf("深色模式", "新消息通知", "通用", "隐私"),
        listOf("清除缓存", "检测更新"),
        listOf("给可话打赏，支持我们❤️", "给可话反馈", "把可话推荐给朋友", "给可话好评，鼓励一下")
    )
    LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
        item {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = KehuaInk) }
                Text("设置", color = KehuaInk, fontSize = 18.sp)
            }
        }
        groups.forEachIndexed { groupIndex, group ->
            items(group) { label ->
                Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(label, color = KehuaInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (label == "深色模式") Text("跟随系统", color = Color(0xFFB7B7BD), fontSize = 11.sp)
                    if (label == "清除缓存") Text("66.61 MB", color = Color(0xFFB7B7BD), fontSize = 11.sp)
                    Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFCECED4), modifier = Modifier.size(20.dp))
                }
            }
            if (groupIndex != groups.lastIndex) item { HorizontalDivider(color = Color(0xFFF3F3F6), thickness = 10.dp) }
        }
        item {
            Spacer(Modifier.height(12.dp))
            Text("退出当前账号", color = KehuaDanger, fontSize = 14.sp, modifier = Modifier.fillMaxWidth().clickable { onLogout() }.padding(20.dp))
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
