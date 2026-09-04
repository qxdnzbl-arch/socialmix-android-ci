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


# 1) Left playback-mode glyph: preserve the approved 24dp slot and 1.22dp line,
# but make the arrow use the exact same 6.1x5.7dp visible triangle as the queue.
# Its RIGHT edge is locked to the loop body's rightmost edge. The single-loop "1"
# is enlarged to 8sp Medium without changing the outer glyph size/hit target.
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

            val loop = androidx.compose.ui.graphics.Path().apply {
                moveTo(px(32f), py(5f))
                lineTo(px(17f), py(5f))
                cubicTo(px(8f), py(5f), px(3f), py(11f), px(3f), py(20f))
                cubicTo(px(3f), py(30f), px(10f), py(36f), px(20f), py(36f))
                lineTo(px(27f), py(36f))
                cubicTo(px(35f), py(36f), px(40f), py(31f), px(40f), py(24f))
            }
            drawPath(loop, c, style = Stroke(width = line, cap = StrokeCap.Round))

            // 13/44*20.8 = 6.15dp wide; 12/40*18.95 = 5.69dp high.
            // Right edge x=40 exactly equals the loop body's rightmost x=40.
            val arrow = androidx.compose.ui.graphics.Path().apply {
                moveTo(px(27f), py(0f))
                lineTo(px(40f), py(6f))
                lineTo(px(27f), py(12f))
                close()
            }
            drawPath(arrow, c)
        }

        if (mode == PlaybackMode.SINGLE_LOOP) {
            Text(
                "1",
                color = Color.White.copy(alpha = .68f),
                fontSize = 8.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
'''
sub(
    r'''@Composable\nprivate fun PlaybackModeGlyph\(mode: PlaybackMode, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun VinylDisc)''',
    playback_fn + '\n',
    'v107 playback-mode geometry',
)


# 2) Remove the extra spindle decoration completely: no white center disc and no
# dark center dot. The cover remains uninterrupted in the label area.
center_hub = '''        Box(\n            Modifier\n                .size(13.dp)\n                .clip(CircleShape)\n                .background(Color(0xFFE8E7E2).copy(alpha = .96f))\n        )\n        Box(\n            Modifier\n                .size(4.4.dp)\n                .clip(CircleShape)\n                .background(Color(0xFF74756F))\n        )\n'''
if center_hub not in s:
    raise SystemExit('vinyl center hub anchor missing; refusing to guess')
s = s.replace(center_hub, '', 1)


# 3) Queue/list glyph: retain its existing 24dp visual slot and 1.22dp stroke.
# The play triangle is exactly 6.1x5.7dp, identical to the left arrow, and its LEFT
# edge is exactly the same as the two full-width list strokes below it.
queue_fn = r'''@Composable
private fun NetEaseQueueGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .68f)
        val vw = 17.2.dp.toPx()
        val vh = 16.4.dp.toPx()
        val left = (size.width - vw) / 2f
        val top = (size.height - vh) / 2f
        val line = 1.22.dp.toPx()
        val triW = 6.1.dp.toPx()
        val triH = 5.7.dp.toPx()

        val triangle = androidx.compose.ui.graphics.Path().apply {
            moveTo(left, top)
            lineTo(left + triW, top + triH / 2f)
            lineTo(left, top + triH)
            close()
        }
        drawPath(triangle, c)

        // First-row line follows the triangle with a clean gap.
        drawLine(
            c,
            Offset(left + 7.6.dp.toPx(), top + triH / 2f),
            Offset(left + vw, top + triH / 2f),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
        // Full-width lines: their left edge is exactly the triangle's left edge.
        drawLine(
            c,
            Offset(left, top + 9.1.dp.toPx()),
            Offset(left + vw, top + 9.1.dp.toPx()),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
        drawLine(
            c,
            Offset(left, top + 15.7.dp.toPx()),
            Offset(left + vw, top + 15.7.dp.toPx()),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
    }
}
'''
sub(
    r'''@Composable\nprivate fun NetEaseQueueGlyph\(modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun ToneArm)''',
    queue_fn + '\n',
    'v107 queue triangle alignment',
)


# 4) Tone arm. Keep the established arm curve/pivot and only fix the head structure.
# Every head part uses the SAME local axis t=0. Rear rounded rectangle is symmetric
# at +/-9.5; terminal rounded square is 27x27 at +/-13.5; gray slots are mirrored
# at +/-5.5. This removes the visible off-center terminal block from v106.
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
        drawCircle(Color.Black.copy(alpha = .15f), radius = px(20f), center = pivot)

        val arm = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(14f), py(14f))
            cubicTo(px(33f), py(40f), px(71f), py(92f), px(103f), py(122f))
            cubicTo(px(113f), py(130f), px(120f), py(134f), px(132f), py(138f))
            cubicTo(px(149f), py(145f), px(167f), py(150f), px(180f), py(154f))
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

        val ox = 180f
        val oy = 154f
        val ux = .9563f
        val uy = .2924f
        val nx = -.2924f
        val ny = .9563f
        fun hp(sv: Float, tv: Float) = Offset(
            px(ox + ux * sv + nx * tv),
            py(oy + uy * sv + ny * tv),
        )

        drawLine(
            Color(0xFFF7F7F3),
            hp(0f, 0f),
            hp(12f, 0f),
            strokeWidth = px(8.2f),
            cap = StrokeCap.Round,
        )
        drawLine(
            Color(0xFF666763).copy(alpha = .96f),
            hp(1.5f, 0f),
            hp(10.5f, 0f),
            strokeWidth = px(2.6f),
            cap = StrokeCap.Round,
        )

        // Symmetric rounded rectangle: s=12..52, t=-9.5..+9.5.
        val rear = androidx.compose.ui.graphics.Path().apply {
            moveTo(hp(15f, -9.5f).x, hp(15f, -9.5f).y)
            lineTo(hp(49f, -9.5f).x, hp(49f, -9.5f).y)
            cubicTo(
                hp(51f, -9.5f).x, hp(51f, -9.5f).y,
                hp(52f, -8f).x, hp(52f, -8f).y,
                hp(52f, -6f).x, hp(52f, -6f).y,
            )
            lineTo(hp(52f, 6f).x, hp(52f, 6f).y)
            cubicTo(
                hp(52f, 8f).x, hp(52f, 8f).y,
                hp(51f, 9.5f).x, hp(51f, 9.5f).y,
                hp(49f, 9.5f).x, hp(49f, 9.5f).y,
            )
            lineTo(hp(15f, 9.5f).x, hp(15f, 9.5f).y)
            cubicTo(
                hp(13f, 9.5f).x, hp(13f, 9.5f).y,
                hp(12f, 8f).x, hp(12f, 8f).y,
                hp(12f, 6f).x, hp(12f, 6f).y,
            )
            lineTo(hp(12f, -6f).x, hp(12f, -6f).y)
            cubicTo(
                hp(12f, -8f).x, hp(12f, -8f).y,
                hp(13f, -9.5f).x, hp(13f, -9.5f).y,
                hp(15f, -9.5f).x, hp(15f, -9.5f).y,
            )
            close()
        }
        drawPath(rear, Color(0xFFF7F7F3))

        // True centered 27x27 rounded terminal square: s=52..79, t=+/-13.5.
        val front = androidx.compose.ui.graphics.Path().apply {
            moveTo(hp(56f, -13.5f).x, hp(56f, -13.5f).y)
            lineTo(hp(75f, -13.5f).x, hp(75f, -13.5f).y)
            cubicTo(
                hp(77.5f, -13.5f).x, hp(77.5f, -13.5f).y,
                hp(79f, -11.5f).x, hp(79f, -11.5f).y,
                hp(79f, -9f).x, hp(79f, -9f).y,
            )
            lineTo(hp(79f, 9f).x, hp(79f, 9f).y)
            cubicTo(
                hp(79f, 11.5f).x, hp(79f, 11.5f).y,
                hp(77.5f, 13.5f).x, hp(77.5f, 13.5f).y,
                hp(75f, 13.5f).x, hp(75f, 13.5f).y,
            )
            lineTo(hp(56f, 13.5f).x, hp(56f, 13.5f).y)
            cubicTo(
                hp(53.5f, 13.5f).x, hp(53.5f, 13.5f).y,
                hp(52f, 11.5f).x, hp(52f, 11.5f).y,
                hp(52f, 9f).x, hp(52f, 9f).y,
            )
            lineTo(hp(52f, -9f).x, hp(52f, -9f).y)
            cubicTo(
                hp(52f, -11.5f).x, hp(52f, -11.5f).y,
                hp(53.5f, -13.5f).x, hp(53.5f, -13.5f).y,
                hp(56f, -13.5f).x, hp(56f, -13.5f).y,
            )
            close()
        }
        drawPath(front, Color(0xFFF8F8F4))

        val slot = Color(0xFFB8BAB6).copy(alpha = .92f)
        drawLine(
            slot,
            hp(58f, -5.5f),
            hp(73.5f, -5.5f),
            strokeWidth = px(2.0f),
            cap = StrokeCap.Round,
        )
        drawLine(
            slot,
            hp(58f, 5.5f),
            hp(73.5f, 5.5f),
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
    'v107 centered tone-arm head',
)


# 5) Replace only the pause visual. Keep the existing 61dp center hit target and
# surrounding playback-control layout. NetEase target: total 18x24dp; each bar
# 6.2x24dp; 5.6dp clear gap; 2.4dp corner radius.
old_pause = '''                    Icon(\n                        if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,\n                        contentDescription = null,\n                        tint = Color.White.copy(alpha = .92f),\n                        modifier = Modifier.size(if (isPlaying) 48.dp else 51.dp),\n                    )'''
new_pause = '''                    if (isPlaying) {\n                        NetEasePauseGlyph()\n                    } else {\n                        Icon(\n                            Icons.Rounded.PlayArrow,\n                            contentDescription = null,\n                            tint = Color.White.copy(alpha = .92f),\n                            modifier = Modifier.size(51.dp),\n                        )\n                    }'''
if old_pause not in s:
    raise SystemExit('play/pause visual anchor missing; refusing to guess')
s = s.replace(old_pause, new_pause, 1)

pause_fn = r'''@Composable
private fun NetEasePauseGlyph() {
    Row(
        Modifier.width(18.dp).height(24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(6.2.dp)
                .height(24.dp)
                .background(
                    Color.White.copy(alpha = .92f),
                    RoundedCornerShape(2.4.dp),
                )
        )
        Box(
            Modifier
                .width(6.2.dp)
                .height(24.dp)
                .background(
                    Color.White.copy(alpha = .92f),
                    RoundedCornerShape(2.4.dp),
                )
        )
    }
}
'''
marker = '@Composable\nprivate fun NetEaseSeekBar('
if marker not in s:
    raise SystemExit('NetEaseSeekBar marker missing')
s = s.replace(marker, pause_fn + '\n' + marker, 1)

path.write_text(s)
