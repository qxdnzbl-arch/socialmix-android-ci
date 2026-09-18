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
                            Color(0xFFC9DAFF),
                            Color(0xFFE9E0F7),
                            Color(0xFFF3DDE8),
                            Color(0xFFF7F7F9)
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
                    .padding(top = 188.dp)
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
                        .height(if (resonance) 360.dp else 348.dp)
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
    val bg = if (dark) Color(0xFF101010) else Color.White
    val ink = if (dark) Color(0xFFF3F3F3) else KehuaInk
    val sub = if (dark) Color(0xFF777777) else Color(0xFFB6B6BC)
    val line = if (dark) Color(0xFF242424) else Color(0xFFF0F0F3)

    data class RowData(
        val name: String,
        val message: String,
        val quote: String,
        val time: String,
        val avatar: Color,
        val badge: Int = 0,
        val vip: Boolean = false
    )

    val rows = if (dark) {
        listOf(
            RowData("黄花远志", "[与你的共鸣]", "", "21:31", Color(0xFF8E735D), 2, true),
            RowData("Ayue", "[与你的共鸣]", "", "21:30", Color(0xFFB79C5E), 2, true),
            RowData("卡斯帕尔", "真没想到，过了将近三年回来，发现你还…", "", "01-25", Color(0xFF426181)),
            RowData("可我还是好困喔", "可我还是好困喔 点亮了你", "", "01-06", Color(0xFF6D9CC5), 0, true),
            RowData("皮不皮得过卡丘", "皮不皮得过卡丘 点亮了你", "", "2024-12-27", Color(0xFF91476D), 2, true),
            RowData("随梦", "[与你的共鸣]", "", "2024-12-26", Color(0xFF335F7A), 2, true),
            RowData("他是她的云天明", "不上班也死气沉沉的", "", "2024-12-26", Color(0xFF777777), 3, true)
        )
    } else {
        listOf(
            RowData("月亮", "嗯嗯嗯", "Zliiiiiii: 看到自己的朋友在那个领域闪闪发光，…", "15:08", Color(0xFFE9C7B8)),
            RowData("夜雨寄北", "夜雨寄北 点亮了你", "Zliiiiiii: 现在好像过得很好了，不像那个时候适…", "昨天", Color(0xFF94A8C7)),
            RowData("不知道叫什么", "很难相处", "Zliiiiiii: 不太适应人类社会", "2021-12-27", Color(0xFF9B8B78)),
            RowData("可话君", "Zliiiiiii，你发布了第 1 条动态！🌟 点亮你…", "Zliiiiiii: 不太适应人类社会", "2021-12-27", KehuaPink)
        )
    }

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
                    Modifier
                        .fillMaxWidth()
                        .height(76.dp)
                        .padding(horizontal = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "消息",
                        color = ink,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    if (dark) {
                        Text("♕", color = Color(0xFFE6E6E6), fontSize = 30.sp)
                        Spacer(Modifier.width(26.dp))
                        Text("♧", color = Color(0xFFE6E6E6), fontSize = 28.sp)
                    } else {
                        Icon(
                            Icons.Outlined.ChatBubbleOutline,
                            null,
                            tint = ink,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
                Divider(color = line, modifier = Modifier.padding(horizontal = 22.dp))
            }

            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChat() }
                        .padding(horizontal = 22.dp, vertical = 22.dp)
                ) {
                    Box(
                        Modifier
                            .size(68.dp)
                            .clip(CircleShape)
                            .background(if (dark) Color(0xFF171717) else Color.White)
                            .then(
                                Modifier.background(
                                    if (dark) Color(0xFF171717) else Color.White
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            Modifier
                                .size(62.dp)
                                .clip(CircleShape)
                                .background(if (dark) Color(0xFF1F1F1F) else Color(0xFFF8F8FA)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("~", color = KehuaPink, fontSize = 49.sp, fontWeight = FontWeight.Black)
                        }
                    }
                    Spacer(Modifier.height(7.dp))
                    Text(
                        "遇见",
                        color = if (dark) Color(0xFF8D8D8D) else Color(0xFF9D9DA4),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(start = 17.dp)
                    )
                }
                Divider(color = line, modifier = Modifier.padding(horizontal = 22.dp))
            }

            itemsIndexed(rows) { index, item ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onChat() }
                        .padding(horizontal = 22.dp, vertical = if (dark) 16.dp else 14.dp)
                        .testTag("message-row-" + index),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(
                        initial = if (item.name == "可话君") "~!" else item.name.take(1),
                        color = item.avatar,
                        size = if (dark) 58.dp else 56.dp,
                        modifier = Modifier.clickable { onProfile(index.coerceAtMost(2)) }
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                color = ink,
                                fontSize = if (dark) 16.sp else 15.sp,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1
                            )
                            if (item.vip) {
                                Spacer(Modifier.width(7.dp))
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xFF4D1B2D))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text("♕VIP", color = KehuaPink, fontSize = 10.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            item.message,
                            color = if (dark) Color(0xFF8E8E8E) else Color(0xFF77777E),
                            fontSize = 12.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (item.quote.isNotBlank()) {
                            Spacer(Modifier.height(5.dp))
                            Text(
                                "▌ " + item.quote,
                                color = if (dark) Color(0xFF4F4F4F) else Color(0xFFC8C8CD),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(item.time, color = if (dark) Color(0xFF4C4C4C) else Color(0xFFC9C9CE), fontSize = 10.5.sp)
                        if (item.badge > 0) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(KehuaPink),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(item.badge.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            if (!dark) {
                item {
                    Text(
                        "没有更多消息啦",
                        color = Color(0xFFC7C7CC),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(top = 30.dp),
                        textAlign = TextAlign.Center
                    )
                }
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
    val name = when (kind) {
        ProfileKind.Miraitowa -> "Miraitowa"
        ProfileKind.Lexie -> "Lexie"
        ProfileKind.Zli -> "Zliiiiiii"
    }
    val meta = when (kind) {
        ProfileKind.Miraitowa -> "西安市 · 双子座 · 536 条"
        ProfileKind.Lexie -> "重庆市 · 双子座 · 2188 条"
        ProfileKind.Zli -> "中国 · 白羊座 · 14 条"
    }
    val ip = when (kind) {
        ProfileKind.Miraitowa -> "IP 陕西"
        ProfileKind.Lexie -> "IP 重庆"
        ProfileKind.Zli -> "IP 广东 ⓘ"
    }
    val friends = when (kind) {
        ProfileKind.Miraitowa -> "186"
        ProfileKind.Lexie -> "241"
        ProfileKind.Zli -> "0"
    }
    val lights = when (kind) {
        ProfileKind.Miraitowa -> "5697"
        ProfileKind.Lexie -> "2.86万"
        ProfileKind.Zli -> "25"
    }
    val avatarColor = when (kind) {
        ProfileKind.Miraitowa -> Color(0xFF628EC4)
        ProfileKind.Lexie -> Color(0xFFB98B7D)
        ProfileKind.Zli -> Color(0xFF2D67BE)
    }

    Scaffold(
        containerColor = Color(0xFFF6F6F9),
        bottomBar = { MainBottomBar(selected = 2, dark = false, onSelect = onNav) }
    ) { pad ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(bottom = pad.calculateBottomPadding())
                .background(Color(0xFFF6F6F9))
                .testTag("profile-screen")
        ) {
            item {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(if (kind == ProfileKind.Zli) 500.dp else 405.dp)
                        .background(profileCoverBrush(kind))
                ) {
                    if (kind == ProfileKind.Lexie) {
                        Text(
                            "♥",
                            color = Color(0x4D151515),
                            fontSize = 132.sp,
                            modifier = Modifier.align(Alignment.CenterStart).padding(start = 8.dp, bottom = 62.dp)
                        )
                        Text(
                            "◯",
                            color = Color(0x3D3A2B27),
                            fontSize = 190.sp,
                            modifier = Modifier.align(Alignment.CenterEnd).padding(end = 10.dp, bottom = 40.dp)
                        )
                    } else if (kind == ProfileKind.Miraitowa) {
                        Text(
                            "☁",
                            color = Color(0xE6FFFFFF),
                            fontSize = 88.sp,
                            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 46.dp)
                        )
                        Text(
                            "☁",
                            color = Color(0xCFFFFFFF),
                            fontSize = 72.sp,
                            modifier = Modifier.align(Alignment.TopEnd).padding(end = 24.dp, top = 86.dp)
                        )
                        Text(
                            "▲  ▲   ▲▲",
                            color = Color(0xFF3E8044),
                            fontSize = 46.sp,
                            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 92.dp)
                        )
                    } else {
                        Text(
                            "◇",
                            color = Color(0x99FFFFFF),
                            fontSize = 220.sp,
                            modifier = Modifier.align(Alignment.Center).padding(bottom = 54.dp)
                        )
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(145.dp)
                            .align(Alignment.BottomCenter)
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, Color(0xFFF6F6F9))
                                )
                            )
                    )

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(onClick = {}) {
                            Icon(Icons.Outlined.Search, "搜索", tint = Color.White, modifier = Modifier.size(29.dp))
                        }
                        IconButton(onClick = onSettings, modifier = Modifier.testTag("settings-button")) {
                            Icon(Icons.Outlined.Settings, "设置", tint = Color.White, modifier = Modifier.size(29.dp))
                        }
                    }

                    Avatar(
                        initial = name.take(1),
                        color = avatarColor,
                        size = 96.dp,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp),
                        border = Color.White
                    )
                }

                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, color = KehuaInk, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        if (kind != ProfileKind.Zli) {
                            Spacer(Modifier.width(7.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFFFE7EF))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text("♕VIP", color = KehuaPink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(meta, color = Color(0xFF9E9EA5), fontSize = 13.sp)
                    Spacer(Modifier.height(3.dp))
                    Text(ip, color = Color(0xFFBDBDC3), fontSize = 11.sp)
                    Spacer(Modifier.height(24.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StatCard(
                            friends,
                            "好友",
                            "♣",
                            Color(0xFFEAF9E6),
                            Modifier.weight(1f).clickable { onFriends() }.testTag("profile-friends")
                        )
                        StatCard(
                            lights,
                            "点亮的动态",
                            "☀",
                            Color(0xFFFFF7D8),
                            Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    if (kind != ProfileKind.Zli) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(60.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(Color.White)
                                .padding(horizontal = 17.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("查看 VIP 特权", color = KehuaInk, fontSize = 16.sp, modifier = Modifier.weight(1f))
                            Text("♕", color = KehuaPink, fontSize = 21.sp)
                            Spacer(Modifier.width(5.dp))
                            Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFD0D0D5))
                        }
                        Spacer(Modifier.height(14.dp))

                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(22.dp))
                                .background(Color.White)
                                .padding(16.dp)
                        ) {
                            Text("相册", color = KehuaInk, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                repeat(4) { index ->
                                    AlbumTile(kind, index, Modifier.weight(1f))
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                    }

                    if (kind == ProfileKind.Zli) {
                        ZliPost("2023-04-12 19:45", "今遇到了明日香cos", onPost)
                        Spacer(Modifier.height(14.dp))
                        ZliPost("2023-04-12 19:45", "看到朋友穿旗袍，好美哈哈哈", onPost)
                    } else {
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color.White)
                                .clickable { onPost() }
                                .padding(16.dp)
                                .testTag("profile-post")
                        ) {
                            Text("2025-12-10 13:40", color = Color(0xFFBEBEC4), fontSize = 10.5.sp)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                if (kind == ProfileKind.Lexie)
                                    "看到可话停止运营的消息，非常不舍\n最开始使用这个软件也是在年末，想有一个新的开始，结果也是在年末结束\n比起精修和总是延后的朋友圈，这里承载了更多当下真实的感受和生活…更多"
                                else
                                    "很久没有认真记录生活了。\n希望这里还能一直保留这些真实又普通的瞬间。",
                                color = KehuaInk,
                                fontSize = 14.5.sp,
                                lineHeight = 22.sp
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("☀ 11     ◯ 8     ↗", color = Color(0xFF9F9FA6), fontSize = 12.sp)
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun ZliPost(time: String, text: String, onPost: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .clickable { onPost() }
            .padding(18.dp)
    ) {
        Text(time, color = Color(0xFFB9B9BF), fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Text(text, color = KehuaInk, fontSize = 15.sp)
        Spacer(Modifier.height(10.dp))
        Text("☀ 0     ◯ 0", color = Color(0xFF9F9FA6), fontSize = 12.sp)
    }
}

private fun profileCoverBrush(kind: ProfileKind): Brush = when (kind) {
    ProfileKind.Miraitowa -> Brush.verticalGradient(
        listOf(
            Color(0xFF69BDE7),
            Color(0xFF89CCE9),
            Color(0xFF74B760),
            Color(0xFFB7D58E),
            Color(0xFFF6F6F9)
        )
    )
    ProfileKind.Lexie -> Brush.verticalGradient(
        listOf(
            Color(0xFF9A846F),
            Color(0xFFB6A594),
            Color(0xFFD8CEC4),
            Color(0xFFF6F6F9)
        )
    )
    ProfileKind.Zli -> Brush.verticalGradient(
        listOf(
            Color(0xFFC09B7E),
            Color(0xFFD2B8A2),
            Color(0xFFE4DDD7),
            Color(0xFFF6F6F9)
        )
    )
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
    val palettes = when (kind) {
        ProfileKind.Miraitowa -> listOf(
            listOf(Color(0xFFEAF1EE), Color(0xFFAACB85)),
            listOf(Color(0xFFFFE4EA), Color(0xFFF0BFD2)),
            listOf(Color(0xFFD6E1C4), Color(0xFF7BA257)),
            listOf(Color(0xFFB3BEAE), Color(0xFF546E54))
        )
        ProfileKind.Lexie -> listOf(
            listOf(Color(0xFF7D3D31), Color(0xFFC79B5A)),
            listOf(Color(0xFFC7A260), Color(0xFFF1DBA8)),
            listOf(Color(0xFF8A633F), Color(0xFF4C3527)),
            listOf(Color(0xFF9A8F89), Color(0xFF4D4745))
        )
        ProfileKind.Zli -> listOf(
            listOf(Color(0xFFE0C3AA), Color(0xFF92755F)),
            listOf(Color(0xFF9FB7D0), Color(0xFF4D77AB)),
            listOf(Color(0xFFD3CCC3), Color(0xFF8C8175)),
            listOf(Color(0xFFA8B29B), Color(0xFF526A55))
        )
    }
    Box(
        modifier
            .height(78.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(palettes[index])),
        contentAlignment = Alignment.Center
    ) {
        if (index < 3) {
            Text(
                when (index) {
                    0 -> "▦"
                    1 -> "◉"
                    else -> "◇"
                },
                color = Color(0xCCFFFFFF),
                fontSize = 22.sp
            )
        } else {
            Text(
                "+" + when (kind) {
                    ProfileKind.Miraitowa -> "1054"
                    ProfileKind.Lexie -> "4615"
                    ProfileKind.Zli -> "25"
                },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
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
    val selectedColor = if (dark) Color.White else Color(0xFF26262A)
    val unselected = if (dark) Color(0xFF6A6A6A) else Color(0xFFB9B9BE)
    val icons = listOf(Icons.Outlined.Home, Icons.Outlined.ChatBubbleOutline, Icons.Outlined.PersonOutline)

    Column(
        Modifier
            .fillMaxWidth()
            .background(bg)
            .navigationBarsPadding()
    ) {
        Divider(color = if (dark) Color(0xFF202020) else Color(0xFFF0F0F2), thickness = 0.5.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            icons.forEachIndexed { index, icon ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable { onSelect(index) }
                        .testTag("nav-" + index),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = if (selected == index) selectedColor else unselected,
                        modifier = Modifier.size(if (index == 2) 31.dp else 29.dp)
                    )
                }
            }
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
