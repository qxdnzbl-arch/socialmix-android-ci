package com.suisuinian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBackIosNew
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val KehuaPink = Color(0xFFFF315F)
private val KehuaInk = Color(0xFF242428)
private val KehuaSub = Color(0xFF93939A)
private val KehuaLine = Color(0xFFE8E8ED)
private val KehuaPage = Color(0xFFF5F5F8)
private val KehuaDark = Color(0xFF101010)
private val KehuaDarkCard = Color(0xFF181818)
private val KehuaDarkLine = Color(0xFF2A2A2A)

private enum class KehuaScreen {
    Login, Home, HomeResonance, Publish, Messages, Chat,
    ProfileMiraitowa, ProfileLexie, ProfileZli, PostDetail, Settings, Friends
}

private enum class ProfileKind { Miraitowa, Lexie, Zli }

class KehuaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize(), color = KehuaPage) {
                    KehuaApp()
                }
            }
        }
    }
}

@Composable
private fun KehuaApp() {
    var screen by remember { mutableStateOf(KehuaScreen.Login) }
    var darkMode by remember { mutableStateOf(false) }
    var lit by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val chatMessages = remember {
        mutableStateListOf(
            "你好呀",
            "我刚刚看到你那句话，突然很有共鸣。",
            "这种感觉好像有人真的听见了。"
        )
    }

    fun mainTab(index: Int) {
        screen = when (index) {
            0 -> KehuaScreen.Home
            1 -> KehuaScreen.Messages
            else -> KehuaScreen.ProfileLexie
        }
    }

    when (screen) {
        KehuaScreen.Login -> LoginScreen { screen = KehuaScreen.Home }
        KehuaScreen.Home -> HomeScreen(
            resonance = false,
            onCompose = { screen = KehuaScreen.Publish },
            onOpenResonance = { screen = KehuaScreen.PostDetail },
            onNav = ::mainTab
        )
        KehuaScreen.HomeResonance -> HomeScreen(
            resonance = true,
            onCompose = { screen = KehuaScreen.Publish },
            onOpenResonance = { screen = KehuaScreen.PostDetail },
            onNav = ::mainTab
        )
        KehuaScreen.Publish -> PublishScreen(
            draft = draft,
            onDraft = { draft = it },
            onBack = { screen = KehuaScreen.Home },
            onPublish = { screen = KehuaScreen.HomeResonance }
        )
        KehuaScreen.Messages -> MessagesScreen(
            dark = darkMode,
            onNav = ::mainTab,
            onChat = { screen = KehuaScreen.Chat },
            onProfile = {
                screen = when (it) {
                    0 -> KehuaScreen.ProfileMiraitowa
                    1 -> KehuaScreen.ProfileLexie
                    else -> KehuaScreen.ProfileZli
                }
            },
            onFriends = { screen = KehuaScreen.Friends }
        )
        KehuaScreen.Chat -> ChatScreen(
            dark = true,
            messages = chatMessages,
            onBack = { screen = KehuaScreen.Messages }
        )
        KehuaScreen.ProfileMiraitowa -> ProfileScreen(
            ProfileKind.Miraitowa,
            onNav = ::mainTab,
            onSettings = { screen = KehuaScreen.Settings },
            onFriends = { screen = KehuaScreen.Friends },
            onPost = { screen = KehuaScreen.PostDetail }
        )
        KehuaScreen.ProfileLexie -> ProfileScreen(
            ProfileKind.Lexie,
            onNav = ::mainTab,
            onSettings = { screen = KehuaScreen.Settings },
            onFriends = { screen = KehuaScreen.Friends },
            onPost = { screen = KehuaScreen.PostDetail }
        )
        KehuaScreen.ProfileZli -> ProfileScreen(
            ProfileKind.Zli,
            onNav = ::mainTab,
            onSettings = { screen = KehuaScreen.Settings },
            onFriends = { screen = KehuaScreen.Friends },
            onPost = { screen = KehuaScreen.PostDetail }
        )
        KehuaScreen.PostDetail -> PostDetailScreen(
            lit = lit,
            onLight = { lit = true },
            onBack = { screen = KehuaScreen.ProfileLexie }
        )
        KehuaScreen.Settings -> SettingsScreen(
            darkMode = darkMode,
            onDark = { darkMode = it },
            onBack = { screen = KehuaScreen.ProfileLexie }
        )
        KehuaScreen.Friends -> FriendsScreen(onBack = { screen = KehuaScreen.Messages })
    }
}

