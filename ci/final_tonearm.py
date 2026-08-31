from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()
old = 'targetValue = if (onDisc) 0f else -17f'
new = 'targetValue = if (onDisc) 28f else -17f'
if old not in s:
    raise SystemExit('tone-arm angle source missing')
path.write_text(s.replace(old, new, 1))
