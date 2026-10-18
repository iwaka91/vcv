package id.tg.vcv.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Ikon garis sederhana (digambar sendiri, tanpa pustaka ikon tambahan). Koordinat dalam grid 24×24. */
enum class Ic { SETTINGS, MORE, PLUS, BACK, CLOSE, RELOAD, EXTERNAL, WINDOW, QR, HOME, PENCIL, TRASH, SAVE, COPY, PAGES }

@Composable
fun VIcon(icon: Ic, color: Color, size: Dp = 24.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val u = this.size.width / 24f
        val stroke = Stroke(width = 2f * u, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun p(vararg pts: Float): Path = Path().apply {
            moveTo(pts[0] * u, pts[1] * u)
            var i = 2
            while (i < pts.size) { lineTo(pts[i] * u, pts[i + 1] * u); i += 2 }
        }
        fun line(vararg pts: Float) = drawPath(p(*pts), color, style = stroke)
        fun dot(x: Float, y: Float, r: Float = 2f) = drawCircle(color, r * u, Offset(x * u, y * u))
        fun rect(x: Float, y: Float, w: Float, h: Float) =
            drawRoundRect(color, Offset(x * u, y * u), Size(w * u, h * u),
                androidx.compose.ui.geometry.CornerRadius(1.5f * u), style = stroke)

        when (icon) {
            Ic.SETTINGS -> {
                line(4f, 6f, 20f, 6f); line(4f, 12f, 20f, 12f); line(4f, 18f, 20f, 18f)
                drawKnob(9f, 6f, u, color); drawKnob(15f, 12f, u, color); drawKnob(8f, 18f, u, color)
            }
            Ic.MORE -> { dot(12f, 5f); dot(12f, 12f); dot(12f, 19f) }
            Ic.PLUS -> { line(12f, 5f, 12f, 19f); line(5f, 12f, 19f, 12f) }
            Ic.BACK -> line(15f, 5f, 8f, 12f, 15f, 19f)
            Ic.CLOSE -> { line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f) }
            Ic.RELOAD -> {
                drawArc(color, -60f, 300f, false, Offset(4f * u, 4f * u), Size(16f * u, 16f * u), style = stroke)
                line(20f, 3f, 20f, 9f, 14f, 9f)
            }
            Ic.EXTERNAL -> {
                line(14f, 4f, 20f, 4f, 20f, 10f); line(20f, 4f, 11f, 13f)
                line(18f, 14f, 18f, 20f, 4f, 20f, 4f, 6f, 10f, 6f)
            }
            Ic.WINDOW -> { rect(3f, 4f, 18f, 16f); line(3f, 9f, 21f, 9f) }
            Ic.QR -> {
                rect(3f, 3f, 7f, 7f); rect(14f, 3f, 7f, 7f); rect(3f, 14f, 7f, 7f)
                rect(14f, 14f, 3f, 3f); rect(18f, 18f, 3f, 3f)
            }
            Ic.HOME -> { line(4f, 11f, 12f, 4f, 20f, 11f); line(6f, 10f, 6f, 20f, 18f, 20f, 18f, 10f) }
            Ic.PENCIL -> line(4f, 20f, 8f, 20f, 19f, 9f, 15f, 5f, 4f, 16f, 4f, 20f)
            Ic.TRASH -> { line(4f, 7f, 20f, 7f); line(9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f); line(6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f) }
            Ic.SAVE -> { line(12f, 4f, 12f, 15f); line(7f, 10f, 12f, 15f, 17f, 10f); line(5f, 20f, 19f, 20f) }
            Ic.PAGES -> { rect(7f, 3f, 13f, 15f); line(4f, 7f, 4f, 21f, 16f, 21f); line(10f, 8f, 17f, 8f); line(10f, 12f, 17f, 12f) }
            Ic.COPY -> { rect(8f, 8f, 12f, 12f); line(16f, 8f, 16f, 4f, 4f, 4f, 4f, 16f, 8f, 16f) }
        }
    }
}

private fun DrawScope.drawKnob(x: Float, y: Float, u: Float, color: Color) {
    drawCircle(Vc.Bg, 2.6f * u, Offset(x * u, y * u))
    drawCircle(color, 2f * u, Offset(x * u, y * u), style = Stroke(1.8f * u))
}