@Composable
private fun LoginScreen(onLogin: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .testTag("login-screen")
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 204.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "可话~!",
                color = KehuaPink,
                fontSize = 49.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = (-1).sp
            )
            Spacer(Modifier.height(7.dp))
            Text(
                "说  你  想  说  的  话",
                color = Color(0xFFB6B6BC),
                fontSize = 14.sp,
                letterSpacing = 4.sp
            )
        }

        Button(
            onClick = onLogin,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 128.dp)
                .fillMaxWidth(.58f)
                .height(54.dp)
                .testTag("login-button"),
            colors = ButtonDefaults.buttonColors(containerColor = KehuaPink),
            shape = RoundedCornerShape(28.dp)
        ) {
            Text("手机号登录", fontSize = 17.sp, fontWeight = FontWeight.Normal)
        }
    }
}

@Composable
private fun HomeScreen(
    resonance: Boolean,
    onCompose: () -> Unit,
    onOpenResonance: () -> Unit,
    onNav: (Int) -> Unit
) {
    Scaffold(
        containerColor = Color.Transparent,
        bottomBar = { MainBottomBar(selected = 0, dark = false, onSelect = onNav) }
    ) { pad ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(bottom = pad.calculateBottomPadding())
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFFBFD6FF),
                            Color(0xFFE6D9F7),
                            Color(0xFFF3D8E3),
                            Color(0xFFF5F5F8)
                        )
                    )
                )
                .testTag(if (resonance) "home-resonance-screen" else "home-screen")
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 22.dp)
                    .padding(top = 205.dp)
            ) {
                Text("10月01日 星期六", color = Color(0xFF4A4A50), fontSize = 14.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "此刻，说你想说的话～",
                    color = KehuaInk,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(21.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (resonance) 330.dp else 302.dp)
                        .clip(RoundedCornerShape(25.dp))
                        .background(Color.White)
                        .clickable { onCompose() }
                        .testTag("home-input-card")
                ) {
                    Text(
                        "我想说...",
                        color = Color(0xFFD4D4DA),
                        fontSize = 20.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                    if (resonance) {
                        Column(
                            Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            ResonanceTicket("9", "有人和你说到了同一个地方", onOpenResonance)
                            Spacer(Modifier.height(9.dp))
                            ResonanceTicket("10", "新的共鸣已经到达", onOpenResonance)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResonanceTicket(number: String, text: String, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xFFFFF2F6))
            .clickable { onOpen() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(KehuaPink),
            contentAlignment = Alignment.Center
        ) {
            Text(number, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = KehuaInk, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFC6C6CC))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PublishScreen(
    draft: String,
    onDraft: (String) -> Unit,
    onBack: () -> Unit,
    onPublish: () -> Unit
) {
    var visibility by remember { mutableStateOf("他人可见") }
    var showSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(containerColor = Color(0xFFF5F5F8)) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .statusBarsPadding()
                .testTag("publish-screen")
        ) {
            Row(
                Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.KeyboardArrowDown, "返回", tint = KehuaInk)
                }
                Text("说点什么", color = KehuaInk, fontSize = 17.sp, modifier = Modifier.weight(1f))
                TextButton(
                    onClick = onPublish,
                    enabled = draft.isNotBlank(),
                    modifier = Modifier.testTag("publish-button")
                ) {
                    Text(
                        "发布",
                        color = if (draft.isBlank()) Color(0xFFC7C7CD) else KehuaPink,
                        fontSize = 15.sp
                    )
                }
            }
            Divider(color = KehuaLine)
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 20.dp, vertical = 18.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 1200) onDraft(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(250.dp)
                        .testTag("publish-input"),
                    textStyle = TextStyle(color = KehuaInk, fontSize = 18.sp, lineHeight = 27.sp),
                    decorationBox = { inner ->
                        if (draft.isBlank()) {
                            Text("这一刻你在想什么？", color = Color(0xFFC5C5CB), fontSize = 18.sp)
                        } else {
                            inner()
                        }
                    }
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("图片", color = KehuaSub, fontSize = 13.sp)
                    Spacer(Modifier.width(22.dp))
                    Text("相机", color = KehuaSub, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Text(draft.length.toString() + "/1200", color = Color(0xFFBDBDC3), fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White)
                    .clickable { showSheet = true }
                    .padding(horizontal = 20.dp)
                    .testTag("visibility-button"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("谁可以看", color = KehuaInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                Text(visibility, color = KehuaSub, fontSize = 13.sp)
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFC9C9CF))
            }
        }
    }

    if (showSheet) {
        ModalBottomSheet(
            onDismissRequest = { showSheet = false },
            sheetState = sheetState,
            containerColor = Color.White
        ) {
            Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
                Text(
                    "发布给谁看",
                    color = KehuaInk,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
                VisibilityChoice("他人可见", visibility == "他人可见") {
                    visibility = "他人可见"
                    showSheet = false
                }
                VisibilityChoice("仅自己可见", visibility == "仅自己可见") {
                    visibility = "仅自己可见"
                    showSheet = false
                }
                Divider(color = KehuaLine)
                Text(
                    "取消",
                    color = KehuaInk,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showSheet = false }
                        .padding(18.dp)
                )
            }
        }
    }
}

