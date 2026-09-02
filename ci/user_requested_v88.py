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


# Exact reference contour from the user's NetEase screenshot.
# The isolated source glyph is 45 x 41 px and contains one connected silhouette.
# All 220 boundary points are retained: no generic repeat icon, ellipse, C-shape,
# or hand-designed approximation is used.
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

            val contour = intArrayOf(
                33,0,33,1,33,2,33,3,32,4,31,4,30,4,29,4,28,4,27,4,26,4,25,4,24,4,23,4,22,4,21,4,20,4,19,4,18,4,17,4,16,4,15,4,14,4,13,4,12,5,11,5,10,5,9,6,8,6,7,7,6,8,5,9,4,10,3,11,2,12,2,13,1,14,1,15,1,16,0,17,0,18,0,19,0,20,0,21,0,22,0,23,0,24,0,25,0,26,0,27,1,28,1,29,2,30,2,31,3,32,3,33,4,34,5,35,6,36,7,37,8,37,9,38,10,38,11,39,12,39,13,39,14,40,15,40,16,40,17,40,18,40,19,40,20,40,21,40,22,40,23,40,24,40,25,40,26,40,27,40,28,40,29,40,30,39,31,39,32,39,33,39,34,38,35,38,36,37,37,37,38,36,39,35,40,34,40,33,41,32,42,31,42,30,43,29,43,28,43,27,44,26,44,25,44,24,44,23,44,22,44,21,43,21,42,21,42,22,42,23,42,24,42,25,41,26,41,27,41,28,40,29,40,30,39,31,38,32,37,33,36,34,35,35,34,36,33,36,32,36,31,37,30,37,29,38,28,38,27,38,26,38,25,38,24,38,23,38,22,38,21,38,20,38,19,38,18,38,17,38,16,38,15,37,14,37,13,37,12,37,11,36,10,36,9,35,8,34,7,34,6,33,6,32,5,31,4,30,4,29,3,28,3,27,3,26,2,25,2,24,2,23,2,22,2,21,2,20,2,19,2,18,3,17,3,16,4,15,4,14,5,13,5,12,6,11,7,10,8,9,9,9,10,8,11,7,12,7,13,7,14,6,15,6,16,6,17,6,18,6,19,6,20,6,21,6,22,6,23,6,24,6,25,6,26,6,27,6,28,6,29,6,30,6,31,6,32,6,33,7,33,8,33,9,33,10,34,10,35,10,36,9,37,8,38,8,39,7,40,7,41,6,41,5,41,4,40,3,39,3,38,2,37,2,36,1,35,1,34,0
            )

            fun px(x: Int) = left + vw * (x / 44f)
            fun py(y: Int) = top + vh * (y / 40f)

            val traced = androidx.compose.ui.graphics.Path().apply {
                moveTo(px(contour[0]), py(contour[1]))
                var i = 2
                while (i < contour.size) {
                    lineTo(px(contour[i]), py(contour[i + 1]))
                    i += 2
                }
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
    'full NetEase pixel-contour playback-mode glyph',
)

ui.write_text(s)
