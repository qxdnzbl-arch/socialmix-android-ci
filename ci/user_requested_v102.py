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


# 1) Move the complete vinyl + tone-arm stage down as one object. The user's
# side-by-side screenshots show the current center around y=591/1536 while the
# supplied NetEase reference is around y=637/1536 with essentially the same disc
# diameter. 0.060 * disc diameter reproduces that ~45 px shift on the reference
# phone class and scales continuously on other phone widths. Metadata/control
# layout is intentionally untouched.
old = '''                Box(
                    Modifier.size(
                        width = playerStageWidth,
                        height = discSize * 1.13f,
                    ),
                    contentAlignment = Alignment.Center,
                ) {'''
new = '''                Box(
                    Modifier
                        .size(
                            width = playerStageWidth,
                            height = discSize * 1.13f,
                        )
                        .offset(y = discSize * .060f),
                    contentAlignment = Alignment.Center,
                ) {'''
if old not in s:
    raise SystemExit('v95 player-stage anchor missing; refusing to guess vertical layout')
s = s.replace(old, new, 1)

# The existing compact layout has much less vertical air above the record. Preserve
# the normal-phone NetEase pivot/record relationship exactly, but reduce only the
# compact-phone lift so the pivot cannot overlap the centered "心动" title. This
# reuses the app's existing compact condition; no device-specific pixel coordinate.
old = '''                                x = discSize * .223f,
                                y = -(discSize * .231f),
'''
new = '''                                x = discSize * .223f,
                                y = -(discSize * if (compact) .105f else .231f),
'''
if old not in s:
    raise SystemExit('v101 tone-arm responsive offset missing; refusing to guess compact placement')
s = s.replace(old, new, 1)


# 2) Queue/list glyph. Match the supplied NetEase proportions instead of the v101
# approximation: 40x37 visible box, larger 11x12 play triangle, 22x3 short first
# line, two 40x3 full lines. It stays inside the same 24dp visual slot / 44dp hit
# target as the left playback-mode control, so the app keeps its own scale system.
queue_fn = r'''@Composable
private fun NetEaseQueueGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = Color.White.copy(alpha = .68f)
        val vw = 17.2.dp.toPx()
        val vh = 17.1.dp.toPx()
        val left = (size.width - vw) / 2f
        val top = (size.height - vh) / 2f

        fun px(x: Float) = left + vw * (x / 39f)
        fun py(y: Float) = top + vh * (y / 36f)
        val line = 1.08.dp.toPx()

        val triangle = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(0f), py(0f))
            lineTo(px(11f), py(6f))
            lineTo(px(0f), py(12f))
            close()
        }
        drawPath(triangle, c)

        drawLine(
            c,
            Offset(px(17f), py(6f)),
            Offset(px(39f), py(6f)),
            strokeWidth = line,
            cap = StrokeCap.Round,
        )
        drawLine(
            c,
            Offset(px(0f), py(20f)),
            Offset(px(39f), py(20f)),
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
    'NetEase queue glyph proportions',
)


# 3) Tone arm. Remove the v101 pixel-boundary trace that looked like a cut-out.
# Rebuild it as a clean native vector: one smooth Bezier tube, one pivot assembly,
# one cartridge body. Paused/playing are the exact same rigid object rotating around
# the same pivot; only rotation changes. Geometry follows the supplied NetEase
# 260x190 reference bbox, but remains scaled by this app's responsive disc size.
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

        // Soft mounting shadow behind the bright pivot ring.
        drawCircle(
            Color.Black.copy(alpha = .15f),
            radius = px(20f),
            center = pivot,
        )

        // Smooth arm centerline from the NetEase paused reference. A cubic path
        // avoids the jagged silhouette edges from the previous raster contour.
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
                px(198f), py(158f),
            )
        }
        drawPath(
            arm,
            Color.Black.copy(alpha = .10f),
            style = Stroke(width = px(12.2f), cap = StrokeCap.Round),
        )
        drawPath(
            arm,
            Color(0xFFF7F7F3),
            style = Stroke(width = px(9.8f), cap = StrokeCap.Round),
        )

        // Small dark coupling band visible immediately before the cartridge.
        drawLine(
            Color(0xFF555653).copy(alpha = .86f),
            Offset(px(188f), py(155f)),
            Offset(px(205f), py(160f)),
            strokeWidth = px(3.0f),
            cap = StrokeCap.Round,
        )

        // Clean cartridge/head shell, shaped from the reference but drawn as a
        // smooth vector rather than a screenshot contour.
        val cartridge = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(199f), py(153f))
            cubicTo(px(201f), py(151f), px(204f), py(151f), px(207f), py(152f))
            lineTo(px(251f), py(165f))
            cubicTo(px(255f), py(166f), px(258f), py(170f), px(257f), py(174f))
            lineTo(px(253f), py(185f))
            cubicTo(px(252f), py(188f), px(248f), py(189f), px(245f), py(188f))
            lineTo(px(204f), py(177f))
            cubicTo(px(201f), py(176f), px(199f), py(173f), px(199f), py(170f))
            close()
        }
        drawPath(cartridge, Color(0xFFF7F7F3))

        // Cartridge slots/details from the NetEase reference.
        val detail = Color(0xFFB6B7B3).copy(alpha = .92f)
        drawLine(
            detail,
            Offset(px(220f), py(162f)),
            Offset(px(249f), py(170f)),
            strokeWidth = px(2.0f),
            cap = StrokeCap.Round,
        )
        drawLine(
            detail,
            Offset(px(216f), py(176f)),
            Offset(px(245f), py(184f)),
            strokeWidth = px(2.0f),
            cap = StrokeCap.Round,
        )

        // Pivot ring: bright annulus with a muted center, matching the reference.
        drawCircle(Color(0xFFF7F7F3), radius = px(14.5f), center = pivot)
        drawCircle(Color(0xFFB8BAB6), radius = px(6.2f), center = pivot)
    }
}
'''
sub(
    r'''@Composable\nprivate fun ToneArm\(onDisc: Boolean, modifier: Modifier = Modifier\) \{.*?\n\}\n(?=@Composable\nprivate fun DemoArtwork)''',
    tonearm_fn + '\n',
    'clean NetEase vector tone arm',
)

path.write_text(s)
