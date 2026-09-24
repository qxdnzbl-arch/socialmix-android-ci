package com.suisuinian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonAddAlt
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val KH_PINK = Color(0xFFFF3F70)
private val KH_INK = Color(0xFF202024)
private val KH_SUB = Color(0xFF97979F)
private val KH_LINE = Color(0xFFEDEDF1)
private val KH_BG = Color(0xFFF8F8FB)
private val KH_BLUE = Color(0xFF6675F5)
private val KH_GRADIENT = Brush.verticalGradient(
    listOf(
        Color(0xFFC9DAFF),
        Color(0xFFD9DDF9),
        Color(0xFFF0DCEB),
        Color(0xFFF8F8FB)
    )
)

class KehuaNativeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = KH_PINK,
                    background = Color.White,
                    surface = Color.White,
                    onSurface = KH_INK
                )
            ) {
                KehuaApp(KehuaProdApi(this))
            }
        }
    }
}

@Composable
private fun KehuaApp(api: KehuaProdApi) {
    var loggedIn by remember { mutableStateOf(api.isLoggedIn) }
    if (!loggedIn) {
        Auth(api) { loggedIn = true }
    } else {
        Shell(api) {
            api.logout()
            loggedIn = false
        }
    }
}

@Composable
private fun Auth(api: KehuaProdApi, done: () -> Unit) {
    var register by remember { mutableStateOf(false) }
    var recover by remember { mutableStateOf(false) }
    var account by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var nickname by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var recoveryToShow by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Column(
        Modifier.fillMaxSize().background(Color.White).padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("可话", color = KH_INK, fontSize = 44.sp, fontWeight = FontWeight.Black)
        Text("说你想说的话", color = KH_SUB, fontSize = 14.sp)
        Spacer(Modifier.height(44.dp))

        if (register && !recover) {
            OutlinedTextField(
                nickname,
                { nickname = it.take(24) },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("nickname")
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            account,
            { account = it.lowercase() },
            label = { Text("账号") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login")
        )
        Spacer(Modifier.height(10.dp))

        if (recover) {
            OutlinedTextField(
                code,
                { code = it.uppercase() },
                label = { Text("恢复码") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("recovery-code")
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            password,
            { password = it },
            label = { Text(if (recover) "新密码" else "密码") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth().testTag("password")
        )

        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFB74A58), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
        }

        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                if (!busy) {
                    busy = true
                    error = ""
                    scope.launch {
                        val result = when {
                            recover -> api.recover(account, code, password)
                            register -> api.register(account, password, nickname)
                            else -> api.login(account, password)
                        }
                        result.onSuccess { session ->
                            if (session.recoveryCode != null) recoveryToShow = session.recoveryCode else done()
                        }.onFailure {
                            error = it.message ?: "操作失败"
                        }
                        busy = false
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = KH_PINK),
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth().height(54.dp).testTag("auth-submit")
        ) {
            Text(
                when {
                    recover -> "重置密码"
                    register -> "注册并进入可话"
                    else -> "登录"
                }
            )
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = {
                recover = false
                register = !register
                error = ""
            }) {
                Text(if (register) "已有账号？登录" else "第一次来？注册", color = KH_SUB)
            }
            if (!register) {
                TextButton(onClick = {
                    recover = !recover
                    error = ""
                }) {
                    Text(if (recover) "返回登录" else "忘记密码？", color = KH_SUB)
                }
            }
        }
    }

    recoveryToShow?.let { recovery ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("请保存恢复码") },
            text = { Text(recovery + "\n\n忘记密码时需要它。生成新恢复码后旧码会失效。") },
            confirmButton = {
                Button(
                    onClick = {
                        recoveryToShow = null
                        done()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KH_PINK)
                ) { Text("我已保存") }
            }
        )
    }
}

private enum class AppRoute { ROOT, RESONANCE, CHAT, SETTINGS, EDIT_PROFILE }

