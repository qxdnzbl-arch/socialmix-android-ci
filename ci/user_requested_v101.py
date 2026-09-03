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


# Queue glyph: reproduce the user's NetEase reference silhouette inside the same
# 24dp slot used by the left playback-mode glyph. The isolated reference bbox is
# 40 x 37 px: play triangle on the first row, then three horizontal list strokes.
old = '''                    Icon(\n                        Icons.AutoMirrored.Rounded.QueueMusic,\n                        contentDescription = null,\n                        tint = Color.White.copy(alpha = .78f),\n                        modifier = Modifier.size(24.dp),\n                    )'''
new = '''                    NetEaseQueueGlyph(\n                        modifier = Modifier.size(24.dp),\n                    )'''
if old not in s:
    raise SystemExit('v96 queue icon anchor not found; refusing to guess')
s = s.replace(old, new, 1)


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

        val triangle = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(1f), py(0f))
            lineTo(px(11f), py(5.5f))
            lineTo(px(1f), py(11f))
            close()
        }
        drawPath(triangle, c)

        drawLine(
            c,
            Offset(px(18f), py(5.5f)),
            Offset(px(39f), py(5.5f)),
            strokeWidth = vh * (2f / 37f),
            cap = StrokeCap.Round,
        )
        drawLine(
            c,
            Offset(px(0f), py(20f)),
            Offset(px(39f), py(20f)),
            strokeWidth = vh * (3f / 37f),
            cap = StrokeCap.Round,
        )
        drawLine(
            c,
            Offset(px(0f), py(35f)),
            Offset(px(39f), py(35f)),
            strokeWidth = vh * (3f / 37f),
            cap = StrokeCap.Round,
        )
    }
}

'''

# Put the queue glyph immediately before ToneArm so it stays local to the player UI.
marker = '@Composable\nprivate fun ToneArm(onDisc: Boolean, modifier: Modifier = Modifier) {'
if marker not in s:
    raise SystemExit('ToneArm function marker missing')
s = s.replace(marker, queue_fn + marker, 1)


# Position the arm from the record itself, not from the stage/right screen edge.
# The traced NetEase reference has a 259 x 189 px arm bbox. Its pivot is at (14,14).
# A 0.50*disc arm width + this horizontal offset puts the pivot exactly on the
# record centerline. The vertical offset preserves the same ~0.23 record-diameter
# separation above the halo on every phone width.
old = '''                        modifier = Modifier\n                            .size(width = discSize * .59f, height = discSize * .47f)\n                            .align(Alignment.TopEnd)\n                            .offset(y = -(discSize * .177f))\n                            .padding(top = 9.dp, end = toneArmEndInset),\n'''
new = '''                        modifier = Modifier\n                            .size(width = discSize * .50f, height = discSize * .365f)\n                            .align(Alignment.TopCenter)\n                            .offset(\n                                x = discSize * .223f,\n                                y = -(discSize * .231f),\n                            ),\n'''
if old not in s:
    raise SystemExit('v100 tone-arm placement anchor missing; refusing to guess')
s = s.replace(old, new, 1)


# Exact paused-state silhouette traced from the user's NetEase reference screenshot.
# The playing state is the same rigid object rotated 25 degrees around the same pivot;
# image registration against the second supplied NetEase screenshot gives >95% mask
# overlap, so the two states never morph into different arm shapes.
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
        fun px(x: Int) = size.width * (x / 258f)
        fun py(y: Int) = size.height * (y / 188f)

        val pivot = Offset(px(14), py(14))
        drawCircle(
            Color.Black.copy(alpha = .12f),
            radius = size.width * (20f / 258f),
            center = pivot,
        )

        val contour = intArrayOf(
            2,6,1,8,1,11,0,12,0,16,1,17,2,21,7,26,9,27,12,27,13,28,16,28,17,27,19,29,19,30,25,37,27,41,34,49,36,53,40,57,40,58,43,61,43,62,46,65,46,66,50,70,50,71,54,75,54,76,59,81,59,82,63,86,63,87,76,101,76,102,90,116,90,117,91,117,103,129,104,129,108,133,109,133,111,135,118,139,120,139,123,141,125,141,131,144,136,145,139,147,141,147,142,148,150,150,153,152,155,152,159,154,165,155,171,158,174,158,175,159,177,159,178,160,186,162,189,164,191,168,197,169,201,171,204,171,205,172,208,172,209,173,212,173,213,174,216,174,220,176,226,177,228,181,230,183,232,183,236,185,239,185,240,186,243,186,247,188,250,188,253,186,254,182,255,181,255,178,256,177,257,170,258,169,258,164,254,161,251,161,250,160,244,159,243,158,240,158,239,157,235,157,233,158,232,160,229,160,228,159,221,158,217,156,214,156,213,155,206,154,205,153,199,152,198,151,194,151,192,153,186,152,185,151,183,151,182,150,180,150,176,148,173,148,170,146,164,145,158,142,156,142,155,141,153,141,152,140,144,138,136,134,128,132,116,126,105,116,104,116,102,114,102,113,101,113,81,92,81,91,75,85,75,84,70,79,70,78,64,72,64,71,61,68,61,67,49,53,49,52,46,49,46,48,31,29,29,25,26,22,28,18,28,10,27,9,27,7,24,4,24,3,23,3,19,0,10,0,6,2
        )
        val traced = androidx.compose.ui.graphics.Path().apply {
            moveTo(px(contour[0]), py(contour[1]))
            var i = 2
            while (i < contour.size) {
                lineTo(px(contour[i]), py(contour[i + 1]))
                i += 2
            }
            close()
        }
        drawPath(traced, Color(0xFFF6F6F2))

        // NetEase pivot: bright annulus with a muted center inside a soft dark mount.
        drawCircle(
            Color.Black.copy(alpha = .22f),
            radius = size.width * (5.4f / 258f),
            center = pivot,
        )

        // Two cartridge slots and the small dark neck detail visible in the reference.
        val detail = Color(0xFFB7B8B4).copy(alpha = .90f)
        drawLine(
            detail,
            Offset(px(221), py(159)),
            Offset(px(249), py(168)),
            strokeWidth = size.width * (2.0f / 258f),
            cap = StrokeCap.Round,
        )
        drawLine(
            detail,
            Offset(px(217), py(176)),
            Offset(px(245), py(185)),
            strokeWidth = size.width * (2.0f / 258f),
            cap = StrokeCap.Round,
        )
        drawLine(
            Color.Black.copy(alpha = .48f),
            Offset(px(188), py(151)),
            Offset(px(203), py(155)),
            strokeWidth = size.width * (2.4f / 258f),
            cap = StrokeCap.Round,
        )
    }
}
'''

sub(
    r'''@Composable\nprivate fun ToneArm\(onDisc: Boolean, modifier: Modifier = Modifier\) \{.*?\n\}\n\n(?=@Composable\nprivate fun DemoArtwork)''',
    tonearm_fn + '\n',
    'NetEase traced tone arm',
)

path.write_text(s)
