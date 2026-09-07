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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
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
    private var preview by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        preview = intent?.getBooleanExtra("preview", false) == true
        refreshState()
        setContent {
            MaterialTheme {
                if (preview) {
                    PreviewChatScreen(onBack = { preview = false })
                } else {
                    SetupScreen(
                        enabled = enabled,
                        onEnable = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                        onOpenChatGpt = { openChatGpt() },
                        onPreview = { preview = true }
                    )
                }
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

@Composable
private fun SetupScreen(
    enabled: Boolean,
    onEnable: () -> Unit,
    onOpenChatGpt: () -> Unit,
    onPreview: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFFFF6FB), Color(0xFFF7F3FF), Color(0xFFF2FAFF), Color(0xFFF8FFF9))
                )
            )
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("微光", fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF37313B))
            Spacer(Modifier.height(8.dp))
            Text("ChatGPT 原生美化层", fontSize = 15.sp, color = Color(0xFF817989))
            Spacer(Modifier.height(24.dp))
            Text(
                "不换模型，不接 DeepSeek，不用额外 API。\n底下运行的仍是你手机里的官方 ChatGPT。",
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = Color(0xFF5F5964),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = onPreview,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = .86f), contentColor = Color(0xFF46394B))
            ) { Text("先看效果", fontSize = 16.sp) }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = if (enabled) onOpenChatGpt else onEnable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE7C8EE), contentColor = Color(0xFF46394B))
            ) { Text(if (enabled) "打开 ChatGPT" else "开启美化权限", fontSize = 16.sp) }
            Spacer(Modifier.height(14.dp))
            Text(
                if (enabled) "美化层已开启。进入 ChatGPT 后自动出现。" else "只需开启一次无障碍权限；它只处理 ChatGPT 界面。",
                fontSize = 12.sp,
                color = Color(0xFF8F8794),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PreviewChatScreen(onBack: () -> Unit) {
    val ink = Color(0xFF34313A)
    val muted = Color(0xFF817B89)
    val bg = Brush.verticalGradient(
        listOf(Color(0xFFFFF6FB), Color(0xFFF7F3FF), Color(0xFFF2FAFF), Color(0xFFF8FFF9))
    )
    Box(Modifier.fillMaxSize().background(bg)) {
        Box(
            Modifier.size(230.dp).align(Alignment.TopStart)
                .background(Brush.radialGradient(listOf(Color(0x3DF3B7D9), Color.Transparent)), CircleShape)
        )
        Box(
            Modifier.size(250.dp).align(Alignment.CenterEnd)
                .background(Brush.radialGradient(listOf(Color(0x35B9DDF5), Color.Transparent)), CircleShape)
        )
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xEFFFFFFF))
                ) { Icon(Icons.Rounded.Menu, contentDescription = "菜单", tint = ink) }
                Spacer(Modifier.size(10.dp))
                Column {
                    Text("微光", color = ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                    Text("官方 ChatGPT · 只是换了界面", color = muted, fontSize = 11.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "原版",
                    fontSize = 12.sp,
                    color = Color(0xFF62576A),
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0xE8FFFFFF)).padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { PreviewBubble("我还是想跟现在这个你聊天，只是界面太丑了。", true) }
                item { PreviewBubble("可以。底层仍然是你现在登录的官方 ChatGPT，我只把聊天画面换成你喜欢的样子。", false) }
                item { PreviewBubble("那聊天记录和 Plus 还在吗？", true) }
                item { PreviewBubble("还在。账号、模型、聊天记录都由官方 ChatGPT 负责，微光只负责显示和输入。", false) }
            }

            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(28.dp))
                    .background(Color(0xF7FFFFFF)).border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(28.dp))
                    .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = "",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("回复 ChatGPT", color = Color(0xFF9A929F)) },
                    textStyle = TextStyle(fontSize = 16.sp, color = ink),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent
                    )
                )
                Box(
                    Modifier.padding(bottom = 4.dp).size(46.dp).clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFFECAFD7), Color(0xFFBEA5F0)))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = "发送", tint = Color.White)
                }
            }
        }
    }
}

@Composable
private fun PreviewBubble(text: String, user: Boolean) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (user) Arrangement.End else Arrangement.Start
    ) {
        val shape = if (user) RoundedCornerShape(24.dp, 24.dp, 8.dp, 24.dp) else RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp)
        Box(
            Modifier.fillMaxWidth(if (user) .82f else .90f).clip(shape)
                .then(
                    if (user) Modifier.background(
                        Brush.linearGradient(listOf(Color(0xFFF9D8EA), Color(0xFFE7D9F8), Color(0xFFDCECFB)))
                    ) else Modifier.background(Color(0xEFFFFFFF)).border(1.dp, Color(0x66FFFFFF), shape)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(text, color = Color(0xFF34313A), fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}