@Composable
private fun VisibilityChoice(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, color = KehuaInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
        if (selected) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(KehuaPink))
        }
    }
}

@Composable
private fun MessagesScreen(
    dark: Boolean,
    onNav: (Int) -> Unit,
    onChat: () -> Unit,
    onProfile: (Int) -> Unit,
    onFriends: () -> Unit
) {
    val bg = if (dark) KehuaDark else KehuaPage
    val card = if (dark) KehuaDarkCard else Color.White
    val ink = if (dark) Color(0xFFF2F2F2) else KehuaInk
    val sub = if (dark) Color(0xFF777777) else KehuaSub
    val line = if (dark) KehuaDarkLine else KehuaLine
    val people = listOf(
        Triple("乘风778", "刚刚", "我也有过这种感觉"),
        Triple("Miraitowa", "昨天", "有时候慢一点也没关系"),
        Triple("Zliiiiiiii", "周一", "晚安")
    )

    Scaffold(
        containerColor = bg,
        bottomBar = { MainBottomBar(selected = 1, dark = dark, onSelect = onNav) }
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(bottom = pad.calculateBottomPadding())
                .background(bg)
                .statusBarsPadding()
                .testTag(if (dark) "messages-dark-screen" else "messages-screen")
        ) {
            item {
                Row(
                    Modifier.fillMaxWidth().height(58.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("消息", color = ink, fontSize = 25.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Icon(Icons.Outlined.Search, null, tint = sub, modifier = Modifier.size(24.dp))
                }
            }
            item {
                MessageShortcut("我的好友", "看看已经认识的人", dark, onFriends)
                Divider(color = line)
                MessageShortcut("点亮我的", "收到的点亮会出现在这里", dark) {}
                Spacer(Modifier.height(10.dp))
            }
            itemsIndexed(people) { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(card)
                        .clickable { onChat() }
                        .padding(horizontal = 18.dp, vertical = 13.dp)
                        .testTag("message-row-" + index),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        initial = item.first.take(1),
                        color = listOf(Color(0xFF8098BF), Color(0xFF72A6A0), Color(0xFFB18F7F))[index],
                        size = 48.dp,
                        modifier = Modifier.clickable { onProfile(index) }
                    )
                    Spacer(Modifier.width(13.dp))
                    Column(Modifier.weight(1f)) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(item.first, color = ink, fontSize = 15.sp, modifier = Modifier.weight(1f))
                            Text(item.second, color = sub, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(item.third, color = sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                Divider(color = line, modifier = Modifier.padding(start = 79.dp))
            }
        }
    }
}

@Composable
private fun MessageShortcut(title: String, subtitle: String, dark: Boolean, onClick: () -> Unit) {
    val ink = if (dark) Color(0xFFF2F2F2) else KehuaInk
    val sub = if (dark) Color(0xFF777777) else KehuaSub
    val card = if (dark) KehuaDarkCard else Color.White
    Row(
        Modifier
            .fillMaxWidth()
            .height(70.dp)
            .background(card)
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(if (dark) Color(0xFF242424) else Color(0xFFFFEEF3)),
            contentAlignment = Alignment.Center
        ) {
            Text(if (title == "我的好友") "友" else "☀", color = KehuaPink, fontSize = 16.sp)
        }
        Spacer(Modifier.width(13.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = ink, fontSize = 15.sp)
            Spacer(Modifier.height(3.dp))
            Text(subtitle, color = sub, fontSize = 11.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = sub)
    }
}

@Composable
private fun ChatScreen(
    dark: Boolean,
    messages: MutableList<String>,
    onBack: () -> Unit
) {
    val bg = if (dark) KehuaDark else KehuaPage
    val ink = if (dark) Color(0xFFF2F2F2) else KehuaInk
    val sub = if (dark) Color(0xFF777777) else KehuaSub
    var input by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
            .imePadding()
            .testTag("chat-screen")
    ) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = ink)
            }
            Text("乘风778", color = ink, fontSize = 16.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Icon(Icons.Outlined.MoreHoriz, null, tint = sub, modifier = Modifier.size(27.dp))
            Spacer(Modifier.width(8.dp))
        }
        Divider(color = if (dark) KehuaDarkLine else KehuaLine)
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 15.dp),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            item { Spacer(Modifier.height(14.dp)) }
            itemsIndexed(messages) { index, text ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = if (index % 2 == 0) Arrangement.End else Arrangement.Start
                ) {
                    Box(
                        Modifier
                            .widthIn(max = 285.dp)
                            .clip(RoundedCornerShape(17.dp))
                            .background(if (index % 2 == 0) KehuaPink else if (dark) Color(0xFF242424) else Color.White)
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(text, color = if (index % 2 == 0) Color.White else ink, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (dark) Color(0xFF171717) else Color.White)
                .padding(10.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = { input = it.take(300) },
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (dark) Color(0xFF252525) else KehuaPage)
                    .padding(horizontal = 15.dp, vertical = 12.dp)
                    .testTag("chat-input"),
                textStyle = TextStyle(color = ink, fontSize = 14.sp),
                decorationBox = { inner ->
                    if (input.isBlank()) Text("说点什么", color = sub, fontSize = 14.sp) else inner()
                }
            )
            Spacer(Modifier.width(7.dp))
            IconButton(
                onClick = {
                    if (input.isNotBlank()) {
                        messages.add(input.trim())
                        input = ""
                    }
                },
                modifier = Modifier.testTag("chat-send")
            ) {
                Icon(Icons.Outlined.Send, "发送", tint = if (input.isBlank()) sub else KehuaPink)
            }
        }
    }
}

