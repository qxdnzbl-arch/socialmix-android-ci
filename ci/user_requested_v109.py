from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()

# v109 fixes ONLY the user-reported vinyl outer-ring regression.
# The previous stage drew a 10dp-wide translucent annulus (+20dp diameter)
# plus an outer border. On a real phone that reads as two gray arc/band layers.
# Keep the vinyl itself exactly the same size and position; replace the filled
# annulus with one thin, centered outline sitting immediately outside the disc.
old = '''                    Box(
                        Modifier
                            .size(discSize + 20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = .035f))
                            .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
                            .semantics { contentDescription = "黑胶外框" }
                    )
'''
new = '''                    Box(
                        Modifier
                            .size(discSize + 2.dp)
                            .clip(CircleShape)
                            .border(1.dp, Color.White.copy(alpha = .10f), CircleShape)
                            .semantics { contentDescription = "黑胶外框" }
                    )
'''
if old not in s:
    raise SystemExit('v109 vinyl halo anchor missing; refusing to guess')
s = s.replace(old, new, 1)

# Hard guards: no broad translucent annulus may survive, and the vinyl itself
# must remain at the previously approved discSize.
if '.size(discSize + 20.dp)' in s:
    raise SystemExit('v109 broad outer annulus still present')
if '.background(Color.White.copy(alpha = .035f))' in s:
    raise SystemExit('v109 filled gray halo still present')
if '.size(discSize + 2.dp)' not in s or 'contentDescription = "黑胶外框"' not in s:
    raise SystemExit('v109 single outer ring guard failed')
if 'VinylDisc(track, rotation.value, Modifier.size(discSize))' not in s:
    raise SystemExit('v109 vinyl size/position regression detected')

path.write_text(s)
