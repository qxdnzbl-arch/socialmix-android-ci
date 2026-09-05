from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()

# v110 corrects the v109 misunderstanding:
# - restore the approved broad translucent OUTER halo exactly as it was in v108;
# - remove only the two added highlight arcs INSIDE the black vinyl surface.
# Do not change disc size, artwork, grooves, tonearm, controls, or layout.

# 1) Restore the original outer halo that v109 incorrectly collapsed.
old_halo = '''                    Box(
                        Modifier
                            .size(discSize + 2.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = .10f), CircleShape)
                            .semantics { contentDescription = "黑胶外框" }
                    )
'''
new_halo = '''                    Box(
                        Modifier
                            .size(discSize + 20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = .035f))
                            .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
                            .semantics { contentDescription = "黑胶外框" }
                    )
'''
if old_halo not in s:
    raise SystemExit('v110 outer-halo anchor missing; refusing to guess')
s = s.replace(old_halo, new_halo, 1)

# 2) Remove only the two decorative highlight arcs inside VinylDisc.
start = s.find('@Composable\nprivate fun VinylDisc(')
end = s.find('@Composable\nprivate fun NetEaseQueueGlyph', start)
if start < 0 or end < 0:
    raise SystemExit('v110 VinylDisc boundaries missing')
vinyl = s[start:end]

arc1 = '''            drawArc(
                color = Color.White.copy(alpha = .030f),
                startAngle = 205f,
                sweepAngle = 78f,
                useCenter = false,
                style = Stroke(width = 10.dp.toPx(), cap = StrokeCap.Round),
            )
'''
arc2 = '''            drawArc(
                color = Color.White.copy(alpha = .018f),
                startAngle = 32f,
                sweepAngle = 62f,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
'''
if vinyl.count(arc1) != 1 or vinyl.count(arc2) != 1:
    raise SystemExit('v110 internal highlight arc anchors missing; refusing to guess')
vinyl = vinyl.replace(arc1, '', 1).replace(arc2, '', 1)
s = s[:start] + vinyl + s[end:]

# Hard guards: the outer halo is restored, the two internal highlight arcs are gone,
# while the approved vinyl size, artwork, and groove system remain intact.
if '.size(discSize + 20.dp)' not in s:
    raise SystemExit('v110 outer halo was not restored')
if '.background(Color.White.copy(alpha = .035f))' not in s:
    raise SystemExit('v110 outer translucent halo missing')
if '.size(discSize + 2.dp)' in s:
    raise SystemExit('v110 v109 thin-halo regression still present')
vinyl = s[s.index('@Composable\nprivate fun VinylDisc'):s.index('@Composable\nprivate fun NetEaseQueueGlyph')]
if 'startAngle = 205f' in vinyl or 'startAngle = 32f' in vinyl:
    raise SystemExit('v110 internal highlight arcs still present')
if 'repeat(48)' not in vinyl or 'rememberCoverBitmap(track)' not in vinyl or 'Image(' not in vinyl:
    raise SystemExit('v110 vinyl grooves/artwork regression detected')
if 'VinylDisc(track, rotation.value, Modifier.size(discSize))' not in s:
    raise SystemExit('v110 vinyl size/position regression detected')

path.write_text(s)