@Composable
private fun ProfileScreen(
    kind: ProfileKind,
    onNav: (Int) -> Unit,
    onSettings: () -> Unit,
    onFriends: () -> Unit,
    onPost: () -> Unit
) {
    val data = when (kind) {
        ProfileKind.Miraitowa -> listOf("Miraitowa", "女 · 00后", "IP属地：广东", "186", "5697", "M")
        ProfileKind.Lexie -> listOf("Lexie", "女 · 00后", "IP属地：浙江", "78", "2240", "L")
        ProfileKind.Zli -> listOf("Zliiiiiiii", "女 · 00后", "IP属地：广东", "0", "25", "Z")
    }

    Scaffold(
        containerColor = KehuaPage,
        bottomBar = { MainBottomBar(selected = 2, dark = false, onSelect = onNav) }
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(bottom = pad.calculateBottomPadding())
                .background(KehuaPage)
                .testTag("profile-screen")
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(245.dp)
                        .background(profileCoverBrush(kind))
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = onSettings, modifier = Modifier.testTag("settings-button")) {
                            Icon(Icons.Outlined.Settings, "设置", tint = Color.White)
                        }
                    }
                }
            }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(KehuaPage)
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Avatar(
                            initial = data[5],
                            color = when (kind) {
                                ProfileKind.Miraitowa -> Color(0xFF81A8C3)
                                ProfileKind.Lexie -> Color(0xFFB9897D)
                                ProfileKind.Zli -> Color(0xFFA98F7D)
                            },
                            size = 76.dp,
                            border = Color.White
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(data[0], color = KehuaInk, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.width(7.dp))
                                Box(
                                    Modifier.clip(RoundedCornerShape(8.dp)).background(Color(0xFFFFE9EF)).padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Text("VIP", color = KehuaPink, fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(data[1] + "  ·  " + data[2], color = KehuaSub, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        when (kind) {
                            ProfileKind.Miraitowa -> "愿所有真诚都被温柔接住。"
                            ProfileKind.Lexie -> "今天也想好好生活。"
                            ProfileKind.Zli -> "累了就休息，不需要解释。"
                        },
                        color = Color(0xFF696970),
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatCard(data[3], "好友", "友", Color(0xFFEFF8EF), Modifier.weight(1f).clickable { onFriends() })
                        StatCard(data[4], "点亮", "☀", Color(0xFFFFF7DA), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("查看 VIP 特权", color = KehuaInk, fontSize = 15.sp, modifier = Modifier.weight(1f))
                        Text("♕", color = KehuaPink, fontSize = 20.sp)
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFCECED4))
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.White)
                            .padding(16.dp)
                    ) {
                        Text("相册", color = KehuaInk, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(4) { index -> AlbumTile(kind, index, Modifier.weight(1f)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White)
                            .clickable { onPost() }
                            .padding(16.dp)
                            .testTag("profile-post")
                    ) {
                        Text(
                            if (kind == ProfileKind.Zli) "学会累了就自己休息" else "看到可话停止运营的消息，非常不舍",
                            color = KehuaInk,
                            fontSize = 14.sp,
                            lineHeight = 21.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Text("2025-12-10 13:40", color = Color(0xFFBDBDC3), fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(26.dp))
                }
            }
        }
    }
}

