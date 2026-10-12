package com.qxdnzbl.lumichat

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { LumiApp(this) }
    }
}

@Composable
private fun LumiApp(context: Context) {
    MaterialTheme {
        val messages = remember {
            mutableStateListOf<ChatMessage>().apply { addAll(loadMessages(context)) }
        }
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = Color(0xFFFDF9FC),
                    modifier = Modifier.width(294.dp)
                ) {
                    DrawerContent(
                        onClose = { scope.launch { drawerState.close() } },
                        onNewChat = {
                            messages.clear()
                            saveMessages(context, messages)
                            scope.launch { drawerState.close() }
                        }
                    )
                }
            }
        ) {
            ChatScreen(
                messages = messages,
                onMenu = { scope.launch { drawerState.open() } },
                onClear = {
                    messages.clear()
                    saveMessages(context, messages)
                },
                onMessagesChanged = { saveMessages(context, messages) }
            )
        }
    }
}
