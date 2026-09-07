package com.qxdnzbl.lumichat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
                SetupScreen(
                    enabled = enabled,
                    onEnable = { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
                    onOpenChatGpt = { openChatGpt() }
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

@Composable
private fun SetupScreen(
    enabled: Boolean,
    onEnable: () -> Unit,
    onOpenChatGpt: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF6FB),
                        Color(0xFFF7F3FF),
                        Color(0xFFF2FAFF),
                        Color(0xFFF8FFF9)
                    )
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
            Text(
                text = "微光",
                fontSize = 34.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF37313B)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "ChatGPT 原生美化层",
                fontSize = 15.sp,
                color = Color(0xFF817989)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "不换模型，不接 DeepSeek，不用额外 API。\n底下运行的仍是你手机里的官方 ChatGPT。",
                fontSize = 15.sp,
                lineHeight = 23.sp,
                color = Color(0xFF5F5964),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))

            Button(
                onClick = if (enabled) onOpenChatGpt else onEnable,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFE7C8EE),
                    contentColor = Color(0xFF46394B)
                )
            ) {
                Text(if (enabled) "打开 ChatGPT" else "开启美化权限", fontSize = 16.sp)
            }

            Spacer(Modifier.height(14.dp))
            Text(
                text = if (enabled) "美化层已开启。进入 ChatGPT 后自动出现。" else "只需开启一次无障碍权限；它只处理 ChatGPT 界面。",
                fontSize = 12.sp,
                color = Color(0xFF8F8794),
                textAlign = TextAlign.Center
            )
        }
    }
}