private fun profileCoverBrush(kind: ProfileKind): Brush = when (kind) {
    ProfileKind.Miraitowa -> Brush.verticalGradient(listOf(Color(0xFF76BCE0), Color(0xFFA5D38C), Color(0xFFEFF2E7)))
    ProfileKind.Lexie -> Brush.verticalGradient(listOf(Color(0xFF8D6D5B), Color(0xFFC8B6A7), Color(0xFFE6DDD6)))
    ProfileKind.Zli -> Brush.verticalGradient(listOf(Color(0xFFBBA591), Color(0xFFD7CEC5), Color(0xFFEDE8E3)))
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    icon: String,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .height(78.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(horizontal = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = KehuaInk, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Text(if (label == "好友") "位" else "条", color = KehuaInk, fontSize = 11.sp, modifier = Modifier.padding(bottom = 3.dp))
            }
            Text(label, color = KehuaSub, fontSize = 12.sp)
        }
        Box(Modifier.size(44.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            Text(icon, color = if (icon == "☀") Color(0xFFEBCB36) else Color(0xFF7FC77E), fontSize = 20.sp)
        }
    }
}

@Composable
private fun AlbumTile(kind: ProfileKind, index: Int, modifier: Modifier) {
    val palette = when (kind) {
        ProfileKind.Miraitowa -> listOf(Color(0xFFB8D8E8), Color(0xFFE6C7D3), Color(0xFFACC690), Color(0xFF758B90))
        ProfileKind.Lexie -> listOf(Color(0xFF5B332D), Color(0xFFBE995F), Color(0xFF6C4A32), Color(0xFF7D7470))
        ProfileKind.Zli -> listOf(Color(0xFFD1B59D), Color(0xFF7390B4), Color(0xFFB8AFA4), Color(0xFF6D806E))
    }
    Box(
        modifier
            .height(74.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette[index]),
        contentAlignment = Alignment.Center
    ) {
        if (index == 3) {
            Text("+1054", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun PostDetailScreen(lit: Boolean, onLight: () -> Unit, onBack: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .testTag("post-detail-screen")
    ) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) {
            Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = KehuaInk)
        }
        Text(
            "记第一次剧本杀 远山希子\n可真是太难了！！",
            color = KehuaInk,
            fontSize = 19.sp,
            lineHeight = 30.sp,
            modifier = Modifier.padding(start = 72.dp, top = 106.dp, end = 28.dp)
        )
        Button(
            onClick = onLight,
            modifier = Modifier.align(Alignment.Center).testTag("light-button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFD2D2D7)),
            shape = RoundedCornerShape(22.dp)
        ) {
            Text(if (lit) "☀ 已点亮" else "☀ 点亮", color = if (lit) KehuaPink else KehuaInk, fontSize = 14.sp)
        }
    }
}

@Composable
private fun SettingsScreen(darkMode: Boolean, onDark: (Boolean) -> Unit, onBack: () -> Unit) {
    val groups = listOf(
        listOf("个人信息", "账号与安全", "表情整理"),
        listOf("深色模式", "新消息通知", "通用", "隐私"),
        listOf("清除缓存", "检测更新"),
        listOf("给可话打赏，支持我们❤️", "给可话反馈", "把可话推荐给朋友", "给可话好评，鼓励一下")
    )
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Color.White)
            .statusBarsPadding()
            .testTag("settings-screen")
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = KehuaInk)
                }
                Text("设置", color = KehuaInk, fontSize = 18.sp)
            }
        }
        groups.forEachIndexed { groupIndex, group ->
            itemsIndexed(group) { _, text ->
                Row(
                    Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text, color = KehuaInk, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (text == "深色模式") {
                        Text(if (darkMode) "已开启" else "跟随系统", color = Color(0xFFB7B7BD), fontSize = 11.sp)
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = darkMode,
                            onCheckedChange = onDark,
                            modifier = Modifier.testTag("dark-switch"),
                            colors = SwitchDefaults.colors(checkedTrackColor = KehuaPink, checkedThumbColor = Color.White)
                        )
                    } else {
                        if (text == "清除缓存") {
                            Text("66.61 MB", color = Color(0xFFB7B7BD), fontSize = 11.sp)
                        }
                        Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFCECED4), modifier = Modifier.size(20.dp))
                    }
                }
            }
            if (groupIndex != groups.lastIndex) {
                item { Divider(color = Color(0xFFF3F3F6), thickness = 10.dp) }
            }
        }
    }
}

