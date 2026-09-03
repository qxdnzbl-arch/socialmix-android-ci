from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

# Restore the pre-v95 tone-arm placement exactly: the vinyl remains centered and
# responsive, while the tone arm is anchored to the top-right of the full player
# stage. v95 wrapped both inside a smaller centered inner stage, which pulled the
# tone arm downward and inward. That changed an already-approved visual.
old = '''            BoxWithConstraints(
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
new = '''            Box(
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
'''
if old not in text:
    raise SystemExit('v95 responsive tone-arm stage not found; refusing to patch guessed geometry')
text = text.replace(old, new, 1)
path.write_text(text)
