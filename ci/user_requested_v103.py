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


# Queue/list glyph: keep the same 18.5dp visual width as the left playback-mode glyph
# and use the measured NetEase proportions from the supplied screenshot: 40x37 box,
# 10x12 triangle, short first rule starting at x=17, then two full-width rules.
queue_fn = r'''@Composable
private fun NetEaseQueueGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .68f)
        val vw = 18.5.dp.toPx()
        val vh = (18.5f * 37f / 40f).dp.toPx()
        val left = (size.width - vw) / 2f
        val top = (size.height - vh) / 2f

        fun px(x: Float) = left + vw * (x / 39f)
        fun py(y: Float) = top + vh * (y / 36f)
        val line = .95.dp.toPx()

        val triangle = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(1f), py(0f))
            lineTo(px(11f), py(5.5f))
            lineTo(px(1f), py(11f))
            close()
        }
        drawPath(triangle, c)

        drawLine(
            c,
            Offset(px(17f), py(5.5f)),
            Offset(px(39f), py(5.5f)),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
        drawLine(
            c,
            Offset(px(0f), py(20.5f)),
            Offset(px(39f), py(20.5f)),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
        drawLine(
            c,
            Offset(px(0f), py(35f)),
            Offset(px(39f), py(35f)),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
    }
}
'''
sub(
    r'''@Composable\nprivate fun NetEaseQueueGlyph\(modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun ToneArm)''',
    queue_fn + '\n',
    'v103 NetEase queue glyph',
)


# Tone arm: preserve the v102 stage position and the verified tube curve, but replace
# the oversized one-piece cartridge with the two-stage NetEase head shell measured
# directly from the user's reference (local 259x189 arm bbox). The tube, coupling,
# shell and front head all share one tangent so the stylus no longer looks twisted.
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

        val arm = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(14f), py(14f))
            cubicTo(
                px(39f), py(46f),
                px(79f), py(101f),
                px(111f), py(126f),
            )
            cubicTo(
                px(133f), py(143f),
                px(166f), py(150f),
                px(190f), py(156f),
            )
        }
        drawPath(
            arm,
            Color.Black.copy(alpha = .09f),
            style = Stroke(width = px(11.6f), cap = StrokeCap.Round),
        )
        drawPath(
            arm,
            Color(0xFFF7F7F3),
            style = Stroke(width = px(9.5f), cap = StrokeCap.Round),
        )

        // Dark coupling band directly before the head shell.
        drawLine(
            Color(0xFF555653).copy(alpha = .86f),
            Offset(px(181f), py(151f)),
            Offset(px(193f), py(154f)),
            strokeWidth = px(3.0f),
            cap = StrokeCap.Round,
        )

        // Rear head shell: compact, slightly widening body aligned with the arm tangent.
        val rearShell = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(190f), py(153f))
            cubicTo(px(193f), py(151f), px(196f), py(151f), px(199f), py(152f))
            lineTo(px(228f), py(159f))
            cubicTo(px(231f), py(160f), px(232f), py(162f), px(232f), py(165f))
            lineTo(px(229f), py(177f))
            cubicTo(px(228f), py(180f), px(225f), py(181f), px(222f), py(180f))
            lineTo(px(195f), py(170f))
            cubicTo(px(191f), py(169f), px(189f), py(166f), px(189f), py(162f))
            lineTo(px(189f), py(158f))
            cubicTo(px(189f), py(156f), px(189f), py(154f), px(190f), py(153f))
            close()
        }
        drawPath(rearShell, Color(0xFFF7F7F3))

        // Front cartridge: separate shorter/taller capsule, matching the NetEase head
        // instead of the long horizontal rounded rectangle from v102.
        val frontHead = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(230f), py(158f))
            cubicTo(px(232f), py(157f), px(234f), py(157f), px(236f), py(158f))
            lineTo(px(252f), py(162f))
            cubicTo(px(257f), py(163f), px(259f), py(167f), px(258f), py(171f))
            lineTo(px(255f), py(183f))
            cubicTo(px(254f), py(187f), px(251f), py(189f), px(247f), py(188f))
            lineTo(px(231f), py(184f))
            cubicTo(px(227f), py(183f), px(226f), py(180f), px(227f), py(176f))
            lineTo(px(229f), py(162f))
            cubicTo(px(229f), py(160f), px(229f), py(159f), px(230f), py(158f))
            close()
        }
        drawPath(frontHead, Color(0xFFF7F7F3))

        val detail = Color(0xFFB6B7B3).copy(alpha = .92f)
        drawLine(
            detail,
            Offset(px(238f), py(163f)),
            Offset(px(253f), py(167f)),
            strokeWidth = px(2.0f),
            cap = StrokeCap.Round,
        )
        drawLine(
            detail,
            Offset(px(235f), py(179f)),
            Offset(px(250f), py(183f)),
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
    'v103 clean two-stage NetEase tonearm head',
)

path.write_text(s)