@Composable
private fun FriendsScreen(onBack: () -> Unit) {
    val names = listOf(
        "找到我的你", "YoYo黑寡", "Echo", "Gucci", "不重要小姐姐", "么么茶",
        "或许你会发光", "焕紫", "你猜猜", "白桃汽水贩卖机", "你愚蠢的欧豆豆", "JulieW", "Kashiwa"
    )
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(KehuaDark)
            .statusBarsPadding()
            .testTag("friends-screen")
    ) {
        item {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = Color.White)
                }
                Text("我的好友", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
        }
        itemsIndexed(names) { index, name ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Avatar(name.take(1), Color.hsv((index * 29f) % 360f, .35f, .72f), 38.dp)
                Spacer(Modifier.width(12.dp))
                Text(name, color = Color(0xFFE8E8E8), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("相识 " + (1924 + index * 7).toString() + " 天", color = Color(0xFF747474), fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun MainBottomBar(selected: Int, dark: Boolean, onSelect: (Int) -> Unit) {
    val bg = if (dark) KehuaDark else Color.White
    val selectedColor = if (dark) Color.White else KehuaInk
    val unselected = if (dark) Color(0xFF6A6A6A) else Color(0xFFB8B8BE)
    NavigationBar(containerColor = bg, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
        val icons = listOf(Icons.Outlined.Home, Icons.Outlined.ChatBubbleOutline, Icons.Outlined.PersonOutline)
        icons.forEachIndexed { index, icon ->
            NavigationBarItem(
                selected = selected == index,
                onClick = { onSelect(index) },
                modifier = Modifier.testTag("nav-" + index),
                icon = {
                    Icon(
                        icon,
                        null,
                        tint = if (selected == index) selectedColor else unselected,
                        modifier = Modifier.size(28.dp)
                    )
                },
                label = null,
                alwaysShowLabel = false
            )
        }
    }
}

@Composable
private fun Avatar(
    initial: String,
    color: Color,
    size: Dp,
    modifier: Modifier = Modifier,
    border: Color? = null
) {
    Box(
        modifier
            .then(Modifier.size(size))
            .clip(CircleShape)
            .background(if (border != null) border else Color.Transparent)
            .padding(if (border != null) 5.dp else 0.dp)
            .clip(CircleShape)
            .background(color),
        contentAlignment = Alignment.Center
    ) {
        Text(
            initial,
            color = Color.White,
            fontSize = (size.value * .36f).sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
