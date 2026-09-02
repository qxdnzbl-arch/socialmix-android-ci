from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

# One continuous responsive rule across portrait phone widths. Standard phones keep
# the approved size, then the disc grows smoothly instead of jumping at a breakpoint.
old = '''        val compact = maxHeight < 690.dp
        val discSize = (maxWidth * .755f).coerceAtMost(if (compact) 250.dp else 274.dp)
'''
new = '''        val compact = maxHeight < 690.dp
        val standardDisc = (maxWidth * .755f).coerceAtMost(if (compact) 250.dp else 274.dp)
        val discSize = if (maxWidth <= 411.dp) {
            standardDisc
        } else {
            (274.dp + (maxWidth - 411.dp) * .45f).coerceAtMost(360.dp)
        }
'''
if old not in text:
    raise SystemExit('Home disc-size source not found; refusing to patch guessed geometry')
text = text.replace(old, new, 1)

old = '''            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(discSize + 20.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = .035f))
                        .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
                )
                VinylDisc(track, rotation.value, Modifier.size(discSize))
                ToneArm(
                    onDisc = isPlaying,
                    modifier = Modifier
                        .size(width = discSize * .59f, height = discSize * .47f)
                        .align(Alignment.TopEnd)
                        .padding(top = 9.dp, end = 2.dp),
                )
            }
'''
new = '''            BoxWithConstraints(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                // Vinyl + tone arm are always one centered responsive object. On narrow
                // phones the stage contracts to the available content width; on wider
                // phones it expands proportionally. No device-specific right-edge anchor.
                val playerStageWidth = minOf(maxWidth, discSize * 1.29f)
                Box(
                    Modifier.size(
                        width = playerStageWidth,
                        height = discSize * 1.13f,
                    ),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier
                            .size(discSize + 20.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = .035f))
                            .border(1.dp, Color.White.copy(alpha = .08f), CircleShape)
                            .semantics { contentDescription = "黑胶外框" }
                    )
                    VinylDisc(track, rotation.value, Modifier.size(discSize))
                    ToneArm(
                        onDisc = isPlaying,
                        modifier = Modifier
                            .size(width = discSize * .59f, height = discSize * .47f)
                            .align(Alignment.TopEnd)
                            .padding(top = 9.dp, end = 2.dp),
                    )
                }
            }
'''
if old not in text:
    raise SystemExit('Home vinyl/tone-arm stage not found; refusing to patch guessed layout')
text = text.replace(old, new, 1)

path.write_text(text)
