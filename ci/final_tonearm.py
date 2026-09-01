from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()
old = '''    val angle by animateFloatAsState(
        targetValue = if (onDisc) 0f else -17f,
        animationSpec = tween(430),
        label = "toneArm",
    )'''
new = '''    val angle = if (onDisc) 56f else -17f'''
if old not in s:
    raise SystemExit('tone-arm animation source missing')
path.write_text(s.replace(old, new, 1))
