from pathlib import Path
import re

path = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = path.read_text()


def sub(pattern: str, repl: str, name: str, count: int = 1) -> None:
    global s
    s2, n = re.subn(pattern, repl, s, count=count, flags=re.S)
    if n != count:
        raise SystemExit(f'{name}: expected {count}, replaced {n}')
    s = s2


# User correction: the left playback-mode glyph was visibly smaller and heavier
# than the NetEase queue glyph on the right. Draw it natively with the SAME 1.22dp
# stroke as the queue glyph, while keeping the NetEase reference bbox ratio:
# loop 45x41 versus queue 40x37. This intentionally makes the loop about 12.5%
# wider, matching the supplied NetEase screenshot and balancing both sides by eye.
playback_fn = r'''@Composable
private fun PlaybackModeGlyph(mode: PlaybackMode, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val c = Color.White.copy(alpha = .68f)
            val vw = 20.8.dp.toPx()
            val vh = (20.8f * 41f / 45f).dp.toPx()
            val left = (size.width - vw) / 2f
            val top = (size.height - vh) / 2f
            val line = 1.22.dp.toPx()

            fun px(x: Float) = left + vw * (x / 44f)
            fun py(y: Float) = top + vh * (y / 40f)

            // NetEase-style rounded open loop: straight top/bottom sections with
            // a smooth left turn and the opening on the upper-right.
            val loop = androidx.compose.ui.graphics.Path().apply {
                moveTo(px(32f), py(5f))
                lineTo(px(17f), py(5f))
                cubicTo(px(8f), py(5f), px(3f), py(11f), px(3f), py(20f))
                cubicTo(px(3f), py(30f), px(10f), py(36f), px(20f), py(36f))
                lineTo(px(27f), py(36f))
                cubicTo(px(35f), py(36f), px(40f), py(31f), px(40f), py(24f))
            }
            drawPath(loop, c, style = Stroke(width = line, cap = StrokeCap.Round))

            val arrow = androidx.compose.ui.graphics.Path().apply {
                moveTo(px(31f), py(0f))
                lineTo(px(44f), py(6f))
                lineTo(px(31f), py(12f))
                close()
            }
            drawPath(arrow, c)
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
'''
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    playback_fn + '\n',
    'v105 balanced playback-mode glyph',
)


# Tone arm: keep the v104 stage/pivot placement because the user's latest screenshot
# now aligns with the NetEase reference vertically. Only correct the actual arm object.
# Coordinates below are measured from the supplied NetEase paused-state arm bbox
# (259x189). The centerline is fitted to the reference samples, with the visible
# bend concentrated around x=100..132 instead of the overly broad v104 curve.
# The cartridge is rebuilt from the reference envelope so it stays short, square,
# correctly angled, and continuous with the final tube tangent.
tonearm_fn = r'''@Composable
private fun ToneArm(onDisc: Boolean, modifier: Modifier = Modifier) {
    val angle by animateFloatAsState(
        targetValue = if (onDisc) 25f else 0f,
        animationSpec = tween(430),
        label = "toneArm",
    )

    Canvas(
        modifier
            .graphicsLayer {
                rotationZ = angle
                transformOrigin = TransformOrigin(14f / 258f, 14f / 188f)
            }
            .semantics {
                contentDescription = if (onDisc) "唱针:唱片上" else "唱针:唱片外"
            }
    ) {
        fun px(x: Float) = size.width * (x / 258f)
        fun py(y: Float) = size.height * (y / 188f)

        val pivot = Offset(px(14f), py(14f))

        drawCircle(
            Color.Black.copy(alpha = .15f),
            radius = px(20f),
            center = pivot,
        )

        // Reference-fitted native vector centerline. The three sections preserve
        // the NetEase turn: long descending arm -> distinct easing bend -> shallow
        // final run into the cartridge.
        val arm = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(14f), py(14f))
            cubicTo(
                px(33f), py(40f),
                px(71f), py(92f),
                px(103f), py(122f),
            )
            cubicTo(
                px(113f), py(132f),
                px(120f), py(136f),
                px(132f), py(140f),
            )
            cubicTo(
                px(149f), py(147f),
                px(167f), py(152f),
                px(188f), py(158f),
            )
        }
        drawPath(
            arm,
            Color.Black.copy(alpha = .08f),
            style = Stroke(width = px(11.2f), cap = StrokeCap.Round),
        )
        drawPath(
            arm,
            Color(0xFFF7F7F3),
            style = Stroke(width = px(9.5f), cap = StrokeCap.Round),
        )

        // Small dark coupling visible immediately before the white cartridge.
        drawLine(
            Color(0xFF555653).copy(alpha = .86f),
            Offset(px(181f), py(154f)),
            Offset(px(194f), py(158f)),
            strokeWidth = px(3.0f),
            cap = StrokeCap.Round,
        )

        // Rear shell measured from the NetEase reference instead of the long wedge
        // used in v104. Its centerline follows the final arm tangent.
        val rearShell = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(188f), py(153f))
            cubicTo(px(193f), py(151f), px(200f), py(152f), px(208f), py(154f))
            cubicTo(px(216f), py(156f), px(223f), py(158f), px(229f), py(160f))
            lineTo(px(228f), py(182f))
            cubicTo(px(220f), py(178f), px(211f), py(174f), px(202f), py(171f))
            cubicTo(px(195f), py(169f), px(190f), py(167f), px(188f), py(164f))
            lineTo(px(188f), py(153f))
            close()
        }
        drawPath(rearShell, Color(0xFFF7F7F3))

        // Compact front head: shorter, squarer and aligned with the shell. The
        // outer tip rounds inward like the NetEase reference rather than drooping.
        val frontHead = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(228f), py(160f))
            cubicTo(px(231f), py(159f), px(233f), py(158f), px(236f), py(158f))
            cubicTo(px(243f), py(158f), px(249f), py(160f), px(253f), py(162f))
            cubicTo(px(257f), py(164f), px(259f), py(166f), px(258f), py(170f))
            cubicTo(px(258f), py(175f), px(255f), py(181f), px(253f), py(183f))
            cubicTo(px(252f), py(187f), px(249f), py(189f), px(248f), py(188f))
            cubicTo(px(241f), py(186f), px(234f), py(184f), px(228f), py(182f))
            lineTo(px(228f), py(160f))
            close()
        }
        drawPath(frontHead, Color(0xFFF7F7F3))

        val detail = Color(0xFFB6B7B3).copy(alpha = .92f)
        drawLine(
            detail,
            Offset(px(237f), py(162f)),
            Offset(px(252f), py(166f)),
            strokeWidth = px(2.0f),
            cap = StrokeCap.Round,
        )
        drawLine(
            detail,
            Offset(px(235f), py(179f)),
            Offset(px(249f), py(183f)),
            strokeWidth = px(2.0f),
            cap = StrokeCap.Round,
        )

        drawCircle(Color(0xFFF7F7F3), radius = px(14.5f), center = pivot)
        drawCircle(Color(0xFFB8BAB6), radius = px(6.2f), center = pivot)
    }
}
'''
sub(
    r'''@Composable\nprivate fun ToneArm\(onDisc: Boolean, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun DemoArtwork)''',
    tonearm_fn + '\n',
    'v105 reference-fitted tonearm',
)

path.write_text(s)
