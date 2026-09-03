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


# v106: only correct the tone-arm geometry/head called out by the user.
# Keep v105's balanced left/right playback controls unchanged.
#
# Reference measurements come from the user's clean NetEase paused screenshot.
# The isolated arm bbox is exactly 259x189 px. Its tube centerline was sampled
# directly from that image. The head has four visibly separate parts:
#   tube -> centered dark connector -> rounded small rectangle -> rounded small square.
# The small square contains two light-gray parallel slots.
# Paused/playing continue to be ONE rigid vector rotating around one fixed pivot.
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

        // Centerline fitted to the supplied NetEase reference. The bend is a
        // concentrated turn, then a shallow final run; it is not one broad arc.
        val arm = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(14f), py(14f))
            cubicTo(
                px(33f), py(40f),
                px(71f), py(92f),
                px(103f), py(122f),
            )
            cubicTo(
                px(113f), py(130f),
                px(120f), py(134f),
                px(132f), py(138f),
            )
            cubicTo(
                px(149f), py(145f),
                px(167f), py(150f),
                px(180f), py(154f),
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

        // Head-local reference basis. In the source screenshot the head travels
        // about 17 degrees down/right. s is along the head, t is perpendicular.
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

        // White narrow neck continuing the tube into the first head block.
        drawLine(
            Color(0xFFF7F7F3),
            hp(0f, 0f),
            hp(12f, 0f),
            strokeWidth = px(8.2f),
            cap = StrokeCap.Round,
        )

        // The dark connector in the NetEase reference sits exactly on the center
        // axis of the white neck. This was visibly off-center in the previous build.
        drawLine(
            Color(0xFF666763).copy(alpha = .96f),
            hp(1.5f, 0f),
            hp(10.5f, 0f),
            strokeWidth = px(2.6f),
            cap = StrokeCap.Round,
        )

        // First head piece: a small rounded rectangle. It widens clearly from the
        // narrow connector but remains much slimmer than the terminal square.
        val rear = androidx.compose.ui.graphics.Path().apply {
            moveTo(hp(14f, -9f).x, hp(14f, -9f).y)
            lineTo(hp(47f, -9f).x, hp(47f, -9f).y)
            cubicTo(
                hp(49.5f, -9f).x, hp(49.5f, -9f).y,
                hp(51f, -7f).x, hp(51f, -7f).y,
                hp(51f, -4.5f).x, hp(51f, -4.5f).y,
            )
            lineTo(hp(51f, 5.5f).x, hp(51f, 5.5f).y)
            cubicTo(
                hp(51f, 8f).x, hp(51f, 8f).y,
                hp(49f, 10f).x, hp(49f, 10f).y,
                hp(46.5f, 10f).x, hp(46.5f, 10f).y,
            )
            lineTo(hp(14f, 10f).x, hp(14f, 10f).y)
            cubicTo(
                hp(11.5f, 10f).x, hp(11.5f, 10f).y,
                hp(10f, 8f).x, hp(10f, 8f).y,
                hp(10f, 5.5f).x, hp(10f, 5.5f).y,
            )
            lineTo(hp(10f, -4.5f).x, hp(10f, -4.5f).y)
            cubicTo(
                hp(10f, -7f).x, hp(10f, -7f).y,
                hp(11.5f, -9f).x, hp(11.5f, -9f).y,
                hp(14f, -9f).x, hp(14f, -9f).y,
            )
            close()
        }
        drawPath(rear, Color(0xFFF7F7F3))

        // Terminal head: a separate rounded small square, visibly wider/taller than
        // the rectangle before it. The step in silhouette is intentional and matches
        // the user's description/reference instead of merging both pieces into one blob.
        val front = androidx.compose.ui.graphics.Path().apply {
            moveTo(hp(56f, -14.5f).x, hp(56f, -14.5f).y)
            lineTo(hp(74f, -14.5f).x, hp(74f, -14.5f).y)
            cubicTo(
                hp(77.5f, -14.5f).x, hp(77.5f, -14.5f).y,
                hp(80f, -11.5f).x, hp(80f, -11.5f).y,
                hp(80f, -8f).x, hp(80f, -8f).y,
            )
            lineTo(hp(80f, 6.5f).x, hp(80f, 6.5f).y)
            cubicTo(
                hp(80f, 10f).x, hp(80f, 10f).y,
                hp(77f, 12.5f).x, hp(77f, 12.5f).y,
                hp(73.5f, 12.5f).x, hp(73.5f, 12.5f).y,
            )
            lineTo(hp(57f, 12.5f).x, hp(57f, 12.5f).y)
            cubicTo(
                hp(53.5f, 12.5f).x, hp(53.5f, 12.5f).y,
                hp(51f, 9.5f).x, hp(51f, 9.5f).y,
                hp(51f, 6f).x, hp(51f, 6f).y,
            )
            lineTo(hp(51f, -8f).x, hp(51f, -8f).y)
            cubicTo(
                hp(51f, -11.5f).x, hp(51f, -11.5f).y,
                hp(52.5f, -14.5f).x, hp(52.5f, -14.5f).y,
                hp(56f, -14.5f).x, hp(56f, -14.5f).y,
            )
            close()
        }
        drawPath(front, Color(0xFFF8F8F4))

        // Two faint gray slots INSIDE the terminal square, parallel to the head axis.
        val slot = Color(0xFFB8BAB6).copy(alpha = .92f)
        drawLine(
            slot,
            hp(58f, -5.2f),
            hp(74.5f, -5.2f),
            strokeWidth = px(2.0f),
            cap = StrokeCap.Round,
        )
        drawLine(
            slot,
            hp(57.5f, 6.0f),
            hp(72.5f, 6.0f),
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
    'v106 structured NetEase tonearm head',
)

path.write_text(s)
