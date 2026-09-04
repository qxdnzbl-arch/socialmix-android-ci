from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()

start_anchor = '''                IconButton(\n                    onClick = onPlayPause,'''
next_anchor = '''                IconButton(\n                    onClick = onNext,'''
start = s.find(start_anchor)
if start < 0:
    raise SystemExit('play/pause IconButton start missing')
end = s.find(next_anchor, start)
if end < 0:
    raise SystemExit('next IconButton marker missing')

canonical = '''                IconButton(\n                    onClick = onPlayPause,\n                    modifier = Modifier\n                        .size(61.dp)\n                        .semantics { contentDescription = if (isPlaying) "暂停" else "播放" },\n                ) {\n                    Icon(\n                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,\n                        contentDescription = null,\n                        tint = Color.White.copy(alpha = .92f),\n                        modifier = Modifier.size(if (isPlaying) 48.dp else 51.dp),\n                    )\n                }\n'''

s = s[:start] + canonical + s[end:]
path.write_text(s)
