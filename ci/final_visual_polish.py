from pathlib import Path

ui = Path('app/src/main/java/com/immersive/music/MusicUi.kt')
s = ui.read_text()

old = "((peak - (peak - value) * saturation).coerceIn(0f, 1f) * .96f)"
new = "((peak - (peak - value) * saturation).coerceIn(0f, 1f) * .68f)"
if old not in s:
    raise SystemExit('deep-color scale source missing')
s = s.replace(old, new, 1)

old = '''            drawArc(
                color = Color.White.copy(alpha = .075f),
                startAngle = 205f,
                sweepAngle = 72f,
                useCenter = false,
                style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = .045f),
                startAngle = 28f,
                sweepAngle = 68f,
                useCenter = false,
                style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = .022f),
                startAngle = 123f,
                sweepAngle = 42f,
                useCenter = false,
                style = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round),
            )'''
new = '''            drawArc(
                color = Color.White.copy(alpha = .105f),
                startAngle = 205f,
                sweepAngle = 72f,
                useCenter = false,
                style = Stroke(width = 21.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = .070f),
                startAngle = 28f,
                sweepAngle = 68f,
                useCenter = false,
                style = Stroke(width = 12.dp.toPx(), cap = StrokeCap.Round),
            )
            drawArc(
                color = Color.White.copy(alpha = .030f),
                startAngle = 123f,
                sweepAngle = 42f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
            )'''
if old not in s:
    raise SystemExit('vinyl highlight source missing')
s = s.replace(old, new, 1)

ui.write_text(s)
