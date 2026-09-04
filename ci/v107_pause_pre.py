from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()

old = '''                    CenterPlaybackGlyph(isPlaying = isPlaying, modifier = Modifier.size(54.dp))'''
new = '''                    Icon(
                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = .92f),
                        modifier = Modifier.size(if (isPlaying) 48.dp else 51.dp),
                    )'''
if old not in s:
    raise SystemExit('transformed CenterPlaybackGlyph anchor missing; refusing to guess')
s = s.replace(old, new, 1)
path.write_text(s)
