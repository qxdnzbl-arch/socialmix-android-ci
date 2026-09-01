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


# Reference-locked playback-mode icon pass.
# Geometry is traced from the user's NetEase screenshot rather than redesigned:
# - rounded open loop with flatter top and bottom runs
# - right-side vertical opening
# - horizontal top shaft ending in one filled right-pointing arrowhead
# - no C-shaped circle, no fork, no detached tail
# - single-loop state keeps the exact same outline and only adds a small centered 1
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(2.0.dp)) {
            val c = Color.White.copy(alpha = .68f)
            val stroke = 1.62.dp.toPx()
            val w = size.width
            val h = size.height

            // Trace of the NetEase reference: an open rounded loop with a
            // horizontal top run and a separate lower-right end. This avoids
            // the round "C" silhouette from the previous build.
            val loop = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .88f, h * .52f)
                cubicTo(
                    w * .88f, h * .73f,
                    w * .76f, h * .91f,
                    w * .57f, h * .91f,
                )
                lineTo(w * .31f, h * .91f)
                cubicTo(
                    w * .13f, h * .91f,
                    w * .06f, h * .76f,
                    w * .08f, h * .58f,
                )
                cubicTo(
                    w * .10f, h * .36f,
                    w * .23f, h * .20f,
                    w * .40f, h * .20f,
                )
                lineTo(w * .72f, h * .20f)
            }
            drawPath(
                path = loop,
                color = c,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // One integrated NetEase-style right-facing arrowhead.
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(w * .97f, h * .20f)
                lineTo(w * .72f, h * .055f)
                lineTo(w * .72f, h * .345f)
                close()
            }
            drawPath(head, c)
        }

        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .68f),
                fontSize = 7.0.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

''',
    'NetEase reference traced playback-mode glyph',
)

ui.write_text(s)
