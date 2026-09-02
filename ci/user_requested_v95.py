from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

# Keep the already-approved compact/common/standard phone geometry unchanged.
# Only wider portrait phones switch to a proportional stage. This prevents the
# 270dp vinyl cap and a full-width TopEnd tone-arm anchor from making wide phones
# look like a stretched/cheap copy while keeping one deterministic responsive rule.
old = '''        val compact = maxHeight < 690.dp
        val discSize = (maxWidth * .745f).coerceAtMost(if (compact) 248.dp else 270.dp)
'''
new = '''        val compact = maxHeight < 690.dp
        val widePlayer = maxWidth > 430.dp
        val discSize = if (widePlayer) {
            (maxWidth * .62f).coerceIn(270.dp, 360.dp)
        } else {
            (maxWidth * .745f).coerceAtMost(if (compact) 248.dp else 270.dp)
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
                    onDisc = needleOnDisc,
                    modifier = Modifier
                        .size(width = discSize * .59f, height = discSize * .47f)
                        .align(Alignment.TopEnd)
                        .padding(top = 1.dp, end = 2.dp),
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
                    // On wide phones the vinyl and tone arm are one responsive visual
                    // object. The tone arm is anchored to this object, never to the
                    // phone's right edge, so changing devices cannot pull it away.
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
                            onDisc = needleOnDisc,
                            modifier = Modifier
                                .size(width = discSize * .59f, height = discSize * .47f)
                                .align(Alignment.TopEnd)
                                .padding(top = 1.dp, end = 2.dp),
                        )
                    }
                } else {
                    // Preserve the approved geometry on compact/common/standard phones.
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
                        onDisc = needleOnDisc,
                        modifier = Modifier
                            .size(width = discSize * .59f, height = discSize * .47f)
                            .align(Alignment.TopEnd)
                            .padding(top = 1.dp, end = 2.dp),
                    )
                }
            }
'''
if old not in text:
    raise SystemExit('Home vinyl/tone-arm stage not found; refusing to patch guessed layout')
text = text.replace(old, new, 1)

path.write_text(text)