@Composable
private fun Shell(api: KehuaProdApi, logout: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    var route by remember { mutableStateOf(AppRoute.ROOT) }
    var peer by remember { mutableStateOf<NativePeer?>(null) }
    var resonance by remember { mutableStateOf<NativeResonance?>(null) }

    BackHandler(route != AppRoute.ROOT) {
        route = AppRoute.ROOT
        peer = null
        resonance = null
    }

    Scaffold(
        containerColor = Color.White,
        bottomBar = {
            if (route == AppRoute.ROOT) {
                OriginalBottomBar(tab) { tab = it }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (route) {
                AppRoute.ROOT -> when (tab) {
                    0 -> Home(
                        api = api,
                        openResonance = {
                            resonance = it
                            route = AppRoute.RESONANCE
                        }
                    )
                    1 -> Threads(api) {
                        peer = it
                        route = AppRoute.CHAT
                    }
                    else -> Me(api) { route = AppRoute.SETTINGS }
                }

                AppRoute.RESONANCE -> resonance?.let { item ->
                    ResonanceScreen(
                        api = api,
                        resonance = item,
                        back = {
                            resonance = null
                            route = AppRoute.ROOT
                        },
                        chat = {
                            peer = it
                            resonance = null
                            route = AppRoute.CHAT
                        }
                    )
                }

                AppRoute.CHAT -> peer?.let { item ->
                    Chat(api, item) {
                        peer = null
                        route = AppRoute.ROOT
                    }
                }

                AppRoute.SETTINGS -> SettingsScreen(
                    api = api,
                    back = { route = AppRoute.ROOT },
                    editProfile = { route = AppRoute.EDIT_PROFILE },
                    logout = logout
                )

                AppRoute.EDIT_PROFILE -> EditProfileScreen(
                    api = api,
                    back = { route = AppRoute.SETTINGS }
                )
            }
        }
    }
}

@Composable
private fun OriginalBottomBar(selected: Int, select: (Int) -> Unit) {
    NavigationBar(
        containerColor = Color.White,
        tonalElevation = 0.dp,
        modifier = Modifier.height(72.dp)
    ) {
        val icons = listOf(Icons.Outlined.Home, Icons.Outlined.ChatBubbleOutline, Icons.Outlined.PersonOutline)
        val tags = listOf("nav-home", "nav-messages", "nav-me")
        icons.forEachIndexed { index, icon ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { select(index) },
                modifier = Modifier.testTag(tags[index]),
                icon = {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(27.dp)
                    )
                },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = KH_INK,
                    unselectedIconColor = Color(0xFFBFC0C6),
                    indicatorColor = Color.Transparent
                )
            )
        }
    }
}

