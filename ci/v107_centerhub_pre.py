from pathlib import Path
import re

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()

start = s.find('@Composable\nprivate fun VinylDisc(')
if start < 0:
    raise SystemExit('VinylDisc start missing')
next_markers = [
    s.find('@Composable\nprivate fun NetEaseQueueGlyph', start),
    s.find('@Composable\nprivate fun ToneArm', start),
]
ends = [x for x in next_markers if x > start]
if not ends:
    raise SystemExit('VinylDisc end marker missing')
end = min(ends)
vinyl = s[start:end]

# The current branch has two empty decorative Box layers at the very end of
# VinylDisc (white spindle disc + dark center dot), but their numeric sizes have
# changed across prior visual passes. Normalize ONLY those final two empty boxes
# so v107 can remove them deterministically without touching the cover container.
pattern = re.compile(
    r'''\n        Box\(\n            Modifier\n.*?        \)\n        Box\(\n            Modifier\n.*?        \)\n(?=    \}\n\}\n?$)''',
    re.S,
)
replacement = '''\n        Box(\n            Modifier\n                .size(13.dp)\n                .clip(CircleShape)\n                .background(Color(0xFFE8E7E2).copy(alpha = .96f))\n        )\n        Box(\n            Modifier\n                .size(4.4.dp)\n                .clip(CircleShape)\n                .background(Color(0xFF74756F))\n        )\n'''
vinyl2, n = pattern.subn(replacement, vinyl, count=1)
if n != 1:
    raise SystemExit('legacy vinyl hub pair not found at VinylDisc tail')

s = s[:start] + vinyl2 + s[end:]
path.write_text(s)
