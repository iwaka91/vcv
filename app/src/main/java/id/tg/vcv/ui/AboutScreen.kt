package id.tg.vcv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LIBS = listOf(
    "NanoHTTPD" to "BSD-3-Clause",
    "ZXing" to "Apache 2.0",
    "Jetpack Compose & AndroidX" to "Apache 2.0",
    "Kotlin" to "Apache 2.0",
    "Space Grotesk" to "SIL OFL 1.1",
    "JetBrains Mono" to "SIL OFL 1.1",
)

/** Halaman Tentang (halaman penuh, panah kembali ke Pengaturan). */
@Composable
fun AboutScreen(onBack: () -> Unit, onOpenUrl: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull() ?: ""
    }
    var showLibs by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Vc.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { VIcon(Ic.BACK, Vc.Text) }
            Text("Tentang", style = vcText(20.sp, bold = true))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            // Ikon, nama, versi, deskripsi
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(96.dp).clip(RoundedCornerShape(26.dp)).background(Vc.Surface)
                        .border(3.dp, Vc.Sticker, RoundedCornerShape(26.dp)),
                    contentAlignment = Alignment.Center
                ) { VcvWordmark(34.sp) }
                Spacer(Modifier.height(14.dp))
                Text("Vibe Coding Viewer", style = vcText(22.sp, bold = true))
                Spacer(Modifier.height(4.dp))
                Text("Versi $version", style = TextStyle(fontFamily = VcFonts.mono, fontSize = 13.sp, color = Vc.Muted))
                Spacer(Modifier.height(12.dp))
                Text(
                    "Membuka proyek hasil vibe coding (ZIP, HTML, folder) di HP, lalu membagikannya ke HP siswa lewat QR, tanpa internet.",
                    style = vcText(15.sp, color = Vc.TextSoft).copy(lineHeight = 22.sp), textAlign = TextAlign.Center
                )
            }

            AboutSection("TEKNOLOGI") {
                Bullet("Kotlin dan Jetpack Compose, Android 8.0 ke atas")
                Bullet("Server lokal NanoHTTPD di dalam HP")
                Bullet("Kode QR dibuat dengan ZXing")
            }

            AboutSection("KEAMANAN DATA") {
                Bullet("VcV hanya mengingat lokasi file. File asli tetap di tempatnya.")
                Bullet("Tidak ada data yang dikirim ke internet atau ke pembuat aplikasi.")
                Bullet("Berbagi hanya berjalan di WiFi atau hotspot yang sama.")
                Bullet("Data tiap proyek (skor, progres) disimpan terpisah, tidak terbaca proyek lain.")
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF2A2412)).padding(14.dp)
                ) {
                    Text("Perlu diketahui", style = vcText(15.sp, bold = true, color = Vc.Yellow))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Saat berbagi, siapa pun di jaringan yang sama bisa membuka alamatnya. PIN hanya pencegah iseng, bukan enkripsi. Hapus data peramban, dan data proyek di HP itu ikut hilang.",
                        style = vcText(13.sp, color = Vc.TextSoft).copy(lineHeight = 19.sp)
                    )
                }
            }

            AboutSection("LISENSI") {
                Text("© 2026 Toto Gutomo. Hak cipta dilindungi.", style = vcText(15.sp))
                Text(
                    "Aplikasi ini memakai pustaka sumber terbuka, masing-masing dengan lisensinya sendiri.",
                    style = vcText(13.sp, color = Vc.TextSoft).copy(lineHeight = 19.sp)
                )
                TextButton(
                    onClick = { showLibs = !showLibs },
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = Vc.Surface, contentColor = Vc.Text),
                    modifier = Modifier.fillMaxWidth().height(50.dp)
                ) {
                    Text("Pustaka dan lisensi pihak ketiga", style = vcText(15.sp), modifier = Modifier.weight(1f))
                    Text(if (showLibs) "−" else "+", style = vcText(18.sp, bold = true, color = Vc.Accent))
                }
                if (showLibs) {
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Vc.Surface).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LIBS.forEach { (name, lic) ->
                            Row {
                                Text(name, style = vcText(14.sp), modifier = Modifier.weight(1f))
                                Text(lic, style = TextStyle(fontFamily = VcFonts.mono, fontSize = 12.sp, color = Vc.AccentSoft))
                            }
                        }
                    }
                }
            }

            AboutSection("PEMBUAT") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(Vc.Surface).padding(16.dp)
                ) {
                    Text("Toto Gutomo, S.Pd, Gr, M.Pd, CPSS", style = vcText(16.sp, bold = true))
                    Spacer(Modifier.height(2.dp))
                    Text("Guru SMA Negeri 3 Maliku", style = vcText(14.sp, color = Vc.TextSoft))
                    Spacer(Modifier.height(12.dp))
                    TaglineRow()
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = { onOpenUrl("https://totogutomo.com") },
                        colors = ButtonDefaults.buttonColors(containerColor = Vc.Accent, contentColor = Vc.Ink),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) { Text("totogutomo.com", style = vcText(15.sp, bold = true, color = Vc.Ink)) }
                }
            }

            VcvFooter(Modifier.fillMaxWidth().padding(bottom = 12.dp))
        }
    }
}

/** Lencana </> + tagline, selalu satu baris (huruf mengecil di layar sempit). */
@Composable
private fun TaglineRow() {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val textWidth = maxWidth.value - 52f
        val size = (textWidth / 21.5f).coerceIn(10f, 15f)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(8.dp)).background(Vc.Accent).padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("</>", style = TextStyle(fontFamily = VcFonts.mono, fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, color = Vc.Ink))
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Ngoding Pakai Vibe, Ngajar Pakai Hati",
                style = vcText(size.sp, color = Vc.AccentSoft),
                maxLines = 1, softWrap = false
            )
        }
    }
}

@Composable
private fun AboutSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = vcText(13.sp, bold = true, color = Vc.Accent).copy(letterSpacing = 1.sp))
        content()
    }
}

@Composable
private fun Bullet(text: String) {
    Row {
        Box(Modifier.padding(top = 8.dp).size(6.dp).clip(RoundedCornerShape(3.dp)).background(Vc.Accent))
        Spacer(Modifier.width(10.dp))
        Text(text, style = vcText(14.sp, color = Vc.TextSoft).copy(lineHeight = 20.sp))
    }
}
