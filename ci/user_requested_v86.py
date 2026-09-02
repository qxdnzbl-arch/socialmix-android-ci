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


# Playback-mode glyph geometry traced from the clean NetEase reference screenshot.
# Reference silhouette measurements (normalized from the screenshot crop):
# - open elliptical loop, width:height ~= 1.225
# - arc starts at the right midpoint and sweeps clockwise ~299 degrees
# - opening stays on the upper-right/right edge
# - arrowhead is a short, horizontal right-pointing triangle attached to the arc end
# This intentionally replaces the previous hand-designed rounded-rectangle/C shapes.
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Color.White.copy(alpha = .68f)
            val stroke = 1.45.dp.toPx()

            // Visual bounds are intentionally slightly wider than tall, matching
            // the NetEase screenshot rather than a circular C.
            val left = 1.8.dp.toPx()
            val right = size.width - 1.8.dp.toPx()
            val top = 3.65.dp.toPx()
            val bottom = size.height - 3.65.dp.toPx()
            val ovalW = right - left
            val ovalH = bottom - top

            drawArc(
                color = c,
                startAngle = 0f,
                sweepAngle = 299f,
                useCenter = false,
                topLeft = Offset(left, top),
                size = androidx.compose.ui.geometry.Size(ovalW, ovalH),
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )

            // The reference arrow is horizontal even though the ellipse tangent
            // is diagonal. Keep that exact visual cue: vertical base + right tip.
            val theta = Math.toRadians(299.0)
            val cx = (left + right) / 2f
            val cy = (top + bottom) / 2f
            val rx = ovalW / 2f
            val ry = ovalH / 2f
            val base = Offset(
                x = cx + rx * kotlin.math.cos(theta).toFloat(),
                y = cy + ry * kotlin.math.sin(theta).toFloat(),
            )
            val arrowLength = 4.45.dp.toPx()
            val halfArrowHeight = 2.05.dp.toPx()
            val head = androidx.compose.ui.graphics.Path().apply {
                moveTo(base.x + arrowLength, base.y)
                lineTo(base.x, base.y - halfArrowHeight)
                lineTo(base.x, base.y + halfArrowHeight)
                close()
            }
            drawPath(head, c)
        }

        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .68f),
                fontSize = 6.9.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

''',
    'NetEase screenshot-locked playback-mode glyph',
)

ui.write_text(s)
