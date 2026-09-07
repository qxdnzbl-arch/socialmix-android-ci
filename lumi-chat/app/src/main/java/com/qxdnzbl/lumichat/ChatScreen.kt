package com.qxdnzbl.lumichat

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF34313A)
private val Muted = Color(0xFF817B89)
private val Pink = Color(0xFFF3B7D9)
private val Lilac = Color(0xFFC7B8F7)
private val Sky = Color(0xFFB9DDF5)
private val Mint = Color(0xFFCDEEDF)
private val Paper = Color(0xEFFFFFFF)

@Composable
fun ChatScreen(
    messages: MutableList<ChatMessage>,
    onMenu: () -> Unit,
    onClear: () -> Unit,
    onMessagesChanged: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(messages.size, sending) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFFFFF7FB),
                        Color(0xFFF7F4FF),
                        Color(0xFFF3FAFF),
                        Color(0xFFF8FFF9)
                    )
                )
            )
    ) {
        SoftGlow(Alignment.TopStart, Pink.copy(alpha = 0.24f))
        SoftGlow(Alignment.CenterEnd, Sky.copy(alpha = 0.22f))
        SoftGlow(Alignment.BottomStart, Mint.copy(alpha = 0.20f))

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            TopBar(onMenu = onMenu, onClear = onClear)

            if (messages.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message -> ChatBubble(message) }
                    if (sending) item { ThinkingBubble() }
                }
            }

            AnimatedVisibility(visible = status != null) {
                Text(
                    text = status.orEmpty(),
                    color = Color(0xFF9A617E),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }

            Composer(
                input = input,
                sending = sending,
                onInput = { input = it },
                onSend = {
                    val text = input.trim()
                    if (text.isEmpty() || sending) return@Composer
                    input = ""
                    status = null
                    messages.add(ChatMessage("user", text))
                    onMessagesChanged()
                    sending = true

                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { ChatApi.send(context, messages) } }
                        sending = false
                        result.onSuccess { reply ->
                            messages.add(ChatMessage("assistant", reply))
                            onMessagesChanged()
                        }.onFailure { error ->
                            status = error.message ?: "连接失败"
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun SoftGlow(alignment: Alignment, color: Color) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = alignment) {
        Box(
            modifier = Modifier
                .size(230.dp)
                .background(Brush.radialGradient(listOf(color, Color.Transparent)), CircleShape)
        )
    }
}

@Composable
private fun TopBar(onMenu: () -> Unit, onClear: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onMenu,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(Paper)
                .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(16.dp))
        ) {
            Icon(Icons.Rounded.Menu, contentDescription = "菜单", tint = Ink)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text("微光", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
            Text("DeepSeek V4 Flash", color = Muted, fontSize = 11.sp)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onClear) {
            Icon(Icons.Rounded.DeleteOutline, contentDescription = "清空", tint = Muted)
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(70.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Pink, Lilac, Sky))),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("想说什么就说。", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = Ink)
            Spacer(Modifier.height(8.dp))
            Text("把普通聊天页换成你愿意一直待着的小世界。", fontSize = 13.sp, color = Muted)
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage) {
    val isUser = message.role == "user"
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        val shape = if (isUser) RoundedCornerShape(24.dp, 24.dp, 8.dp, 24.dp)
        else RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp)

        Box(
            modifier = Modifier
                .fillMaxWidth(if (isUser) 0.82f else 0.90f)
                .clip(shape)
                .then(
                    if (isUser) Modifier.background(
                        Brush.linearGradient(
                            listOf(Color(0xFFF9D8EA), Color(0xFFE7D9F8), Color(0xFFDCECFB))
                        )
                    ) else Modifier.background(Color(0xEFFFFFFF)).border(1.dp, Color(0x66FFFFFF), shape)
                )
                .padding(horizontal = 18.dp, vertical = 14.dp)
        ) {
            Text(message.text, color = Ink, fontSize = 16.sp, lineHeight = 24.sp)
        }
    }
}

@Composable
private fun ThinkingBubble() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp, 24.dp, 24.dp, 8.dp))
                .background(Color(0xEFFFFFFF))
                .padding(horizontal = 18.dp, vertical = 13.dp)
        ) {
            Text("正在想…", color = Muted, fontSize = 14.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Composer(
    input: String,
    sending: Boolean,
    onInput: (String) -> Unit,
    onSend: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Color(0xF7FFFFFF))
            .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(28.dp))
            .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = onInput,
            modifier = Modifier.weight(1f),
            placeholder = { Text("回复 微光", color = Color(0xFF9A929F)) },
            minLines = 1,
            maxLines = 5,
            textStyle = TextStyle(fontSize = 16.sp, color = Ink),
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                cursorColor = Color(0xFFB77ACC)
            )
        )
        IconButton(
            onClick = onSend,
            enabled = input.isNotBlank() && !sending,
            modifier = Modifier
                .padding(bottom = 4.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(
                    if (input.isNotBlank() && !sending)
                        Brush.linearGradient(listOf(Color(0xFFECAFD7), Color(0xFFBEA5F0)))
                    else Brush.linearGradient(listOf(Color(0xFFE9E4E8), Color(0xFFE9E4E8)))
                )
        ) {
            Icon(Icons.Rounded.Send, contentDescription = "发送", tint = Color.White)
        }
    }
}
