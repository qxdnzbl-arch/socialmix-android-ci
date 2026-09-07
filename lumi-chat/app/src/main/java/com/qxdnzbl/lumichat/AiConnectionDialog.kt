package com.qxdnzbl.lumichat

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DialogInk = Color(0xFF34313A)
private val DialogMuted = Color(0xFF817B89)

@Composable
fun AiConnectionDialog(
    context: Context,
    onSaved: () -> Unit,
    onDismiss: () -> Unit
) {
    var apiKey by remember { mutableStateOf(SecureConfigStore.loadApiKey(context)) }
    var checking by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current

    Dialog(
        onDismissRequest = { if (!checking) onDismiss() },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .background(Color(0xFFFFFBFD), RoundedCornerShape(28.dp))
                .padding(22.dp)
        ) {
            Text("连接 AI", fontSize = 21.sp, fontWeight = FontWeight.SemiBold, color = DialogInk)
            Spacer(Modifier.height(7.dp))
            Text(
                "使用硅基流动的 DeepSeek V4 Flash。支持支付宝、微信充值；密钥只加密保存在这台手机里。",
                color = DialogMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp
            )

            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    status = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("API 密钥") },
                placeholder = { Text("sk-…") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(18.dp)
            )

            status?.let {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = it,
                    color = if (it == "连接成功") Color(0xFF3F8062) else Color(0xFFB45B77),
                    fontSize = 12.sp
                )
            }

            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = { uriHandler.openUri("https://cloud.siliconflow.cn/account/ak") },
                    enabled = !checking
                ) {
                    Text("去创建密钥")
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDismiss, enabled = !checking) {
                    Text("稍后")
                }
                Button(
                    onClick = {
                        val clean = apiKey.trim()
                        if (clean.isEmpty()) {
                            status = "先粘贴 API 密钥"
                            return@Button
                        }
                        checking = true
                        status = "正在验证…"
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) { ChatApi.test(clean) }
                            if (ok) {
                                withContext(Dispatchers.IO) { SecureConfigStore.saveApiKey(context, clean) }
                                status = "连接成功"
                                checking = false
                                onSaved()
                            } else {
                                status = "连接失败，请检查密钥或账户余额"
                                checking = false
                            }
                        }
                    },
                    enabled = !checking && apiKey.isNotBlank(),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB99BE7))
                ) {
                    Text(if (checking) "验证中" else "验证并保存")
                }
            }
        }
    }
}
