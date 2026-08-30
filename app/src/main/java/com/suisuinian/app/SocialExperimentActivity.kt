package com.suisuinian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val BGreen = Color(0xFF21A675)
private val BInk = Color(0xFF171A1F)
private val BSub = Color(0xFF7C828A)
private val BLine = Color(0xFFE9ECEF)
private val BBg = Color(0xFFF5F6F4)

class SocialExperimentActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val api = remember { SupabaseApi(this@SocialExperimentActivity) }
                Surface(Modifier.fillMaxSize(), color = BBg) { SocialLiveApp(api) }
            }
        }
    }
}

@Composable
private fun SocialLiveApp(api: SupabaseApi) {
    var loggedIn by remember { mutableStateOf(api.isLoggedIn) }
    if (!loggedIn) {
        AuthGate(api) { loggedIn = true }
    } else {
        LiveShell(api) {
            api.logout()
            loggedIn = false
        }
    }
}

@Composable
private fun AuthGate(api: SupabaseApi, onAuthenticated: () -> Unit) {
    var loginMode by remember { mutableStateOf(true) }
    var displayName by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(Color.White).padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(if (loginMode) "登录" else "注册", color = BInk, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (loginMode) "进入你的消息和朋友" else "创建一个可以被朋友找到的账号", color = BSub, fontSize = 14.sp)
        Spacer(Modifier.height(28.dp))

        if (!loginMode) {
            AuthField("昵称", displayName, { displayName = it }, KeyboardType.Text)
            Spacer(Modifier.height(12.dp))
            AuthField("唯一账号（3-20位字母/数字/_）", username, { username = it.lowercase() }, KeyboardType.Ascii)
            Spacer(Modifier.height(12.dp))
        }
        AuthField("邮箱", email, { email = it }, KeyboardType.Email)
        Spacer(Modifier.height(12.dp))
        AuthField("密码", password, { password = it }, KeyboardType.Password, password = true)

        if (status.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            Text(status, color = if (status.contains("成功")) BGreen else Color(0xFFB44A4A), fontSize = 13.sp, lineHeight = 19.sp)
        }

        Spacer(Modifier.height(20.dp))
        Button(
            onClick = {
                if (busy) return@Button
                status = ""
                if (email.isBlank() || password.length < 6) {
                    status = "请输入有效邮箱，密码至少 6 位"
                    return@Button
                }
                if (!loginMode) {
                    if (displayName.isBlank()) {
                        status = "请输入昵称"
                        return@Button
                    }
                    if (!Regex("^[a-z0-9_]{3,20}$").matches(username)) {
                        status = "账号只能用 3-20 位小写字母、数字或下划线"
                        return@Button
                    }
                }
                busy = true
                scope.launch {
                    val result = if (loginMode) api.signIn(email, password)
                    else api.signUp(displayName, username, email, password)
                    busy = false
                    if (result.success && !result.needsEmailConfirmation) {
                        onAuthenticated()
                    } else if (result.success) {
                        status = result.message
                        loginMode = true
                        password = ""
                    } else {
                        status = if (result.message.contains("Database error", true)) "账号可能已被使用，请换一个账号再试" else result.message
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BGreen),
            enabled = !busy
        ) {
            Text(if (busy) "处理中…" else if (loginMode) "登录" else "注册", fontSize = 16.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (loginMode) "没有账号？注册" else "已有账号？登录",
            color = BGreen,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally).clickable {
                loginMode = !loginMode
                status = ""
            }.padding(8.dp)
        )
    }
}

@Composable
private fun AuthField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType,
    password: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = BGreen,
            focusedLabelColor = BGreen,
            cursorColor = BGreen
        )
    )
}

@Composable
private fun LiveShell(api: SupabaseApi, onLogout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var page by remember { mutableStateOf("main") }
    var chatFriend by remember { mutableStateOf<LiveProfile?>(null) }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when {
                chatFriend != null -> LiveChatPage(api, chatFriend!!, onBack = { chatFriend = null })
                page == "contacts" -> ContactsPage(api, onBack = { page = "main" }, onAdd = { page = "add" }, onChat = { chatFriend = it })
                page == "add" -> AddFriendPage(api, onBack = { page = "contacts" })
                tab == 0 -> LiveMessagesPage(api) { chatFriend = it }
                tab == 1 -> FriendsPhasePlaceholder()
                else -> MePage(api, onContacts = { page = "contacts" }, onLogout = onLogout)
            }
        }
        if (chatFriend == null && page == "main") BottomBar(tab) { tab = it }
    }
}

@Composable
private fun TopBar(title: String, leftBack: (() -> Unit)? = null, right: String? = null, onRight: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leftBack != null) {
            Text("‹", color = BInk, fontSize = 32.sp, modifier = Modifier.clickable { leftBack() }.padding(end = 12.dp))
        }
        Text(title, color = BInk, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (right != null) {
            Text(right, color = BGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onRight?.invoke() }.padding(5.dp))
        }
    }
}

