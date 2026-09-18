package com.suisuinian.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Pink = Color(0xFFFF2C62)
private val Ink = Color(0xFF202126)
private val Sub = Color(0xFF9B9BA3)
private val Line = Color(0xFFECECF1)
private val Page = Color(0xFFF7F7FA)
private val Dark = Color(0xFF101010)
private val DarkCard = Color(0xFF1A1A1A)

private enum class Screen { LOGIN, HOME, PUBLISH, MESSAGES, CHAT, PROFILE, SETTINGS, FRIENDS, POST }

class KehuaActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MaterialTheme { KehuaApp() } }
    }
}

@Composable
private fun KehuaApp() {
    var screen by remember { mutableStateOf(Screen.LOGIN) }
    var resonance by remember { mutableStateOf(false) }
    var dark by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var lit by remember { mutableStateOf(false) }
    val chat = remember { mutableStateListOf("点亮了你", "能看见自己慢慢变好，已经很不容易了。") }

    fun nav(i: Int) {
        screen = when (i) { 0 -> Screen.HOME; 1 -> Screen.MESSAGES; else -> Screen.PROFILE }
    }

    when (screen) {
        Screen.LOGIN -> Login { screen = Screen.HOME }
        Screen.HOME -> Home(resonance, { screen = Screen.PUBLISH }, { screen = Screen.POST }, ::nav)
        Screen.PUBLISH -> Publish(draft, { draft = it }, { screen = Screen.HOME }) {
            resonance = true
            screen = Screen.HOME
        }
        Screen.MESSAGES -> Messages(dark, ::nav, { screen = Screen.CHAT }, { screen = Screen.FRIENDS }) { dark = !dark }
        Screen.CHAT -> Chat(chat) { screen = Screen.MESSAGES }
        Screen.PROFILE -> Profile(::nav, { screen = Screen.SETTINGS }, { screen = Screen.FRIENDS }, { screen = Screen.POST })
        Screen.SETTINGS -> Settings(dark, { dark = it }) { screen = Screen.PROFILE }
        Screen.FRIENDS -> Friends { screen = Screen.PROFILE }
        Screen.POST -> Post(lit, { lit = true }) { screen = Screen.PROFILE }
    }
}

@Composable
private fun Login(onLogin: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White)) {
        Column(
            Modifier.fillMaxWidth().align(Alignment.TopCenter).statusBarsPadding().padding(top = 210.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("可话~!", color = Pink, fontSize = 50.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(6.dp))
            Text("说  你  想  说  的  话", color = Color(0xFFB8B8BE), fontSize = 15.sp)
        }
        Button(
            onClick = onLogin,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 125.dp).fillMaxWidth(.56f).height(54.dp).testTag("login-button"),
            colors = ButtonDefaults.buttonColors(containerColor = Pink),
            shape = RoundedCornerShape(28.dp)
        ) { Text("手机号登录", fontSize = 18.sp) }
    }
}

