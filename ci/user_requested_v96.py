from pathlib import Path
import re

ui = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = ui.read_text()


def sub(pattern: str, repl: str, name: str, count: int = 1) -> None:
    global s
    s2, n = re.subn(pattern, repl, s, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f'{name}: expected {count}, replaced {n}')
    s = s2


# 1) Search header is one component: back target + gap + search field.
# Remove the synthetic right-side reserve added in v94. That reserve centered the
# pill alone but made the complete header look short and disconnected from the app.
needle = '''                Spacer(Modifier.width(AppTopIconHit + 6.dp))\n'''
if needle not in s:
    raise SystemExit('v94 synthetic right reserve missing; refusing to guess search layout')
s = s.replace(needle, '', 1)

# 2) Short player text is visually centered; overflow keeps the existing native
# one-way marquee behavior. This function is only used for title/artist metadata.
needle = '''        letterSpacing = if (title) .08.sp else .04.sp,\n        maxLines = 1,'''
replacement = '''        letterSpacing = if (title) .08.sp else .04.sp,\n        textAlign = TextAlign.Center,\n        maxLines = 1,'''
if needle not in s:
    raise SystemExit('player marquee typography anchor missing')
s = s.replace(needle, replacement, 1)

# 3) Rebuild the metadata row as one balanced player component:
# left = playback mode, center = title/artist, right = queue.
# Equal 44dp side targets keep the content center on the same visual axis at every
# phone width and preserve the app's existing touch/spacing system.
pattern = r'''            Row\(\n                Modifier\n                    \.fillMaxWidth\(\)\n                    \.padding\(top = if \(compact\) 8\.dp else 12\.dp, bottom = 2\.dp\),\n                verticalAlignment = Alignment\.CenterVertically,\n            \) \{\n                Column\(\n                    Modifier\n                        \.weight\(1f\)\n                        \.padding\(end = 16\.dp\)\n                \) \{.*?\n                \}\n\n                Row\(\n                    Modifier\.width\(100\.dp\),\n                    horizontalArrangement = Arrangement\.SpaceBetween,\n                    verticalAlignment = Alignment\.CenterVertically,\n                \) \{.*?\n                \}\n            \}\n\n            Spacer\(Modifier\.height\(if \(compact\) 5\.dp else 8\.dp\)\)'''
replacement = '''            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = if (compact) 8.dp else 12.dp, bottom = 2.dp)
                    .semantics { contentDescription = "播放器信息区" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                QuietIconButton(
                    onClick = onTogglePlaybackMode,
                    modifier = Modifier
                        .size(AppTopIconHit)
                        .semantics {
                            contentDescription = if (playbackMode == PlaybackMode.SEQUENTIAL) {
                                "顺序播放"
                            } else {
                                "单曲循环"
                            }
                        },
                ) {
                    PlaybackModeGlyph(
                        mode = playbackMode,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .semantics { contentDescription = "歌曲信息中心" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PlayerMarqueeLine(
                        text = track.title,
                        title = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(3.dp))
                    PlayerMarqueeLine(
                        text = track.artist,
                        title = false,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                QuietIconButton(
                    onClick = onQueue,
                    modifier = Modifier
                        .size(AppTopIconHit)
                        .semantics { contentDescription = "播放列表" },
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.QueueMusic,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .78f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }

            Spacer(Modifier.height(if (compact) 5.dp else 8.dp))'''
sub(pattern, replacement, 'balanced centered player metadata')

ui.write_text(s)
