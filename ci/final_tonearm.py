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
s = s.replace(old, new, 1)

old_call = '                    onDisc = needleOnDisc,'
new_call = '                    onDisc = isPlaying,'
if old_call not in s:
    raise SystemExit('tone-arm home binding source missing')
s = s.replace(old_call, new_call, 1)

path.write_text(s)
