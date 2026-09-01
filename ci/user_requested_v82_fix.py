from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()

s = s.replace('import androidx.compose.foundation.layout.matchParentSize\n', '', 1)
s = s.replace('.matchParentSize()', '.fillMaxWidth().height(52.dp)', 1)

path.write_text(s)