@Composable
private fun Home(resonance: Boolean, onCompose: () -> Unit, onOpen: () -> Unit, onNav: (Int) -> Unit) {
    Scaffold(containerColor = Color.Transparent, bottomBar = { BottomBar(0, false, onNav) }) { pad ->
        Box(
            Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).background(
                Brush.verticalGradient(listOf(Color(0xFFC7DCFF), Color(0xFFF1D7E4), Page, Page), endY = 1200f)
            )
        ) {
            Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 22.dp).padding(top = 195.dp)) {
                Text("10月01日 星期六", color = Color(0xFF505158), fontSize = 15.sp)
                Spacer(Modifier.height(14.dp))
                Text("此刻，说你想说的话～", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier.fillMaxWidth().height(305.dp).clip(RoundedCornerShape(25.dp)).background(Color.White)
                        .clickable { onCompose() }.testTag("home-input")
                ) {
                    Text("我想说...", color = Color(0xFFD8D8DE), fontSize = 21.sp, modifier = Modifier.padding(24.dp))
                    if (resonance) {
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp)) {
                            Resonance("9", "有人和你说到了同一个地方", onOpen)
                            Spacer(Modifier.height(8.dp))
                            Resonance("10", "新的共鸣已经到达", onOpen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Resonance(n: String, text: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(15.dp)).background(Color(0xFFFFF3F7))
            .clickable { onClick() }.padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(38.dp).clip(CircleShape).background(Pink), contentAlignment = Alignment.Center) {
            Text(n, color = Color.White, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = Ink, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Icon(Icons.Outlined.ChevronRight, null, tint = Sub)
    }
}

@Composable
private fun Publish(draft: String, onDraft: (String) -> Unit, onBack: () -> Unit, onPublish: () -> Unit) {
    var visibility by remember { mutableStateOf("他人可见") }
    var sheet by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize().background(Color(0xFFF6F6F8))) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.KeyboardArrowDown, "返回", tint = Ink) }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { if (draft.isNotBlank()) onPublish() }, modifier = Modifier.testTag("publish-button")) {
                    Text("发布", color = if (draft.isBlank()) Color(0xFFC9C9CE) else Pink)
                }
            }
            Column(
                Modifier.padding(horizontal = 18.dp).fillMaxWidth().height(365.dp)
                    .clip(RoundedCornerShape(18.dp)).background(Color.White).padding(16.dp)
            ) {
                BasicTextField(
                    value = draft,
                    onValueChange = { if (it.length <= 1200) onDraft(it) },
                    textStyle = TextStyle(color = Ink, fontSize = 17.sp, lineHeight = 25.sp),
                    modifier = Modifier.fillMaxWidth().weight(1f).testTag("publish-input"),
                    decorationBox = { inner ->
                        if (draft.isBlank()) Text("我想说...", color = Color(0xFFD8D8DE), fontSize = 17.sp) else inner()
                    }
                )
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xFFF3F3F5)), contentAlignment = Alignment.Center) {
                    Text("＋", color = Color(0xFFC2C2C7), fontSize = 31.sp, fontWeight = FontWeight.Light)
                }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.PhotoCamera, null, tint = Sub, modifier = Modifier.padding(10.dp))
                Icon(Icons.Outlined.Image, null, tint = Sub, modifier = Modifier.padding(10.dp))
                Spacer(Modifier.weight(1f))
                Text(visibility, color = Sub, fontSize = 12.sp, modifier = Modifier.clickable { sheet = true }.padding(12.dp).testTag("visibility-button"))
            }
        }
        if (sheet) {
            Box(Modifier.fillMaxSize().background(Color(0x33000000)).clickable { sheet = false })
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.White).navigationBarsPadding()) {
                SheetRow("他人可见") { visibility = "他人可见"; sheet = false }
                Divider(color = Line)
                SheetRow("仅自己可见") { visibility = "仅自己可见"; sheet = false }
                Divider(color = Line)
                SheetRow("取消") { sheet = false }
            }
        }
    }
}

@Composable
private fun SheetRow(text: String, onClick: () -> Unit) {
    Text(text, color = Ink, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 18.dp))
}

private data class Msg(val name: String, val sub: String, val time: String, val badge: Int, val vip: Boolean, val color: Color)

