from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

# Keep the already-approved compact/common/standard phone geometry unchanged.
# Only wider portrait phones switch to a proportional stage. This prevents the
# 274dp vinyl cap and a full-width TopEnd tone-arm anchor from making wide phones
# look stretched while keeping one deterministic responsive rule.
old = '''        val compact = maxHeight < 690.dp
        val discSize = (maxWidth * .755f).coerceAtMost(if (compact) 250.dp else 274.dp)
'''
new = '''        val compact = maxHeight < 690.dp
        val widePlayer = maxWidth > 430.dp
        val discSize = if (widePlayer) {
            (maxWidth * .62f).coerceIn(274.dp, 360.dp)
        } else {
            (maxWidth * .755f).coerceAtMost(if (compact) 250.dp else 274.dp)
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
new = '''            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                if (widePlayer) {
                    // Wide phones keep the vinyl and tone arm as one visual object.
                    // The tone arm is anchored to this stage, never to the phone edge.
                    Box(
                        Modifier.size(
                            width = discSize * 1.29f,
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
                } else {
                    // Preserve approved compact/common/standard phone geometry exactly.
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
