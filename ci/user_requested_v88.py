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


# Final NetEase screenshot trace.
# Coordinates below follow the centerline measured from the user's clean NetEase
# screenshot crop rather than a generic repeat icon. Reference pixel centerline:
# lower-right open end ~ (57,44), bottom run y~59, left side x~16,
# top run y~26 from x~28 to x~50, arrow tip ~ (59,26).
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Color.White.copy(alpha = .72f)
            val vw = 22.0.dp.toPx()
            val vh = 20.0.dp.toPx()
            val left = (size.width - vw) / 2f
            val top = (size.height - vh) / 2f
            fun p(x: Float, y: Float) = Offset(left + vw * x, top + vh * y)

            val loop = androidx.compose.ui.graphics.Path().apply {
                val a = p(.956f, .600f)
                moveTo(a.x, a.y)

                var q = p(.956f, .825f)
                var r = p(.820f, .975f)
                var t = p(.667f, .975f)
                cubicTo(q.x, q.y, r.x, r.y, t.x, t.y)

                t = p(.311f, .975f)
                lineTo(t.x, t.y)

                q = p(.160f, .975f)
                r = p(.044f, .820f)
                t = p(.044f, .675f)
                cubicTo(q.x, q.y, r.x, r.y, t.x, t.y)

                q = p(.000f, .550f)
                r = p(.044f, .350f)
                t = p(.156f, .250f)
                cubicTo(q.x, q.y, r.x, r.y, t.x, t.y)

                q = p(.200f, .190f)
                r = p(.260f, .150f)
                t = p(.311f, .150f)
                cubicTo(q.x, q.y, r.x, r.y, t.x, t.y)

                t = p(.800f, .150f)
                lineTo(t.x, t.y)
            }
            drawPath(
                path = loop,
                color = c,
                style = Stroke(
                    width = 1.52.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )

            // Screenshot-matched short horizontal arrowhead: one clean triangle,
            // no fork, no detached tail, no circular-C silhouette.
            val tip = p(1.000f, .150f)
            val upper = p(.800f, .025f)
            val lower = p(.800f, .275f)
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(tip.x, tip.y)
                lineTo(upper.x, upper.y)
                lineTo(lower.x, lower.y)
                close()
            }
            drawPath(head, c)
        }

        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .72f),
                fontSize = 7.0.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

''',
    'smooth NetEase screenshot-traced playback-mode glyph',
)

ui.write_text(s)
