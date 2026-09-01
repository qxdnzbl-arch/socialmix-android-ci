from pathlib import Path
import re

ui = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = ui.read_text()


def sub(pattern: str, repl: str, name: str, count: int = 1) -> None:
    global s
    s2, n = re.subn(pattern, repl, s, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f'{name}: expected {count}, replaced {n}')
    s = s2


# Final playback-mode icon pass based on the user's NetEase reference:
# - not a round C
# - visibly flatter and wider than it is tall
# - one continuous rounded stroke
# - one clear filled arrowhead integrated at the upper-right opening
# - no forked/crossed short strokes
# - single-loop reuses the same geometry and only adds a small centered "1"
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 1.4.dp, vertical = 4.0.dp)) {
            val c = Color.White.copy(alpha = .78f)
            val stroke = 1.72.dp.toPx()
            val w = size.width
            val h = size.height

            val loop = androidx.compose.ui.graphics.Path().apply {
                // A horizontally stretched open loop, not a circular C.
                moveTo(w * .76f, h * .73f)
                cubicTo(
                    w * .58f, h * .90f,
                    w * .28f, h * .88f,
                    w * .15f, h * .64f,
                )
                cubicTo(
                    w * .04f, h * .42f,
                    w * .20f, h * .17f,
                    w * .46f, h * .17f,
                )
                cubicTo(
                    w * .64f, h * .17f,
                    w * .78f, h * .23f,
                    w * .84f, h * .34f,
                )
            }
            drawPath(
                path = loop,
                color = c,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // Clear compact arrowhead. It reads as an arrow at 24dp instead of
            // collapsing into a tiny tail or fork.
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .98f, h * .34f)
                lineTo(w * .79f, h * .23f)
                lineTo(w * .81f, h * .46f)
                close()
            }
            drawPath(head, c)
        }

        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .78f),
                fontSize = 7.3.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

''',
    'flattened professional playback-mode glyph',
)

ui.write_text(s)