@Composable
private fun Home(api: KehuaProdApi, openResonance: (NativeResonance) -> Unit) {
    var home by remember { mutableStateOf<NativeHome?>(null) }
    var draft by remember { mutableStateOf("") }
    var privatePost by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var publishing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val dateLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日EEEE", Locale.SIMPLIFIED_CHINESE))
    }

    fun load() {
        scope.launch {
            api.home()
                .onSuccess {
                    home = it
                    error = ""
                }
                .onFailure { error = it.message ?: "加载失败" }
        }
    }

    LaunchedEffect(Unit) { load() }

    BoxWithConstraints(
        Modifier.fillMaxSize().background(KH_GRADIENT).testTag("home-screen")
    ) {
        val firstGap = maxHeight * 0.28f

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Spacer(Modifier.height(firstGap))
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(dateLabel, color = Color(0xFF777780), fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "此刻，说你想说的话～",
                        color = KH_INK,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.Black
                    )
                    Spacer(Modifier.height(16.dp))
                    ComposerCard(
                        draft = draft,
                        privatePost = privatePost,
                        publishing = publishing,
                        onDraft = { if (it.length <= 1000) draft = it },
                        onPrivacy = { privatePost = !privatePost },
                        onPublish = {
                            val text = draft.trim()
                            if (text.isNotEmpty() && !publishing) {
                                publishing = true
                                scope.launch {
                                    api.createPost(text, privatePost)
                                        .onSuccess {
                                            draft = ""
                                            privatePost = false
                                            load()
                                            snackbar.showSnackbar("正在为你寻找共鸣～")
                                        }
                                        .onFailure { error = it.message ?: "发布失败" }
                                    publishing = false
                                }
                            }
                        }
                    )
                    if (error.isNotBlank()) {
                        Text(error, color = Color(0xFFB74A58), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                    }
                }
            }

            val resonances = home?.resonances.orEmpty()
            if (resonances.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "共鸣",
                        color = KH_SUB,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                items(resonances, key = { it.id }) { item ->
                    ResonanceArrivalCard(item) {
                        scope.launch {
                            api.openResonance(item.id)
                            openResonance(item)
                        }
                    }
                }
            }

            val posts = home?.posts.orEmpty()
            if (posts.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(18.dp))
                    Text(
                        "我说过的话",
                        color = KH_SUB,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                items(posts, key = { it.id }) { post ->
                    OwnPostCard(post)
                }
            }
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp)
        ) { data ->
            Surface(
                color = KH_INK,
                shape = RoundedCornerShape(24.dp)
            ) {
                Text(
                    data.visuals.message,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 22.dp, vertical = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun ComposerCard(
    draft: String,
    privatePost: Boolean,
    publishing: Boolean,
    onDraft: (String) -> Unit,
    onPrivacy: () -> Unit,
    onPublish: () -> Unit
) {
    Surface(
        color = Color.White,
        shape = RoundedCornerShape(25.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 18.dp, vertical = 16.dp)) {
            Box(Modifier.fillMaxWidth().height(210.dp)) {
                BasicTextField(
                    value = draft,
                    onValueChange = onDraft,
                    textStyle = TextStyle(color = KH_INK, fontSize = 16.sp, lineHeight = 23.sp),
                    modifier = Modifier.fillMaxSize().testTag("composer"),
                    decorationBox = { inner ->
                        if (draft.isEmpty()) {
                            Text("我想说...", color = Color(0xFFD0D0D5), fontSize = 17.sp)
                        }
                        inner()
                    }
                )
            }

            HorizontalDivider(color = KH_LINE)

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFFF5F4F8),
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Add, null, tint = Color(0xFF8D8D95), modifier = Modifier.size(27.dp))
                    }
                }

                Spacer(Modifier.width(10.dp))
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color(0xFFF5F4F8),
                    modifier = Modifier.clickable(onClick = onPrivacy)
                ) {
                    Text(
                        if (privatePost) "仅自己" else "公开",
                        color = Color(0xFF8D8D95),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                    )
                }

                Spacer(Modifier.width(10.dp))
                Text(draft.length.toString() + "/1000", color = Color(0xFFADADB4), fontSize = 12.sp)
                Spacer(Modifier.weight(1f))

                Button(
                    onClick = onPublish,
                    enabled = draft.trim().isNotEmpty() && !publishing,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KH_PINK,
                        disabledContainerColor = Color(0xFFFFB7C9)
                    ),
                    shape = RoundedCornerShape(24.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    modifier = Modifier.testTag("publish")
                ) {
                    Text(if (publishing) "发布中" else "发表", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ResonanceArrivalCard(item: NativeResonance, open: () -> Unit) {
    Surface(
        color = Color.White.copy(alpha = 0.96f),
        shape = RoundedCornerShape(22.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clickable(onClick = open)
            .testTag("resonance-card")
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("✦", color = KH_PINK, fontSize = 22.sp)
                Spacer(Modifier.width(10.dp))
                Text(
                    "共鸣已到达，请签收～！",
                    color = KH_INK,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                item.content,
                color = KH_SUB,
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun OwnPostCard(post: NativePost) {
    Surface(
        color = Color.White.copy(alpha = 0.96f),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)
    ) {
        Column(Modifier.padding(17.dp)) {
            Text(post.content, color = KH_INK, fontSize = 16.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(10.dp))
            Text(
                if (post.isPrivate) "仅自己可见" else post.lightCount.toString() + " 次点亮",
                color = KH_SUB,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
private fun ResonanceScreen(
    api: KehuaProdApi,
    resonance: NativeResonance,
    back: () -> Unit,
    chat: (NativePeer) -> Unit
) {
    var error by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize().background(Color.White).testTag("resonance-screen")) {
        IconButton(onClick = back, modifier = Modifier.align(Alignment.TopStart).padding(10.dp)) {
            Icon(Icons.Outlined.Close, null, tint = KH_INK)
        }

        Surface(
            color = Color(0xFFF3F3F6),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
        ) {
            Text("1/1", color = Color(0xFF66666E), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
        }

        Column(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).align(Alignment.CenterStart)
        ) {
            Text(
                resonance.content,
                color = KH_INK,
                fontSize = 20.sp,
                lineHeight = 31.sp
            )
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFB74A58), fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
            }
        }

        OutlinedButton(
            onClick = {
                if (!busy) {
                    busy = true
                    scope.launch {
                        api.light(resonance.id)
                            .onSuccess(chat)
                            .onFailure { error = it.message ?: "点亮失败" }
                        busy = false
                    }
                }
            },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = KH_INK),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.align(Alignment.BottomEnd).padding(22.dp).testTag("light-button")
        ) {
            Text(if (resonance.status == "lit") "继续聊" else "☀  点亮", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Threads(api: KehuaProdApi, open: (NativePeer) -> Unit) {
    var threads by remember { mutableStateOf<List<NativeThread>>(emptyList()) }
    var friends by remember { mutableStateOf<List<NativePeer>>(emptyList()) }
    var requests by remember { mutableStateOf<List<NativeFriendRequest>>(emptyList()) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            api.threads().onSuccess { threads = it }.onFailure { error = it.message ?: "加载失败" }
            api.friends().onSuccess { friends = it }
            api.incomingRequests().onSuccess { requests = it }
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            load()
            delay(2500)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White).testTag("messages-screen")) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("消息", fontSize = 27.sp, fontWeight = FontWeight.Black, color = KH_INK)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.PersonAddAlt, null, tint = KH_INK, modifier = Modifier.size(25.dp))
            }
            IconButton(onClick = {}) {
                Icon(Icons.Outlined.ChatBubbleOutline, null, tint = KH_INK, modifier = Modifier.size(25.dp))
            }
        }

        if (friends.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                items(friends, key = { it.id }) { friend ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(58.dp).clickable { open(friend) }
                    ) {
                        Avatar(friend.nickname, 50.dp, ring = true)
                        Text(
                            friend.nickname,
                            color = Color(0xFF6D6D74),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
        }

        HorizontalDivider(color = KH_LINE)

        if (requests.isNotEmpty()) {
            requests.forEach { request ->
                Surface(color = Color(0xFFFFF3F6), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(horizontal = 18.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("收到新的好友申请", color = KH_INK, fontSize = 14.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = {
                            scope.launch {
                                api.respondRequest(request.id, true)
                                load()
                            }
                        }) {
                            Text("通过", color = KH_PINK)
                        }
                    }
                }
            }
        }

        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFB74A58), fontSize = 12.sp, modifier = Modifier.padding(18.dp))
        }

        if (threads.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Text(
                    "点亮共鸣后，对话会出现在这里。",
                    color = Color(0xFFBDBDC4),
                    fontSize = 15.sp,
                    modifier = Modifier.padding(top = 130.dp)
                )
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(threads, key = { it.id }) { thread ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                open(NativePeer(thread.id, thread.nickname, "", thread.isFriend))
                            }
                            .padding(horizontal = 18.dp, vertical = 13.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(thread.nickname, 54.dp, ring = false)
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Text(thread.nickname, color = KH_INK, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                thread.lastMessage.ifBlank { "点开继续聊天" },
                                color = KH_SUB,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(shortTime(thread.lastAt), color = Color(0xFFC0C0C6), fontSize = 11.sp)
                    }
                    HorizontalDivider(color = KH_LINE, modifier = Modifier.padding(start = 84.dp))
                }
            }
        }
    }
}

private fun shortTime(raw: String): String {
    if (raw.isBlank()) return ""
    return raw.substringAfter("T").take(5).ifBlank { raw.takeLast(5) }
}

@Composable
private fun Chat(api: KehuaProdApi, start: NativePeer, back: () -> Unit) {
    var peer by remember { mutableStateOf(start) }
    var messages by remember { mutableStateOf<List<NativeMessage>>(emptyList()) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var myId by remember { mutableStateOf("") }
    var menu by remember { mutableStateOf(false) }
    var report by remember { mutableStateOf(false) }
    var reason by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            api.chat(peer.id)
                .onSuccess {
                    peer = it.first
                    messages = it.second
                }
                .onFailure { error = it.message ?: "加载失败" }
            if (myId.isBlank()) api.me().onSuccess { myId = it.id }
        }
    }

    LaunchedEffect(peer.id) {
        while (true) {
            load()
            delay(1800)
        }
    }

    Column(Modifier.fillMaxSize().background(Color.White).testTag("chat-screen")) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = back) {
                Icon(Icons.Outlined.ArrowBackIosNew, null, tint = KH_INK, modifier = Modifier.size(21.dp))
            }
            Text(peer.nickname, color = KH_INK, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (!peer.isFriend) {
                TextButton(onClick = {
                    scope.launch {
                        api.friendRequest(peer.id).onFailure { error = it.message ?: "申请失败" }
                        load()
                    }
                }) {
                    Text("加好友", color = KH_PINK, fontSize = 13.sp)
                }
            }
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Outlined.MoreHoriz, null, tint = KH_INK)
            }
        }
        HorizontalDivider(color = KH_LINE)

        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFB74A58), fontSize = 12.sp, modifier = Modifier.padding(12.dp))
        }

        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 14.dp),
            contentPadding = PaddingValues(vertical = 14.dp)
        ) {
            items(messages, key = { it.id }) { message ->
                val mine = myId.isNotBlank() && message.fromId == myId
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = if (mine) Arrangement.End else Arrangement.Start
                ) {
                    Surface(
                        color = if (mine) KH_BLUE else Color(0xFFF0F0F4),
                        shape = RoundedCornerShape(18.dp)
                    ) {
                        Text(
                            message.body,
                            color = if (mine) Color.White else KH_INK,
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp).widthIn(max = 270.dp)
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().background(Color(0xFFF8F8FA)).padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = Color.White,
                modifier = Modifier.weight(1f)
            ) {
                BasicTextField(
                    input,
                    { input = it.take(1000) },
                    textStyle = TextStyle(color = KH_INK, fontSize = 15.sp),
                    singleLine = true,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 13.dp).testTag("chat-input"),
                    decorationBox = { inner ->
                        if (input.isEmpty()) Text("回复 " + peer.nickname + "：", color = Color(0xFFB8B8BF), fontSize = 14.sp)
                        inner()
                    }
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        input = ""
                        scope.launch {
                            api.sendMessage(peer.id, text)
                                .onSuccess { load() }
                                .onFailure { error = it.message ?: "发送失败" }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = KH_BLUE),
                shape = RoundedCornerShape(20.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("chat-send")
            ) {
                Text("发送")
            }
        }
    }

    if (menu) {
        AlertDialog(
            onDismissRequest = { menu = false },
            title = { Text("这段关系") },
            text = {
                Column {
                    TextButton(onClick = {
                        menu = false
                        report = true
                    }) { Text("举报", color = KH_INK) }
                    TextButton(onClick = {
                        scope.launch {
                            api.block(peer.id)
                                .onSuccess {
                                    menu = false
                                    back()
                                }
                                .onFailure {
                                    error = it.message ?: "屏蔽失败"
                                    menu = false
                                }
                        }
                    }) { Text("屏蔽这个人", color = Color(0xFFB14B57)) }
                }
            },
            confirmButton = { TextButton(onClick = { menu = false }) { Text("取消") } }
        )
    }

    if (report) {
        AlertDialog(
            onDismissRequest = { report = false },
            title = { Text("举报") },
            text = {
                OutlinedTextField(reason, { reason = it.take(500) }, label = { Text("原因") })
            },
            dismissButton = { TextButton(onClick = { report = false }) { Text("取消") } },
            confirmButton = {
                Button(
                    onClick = {
                        val text = reason.trim()
                        if (text.length >= 2) {
                            scope.launch {
                                api.report(peer.id, text)
                                    .onSuccess {
                                        report = false
                                        reason = ""
                                    }
                                    .onFailure { error = it.message ?: "举报失败" }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = KH_PINK)
                ) { Text("提交") }
            }
        )
    }
}

@Composable
private fun Me(api: KehuaProdApi, settings: () -> Unit) {
    var me by remember { mutableStateOf<NativeMe?>(null) }
    var posts by remember { mutableStateOf<List<NativePost>>(emptyList()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    fun load() {
        scope.launch {
            api.me().onSuccess { me = it }.onFailure { error = it.message ?: "加载失败" }
            api.home().onSuccess { posts = it.posts }
        }
    }

    LaunchedEffect(Unit) { load() }

    LazyColumn(
        Modifier.fillMaxSize().background(KH_BG).testTag("me-screen"),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item {
            Box(Modifier.fillMaxWidth().height(260.dp)) {
                Box(Modifier.fillMaxWidth().height(185.dp).background(KH_GRADIENT))
                IconButton(
                    onClick = settings,
                    modifier = Modifier.align(Alignment.TopEnd).padding(10.dp).testTag("settings-button")
                ) {
                    Icon(Icons.Outlined.Settings, null, tint = Color.White, modifier = Modifier.size(27.dp))
                }

                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Avatar(me?.nickname ?: "可", 82.dp, ring = true)
                    Spacer(Modifier.height(9.dp))
                    Text(
                        me?.nickname ?: "加载中…",
                        color = KH_INK,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!me?.bio.isNullOrBlank()) {
                        Text(me?.bio.orEmpty(), color = KH_SUB, fontSize = 13.sp, modifier = Modifier.padding(top = 3.dp))
                    }
                }
            }

            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFB74A58), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
            }

            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(vertical = 18.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                ProfileStat((me?.postCount ?: 0).toString(), "记录")
                Spacer(Modifier.width(64.dp))
                ProfileStat((me?.friendCount ?: 0).toString(), "好友")
            }

            Spacer(Modifier.height(10.dp))

            Row(
                Modifier.fillMaxWidth().background(Color.White).padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                ProfileTab("动态", selectedTab == 0) { selectedTab = 0 }
                ProfileTab("点亮", selectedTab == 1) { selectedTab = 1 }
            }
        }

        if (selectedTab == 0) {
            if (posts.isEmpty()) {
                item {
                    Text(
                        "还没有记录。",
                        color = Color(0xFFBDBDC4),
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 70.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            } else {
                items(posts, key = { it.id }) { post ->
                    Surface(
                        color = Color.White,
                        shape = RoundedCornerShape(18.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(post.content, color = KH_INK, fontSize = 15.sp, lineHeight = 22.sp)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (post.isPrivate) "仅自己可见" else post.lightCount.toString() + " 次点亮",
                                color = KH_SUB,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "点亮过的共鸣会留在这里。",
                    color = Color(0xFFBDBDC4),
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 70.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ProfileStat(number: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number, color = KH_INK, fontSize = 21.sp, fontWeight = FontWeight.Bold)
        Text(label, color = KH_SUB, fontSize = 13.sp)
    }
}

@Composable
private fun ProfileTab(label: String, selected: Boolean, click: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (selected) Color.White else Color.Transparent,
        modifier = Modifier.clickable(onClick = click)
    ) {
        Text(
            label,
            color = if (selected) KH_INK else KH_SUB,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 28.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun SettingsScreen(
    api: KehuaProdApi,
    back: () -> Unit,
    editProfile: () -> Unit,
    logout: () -> Unit
) {
    var error by remember { mutableStateOf("") }
    var recovery by remember { mutableStateOf<String?>(null) }
    var deleteConfirm by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize().background(KH_BG).testTag("settings-screen")) {
        Row(
            Modifier.fillMaxWidth().background(Color.White).padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = back) {
                Icon(Icons.Outlined.ArrowBackIosNew, null, tint = KH_INK, modifier = Modifier.size(20.dp))
            }
            Text("设置", color = KH_INK, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        SettingRow("编辑资料", "›", click = editProfile)
        SettingRow("我的恢复码", "重新生成") {
            scope.launch {
                api.rotateRecovery()
                    .onSuccess { recovery = it }
                    .onFailure { error = it.message ?: "生成失败" }
            }
        }

        Spacer(Modifier.height(12.dp))
        SettingRow("退出登录", "退出") {
            scope.launch {
                api.logoutRemote().onSuccess { logout() }.onFailure {
                    api.logout()
                    logout()
                }
            }
        }
        SettingRow("注销账号", "注销", destructive = true) { deleteConfirm = true }

        if (error.isNotBlank()) {
            Text(error, color = Color(0xFFB74A58), fontSize = 12.sp, modifier = Modifier.padding(20.dp))
        }
    }

    recovery?.let { code ->
        AlertDialog(
            onDismissRequest = { recovery = null },
            title = { Text("新的恢复码") },
            text = { Text(code + "\n\n旧恢复码已经失效，请保存这一份。") },
            confirmButton = {
                Button(onClick = { recovery = null }, colors = ButtonDefaults.buttonColors(containerColor = KH_PINK)) {
                    Text("我已保存")
                }
            }
        )
    }

    if (deleteConfirm) {
        AlertDialog(
            onDismissRequest = { deleteConfirm = false },
            title = { Text("永久注销账号？") },
            text = { Text("你的内容、聊天和好友关系将永久删除。") },
            dismissButton = { TextButton(onClick = { deleteConfirm = false }) { Text("取消") } },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            api.deleteAccount()
                                .onSuccess {
                                    deleteConfirm = false
                                    logout()
                                }
                                .onFailure {
                                    error = it.message ?: "注销失败"
                                    deleteConfirm = false
                                }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB14B57))
                ) { Text("确认注销") }
            }
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    trailing: String,
    destructive: Boolean = false,
    click: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().background(Color.White).clickable(onClick = click).padding(horizontal = 20.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            color = if (destructive) Color(0xFFD65B6C) else KH_INK,
            fontSize = 16.sp,
            modifier = Modifier.weight(1f)
        )
        Text(trailing, color = KH_SUB, fontSize = 14.sp)
    }
    HorizontalDivider(color = KH_LINE)
}

@Composable
private fun EditProfileScreen(api: KehuaProdApi, back: () -> Unit) {
    var nickname by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var saving by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        api.me()
            .onSuccess {
                nickname = it.nickname
                bio = it.bio
            }
            .onFailure { error = it.message ?: "加载失败" }
    }

    Column(Modifier.fillMaxSize().background(Color.White).testTag("edit-profile-screen")) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = back) {
                Icon(Icons.Outlined.ArrowBackIosNew, null, tint = KH_INK, modifier = Modifier.size(20.dp))
            }
            Text("编辑资料", color = KH_INK, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (!saving) {
                        saving = true
                        scope.launch {
                            api.updateProfile(nickname, bio)
                                .onSuccess { back() }
                                .onFailure { error = it.message ?: "保存失败" }
                            saving = false
                        }
                    }
                }
            ) {
                Text(if (saving) "保存中" else "保存", color = KH_PINK)
            }
        }
        HorizontalDivider(color = KH_LINE)

        Column(Modifier.padding(20.dp)) {
            OutlinedTextField(
                nickname,
                { nickname = it.take(24) },
                label = { Text("昵称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                bio,
                { bio = it.take(120) },
                label = { Text("简介") },
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
            if (error.isNotBlank()) {
                Text(error, color = Color(0xFFB74A58), fontSize = 12.sp, modifier = Modifier.padding(top = 12.dp))
            }
        }
    }
}

@Composable
private fun Avatar(name: String, size: androidx.compose.ui.unit.Dp, ring: Boolean) {
    Box(
        modifier = Modifier
            .size(if (ring) size + 4.dp else size)
            .clip(CircleShape)
            .background(if (ring) Color.White else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFE7DDF8),
            modifier = Modifier.size(size)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    name.take(1).ifBlank { "可" },
                    color = Color(0xFF685D72),
                    fontSize = (size.value * 0.34f).sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
