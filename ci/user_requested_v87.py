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


# Final reference-traced playback mode icon.
# This is not a redesigned repeat icon: the filled silhouette below is vectorized
# directly from the clean NetEase screenshot supplied by the user. The outline
# preserves the screenshot's exact open-loop proportions, stroke body and short
# horizontal arrowhead instead of approximating them with a generic C/ellipse.
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    '''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Color.White.copy(alpha = .68f)

            // Match the NetEase icon's visual footprint to the adjacent queue icon.
            val vw = 18.5.dp.toPx()
            val vh = 16.5.dp.toPx()
            val left = (size.width - vw) / 2f
            val top = (size.height - vh) / 2f

            val traced = androidx.compose.ui.graphics.Path().apply {
                moveTo(left + vw * 0.911111f, top + vh * 0.075000f)
                lineTo(left + vw * 0.844444f, top + vh * 0.050000f)
                lineTo(left + vw * 0.800000f, top + vh * 0.000000f)
                lineTo(left + vw * 0.755556f, top + vh * 0.000000f)
                lineTo(left + vw * 0.711111f, top + vh * 0.100000f)
                lineTo(left + vw * 0.288889f, top + vh * 0.100000f)
                lineTo(left + vw * 0.133333f, top + vh * 0.200000f)
                lineTo(left + vw * 0.088889f, top + vh * 0.250000f)
                lineTo(left + vw * 0.022222f, top + vh * 0.400000f)
                lineTo(left + vw * 0.022222f, top + vh * 0.500000f)
                lineTo(left + vw * 0.000000f, top + vh * 0.525000f)
                lineTo(left + vw * 0.022222f, top + vh * 0.575000f)
                lineTo(left + vw * 0.022222f, top + vh * 0.675000f)
                lineTo(left + vw * 0.044444f, top + vh * 0.700000f)
                lineTo(left + vw * 0.066667f, top + vh * 0.800000f)
                lineTo(left + vw * 0.177778f, top + vh * 0.925000f)
                lineTo(left + vw * 0.200000f, top + vh * 0.925000f)
                lineTo(left + vw * 0.244444f, top + vh * 0.975000f)
                lineTo(left + vw * 0.311111f, top + vh * 0.975000f)
                lineTo(left + vw * 0.333333f, top + vh * 1.000000f)
                lineTo(left + vw * 0.688889f, top + vh * 1.000000f)
                lineTo(left + vw * 0.711111f, top + vh * 0.975000f)
                lineTo(left + vw * 0.777778f, top + vh * 0.975000f)
                lineTo(left + vw * 0.822222f, top + vh * 0.925000f)
                lineTo(left + vw * 0.844444f, top + vh * 0.925000f)
                lineTo(left + vw * 0.933333f, top + vh * 0.825000f)
                lineTo(left + vw * 1.000000f, top + vh * 0.675000f)
                lineTo(left + vw * 1.000000f, top + vh * 0.525000f)
                lineTo(left + vw * 0.955556f, top + vh * 0.525000f)
                lineTo(left + vw * 0.933333f, top + vh * 0.675000f)
                lineTo(left + vw * 0.888889f, top + vh * 0.775000f)
                lineTo(left + vw * 0.800000f, top + vh * 0.875000f)
                lineTo(left + vw * 0.711111f, top + vh * 0.925000f)
                lineTo(left + vw * 0.311111f, top + vh * 0.925000f)
                lineTo(left + vw * 0.288889f, top + vh * 0.900000f)
                lineTo(left + vw * 0.244444f, top + vh * 0.900000f)
                lineTo(left + vw * 0.088889f, top + vh * 0.700000f)
                lineTo(left + vw * 0.088889f, top + vh * 0.625000f)
                lineTo(left + vw * 0.066667f, top + vh * 0.600000f)
                lineTo(left + vw * 0.066667f, top + vh * 0.475000f)
                lineTo(left + vw * 0.088889f, top + vh * 0.450000f)
                lineTo(left + vw * 0.111111f, top + vh * 0.350000f)
                lineTo(left + vw * 0.244444f, top + vh * 0.200000f)
                lineTo(left + vw * 0.288889f, top + vh * 0.175000f)
                lineTo(left + vw * 0.355556f, top + vh * 0.175000f)
                lineTo(left + vw * 0.377778f, top + vh * 0.150000f)
                lineTo(left + vw * 0.711111f, top + vh * 0.150000f)
                lineTo(left + vw * 0.733333f, top + vh * 0.175000f)
                lineTo(left + vw * 0.733333f, top + vh * 0.225000f)
                lineTo(left + vw * 0.755556f, top + vh * 0.250000f)
                lineTo(left + vw * 0.800000f, top + vh * 0.250000f)
                lineTo(left + vw * 0.955556f, top + vh * 0.125000f)
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
    'pixel-traced NetEase playback-mode glyph',
)

ui.write_text(s)
