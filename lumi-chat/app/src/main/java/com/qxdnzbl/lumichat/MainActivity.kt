package com.qxdnzbl.lumichat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    private var enabled by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        refreshState()
        setContent {
            MaterialTheme {
                WeiguangShell(
                    accessibilityEnabled = enabled,
                    onEnableAccessibility = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onOpenOfficial = { openChatGpt() }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshState()
    }

    private fun refreshState() {
        enabled = isAccessibilityServiceEnabled(this, ChatGptSkinService::class.java)
    }

    private fun openChatGpt() {
        val launch = packageManager.getLaunchIntentForPackage(ChatGptSkinService.TARGET_PACKAGE)
        if (launch != null) {
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(launch)
        } else {
            startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("market://details?id=${ChatGptSkinService.TARGET_PACKAGE}")))
        }
    }
}

private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
    val expected = ComponentName(context, serviceClass).flattenToString()
    val enabledServices = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ).orEmpty()
    return enabledServices.split(':').any { it.equals(expected, ignoreCase = true) }
}

private enum class ShellPage { CHAT, DRAWER, MODEL, TOOLS, VOICE, SETTINGS }
private data class TrialMessage(val text: String, val user: Boolean)

private val Ink = Color(0xFF34313A)
private val Muted = Color(0xFF817B89)
private val Glass = Color(0xEAFFFFFF)
private val Background = Brush.verticalGradient(
    listOf(Color(0xFFFFF7FB), Color(0xFFF7F4FF), Color(0xFFF3FAFF), Color(0xFFF8FFF9))
)

@Composable
private fun WeiguangShell(
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onOpenOfficial: () -> Unit
) {
    var page by remember { mutableStateOf(ShellPage.CHAT) }
    var selectedModel by remember { mutableStateOf("GPT-5.6 Sol") }
    var input by remember { mutableStateOf("") }
    val messages = remember {
        mutableStateListOf(
            TrialMessage("我还是想跟现在这个你聊天，只是把界面换漂亮。", true),
            TrialMessage("底下仍然是官方 ChatGPT。微光只负责你看到和操作的界面。", false),
            TrialMessage("我先装上试试手感。", true)
        )
    }

    Box(Modifier.fillMaxSize().background(Background)) {
        SoftGlow(Alignment.TopStart, Color(0x3DF3B7D9))
        SoftGlow(Alignment.CenterEnd, Color(0x35B9DDF5))
        SoftGlow(Alignment.BottomStart, Color(0x32CDEEDF))

        when (page) {
            ShellPage.CHAT -> ChatPage(
                model = selectedModel,
                messages = messages,
                input = input,
                onInput = { input = it },
                onMenu = { page = ShellPage.DRAWER },
                onModel = { page = ShellPage.MODEL },
                onTools = { page = ShellPage.TOOLS },
                onVoice = { page = ShellPage.VOICE },
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        messages.add(TrialMessage(text, true))
                        input = ""
                    }
                },
                accessibilityEnabled = accessibilityEnabled,
                onEnableAccessibility = onEnableAccessibility,
                onOpenOfficial = onOpenOfficial
            )
            ShellPage.DRAWER -> DrawerPage(
                onBack = { page = ShellPage.CHAT },
                onNewChat = {
                    messages.clear()
                    input = ""
                    page = ShellPage.CHAT
                },
                onSettings = { page = ShellPage.SETTINGS }
            )
            ShellPage.MODEL -> ModelPage(
                selected = selectedModel,
                onSelect = {
                    selectedModel = it
                    page = ShellPage.CHAT
                },
                onBack = { page = ShellPage.CHAT }
            )
            ShellPage.TOOLS -> ToolsPage(
                onBack = { page = ShellPage.CHAT },
                onOpenOfficial = onOpenOfficial,
                onVoice = { page = ShellPage.VOICE }
            )
            ShellPage.VOICE -> VoicePage(
                onBack = { page = ShellPage.CHAT },
                onOpenOfficial = onOpenOfficial
            )
            ShellPage.SETTINGS -> SettingsPage(
                accessibilityEnabled = accessibilityEnabled,
                onBack = { page = ShellPage.DRAWER },
                onEnableAccessibility = onEnableAccessibility,
                onOpenOfficial = onOpenOfficial
            )
        }
    }
}

