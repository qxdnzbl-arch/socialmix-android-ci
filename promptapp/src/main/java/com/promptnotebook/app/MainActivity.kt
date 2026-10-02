package com.promptnotebook.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { PromptNotebookTheme { PromptNotebookApp() } }
    }
}

private val AppColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF292927),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = androidx.compose.ui.graphics.Color(0xFFEDECE7),
    onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF242422),
    background = androidx.compose.ui.graphics.Color(0xFFFAFAF8),
    onBackground = androidx.compose.ui.graphics.Color(0xFF1D1D1B),
    surface = androidx.compose.ui.graphics.Color.White,
    onSurface = androidx.compose.ui.graphics.Color(0xFF1D1D1B),
    surfaceVariant = androidx.compose.ui.graphics.Color(0xFFF1F0EC),
    onSurfaceVariant = androidx.compose.ui.graphics.Color(0xFF686862),
    outline = androidx.compose.ui.graphics.Color(0xFFD9D8D2),
    error = androidx.compose.ui.graphics.Color(0xFFB3261E)
)

@Composable
fun PromptNotebookTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = Typography(
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 22.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PromptNotebookApp() {
    val context = LocalContext.current
    var allItems by remember { mutableStateOf(PromptStore.load(context)) }
    var editing by remember { mutableStateOf<PromptItem?>(null) }
    var creating by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf("全部") }
    var deleteTarget by remember { mutableStateOf<PromptItem?>(null) }
    var importCandidate by remember { mutableStateOf<List<PromptItem>?>(null) }

    fun persist(next: List<PromptItem>) {
        allItems = next.sortedByDescending { it.updatedAt }
        PromptStore.save(context, allItems)
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use {
                    it.write(PromptStore.encode(allItems).toByteArray(Charsets.UTF_8))
                }
            }.onSuccess {
                Toast.makeText(context, "备份已导出", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, "导出失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val text = context.contentResolver.openInputStream(uri)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                    ?: error("empty")
                PromptStore.decode(text)
            }.onSuccess { importCandidate = it }
             .onFailure { Toast.makeText(context, "这不是有效的提示词备份", Toast.LENGTH_SHORT).show() }
        }
    }

    if (editing != null) {
        EditorScreen(
            initial = editing!!,
            isNew = creating,
            onBack = {
                editing = null
                creating = false
            },
            onDraft = { if (creating) PromptStore.saveDraft(context, it) },
            onSave = { saved ->
                val fixed = saved.copy(updatedAt = System.currentTimeMillis())
                val found = allItems.any { it.id == fixed.id }
                val next = if (found) {
                    allItems.map { if (it.id == fixed.id) fixed else it }
                } else {
                    listOf(fixed) + allItems
                }
                persist(next)
                PromptStore.saveDraft(context, null)
                editing = null
                creating = false
                Toast.makeText(context, "已保存", Toast.LENGTH_SHORT).show()
            }
        )
        return
    }

    val categories = remember(allItems) {
        allItems.map { it.category.trim() }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val visible = remember(allItems, query, activeFilter) {
        val q = query.trim().lowercase()
        allItems.filter { item ->
            val filterOk = when (activeFilter) {
                "全部" -> true
                "收藏" -> item.favorite
                else -> item.category == activeFilter
            }
            val searchable = listOf(
                item.title,
                item.category,
                item.tags.joinToString(" "),
                item.content,
                item.note
            ).joinToString(" ").lowercase()
            filterOk && (q.isBlank() || searchable.contains(q))
        }
    }

    var topMenu by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("提示词记事本")
                        Text(
                            allItems.size.toString() + " 条 · 离线保存在本机",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { topMenu = true }) {
                            Icon(Icons.Rounded.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = topMenu, onDismissRequest = { topMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("导出备份") },
                                leadingIcon = { Icon(Icons.Rounded.FileDownload, null) },
                                onClick = {
                                    topMenu = false
                                    val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
                                    exportLauncher.launch("提示词记事本-" + stamp + ".json")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("导入备份") },
                                leadingIcon = { Icon(Icons.Rounded.FileUpload, null) },
                                onClick = {
                                    topMenu = false
                                    importLauncher.launch(arrayOf("application/json", "text/plain"))
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("新建提示词") },
                icon = { Icon(Icons.Rounded.Add, null) },
                onClick = {
                    editing = PromptStore.loadDraft(context) ?: PromptItem()
                    creating = true
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                singleLine = true,
                placeholder = { Text("搜索名称、分类、标签、正文或备注") },
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { query = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "清空")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )

            Spacer(Modifier.height(12.dp))

            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                        selected = activeFilter == "全部",
                        onClick = { activeFilter = "全部" },
                        label = { Text("全部") }
                    )
                }
                item {
                    FilterChip(
                        selected = activeFilter == "收藏",
                        onClick = { activeFilter = "收藏" },
                        label = { Text("收藏") },
                        leadingIcon = { Icon(Icons.Rounded.Star, null, modifier = Modifier.size(17.dp)) }
                    )
                }
                items(categories) { category ->
                    FilterChip(
                        selected = activeFilter == category,
                        onClick = { activeFilter = category },
                        label = { Text(category) }
                    )
                }
            }

            Spacer(Modifier.height(6.dp))

            if (visible.isEmpty()) {
                EmptyState(
                    hasAny = allItems.isNotEmpty(),
                    filtering = query.isNotBlank() || activeFilter != "全部",
                    onAdd = {
                        editing = PromptStore.loadDraft(context) ?: PromptItem()
                        creating = true
                    }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 104.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(visible, key = { it.id }) { item ->
                        PromptCard(
                            item = item,
                            onOpen = {
                                editing = item
                                creating = false
                            },
                            onFavorite = {
                                persist(allItems.map {
                                    if (it.id == item.id) {
                                        it.copy(
                                            favorite = !it.favorite,
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    } else it
                                })
                            },
                            onDuplicate = {
                                val copy = item.copy(
                                    id = UUID.randomUUID().toString(),
                                    title = if (item.title.isBlank()) "未命名提示词 - 副本" else item.title + " - 副本",
                                    favorite = false,
                                    createdAt = System.currentTimeMillis(),
                                    updatedAt = System.currentTimeMillis()
                                )
                                persist(listOf(copy) + allItems)
                                Toast.makeText(context, "已复制一份", Toast.LENGTH_SHORT).show()
                            },
                            onDelete = { deleteTarget = item }
                        )
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除这条提示词？") },
            text = { Text(target.title.ifBlank { "未命名提示词" }) },
            confirmButton = {
                TextButton(onClick = {
                    persist(allItems.filterNot { it.id == target.id })
                    deleteTarget = null
                }) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }

    importCandidate?.let { imported ->
        AlertDialog(
            onDismissRequest = { importCandidate = null },
            title = { Text("导入备份") },
            text = {
                Text(
                    "备份中有 " + imported.size + " 条提示词。导入会替换当前 " +
                        allItems.size + " 条内容。"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    persist(imported)
                    importCandidate = null
                    Toast.makeText(context, "已恢复 " + imported.size + " 条", Toast.LENGTH_SHORT).show()
                }) { Text("确认导入") }
            },
            dismissButton = {
                TextButton(onClick = { importCandidate = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyState(hasAny: Boolean, filtering: Boolean, onAdd: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 54.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(66.dp)
                .clip(RoundedCornerShape(21.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (filtering) Icons.Rounded.SearchOff else Icons.Rounded.Description,
                contentDescription = null,
                modifier = Modifier.size(31.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(
            when {
                filtering -> "没有找到匹配的提示词"
                hasAny -> "这里暂时没有内容"
                else -> "把常用提示词都放在这里"
            },
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(7.dp))
        Text(
            if (filtering) "换个关键词或筛选条件试试" else "离线保存，随时搜索、复制和修改",
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (!filtering && !hasAny) {
            Spacer(Modifier.height(20.dp))
            Button(onClick = onAdd) {
                Icon(Icons.Rounded.Add, null)
                Spacer(Modifier.width(6.dp))
                Text("新建第一条")
            }
        }
    }
}

@Composable
private fun PromptCard(
    item: PromptItem,
    onOpen: () -> Unit,
    onFavorite: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.72f))
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        item.title.ifBlank { "未命名提示词" },
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.category.isNotBlank()) {
                        Spacer(Modifier.height(7.dp))
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Text(
                                item.category,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                IconButton(onClick = onFavorite) {
                    Icon(
                        if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (item.favorite) "取消收藏" else "收藏",
                        tint = if (item.favorite) androidx.compose.ui.graphics.Color(0xFFB7831D)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box {
                    IconButton(onClick = { menu = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                            onClick = {
                                menu = false
                                onOpen()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("复制一份") },
                            leadingIcon = { Icon(Icons.Rounded.ContentCopy, null) },
                            onClick = {
                                menu = false
                                onDuplicate()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                            onClick = {
                                menu = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            if (item.tags.isNotEmpty()) {
                Spacer(Modifier.height(9.dp))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    item.tags.forEach { tag ->
                        Text(
                            "#" + tag,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (item.content.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Text(
                    item.content,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                FilledTonalButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(item.content))
                        Toast.makeText(context, "提示词已复制", Toast.LENGTH_SHORT).show()
                    },
                    contentPadding = PaddingValues(horizontal = 14.dp)
                ) {
                    Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("复制提示词")
                }
                Spacer(Modifier.weight(1f))
                Text(
                    formatTime(item.updatedAt),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditorScreen(
    initial: PromptItem,
    isNew: Boolean,
    onBack: () -> Unit,
    onDraft: (PromptItem) -> Unit,
    onSave: (PromptItem) -> Unit
) {
    var title by remember(initial.id) { mutableStateOf(initial.title) }
    var category by remember(initial.id) { mutableStateOf(initial.category) }
    var tags by remember(initial.id) { mutableStateOf(initial.tags.joinToString("，")) }
    var content by remember(initial.id) { mutableStateOf(initial.content) }
    var note by remember(initial.id) { mutableStateOf(initial.note) }

    val draft = remember(title, category, tags, content, note) {
        initial.copy(
            title = title,
            category = category,
            tags = parseTags(tags),
            content = content,
            note = note,
            updatedAt = System.currentTimeMillis()
        )
    }

    LaunchedEffect(draft, isNew) {
        if (isNew) onDraft(draft)
    }

    BackHandler(onBack = onBack)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "新建提示词" else "编辑提示词") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(onClick = { onSave(draft) }) {
                        Text("保存", fontWeight = FontWeight.SemiBold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("名称") },
                    placeholder = { Text("例如：Claude 视觉母版提示词") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    label = { Text("分类") },
                    placeholder = { Text("例如：软件、写作、生图") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    shape = RoundedCornerShape(15.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = tags,
                    onValueChange = { tags = it },
                    label = { Text("标签") },
                    placeholder = { Text("用逗号分开，例如：Claude，视觉，母版") },
                    supportingText = { Text("标签方便以后跨分类搜索") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("完整提示词") },
                    placeholder = { Text("把需要反复使用的完整提示词放在这里……") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 250.dp).testTag("content_input"),
                    minLines = 10,
                    shape = RoundedCornerShape(15.dp)
                )
            }
            item {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注") },
                    placeholder = { Text("用途、版本、注意事项……") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 110.dp),
                    minLines = 4,
                    shape = RoundedCornerShape(15.dp)
                )
            }
            if (isNew) {
                item {
                    Text(
                        "新建内容会自动保留本机草稿，点保存后进入提示词库。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                Button(
                    onClick = { onSave(draft) },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = RoundedCornerShape(15.dp)
                ) {
                    Text("保存提示词")
                }
            }
        }
    }
}

private fun formatTime(time: Long): String {
    val diff = System.currentTimeMillis() - time
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> (diff / 60_000).toString() + " 分钟前"
        diff < 86_400_000 -> (diff / 3_600_000).toString() + " 小时前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(time))
    }
}
