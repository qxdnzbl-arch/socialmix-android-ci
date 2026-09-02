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


# Reference-locked silhouette from the user's NetEase screenshot.
# Source silhouette is 45 x 41 pixels. These measured contour points reproduce
# that shape and aspect ratio instead of substituting a generic repeat icon.
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Color.White.copy(alpha = .68f)
            val vw = 18.5.dp.toPx()
            val vh = (18.5f * 41f / 45f).dp.toPx()
            val left = (size.width - vw) / 2f
            val top = (size.height - vh) / 2f

            fun px(x: Int) = left + vw * (x / 44f)
            fun py(y: Int) = top + vh * (y / 40f)

            val traced = androidx.compose.ui.graphics.Path().apply {
                moveTo(px(41), py(4))
                lineTo(px(33), py(0))
                lineTo(px(33), py(3))
                lineTo(px(32), py(4))
                lineTo(px(14), py(4))
                lineTo(px(13), py(5))
                lineTo(px(11), py(5))
                lineTo(px(7), py(7))
                lineTo(px(3), py(11))
                lineTo(px(0), py(17))
                lineTo(px(0), py(26))
                lineTo(px(1), py(27))
                lineTo(px(1), py(29))
                lineTo(px(3), py(33))
                lineTo(px(7), py(37))
                lineTo(px(11), py(39))
                lineTo(px(14), py(39))
                lineTo(px(15), py(40))
                lineTo(px(29), py(40))
                lineTo(px(30), py(39))
                lineTo(px(32), py(39))
                lineTo(px(36), py(37))
                lineTo(px(41), py(32))
                lineTo(px(43), py(28))
                lineTo(px(43), py(26))
                lineTo(px(44), py(25))
                lineTo(px(44), py(21))
                lineTo(px(42), py(21))
                lineTo(px(42), py(25))
                lineTo(px(41), py(26))
                lineTo(px(40), py(30))
                lineTo(px(34), py(36))
                lineTo(px(30), py(37))
                lineTo(px(29), py(38))
                lineTo(px(15), py(38))
                lineTo(px(7), py(34))
                lineTo(px(3), py(29))
                lineTo(px(3), py(27))
                lineTo(px(2), py(26))
                lineTo(px(2), py(18))
                lineTo(px(5), py(12))
                lineTo(px(9), py(8))
                lineTo(px(11), py(7))
                lineTo(px(13), py(7))
                lineTo(px(14), py(6))
                lineTo(px(32), py(6))
                lineTo(px(33), py(7))
                lineTo(px(33), py(10))
                lineTo(px(34), py(10))
                lineTo(px(38), py(7))
                lineTo(px(41), py(6))
                close()
            }
            drawPath(traced, c)
        }

        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .68f),
                fontSize = 6.8.sp,
                fontWeight = FontWeight.Normal,
            )
        }
    }
}

''',
    'NetEase pixel-contour playback-mode glyph',
)

ui.write_text(s)
