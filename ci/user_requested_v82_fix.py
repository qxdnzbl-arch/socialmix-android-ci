from pathlib import Path

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()

s = s.replace('import androidx.compose.foundation.layout.matchParentSize\n', '', 1)
s = s.replace('.matchParentSize()', '.fillMaxWidth().height(52.dp)', 1)

queue_parent = '''        Column(
            Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = blankTapSource,
                    indication = null,
                    onClick = { dismissSignal += 1 },
                )
                .padding(horizontal = 18.dp)
        ) {'''
queue_safe = '''        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
        ) {'''
if queue_parent not in s:
    raise SystemExit('queue parent gesture block missing')
s = s.replace(queue_parent, queue_safe, 1)

library_parent = '''                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = blankTapSource,
                        indication = null,
                        onClick = { dismissSignal += 1 },
                    ),'''
library_safe = '''                Modifier
                    .weight(1f)
                    .fillMaxWidth(),'''
if library_parent not in s:
    raise SystemExit('library parent gesture block missing')
s = s.replace(library_parent, library_safe, 1)

path.write_text(s)
