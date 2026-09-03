from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

# Keep the user's restored tone-arm relation unchanged on normal phones. On wider
# phones, add only a small continuous end inset so the rotated stylus never touches
# the physical edge. No breakpoint jump and no vertical repositioning.
old = '''        val discSize = if (maxWidth <= 411.dp) {
            standardDisc
        } else {
            (274.dp + (maxWidth - 411.dp) * .45f).coerceAtMost(360.dp)
        }
'''
new = '''        val discSize = if (maxWidth <= 411.dp) {
            standardDisc
        } else {
            (274.dp + (maxWidth - 411.dp) * .45f).coerceAtMost(360.dp)
        }
        val toneArmEndInset = (
            2.dp + (maxWidth - 411.dp).coerceAtLeast(0.dp) * .026f
        ).coerceAtMost(7.dp)
'''
if old not in text:
    raise SystemExit('responsive disc sizing anchor not found; refusing to guess')
text = text.replace(old, new, 1)

old = '''                            .align(Alignment.TopEnd)
                            .offset(y = -(discSize * .177f))
                            .padding(top = 9.dp, end = 2.dp),
'''
new = '''                            .align(Alignment.TopEnd)
                            .offset(y = -(discSize * .177f))
                            .padding(top = 9.dp, end = toneArmEndInset),
'''
if old not in text:
    raise SystemExit('restored tone-arm placement anchor not found; refusing to guess')
text = text.replace(old, new, 1)

path.write_text(text)
