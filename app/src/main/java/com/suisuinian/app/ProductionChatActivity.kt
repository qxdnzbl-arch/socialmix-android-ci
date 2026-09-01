package com.suisuinian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private val ChatGreen = Color(0xFF21A675)
private val ChatInk = Color(0xFF171A1F)
private val ChatSub = Color(0xFF7C828A)
private val ChatLine = Color(0xFFE9ECEF)
private val ChatBg = Color(0xFFF5F6F4)
private val ChatError = Color(0xFFB44A4A)

class ProductionChatActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                val api = remember { SupabaseApi(this@ProductionChatActivity) }
                Surface(Modifier.fillMaxSize(), color = ChatBg) {
                    SocialMixApp(api)
                }
            }
        }
    }
}

@Composable
private fun SocialMixApp(api: SupabaseApi) {
    var loggedIn by remember { mutableStateOf(api.isLoggedIn) }
    LaunchedEffect(loggedIn) {
        if (loggedIn) api.startRealtime()
    }
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
        Text(if (loginMode) "登录" else "注册", color = ChatInk, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(if (loginMode) "进入你的消息和朋友" else "创建一个可以被朋友找到的账号", color = ChatSub, fontSize = 14.sp)
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
            Text(status, color = if (status.contains("成功")) ChatGreen else ChatError, fontSize = 13.sp, lineHeight = 19.sp)
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
            colors = ButtonDefaults.buttonColors(containerColor = ChatGreen),
            enabled = !busy
        ) {
            Text(if (busy) "处理中…" else if (loginMode) "登录" else "注册", fontSize = 16.sp)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            if (loginMode) "没有账号？注册" else "已有账号？登录",
            color = ChatGreen,
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
            focusedBorderColor = ChatGreen,
            focusedLabelColor = ChatGreen,
            cursorColor = ChatGreen
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
            Text("‹", color = ChatInk, fontSize = 32.sp, modifier = Modifier.clickable { leftBack() }.padding(end = 12.dp))
        }
        Text(title, color = ChatInk, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (right != null) {
            Text(right, color = ChatGreen, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable { onRight?.invoke() }.padding(5.dp))
        }
    }
}

