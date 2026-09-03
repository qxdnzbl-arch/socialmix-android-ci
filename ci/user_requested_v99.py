from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
text = path.read_text()

# Preserve the responsive vinyl stage from v95. Only restore the tone arm's approved
# vertical relationship. On the user's own before/after screenshots the vinyl is in
# the same place and size; the tone arm alone moved down by 92 px. Relative to the
# visible disc that is ~17.7%, so use discSize as the anchor instead of a fixed pixel
# or the old full-height stage. This keeps the same visual relationship on all phones.
import_anchor = 'import androidx.compose.foundation.layout.navigationBars\n'
import_line = 'import androidx.compose.foundation.layout.offset\n'
if import_line not in text:
    if import_anchor not in text:
        raise SystemExit('offset import anchor missing')
    text = text.replace(import_anchor, import_anchor + import_line, 1)

old = '''                    ToneArm(
                        onDisc = isPlaying,
                        modifier = Modifier
                            .size(width = discSize * .59f, height = discSize * .47f)
                            .align(Alignment.TopEnd)
                            .padding(top = 9.dp, end = 2.dp),
                    )'''
new = '''                    ToneArm(
                        onDisc = isPlaying,
                        modifier = Modifier
                            .size(width = discSize * .59f, height = discSize * .47f)
                            .align(Alignment.TopEnd)
                            .offset(y = -(discSize * .177f))
                            .padding(top = 9.dp, end = 2.dp),
                    )'''
if old not in text:
    raise SystemExit('v95 responsive tone-arm anchor not found; refusing to patch guessed geometry')
text = text.replace(old, new, 1)

path.write_text(text)