@Composable
private fun Messages(dark: Boolean, onNav: (Int) -> Unit, onChat: () -> Unit, onFriends: () -> Unit, onToggle: () -> Unit) {
    val bg = if (dark) Dark else Color.White
    val ink = if (dark) Color.White else Ink
    val sub = if (dark) Color(0xFF777777) else Color(0xFFB6B6BC)
    val rows = if (dark) listOf(
        Msg("黄花远志", "[与你的共鸣]", "21:31", 2, true, Color(0xFF9F7457)),
        Msg("Ayue", "[与你的共鸣]", "21:30", 2, true, Color(0xFFB99A55)),
        Msg("卡斯帕尔", "跟没想到，过了将近三年回来...", "01-25", 0, false, Color(0xFF4A6277)),
        Msg("可我还是好困喔", "可我还是好困喔 点亮了你", "01-06", 0, true, Color(0xFF6E9BC2)),
        Msg("皮不皮得过卡丘", "皮不皮得过卡丘 点亮了你", "2024-12-27", 2, true, Color(0xFF8E4668))
    ) else listOf(
        Msg("月亮", "嗯嗯嗯", "15:08", 0, false, Color(0xFFF0C7CE)),
        Msg("夜雨寄北", "夜雨寄北 点亮了你", "昨天", 0, false, Color(0xFF9EB4D4)),
        Msg("不知道叫什么", "很难相处", "2021-12-27", 0, false, Color(0xFF9D8A72)),
        Msg("可话君", "你发布了第 1 条动态！🌟", "2021-12-27", 0, false, Pink)
    )

    Scaffold(containerColor = bg, bottomBar = { BottomBar(1, dark, onNav) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).background(bg).statusBarsPadding()) {
            item {
                Row(Modifier.fillMaxWidth().height(70.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("消息", color = ink, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onToggle) { Icon(Icons.Outlined.Tune, "深色", tint = ink) }
                    IconButton(onClick = onFriends, modifier = Modifier.testTag("friends-button")) { Icon(Icons.Outlined.Groups, "好友", tint = ink) }
                }
                Divider(color = if (dark) Color(0xFF272727) else Line)
                Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(62.dp).clip(CircleShape).background(Pink), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(52.dp).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
                            Text("~", color = Pink, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text("遇见", color = sub, fontSize = 13.sp)
                }
                Divider(color = if (dark) Color(0xFF272727) else Line)
            }
            itemsIndexed(rows) { index, row ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChat() }.testTag("message-row-$index").padding(horizontal = 18.dp, vertical = if (dark) 10.dp else 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(row.name.take(1), row.color, if (dark) 48.dp else 58.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(row.name, color = ink, fontSize = if (dark) 15.sp else 17.sp)
                            if (row.vip) { Spacer(Modifier.width(4.dp)); Text("VIP", color = Pink, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(row.sub, color = sub, fontSize = if (dark) 12.sp else 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(row.time, color = sub, fontSize = 11.sp)
                        if (row.badge > 0) {
                            Spacer(Modifier.height(7.dp))
                            Box(Modifier.size(18.dp).clip(CircleShape).background(Pink), contentAlignment = Alignment.Center) {
                                Text(row.badge.toString(), color = Color.White, fontSize = 9.sp)
                            }
                        }
                    }
                }
            }
            if (!dark) item {
                Text("没有更多消息啦", color = Color(0xFFC8C8CD), fontSize = 14.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 26.dp))
            }
        }
    }
}

@Composable
private fun Chat(messages: MutableList<String>, onBack: () -> Unit) {
    var input by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Dark).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Outlined.Close, "返回", tint = Color(0xFF999999)) }
            Text("乘风778", color = Color.White, fontSize = 15.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Spacer(Modifier.width(48.dp))
        }
        Text("2024-12-19 11:16", color = Color(0xFF4A4A4A), fontSize = 10.sp, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
        LazyColumn(Modifier.weight(1f).padding(14.dp)) {
            itemsIndexed(messages) { _, text ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Avatar("乘", Color(0xFF8B7B5D), 30.dp)
                    Spacer(Modifier.width(8.dp))
                    Box(Modifier.widthIn(max = 270.dp).clip(RoundedCornerShape(12.dp)).background(DarkCard).padding(11.dp)) {
                        Text(text, color = Color(0xFFBDBDBD), fontSize = 13.sp, lineHeight = 19.sp)
                    }
                }
            }
            item { Text("😺   ✨   🤍", fontSize = 38.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(42.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF1C1C1C)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(
                    value = input, onValueChange = { input = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                    modifier = Modifier.fillMaxWidth().testTag("chat-input"),
                    decorationBox = { inner -> if (input.isBlank()) Text("回复 乘风778:", color = Color(0xFF777777), fontSize = 13.sp) else inner() }
                )
            }
            IconButton(onClick = { if (input.isNotBlank()) { messages.add(input); input = "" } }, modifier = Modifier.testTag("chat-send")) {
                Icon(Icons.Outlined.Send, "发送", tint = Color(0xFF999999))
            }
        }
    }
}