@Composable
private fun LiveMessagesPage(api: SupabaseApi, onChat: (LiveProfile) -> Unit) {
    var conversations by remember { mutableStateOf<List<ConversationSummary>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            runCatching { api.directConversations() }
                .onSuccess {
                    conversations = it
                    error = ""
                }
                .onFailure { error = it.message ?: "加载失败" }
            loading = false
            delay(1500)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("消息")
        when {
            loading -> CenterText("正在加载…")
            error.isNotBlank() -> RetryState(error)
            conversations.isEmpty() -> CenterText("还没有聊天")
            else -> LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
                items(conversations, key = { it.conversationId }) { item ->
                    ConversationRow(item, onClick = { onChat(item.friend) })
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun ConversationRow(item: ConversationSummary, onClick: () -> Unit) {
    val subtitle = when {
        item.lastMessageType == "image" -> "[图片]"
        item.lastMessageType == "video" -> "[视频]"
        item.lastMessageType == "voice" -> "[语音]"
        item.lastMessage.isNotBlank() -> item.lastMessage
        else -> "开始聊天"
    }
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(item.friend.displayName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(item.friend.displayName, color = BInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = BSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (item.lastMessageAt.isNotBlank()) Text(shortTime(item.lastMessageAt), color = BSub, fontSize = 11.sp)
            if (item.unreadCount > 0) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(CircleShape).background(BGreen).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(if (item.unreadCount > 99) "99+" else item.unreadCount.toString(), color = Color.White, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun FriendsPhasePlaceholder() {
    Column(Modifier.fillMaxSize()) {
        TopBar("朋友")
        Text("这里只看认识的人，没有推荐流。", color = BSub, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 2.dp))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Text("还没有动态", color = BSub, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MePage(api: SupabaseApi, onContacts: () -> Unit, onLogout: () -> Unit) {
    var me by remember { mutableStateOf<LiveProfile?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        runCatching { api.myProfile() }
            .onSuccess { me = it }
            .onFailure { error = it.message ?: "加载失败" }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("我")
        Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(me?.displayName ?: "我", 66.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(me?.displayName ?: "我的账号", color = BInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            me != null -> "账号：${me!!.username}"
                            error.isNotBlank() -> error
                            else -> "加载中…"
                        },
                        color = if (error.isBlank()) BSub else Color(0xFFB44A4A),
                        fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MenuRow("联系人", onContacts)
        DividerLine()
        MenuRow("退出登录", onLogout, arrow = false, textColor = Color(0xFFB44A4A))
    }
}

@Composable
private fun ContactsPage(api: SupabaseApi, onBack: () -> Unit, onAdd: () -> Unit, onChat: (LiveProfile) -> Unit) {
    var friends by remember { mutableStateOf<List<LiveProfile>>(emptyList()) }
    var requests by remember { mutableStateOf<List<IncomingRequest>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        loading = true
        runCatching {
            requests = api.incomingRequests()
            friends = api.friends()
        }.onSuccess {
            status = ""
        }.onFailure {
            status = it.message ?: "加载失败"
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("联系人", leftBack = onBack, right = "＋", onRight = onAdd)
        if (loading) {
            CenterText("正在加载…")
            return@Column
        }
        if (status.isNotBlank()) Text(status, color = Color(0xFFB44A4A), fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
            if (requests.isNotEmpty()) {
                item {
                    Text("好友申请", color = BSub, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(BBg).padding(horizontal = 18.dp, vertical = 10.dp))
                }
                items(requests, key = { it.id }) { request ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(request.sender.displayName)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(request.sender.displayName, color = BInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("账号：${request.sender.username}", color = BSub, fontSize = 12.sp)
                        }
                        Text("拒绝", color = BSub, fontSize = 13.sp, modifier = Modifier.clickable {
                            scope.launch {
                                status = api.respondFriendRequest(request.id, false) ?: ""
                                refresh++
                            }
                        }.padding(8.dp))
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(BGreen).clickable {
                            scope.launch {
                                status = api.respondFriendRequest(request.id, true) ?: ""
                                refresh++
                            }
                        }.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            Text("接受", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    DividerLine()
                }
            }
            item {
                Text("朋友", color = BSub, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(BBg).padding(horizontal = 18.dp, vertical = 10.dp))
            }
            if (friends.isEmpty()) {
                item { Text("还没有好友", color = BSub, fontSize = 14.sp, modifier = Modifier.padding(20.dp)) }
            } else {
                items(friends, key = { it.id }) { friend ->
                    PersonRow(friend, subtitle = "账号：${friend.username}", onClick = { onChat(friend) })
                    DividerLine()
                }
            }
        }
    }
}

@Composable
private fun AddFriendPage(api: SupabaseApi, onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var result by remember { mutableStateOf<LiveProfile?>(null) }
    var searched by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar("添加朋友", leftBack = onBack)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.lowercase(); result = null; searched = false; status = "" },
                placeholder = { Text("输入对方完整账号") },
                singleLine = true,
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BGreen, cursorColor = BGreen)
            )
            Spacer(Modifier.width(10.dp))
            Text("搜索", color = BGreen, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !busy && query.isNotBlank()) {
                busy = true
                scope.launch {
                    runCatching { api.searchExactUsername(query) }
                        .onSuccess { result = it; status = "" }
                        .onFailure { status = it.message ?: "搜索失败" }
                    searched = true
                    busy = false
                }
            }.padding(8.dp))
        }
        when {
            busy -> CenterText("正在搜索…")
            result != null -> {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Avatar(result!!.displayName)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(result!!.displayName, color = BInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("账号：${result!!.username}", color = BSub, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val error = api.sendFriendRequest(result!!.id)
                                status = error ?: "好友申请已发送"
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = BGreen)
                    ) { Text("添加") }
                }
            }
            searched && status.isBlank() -> CenterText("没有找到这个账号")
        }
        if (status.isNotBlank()) {
            Text(status, color = if (status.contains("已发送")) BGreen else Color(0xFFB44A4A), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun LiveChatPage(api: SupabaseApi, friend: LiveProfile, onBack: () -> Unit) {
    var conversationId by remember(friend.id) { mutableStateOf<String?>(null) }
    var messages by remember(friend.id) { mutableStateOf<List<LiveMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val me = api.userId()

    LaunchedEffect(friend.id) {
        runCatching { api.conversationId(friend.id) }
            .onSuccess { conversationId = it }
            .onFailure { status = it.message ?: "会话加载失败" }
        loading = false
        val cid = conversationId ?: return@LaunchedEffect
        while (isActive) {
            runCatching {
                val latest = api.messages(cid)
                api.markConversationRead(cid)
                latest
            }.onSuccess {
                if (it != messages) messages = it
                status = ""
            }.onFailure {
                status = it.message ?: "消息加载失败"
            }
            delay(1000)
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar(friend.displayName, leftBack = onBack)
        DividerLine()
        when {
            loading -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("正在打开会话…", color = BSub) }
            conversationId == null -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(status.ifBlank { "会话还没准备好，请返回联系人后重试" }, color = Color(0xFFB44A4A), fontSize = 14.sp)
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().background(BBg).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        val mine = message.senderId == me
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
                            Box(
                                Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(14.dp))
                                    .background(if (mine) Color(0xFFCFF1E5) else Color.White)
                                    .padding(horizontal = 13.dp, vertical = 10.dp)
                            ) {
                                Text(message.content, color = BInk, fontSize = 15.sp, lineHeight = 21.sp)
                            }
                        }
                    }
                }
                if (status.isNotBlank()) Text(status, color = Color(0xFFB44A4A), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                Row(Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(BBg).padding(horizontal = 14.dp, vertical = 11.dp)) {
                        BasicTextField(input, { input = it }, Modifier.fillMaxWidth(), textStyle = TextStyle(color = BInk, fontSize = 15.sp))
                        if (input.isEmpty()) Text("发消息", color = BSub, fontSize = 15.sp)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("发送", color = if (input.isBlank()) BSub else BGreen, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = input.isNotBlank()) {
                        val text = input.trim()
                        val cid = conversationId ?: return@clickable
                        scope.launch {
                            val error = api.sendText(cid, text)
                            if (error == null) {
                                input = ""
                                status = ""
                                runCatching { api.messages(cid) }.onSuccess { messages = it }
                            } else status = error
                        }
                    }.padding(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PersonRow(profile: LiveProfile, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(profile.displayName)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(profile.displayName, color = BInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = BSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = BSub, fontSize = 22.sp)
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit, arrow: Boolean = true, textColor: Color = BInk) {
    Row(Modifier.fillMaxWidth().background(Color.White).clickable { onClick() }.padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (arrow) Text("›", color = BSub, fontSize = 22.sp)
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val labels = listOf("消息", "朋友", "我")
    Row(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 9.dp)) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f).clickable { onTab(index) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (index == tab) "●" else "○", color = if (index == tab) BGreen else BSub, fontSize = 13.sp)
                Text(label, color = if (index == tab) BGreen else BSub, fontSize = 12.sp, fontWeight = if (index == tab) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp = 46.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFDDEFE8)), contentAlignment = Alignment.Center) {
        Text(name.take(1).ifBlank { "?" }, color = BGreen, fontWeight = FontWeight.Bold, fontSize = if (size > 50.dp) 22.sp else 16.sp)
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(BLine))
}

@Composable
private fun ColumnScope.CenterText(text: String) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = BSub, fontSize = 14.sp)
    }
}

@Composable
private fun ColumnScope.RetryState(text: String) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = Color(0xFFB44A4A), fontSize = 14.sp)
    }
}

private fun shortTime(value: String): String {
    if (value.length >= 16 && value.contains('T')) return value.substring(11, 16)
    return ""
}