@Composable
private fun LiveMessagesPage(api: SupabaseApi, onChat: (LiveProfile) -> Unit) {
    val conversations = api.conversationFeed()
    var firstLoad by remember { mutableStateOf(conversations.isEmpty()) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (isActive) {
            runCatching { api.refreshConversations() }
                .onSuccess { error = "" }
                .onFailure { if (conversations.isEmpty()) error = it.message ?: "网络暂不可用" }
            firstLoad = false
            delay(30_000)
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("消息")
        when {
            firstLoad && conversations.isEmpty() -> CenterText("正在同步消息…")
            conversations.isEmpty() && error.isNotBlank() -> CenterText("网络暂不可用")
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
            Text(item.friend.displayName, color = ChatInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = ChatSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (item.lastMessageAt.isNotBlank()) Text(shortTime(item.lastMessageAt), color = ChatSub, fontSize = 11.sp)
            if (item.unreadCount > 0) {
                Spacer(Modifier.height(6.dp))
                Box(Modifier.clip(CircleShape).background(ChatGreen).padding(horizontal = 7.dp, vertical = 2.dp)) {
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
        Text("这里只看认识的人，没有推荐流。", color = ChatSub, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 18.dp, vertical = 2.dp))
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
            Text("还没有动态", color = ChatSub, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MePage(api: SupabaseApi, onContacts: () -> Unit, onLogout: () -> Unit) {
    var me by remember { mutableStateOf(api.cachedProfile()) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        runCatching { api.myProfile() }
            .onSuccess {
                if (it != null) me = it
                error = ""
            }
            .onFailure { if (me == null) error = "账号信息暂不可用" }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("我")
        Column(Modifier.fillMaxWidth().background(Color.White).padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(me?.displayName ?: "我", 66.dp)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(me?.displayName ?: "我的账号", color = ChatInk, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        when {
                            me != null -> "账号：${me!!.username}"
                            error.isNotBlank() -> error
                            else -> "账号信息同步中"
                        },
                        color = if (error.isBlank()) ChatSub else ChatError,
                        fontSize = 13.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MenuRow("联系人", onContacts)
        DividerLine()
        MenuRow("退出登录", onLogout, arrow = false, textColor = ChatError)
    }
}

@Composable
private fun ContactsPage(api: SupabaseApi, onBack: () -> Unit, onAdd: () -> Unit, onChat: (LiveProfile) -> Unit) {
    val friends = api.friendFeed()
    var requests by remember { mutableStateOf<List<IncomingRequest>>(emptyList()) }
    var refresh by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(friends.isEmpty()) }
    var status by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    LaunchedEffect(refresh) {
        loading = friends.isEmpty()
        runCatching {
            requests = api.incomingRequests()
            api.refreshFriends()
        }.onSuccess {
            status = ""
        }.onFailure {
            if (friends.isEmpty()) status = it.message ?: "加载失败"
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        TopBar("联系人", leftBack = onBack, right = "＋", onRight = onAdd)
        if (loading && friends.isEmpty()) {
            CenterText("正在同步联系人…")
            return@Column
        }
        if (status.isNotBlank() && friends.isEmpty()) {
            Text(status, color = ChatError, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
        }
        LazyColumn(Modifier.fillMaxSize().background(Color.White)) {
            if (requests.isNotEmpty()) {
                item {
                    Text("好友申请", color = ChatSub, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(ChatBg).padding(horizontal = 18.dp, vertical = 10.dp))
                }
                items(requests, key = { it.id }) { request ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                        Avatar(request.sender.displayName)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(request.sender.displayName, color = ChatInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text("账号：${request.sender.username}", color = ChatSub, fontSize = 12.sp)
                        }
                        Text("拒绝", color = ChatSub, fontSize = 13.sp, modifier = Modifier.clickable {
                            scope.launch {
                                status = api.respondFriendRequest(request.id, false) ?: ""
                                refresh++
                            }
                        }.padding(8.dp))
                        Spacer(Modifier.width(4.dp))
                        Box(Modifier.clip(RoundedCornerShape(10.dp)).background(ChatGreen).clickable {
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
            if (friends.isNotEmpty()) {
                item {
                    Text("朋友", color = ChatSub, fontSize = 13.sp, modifier = Modifier.fillMaxWidth().background(ChatBg).padding(horizontal = 18.dp, vertical = 10.dp))
                }
                items(friends, key = { it.id }) { friend ->
                    PersonRow(friend, subtitle = "账号：${friend.username}", onClick = { onChat(friend) })
                    DividerLine()
                }
            }
            if (friends.isEmpty() && requests.isEmpty() && !loading) {
                item {
                    Box(Modifier.fillMaxWidth().padding(vertical = 72.dp), contentAlignment = Alignment.Center) {
                        Text("还没有联系人", color = ChatSub, fontSize = 14.sp)
                    }
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
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = ChatGreen, cursorColor = ChatGreen)
            )
            Spacer(Modifier.width(10.dp))
            Text("搜索", color = ChatGreen, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(enabled = !busy && query.isNotBlank()) {
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
                        Text(result!!.displayName, color = ChatInk, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                        Text("账号：${result!!.username}", color = ChatSub, fontSize = 13.sp)
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                val error = api.sendFriendRequest(result!!.id)
                                status = error ?: "好友申请已发送"
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ChatGreen)
                    ) { Text("添加") }
                }
            }
            searched && status.isBlank() -> CenterText("没有找到这个账号")
        }
        if (status.isNotBlank()) {
            Text(status, color = if (status.contains("已发送")) ChatGreen else ChatError, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun LiveChatPage(api: SupabaseApi, friend: LiveProfile, onBack: () -> Unit) {
    var conversationId by remember(friend.id) { mutableStateOf(api.cachedConversationId(friend.id)) }
    var openingError by remember(friend.id) { mutableStateOf("") }

    LaunchedEffect(friend.id) {
        if (conversationId == null) {
            runCatching { api.conversationId(friend.id) }
                .onSuccess { conversationId = it }
                .onFailure { openingError = it.message ?: "会话暂时无法打开" }
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White)) {
        TopBar(friend.displayName, leftBack = onBack)
        DividerLine()
        val cid = conversationId
        when {
            cid != null -> ChatContent(api, cid)
            openingError.isNotBlank() -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("网络暂不可用，请稍后再试", color = ChatSub, fontSize = 14.sp)
            }
            else -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("正在打开聊天…", color = ChatSub, fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun ColumnScope.ChatContent(api: SupabaseApi, conversationId: String) {
    val messages = api.chatFeed(conversationId)
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val me = api.userId()
    val focusManager = LocalFocusManager.current
    var input by remember { mutableStateOf("") }
    var initialSyncError by remember { mutableStateOf("") }
    var firstScrollDone by remember(conversationId) { mutableStateOf(false) }
    var previousCount by remember(conversationId) { mutableIntStateOf(messages.size) }

    DisposableEffect(conversationId) {
        api.enterChat(conversationId)
        onDispose { api.leaveChat(conversationId) }
    }

    LaunchedEffect(conversationId) {
        runCatching { api.syncMessages(conversationId) }
            .onSuccess { initialSyncError = "" }
            .onFailure { if (messages.isEmpty()) initialSyncError = "网络暂不可用" }
    }

    LaunchedEffect(messages.size) {
        if (messages.isEmpty()) {
            previousCount = 0
            return@LaunchedEffect
        }
        if (!firstScrollDone) {
            listState.scrollToItem(messages.lastIndex)
            firstScrollDone = true
            previousCount = messages.size
            return@LaunchedEffect
        }
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
        val wasNearBottom = lastVisible >= previousCount - 3
        if (wasNearBottom || messages.lastOrNull()?.senderId == me) {
            listState.animateScrollToItem(messages.lastIndex)
        }
        previousCount = messages.size
    }

    if (initialSyncError.isNotBlank() && messages.isEmpty()) {
        Box(Modifier.weight(1f).fillMaxWidth().background(ChatBg), contentAlignment = Alignment.Center) {
            Text(initialSyncError, color = ChatSub, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth().background(ChatBg).padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(messages, key = { message ->
                if (message.clientMessageId.isNotBlank()) "${message.senderId}:${message.clientMessageId}" else message.id
            }) { message ->
                val mine = message.senderId == me
                MessageRow(
                    message = message,
                    mine = mine,
                    onRetry = if (mine && message.delivery == MessageDelivery.FAILED) {
                        { scope.launch { api.retryText(conversationId, message) } }
                    } else null
                )
            }
        }
    }

    Row(
        Modifier.fillMaxWidth().background(Color.White).padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.weight(1f).clip(RoundedCornerShape(20.dp)).background(ChatBg)
                .padding(horizontal = 14.dp, vertical = 11.dp)
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = ChatInk, fontSize = 15.sp)
            )
            if (input.isEmpty()) Text("发消息", color = ChatSub, fontSize = 15.sp)
        }
        Spacer(Modifier.width(8.dp))
        Text(
            "发送",
            color = if (input.isBlank()) ChatSub else ChatGreen,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable(enabled = input.isNotBlank()) {
                val text = input.trim()
                if (text.isBlank()) return@clickable
                input = ""
                focusManager.clearFocus(force = false)
                scope.launch { api.sendText(conversationId, text) }
            }.padding(8.dp)
        )
    }
}

@Composable
private fun MessageRow(message: LiveMessage, mine: Boolean, onRetry: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start) {
        Column(horizontalAlignment = if (mine) Alignment.End else Alignment.Start) {
            Box(
                Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (mine) Color(0xFFCFF1E5) else Color.White)
                    .padding(horizontal = 13.dp, vertical = 10.dp)
            ) {
                Text(message.content, color = ChatInk, fontSize = 15.sp, lineHeight = 21.sp)
            }
            if (mine && message.delivery != MessageDelivery.SENT) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = if (message.delivery == MessageDelivery.SENDING) "发送中…" else "发送失败，点此重试",
                    color = if (message.delivery == MessageDelivery.SENDING) ChatSub else ChatError,
                    fontSize = 11.sp,
                    modifier = if (onRetry != null) Modifier.clickable { onRetry() }.padding(horizontal = 2.dp, vertical = 2.dp) else Modifier
                )
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
            Text(profile.displayName, color = ChatInk, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = ChatSub, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text("›", color = ChatSub, fontSize = 22.sp)
    }
}

@Composable
private fun MenuRow(text: String, onClick: () -> Unit, arrow: Boolean = true, textColor: Color = ChatInk) {
    Row(Modifier.fillMaxWidth().background(Color.White).clickable { onClick() }.padding(horizontal = 18.dp, vertical = 17.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = textColor, fontSize = 16.sp, modifier = Modifier.weight(1f))
        if (arrow) Text("›", color = ChatSub, fontSize = 22.sp)
    }
}

@Composable
private fun BottomBar(tab: Int, onTab: (Int) -> Unit) {
    val labels = listOf("消息", "朋友", "我")
    Row(Modifier.fillMaxWidth().background(Color.White).padding(vertical = 9.dp)) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f).clickable { onTab(index) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (index == tab) "●" else "○", color = if (index == tab) ChatGreen else ChatSub, fontSize = 13.sp)
                Text(label, color = if (index == tab) ChatGreen else ChatSub, fontSize = 12.sp, fontWeight = if (index == tab) FontWeight.Bold else FontWeight.Normal)
            }
        }
    }
}

@Composable
private fun Avatar(name: String, size: Dp = 46.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(0xFFDDEFE8)), contentAlignment = Alignment.Center) {
        Text(name.take(1).ifBlank { "?" }, color = ChatGreen, fontWeight = FontWeight.Bold, fontSize = if (size > 50.dp) 22.sp else 16.sp)
    }
}

@Composable
private fun DividerLine() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(ChatLine))
}

@Composable
private fun ColumnScope.CenterText(text: String) {
    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(text, color = ChatSub, fontSize = 14.sp)
    }
}

private fun shortTime(value: String): String {
    if (value.length >= 16 && value.contains('T')) return value.substring(11, 16)
    return ""
}
