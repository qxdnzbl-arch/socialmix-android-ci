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


# Match the right queue glyph's visual weight to the left playback-mode glyph while
# preserving the NetEase 40x37 proportions measured from the supplied screenshot.
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
        val line = 1.22.dp.toPx()

        val triangle = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(1f), py(0f))
            lineTo(px(11.5f), py(5.5f))
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
    'v104 balanced queue glyph',
)


# Keep the verified tube/pivot geometry from v103. Only refine the cartridge to the
# measured NetEase silhouette: compact rear shell plus a shorter front head whose
# outer tip narrows upward instead of becoming a long horizontal rounded block.
old_rear = r'''        val rearShell = androidx.compose.ui.graphics.Path\(\)\.apply \{.*?        drawPath\(rearShell, Color\(0xFFF7F7F3\)\)'''
new_rear = r'''        val rearShell = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(190f), py(153f))
            cubicTo(px(193f), py(152f), px(196f), py(151f), px(200f), py(152f))
            cubicTo(px(209f), py(154f), px(219f), py(157f), px(230f), py(160f))
            lineTo(px(230f), py(182f))
            cubicTo(px(220f), py(177f), px(207f), py(173f), px(195f), py(169f))
            cubicTo(px(191f), py(168f), px(190f), py(165f), px(190f), py(161f))
            lineTo(px(190f), py(153f))
            close()
        }
        drawPath(rearShell, Color(0xFFF7F7F3))'''
sub(old_rear, new_rear, 'v104 rear cartridge shell')

old_front = r'''        val frontHead = androidx.compose.ui.graphics.Path\(\)\.apply \{.*?        drawPath\(frontHead, Color\(0xFFF7F7F3\)\)'''
new_front = r'''        val frontHead = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(230f), py(160f))
            cubicTo(px(232f), py(159f), px(233f), py(158f), px(235f), py(158f))
            cubicTo(px(241f), py(158f), px(248f), py(159f), px(252f), py(161f))
            cubicTo(px(256f), py(162f), px(258f), py(164f), px(258f), py(168f))
            cubicTo(px(258f), py(173f), px(256f), py(178f), px(255f), py(181f))
            cubicTo(px(254f), py(185f), px(252f), py(188f), px(249f), py(188f))
            cubicTo(px(243f), py(187f), px(236f), py(185f), px(230f), py(182f))
            cubicTo(px(227f), py(181f), px(227f), py(177f), px(228f), py(174f))
            lineTo(px(230f), py(160f))
            close()
        }
        drawPath(frontHead, Color(0xFFF7F7F3))'''
sub(old_front, new_front, 'v104 front cartridge head')

# Align the two cartridge slots to the new shorter head.
s = s.replace(
    '''            Offset(px(238f), py(163f)),\n            Offset(px(253f), py(167f)),''',
    '''            Offset(px(238f), py(163f)),\n            Offset(px(252f), py(167f)),''',
    1,
)
s = s.replace(
    '''            Offset(px(235f), py(179f)),\n            Offset(px(250f), py(183f)),''',
    '''            Offset(px(235f), py(179f)),\n            Offset(px(249f), py(183f)),''',
    1,
)

path.write_text(s)
