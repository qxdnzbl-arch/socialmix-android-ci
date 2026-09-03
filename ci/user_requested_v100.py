from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

# v99 restores the exact approved tone-arm placement on <=411dp phones. On very
# wide phones the rotated drawing can touch the physical right edge even though its
# layout box is technically on-screen. Add only a small CONTINUOUS extra end inset
# after 411dp, so the approved phone geometry is unchanged and wider devices gain
# enough optical breathing room without a breakpoint jump.
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

old = '''                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 2.dp),
'''
new = '''                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = toneArmEndInset),
'''
if old not in text:
    raise SystemExit('restored tone-arm placement anchor not found; refusing to guess')
text = text.replace(old, new, 1)

path.write_text(text)