@Composable
private fun SoftGlow(alignment: Alignment, color: Color) {
    Box(Modifier.fillMaxSize(), contentAlignment = alignment) {
        Box(
            Modifier.size(240.dp).background(
                Brush.radialGradient(listOf(color, Color.Transparent)), CircleShape
            )
        )
    }
}

@Composable
private fun ChatPage(
    model: String,
    messages: List<TrialMessage>,
    input: String,
    onInput: (String) -> Unit,
    onMenu: () -> Unit,
    onModel: () -> Unit,
    onTools: () -> Unit,
    onVoice: () -> Unit,
    onSend: () -> Unit,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onOpenOfficial: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onMenu,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Glass)
            ) {
                Icon(Icons.Rounded.Menu, contentDescription = "菜单", tint = Ink)
            }
            Spacer(Modifier.width(10.dp))
            Column(
                Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onModel).padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Text("ChatGPT", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text("$model  ·  点击切换", color = Muted, fontSize = 11.sp)
            }
            Spacer(Modifier.weight(1f))
            Text(
                if (accessibilityEnabled) "进入官方" else "开启连接",
                color = Color(0xFF66586C),
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Glass)
                    .clickable { if (accessibilityEnabled) onOpenOfficial() else onEnableAccessibility() }
                    .padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }

        if (messages.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(74.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(Color(0xFFF1B9DB), Color(0xFFCAB8F5), Color(0xFFB9DDF5))))
                    )
                    Spacer(Modifier.height(18.dp))
                    Text("今天想聊什么？", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = Ink)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(messages) { message -> Bubble(message.text, message.user) }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "+",
                fontSize = 28.sp,
                color = Color(0xFF756A7C),
                textAlign = TextAlign.Center,
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color(0xDFFFFFFF))
                    .clickable(onClick = onTools).padding(top = 2.dp)
            )
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(28.dp)).background(Color(0xF4FFFFFF))
                    .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(28.dp)).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("回复 ChatGPT", color = Color(0xFF9A929F)) },
                    minLines = 1,
                    maxLines = 5,
                    textStyle = TextStyle(fontSize = 16.sp, color = Ink),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        cursorColor = Color(0xFFB77ACC)
                    )
                )
                Text(
                    "语音",
                    fontSize = 12.sp,
                    color = Muted,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onVoice)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                )
                IconButton(
                    onClick = onSend,
                    enabled = input.isNotBlank(),
                    modifier = Modifier.size(46.dp).clip(CircleShape).background(
                        if (input.isNotBlank()) Brush.linearGradient(listOf(Color(0xFFECAFD7), Color(0xFFBEA5F0)))
                        else Brush.linearGradient(listOf(Color(0xFFE9E4E8), Color(0xFFE9E4E8)))
                    )
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = "发送", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun Bubble(text: String, user: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (user) Arrangement.End else Arrangement.Start) {
        val shape = if (user) RoundedCornerShape(24.dp, 24.dp, 8.dp, 24.dp)
        else RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp)
        Box(
            Modifier.fillMaxWidth(if (user) .82f else .90f).clip(shape).then(
                if (user) Modifier.background(
                    Brush.linearGradient(listOf(Color(0xFFF9D8EA), Color(0xFFE7D9F8), Color(0xFFDCECFB)))
                ) else Modifier.background(Color(0xEFFFFFFF)).border(1.dp, Color(0x66FFFFFF), shape)
            ).padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(text, color = Ink, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun DrawerPage(onBack: () -> Unit, onNewChat: () -> Unit, onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("ChatGPT", fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = Ink)
            Spacer(Modifier.weight(1f))
            Text("关闭", color = Muted, modifier = Modifier.clickable(onClick = onBack).padding(10.dp))
        }
        Spacer(Modifier.height(18.dp))
        GlassRow("＋  新对话", "") { onNewChat() }
        Spacer(Modifier.height(10.dp))
        GlassRow("⌕  搜索聊天记录", "") { }
        Spacer(Modifier.height(26.dp))
        Text("今天", fontSize = 13.sp, color = Muted, modifier = Modifier.padding(horizontal = 8.dp))
        Spacer(Modifier.height(8.dp))
        listOf("ChatGPT 美化到底怎么做", "实体手机方案", "蛇系角色母版", "工作模式宠物").forEach {
            GlassRow(it, "") { onBack() }
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.weight(1f))
        GlassRow("设置", "外观与连接") { onSettings() }
        Spacer(Modifier.height(8.dp))
        GlassRow("nzbl qxd", "Plus") { }
    }
}

@Composable
private fun ModelPage(selected: String, onSelect: (String) -> Unit, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        PageTitle("模型", onBack)
        Spacer(Modifier.height(18.dp))
        listOf(
            Triple("GPT-5.6 Sol", "复杂任务、代码、研究", ""),
            Triple("GPT-5.6 Luna", "更快的日常聊天", ""),
            Triple("GPT-5.6 Pro", "最难任务", ""),
            Triple("自动", "让 ChatGPT 自动选择", "")
        ).forEach { (name, desc, _) ->
            GlassRow(name, if (name == selected) "当前 · $desc" else desc) { onSelect(name) }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun ToolsPage(onBack: () -> Unit, onOpenOfficial: () -> Unit, onVoice: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        PageTitle("添加内容", onBack)
        Spacer(Modifier.height(20.dp))
        listOf(
            "图片" to "从相册选择",
            "相机" to "拍一张",
            "文件" to "上传文档",
            "语音" to "开始语音",
            "深度研究" to "查资料并整理",
            "更多官方功能" to "临时切回官方界面"
        ).forEach { (name, desc) ->
            GlassRow(name, desc) {
                if (name == "语音") onVoice() else if (name == "更多官方功能" || name == "图片" || name == "相机" || name == "文件" || name == "深度研究") onOpenOfficial()
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun VoicePage(onBack: () -> Unit, onOpenOfficial: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PageTitle("语音", onBack)
        Spacer(Modifier.height(170.dp))
        Text("正在听", color = Muted, fontSize = 15.sp)
        Spacer(Modifier.height(34.dp))
        Box(
            Modifier.size(250.dp).clip(CircleShape).background(
                Brush.radialGradient(listOf(Color(0xFFEED2F0), Color(0xFFCFE4FA), Color(0x00FFFFFF)))
            )
        )
        Spacer(Modifier.height(48.dp))
        Text("声音能力继续用官方 ChatGPT", color = Ink, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(12.dp))
        Text("点下面进入官方语音", color = Muted, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Button(
            onClick = onOpenOfficial,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE6D4F1), contentColor = Ink)
        ) { Text("进入官方语音") }
    }
}

@Composable
private fun SettingsPage(
    accessibilityEnabled: Boolean,
    onBack: () -> Unit,
    onEnableAccessibility: () -> Unit,
    onOpenOfficial: () -> Unit
) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
        PageTitle("设置", onBack)
        Spacer(Modifier.height(20.dp))
        GlassRow("外观", "彩光 · 轻透") { }
        Spacer(Modifier.height(10.dp))
        GlassRow("背景", "粉紫蓝柔光") { }
        Spacer(Modifier.height(10.dp))
        GlassRow("聊天气泡", "彩光 / 白玻璃") { }
        Spacer(Modifier.height(10.dp))
        GlassRow("字体与间距", "清爽 · 舒服") { }
        Spacer(Modifier.height(10.dp))
        GlassRow("美化层权限", if (accessibilityEnabled) "已开启" else "未开启") {
            if (!accessibilityEnabled) onEnableAccessibility()
        }
        Spacer(Modifier.height(10.dp))
        GlassRow("打开官方 ChatGPT", "账号、Plus、聊天记录都在这里") { onOpenOfficial() }
    }
}

@Composable
private fun PageTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            "‹",
            fontSize = 40.sp,
            color = Ink,
            modifier = Modifier.size(48.dp).clip(CircleShape).background(Glass).clickable(onClick = onBack),
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.width(12.dp))
        Text(title, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, color = Ink)
    }
}

@Composable
private fun GlassRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Color(0xE6FFFFFF))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(24.dp))
            .clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            if (subtitle.isNotEmpty()) {
                Spacer(Modifier.height(3.dp))
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
        }
        Text("›", color = Muted, fontSize = 26.sp)
    }
}
