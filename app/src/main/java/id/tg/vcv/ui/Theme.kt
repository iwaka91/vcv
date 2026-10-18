package id.tg.vcv.ui

import android.content.Context
import android.graphics.Typeface as AndroidTypeface
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tg.vcv.data.Kind
import id.tg.vcv.data.ProjectRecord

object Vc {
    val Bg = Color(0xFF101017)
    val Surface = Color(0xFF1C1C26)
    val Surface2 = Color(0xFF24242F)
    val Border = Color(0xFF3A3A4A)
    val Text = Color(0xFFECEAF2)
    val TextSoft = Color(0xFFC9C5D6)
    val Muted = Color(0xFF9C98AD)
    val Accent = Color(0xFFFF7A1A)
    val AccentSoft = Color(0xFFFFB067)
    val Ink = Color(0xFF17121F)
    val Danger = Color(0xFFFF8A7A)
    val Yellow = Color(0xFFFFC845)
    val Teal = Color(0xFF3DD6A3)
    val Purple = Color(0xFFA78BFA)
    val Blue = Color(0xFF3DA5FF)
    val Sticker = Color(0xFFFFF4E8)

    val TagZip = Color(0xFFFF9A4D)
    val TagHtml = Color(0xFFFFC845)
    val TagDir = Color(0xFFB9A2FF)
    val TagShared = Color(0xFF6FE3B5)
}

/**
 * Font merek: Space Grotesk (tampilan) & JetBrains Mono (kode).
 * Bila file font belum dimasukkan ke app/src/main/assets/fonts/, otomatis memakai font sistem.
 */
object VcFonts {
    var display: FontFamily = FontFamily.SansSerif; private set
    var displayBold: FontFamily = FontFamily.SansSerif; private set
    var mono: FontFamily = FontFamily.Monospace; private set
    private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        fun tf(name: String): FontFamily? = runCatching {
            val list = context.assets.list("fonts") ?: emptyArray()
            if (name !in list) null
            else FontFamily(Typeface(AndroidTypeface.createFromAsset(context.assets, "fonts/$name")))
        }.getOrNull()
        tf("SpaceGrotesk-Medium.ttf")?.let { display = it }
        tf("SpaceGrotesk-Bold.ttf")?.let { displayBold = it }
        tf("JetBrainsMono-ExtraBold.ttf")?.let { mono = it }
    }
}

@Composable
fun VcvTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    remember { VcFonts.load(ctx.applicationContext); true }
    val scheme = darkColorScheme(
        primary = Vc.Accent,
        onPrimary = Vc.Ink,
        secondary = Vc.Yellow,
        background = Vc.Bg,
        onBackground = Vc.Text,
        surface = Vc.Surface,
        onSurface = Vc.Text,
        surfaceVariant = Vc.Surface2,
        onSurfaceVariant = Vc.TextSoft,
        surfaceContainer = Vc.Surface,
        surfaceContainerHigh = Vc.Surface,
        surfaceContainerLow = Vc.Bg,
        outline = Vc.Border,
        error = Vc.Danger,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Teks dengan gaya merek. */
fun vcText(size: TextUnit, bold: Boolean = false, color: Color = Vc.Text): TextStyle =
    TextStyle(
        fontFamily = if (bold) VcFonts.displayBold else VcFonts.display,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        fontSize = size,
        color = color,
    )

/** Logo "VcV" dengan huruf c miring oranye (gaya opsi 3). */
@Composable
fun VcvWordmark(size: TextUnit, modifier: Modifier = Modifier) {
    val style = vcText(size, bold = true).copy(letterSpacing = (-0.04f * size.value).sp)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("V", style = style)
        Text(
            "c",
            style = style.copy(color = Vc.Accent),
            modifier = Modifier.rotate(-12f).offset(y = (size.value * 0.05f).dp)
        )
        Text("V", style = style)
    }
}

/** Footer tetap semua layar utama. */
@Composable
fun VcvFooter(modifier: Modifier = Modifier) {
    Text(
        "Vibe Coding Viewer ©2026 | totogutomo.com",
        style = vcText(12.sp, color = Vc.Muted),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = modifier
    )
}

/** Stiker kecil bergaya opsi 3 (JS, </>, CSS, dll). */
@Composable
fun Sticker(label: String, bg: Color, angle: Float, modifier: Modifier = Modifier, fontSize: TextUnit = 16.sp) {
    Box(
        modifier
            .rotate(angle)
            .background(bg, RoundedCornerShape(12.dp))
            .border(3.dp, Vc.Sticker, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, style = TextStyle(fontFamily = VcFonts.mono, fontWeight = FontWeight.ExtraBold, fontSize = fontSize, color = Vc.Ink))
    }
}

/** Kotak lencana jenis proyek (ZIP / HTML / DIR / WA). */
@Composable
fun KindBadge(r: ProjectRecord, size: Dp = 48.dp) {
    val (label, color) = when (r.kind) {
        Kind.ZIP -> "ZIP" to Vc.TagZip
        Kind.HTML -> "HTML" to Vc.TagHtml
        Kind.FOLDER -> "DIR" to Vc.TagDir
        Kind.SHARED -> (r.sharedTag.ifEmpty { "APP" }) to Vc.TagShared
    }
    val faded = r.isSharedCopyGone
    Box(
        Modifier
            .size(size)
            .background(if (faded) color.copy(alpha = 0.35f) else color, RoundedCornerShape(14.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(label, style = TextStyle(fontFamily = VcFonts.mono, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp, color = Vc.Ink))
    }
}