@Composable
private fun Profile(onNav: (Int) -> Unit, onSettings: () -> Unit, onFriends: () -> Unit, onPost: () -> Unit) {
    Scaffold(containerColor = Page, bottomBar = { BottomBar(2, false, onNav) }) { pad ->
        LazyColumn(Modifier.fillMaxSize().padding(bottom = pad.calculateBottomPadding()).background(Page)) {
            item {
                Box(Modifier.fillMaxWidth().height(390.dp).background(Brush.verticalGradient(listOf(Color(0xFFBBAE9E), Color(0xFFE5DED6), Page)))) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.End) {
                        IconButton(onClick = {}) { Icon(Icons.Outlined.Search, "搜索", tint = Color.White) }
                        IconButton(onClick = onSettings, modifier = Modifier.testTag("settings-button")) { Icon(Icons.Outlined.Settings, "设置", tint = Color.White) }
                    }
                    Box(Modifier.fillMaxWidth().height(170.dp).align(Alignment.BottomCenter).background(Brush.verticalGradient(listOf(Color.Transparent, Page))))
                    Avatar("L", Color(0xFFB99FA1), 92.dp, Modifier.align(Alignment.BottomCenter).padding(bottom = 18.dp), Color.White)
                }
                Column(Modifier.fillMaxWidth().padding(horizontal = 18.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Lexie", color = Ink, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.width(7.dp))
                        Text("♕VIP", color = Pink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("重庆市 · 双子座 · 2108 条", color = Sub, fontSize = 14.sp)
                    Text("IP 重庆", color = Color(0xFFB8B8BE), fontSize = 12.sp)
                    Spacer(Modifier.height(20.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Stat("241", "好友", "♣", Color(0xFFEAF8E5), Modifier.weight(1f).clickable { onFriends() }.testTag("profile-friends"))
                        Stat("2.86万", "点亮的动态", "☀", Color(0xFFFFF8DD), Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth().height(62.dp).clip(RoundedCornerShape(18.dp)).background(Color.White).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("查看 VIP 特权", color = Ink, fontSize = 16.sp, modifier = Modifier.weight(1f))
                        Text("♕", color = Pink, fontSize = 21.sp)
                        Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFD0D0D6))
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Color.White).padding(16.dp)) {
                        Text("相册", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(Color(0xFF5E3026), Color(0xFFCBA66B), Color(0xFF67462F), Color(0xFF7E7772)).forEachIndexed { i, c ->
                                Box(Modifier.weight(1f).height(72.dp).clip(RoundedCornerShape(8.dp)).background(c), contentAlignment = Alignment.Center) {
                                    if (i == 3) Text("+4815", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Color.White).clickable { onPost() }.padding(16.dp).testTag("profile-post")) {
                        Text("看到可话停止运营的消息，非常不舍。", color = Ink, fontSize = 15.sp, lineHeight = 22.sp)
                        Spacer(Modifier.height(8.dp))
                        Text("2025-12-10 13:40", color = Color(0xFFBEBEC4), fontSize = 10.sp)
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String, icon: String, iconBg: Color, modifier: Modifier = Modifier) {
    Row(modifier.height(78.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(horizontal = 15.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(value, color = Ink, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            Text(label, color = Sub, fontSize = 13.sp)
        }
        Box(Modifier.size(46.dp).clip(CircleShape).background(iconBg), contentAlignment = Alignment.Center) {
            Text(icon, fontSize = 22.sp, color = if (icon == "☀") Color(0xFFF0CC35) else Color(0xFF9AD98B))
        }
    }
}

@Composable
private fun Post(lit: Boolean, onLight: () -> Unit, onBack: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart)) { Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = Ink) }
        Text("记第一次剧本杀 远山希子\n可真是太难了！！", color = Ink, fontSize = 19.sp, lineHeight = 30.sp, modifier = Modifier.padding(start = 76.dp, top = 105.dp, end = 28.dp))
        Button(
            onClick = onLight, modifier = Modifier.align(Alignment.Center).testTag("light-button"),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFD2D2D7)), shape = RoundedCornerShape(22.dp)
        ) { Text(if (lit) "☀ 已点亮" else "☀ 点亮", color = if (lit) Pink else Ink) }
    }
}

@Composable
private fun Settings(dark: Boolean, onDark: (Boolean) -> Unit, onBack: () -> Unit) {
    val a = listOf("个人信息", "账号与安全", "表情整理")
    val b = listOf("深色模式", "新消息通知", "通用", "隐私")
    val c = listOf("清除缓存", "检测更新")
    val d = listOf("给可话打赏，支持我们❤️", "给可话反馈", "把可话推荐给朋友", "给可话好评，鼓励一下")
    LazyColumn(Modifier.fillMaxSize().background(Color.White).statusBarsPadding()) {
        item {
            Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = Ink) }
                Text("设置", color = Ink, fontSize = 18.sp)
            }
        }
        itemsIndexed(a) { _, t -> Setting(t) }
        item { Divider(color = Color(0xFFF4F4F6), thickness = 10.dp) }
        itemsIndexed(b) { _, t -> if (t == "深色模式") Setting(t, if (dark) "已开启" else "跟随系统", dark, onDark) else Setting(t) }
        item { Divider(color = Color(0xFFF4F4F6), thickness = 10.dp) }
        itemsIndexed(c) { _, t -> Setting(t, if (t == "清除缓存") "66.61 MB" else "") }
        item { Divider(color = Color(0xFFF4F4F6), thickness = 10.dp) }
        itemsIndexed(d) { _, t -> Setting(t) }
    }
}

