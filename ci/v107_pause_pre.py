from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()
key = 'contentDescription = if (isPlaying) "暂停" else "播放"'
i = s.find(key)
if i < 0:
    key = 'onPlayPause'
    i = s.find(key)
if i < 0:
    raise SystemExit('play/pause marker missing')
print('V107_PAUSE_CONTEXT_START')
print(s[max(0, i-1400):i+1800])
print('V107_PAUSE_CONTEXT_END')
raise SystemExit('debug pause context captured')
