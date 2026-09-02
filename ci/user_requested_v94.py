from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

old = '''            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 14.dp, top = 8.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.semantics { contentDescription = "返回" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        tint = MainText,
                        modifier = Modifier.size(24.dp),
                    )
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "搜索歌曲或歌手",
                            color = Color(0xFF999B96),
                            fontSize = 14.5.sp,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .semantics { contentDescription = "搜索输入框" },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(0xFF7C7E79),
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = .62f),
                        unfocusedContainerColor = Color.White.copy(alpha = .62f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MainText,
                    ),
                )
            }
'''

new = '''            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.size(48.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .size(48.dp)
                            .semantics { contentDescription = "返回" },
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = MainText,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "搜索歌曲或歌手",
                            color = Color(0xFF999B96),
                            fontSize = 14.5.sp,
                        )
                    },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(26.dp))
                        .semantics { contentDescription = "搜索输入框" },
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = Color(0xFF7C7E79),
                            modifier = Modifier.size(22.dp),
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = .62f),
                        unfocusedContainerColor = Color.White.copy(alpha = .62f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = MainText,
                    ),
                )
                Spacer(Modifier.width(48.dp))
            }
'''

if old not in text:
    raise SystemExit('Search header target block not found; refusing to patch a guessed layout')

text = text.replace(old, new, 1)
path.write_text(text)