@Composable
private fun Setting(text: String, right: String = "", toggle: Boolean? = null, onToggle: (Boolean) -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(54.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = Ink, fontSize = 14.sp, modifier = Modifier.weight(1f))
        if (right.isNotBlank()) Text(right, color = Color(0xFFB9B9BF), fontSize = 12.sp)
        if (toggle != null) { Spacer(Modifier.width(8.dp)); Switch(checked = toggle, onCheckedChange = onToggle) }
        else Icon(Icons.Outlined.ChevronRight, null, tint = Color(0xFFD0D0D5), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun Friends(onBack: () -> Unit) {
    val names = listOf("找到我的你", "YoYo黑寡", "Echo", "Gucci", "不重要小姐姐", "么么茶", "或许你会发光", "焕紫", "你猜猜", "白桃汽水贩卖机", "你愚蠢的欧豆豆", "JulieW", "Kashiwa")
    LazyColumn(Modifier.fillMaxSize().background(Dark).statusBarsPadding()) {
        item {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Outlined.ArrowBackIosNew, "返回", tint = Color.White) }
                Text("我的好友", color = Color.White, fontSize = 16.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                Spacer(Modifier.width(48.dp))
            }
        }
        itemsIndexed(names) { index, name ->
            Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(name.take(1), Color.hsv((index * 29f) % 360f, .35f, .7f), 36.dp)
                Spacer(Modifier.width(12.dp))
                Text(name, color = Color(0xFFE6E6E6), fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("相识 " + (1924 + index * 7) + " 天", color = Color(0xFF777777), fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun BottomBar(selected: Int, dark: Boolean, onSelect: (Int) -> Unit) {
    val bg = if (dark) Dark else Color.White
    val chosen = if (dark) Color.White else Ink
    val idle = if (dark) Color(0xFF6E6E6E) else Color(0xFFB8B8BE)
    NavigationBar(containerColor = bg, tonalElevation = 0.dp, modifier = Modifier.navigationBarsPadding()) {
        listOf(Icons.Outlined.Home, Icons.Outlined.ChatBubbleOutline, Icons.Outlined.PersonOutline).forEachIndexed { index, icon ->
            NavigationBarItem(
                selected = selected == index, onClick = { onSelect(index) }, modifier = Modifier.testTag("nav-$index"),
                icon = { Icon(icon, null, tint = if (selected == index) chosen else idle, modifier = Modifier.size(28.dp)) },
                label = null, alwaysShowLabel = false
            )
        }
    }
}

@Composable
private fun Avatar(initial: String, color: Color, size: Dp, modifier: Modifier = Modifier, border: Color? = null) {
    Box(
        modifier.then(
            Modifier.size(size).clip(CircleShape)
                .background(if (border != null) border else Color.Transparent)
                .padding(if (border != null) 5.dp else 0.dp)
                .clip(CircleShape).background(color)
        ),
        contentAlignment = Alignment.Center
    ) {
        Text(initial, color = Color.White, fontSize = (size.value * .36f).sp, fontWeight = FontWeight.SemiBold)
    }
}
