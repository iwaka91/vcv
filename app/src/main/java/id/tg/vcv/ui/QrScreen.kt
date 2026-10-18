package id.tg.vcv.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import id.tg.vcv.server.ServerState
import java.util.Locale

private const val CLASS_SIZE = 36
private const val ASSUMED_BYTES_PER_SEC = 2.5 * 1024 * 1024 // ±20 Mbps lewat WiFi/hotspot 2.4 GHz

@Composable
fun QrScreen(
    st: ServerState,
    onBack: () -> Unit,
    onStop: () -> Unit,
    onRefresh: () -> Unit,
    onToast: (String) -> Unit,
    onPin: (Boolean) -> Unit,
    onChangePin: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val clipboard = LocalClipboardManager.current
    var ipIndex by remember { mutableIntStateOf(0) }
    val ips = st.lanIps
    val ip = ips.getOrNull(ipIndex.coerceAtMost((ips.size - 1).coerceAtLeast(0)))
    val url = ip?.let { st.lanUrl(it) } ?: ""          // tanpa PIN (untuk Salin)
    val qrUrl = ip?.let { st.qrUrl(it) } ?: ""        // PIN tertanam (scan langsung masuk)
    val project = st.project

    Column(Modifier.fillMaxSize().background(Vc.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { VIcon(Ic.BACK, Vc.Text) }
            Text("Bagikan ke HP siswa", style = vcText(20.sp, bold = true))
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (!st.running || !st.sharing || project == null) {
                Text("Server tidak sedang berbagi.", style = vcText(16.sp, color = Vc.TextSoft))
                return@Column
            }

            if (ip == null) {
                NoNetworkCard(onRefresh)
            } else {
                PinToggleCard(st.pinOn, onPin)
                Text("Siswa pindai kode ini dengan kamera HP", style = vcText(15.sp, color = Vc.TextSoft), textAlign = TextAlign.Center)
                QrImage(qrUrl)
                Text(project.record.name, style = vcText(18.sp, bold = true), textAlign = TextAlign.Center)
                if (st.pinOn) PinCard(st.pin, onChangePin)

                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Vc.Surface).padding(start = 16.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(url, style = TextStyle(fontFamily = VcFonts.mono, fontWeight = FontWeight.Medium, fontSize = 14.sp, color = Vc.Accent), modifier = Modifier.weight(1f))
                    Button(
                        onClick = {
                            clipboard.setText(AnnotatedString(url))
                            onToast(if (st.pinOn) "Alamat disalin (tanpa PIN)" else "Alamat disalin")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Vc.Surface2, contentColor = Vc.Text),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.height(44.dp)
                    ) { Text("Salin", style = vcText(14.sp)) }
                }

                if (ips.size > 1) {
                    Text("Alamat lain (pilih jika QR tidak bisa dibuka):", style = vcText(13.sp, color = Vc.Muted))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ips.forEachIndexed { i, a ->
                            val sel = i == ipIndex
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) Vc.Accent else Vc.Surface)
                                    .clickable { ipIndex = i }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                            ) { Text(a, style = vcText(13.sp, color = if (sel) Vc.Ink else Vc.TextSoft)) }
                        }
                    }
                }
            }

            // Statistik: perangkat terhubung, ukuran & perkiraan waktu
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("Terhubung", "${st.devices} perangkat", Modifier.weight(1f))
                StatCard("Ukuran proyek", formatBytes(project.totalBytes), Modifier.weight(1f))
            }
            val perStudent = if (st.transferBytes > 0) st.transferBytes else project.totalBytes
            val seconds = (perStudent.toDouble() * CLASS_SIZE / ASSUMED_BYTES_PER_SEC).toLong().coerceAtLeast(1)
            InfoCard(
                title = "Perkiraan waktu muat",
                body = "${formatBytes(perStudent)} per siswa setelah dikompresi · ±${formatDuration(seconds)} untuk $CLASS_SIZE siswa sekaligus. Minta siswa memindai bergiliran per baris.",
                color = Vc.Blue
            )

            // Cek kesiapan offline
            if (project.externalHosts.isEmpty()) {
                InfoCard("Siap tanpa internet", "Proyek tidak memanggil file dari luar. Aman lewat hotspot tanpa kuota.", Vc.Teal)
            } else {
                val shown = project.externalHosts.take(4).joinToString(", ")
                val more = if (project.externalHosts.size > 4) " dan ${project.externalHosts.size - 4} lainnya" else ""
                InfoCard(
                    "Butuh internet: ${project.externalHosts.size} sumber luar",
                    "Proyek memanggil $shown$more. Tanpa kuota internet, bagian itu gagal dimuat di HP siswa.",
                    Vc.Danger
                )
            }

            Text(
                "HP siswa harus satu WiFi dengan HP ini, atau tersambung ke hotspot HP ini. Hotspot HP umumnya hanya menerima sekitar 8–10 perangkat; untuk satu kelas gunakan router WiFi.",
                style = vcText(13.sp, color = Vc.Muted).copy(lineHeight = 19.sp),
                textAlign = TextAlign.Center
            )

            OutlineAction("Hentikan berbagi", Modifier.fillMaxWidth(), color = Vc.Danger, onClick = onStop)
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PinToggleCard(on: Boolean, onPin: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Vc.Surface)
            .clickable { onPin(!on) }.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text("Kunci dengan PIN", style = vcText(16.sp, bold = true))
            Spacer(Modifier.height(2.dp))
            Text(
                if (on) "Scan QR langsung masuk. Yang membuka lewat alamat diminta PIN."
                else "Opsional. Cegah yang iseng membuka tanpa izin.",
                style = vcText(13.sp, color = Vc.Muted).copy(lineHeight = 18.sp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Switch(
            checked = on, onCheckedChange = onPin,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Vc.Ink, checkedTrackColor = Vc.Accent,
                uncheckedThumbColor = Vc.Muted, uncheckedTrackColor = Vc.Surface2, uncheckedBorderColor = Vc.Border
            )
        )
    }
}

@Composable
private fun PinCard(pin: String, onChangePin: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Vc.Surface).padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("PIN", style = vcText(13.sp, bold = true, color = Vc.Accent).copy(letterSpacing = 1.sp))
        Text(
            pin,
            style = TextStyle(fontFamily = VcFonts.mono, fontWeight = FontWeight.ExtraBold, fontSize = 48.sp, color = Vc.Text, letterSpacing = 12.sp)
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onChangePin,
            colors = ButtonDefaults.buttonColors(containerColor = Vc.Surface2, contentColor = Vc.Text),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(44.dp)
        ) { Text("Ganti PIN", style = vcText(14.sp)) }
        Spacer(Modifier.height(10.dp))
        Text(
            "Ganti PIN: QR lama tidak berlaku, siswa yang sudah masuk tetap tersambung. Salah 5 kali, dikunci 1 menit. PIN hanya pencegah iseng, bukan enkripsi.",
            style = vcText(12.sp, color = Vc.Muted).copy(lineHeight = 17.sp), textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun QrImage(url: String) {
    val bmp = remember(url) { makeQr(url, 720) }
    Box(
        Modifier.clip(RoundedCornerShape(28.dp)).background(androidx.compose.ui.graphics.Color.White).padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        if (bmp != null) {
            Image(bmp.asImageBitmap(), contentDescription = "Kode QR $url", modifier = Modifier.size(220.dp), filterQuality = FilterQuality.None)
        }
        Box(
            Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Vc.Bg)
                .border(3.dp, androidx.compose.ui.graphics.Color.White, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) { VcvWordmark(15.sp) }
    }
}

private fun makeQr(text: String, size: Int): Bitmap? = runCatching {
    val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.H, EncodeHintType.MARGIN to 1)
    val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
    val px = IntArray(m.width * m.height)
    for (y in 0 until m.height) for (x in 0 until m.width) {
        px[y * m.width + x] = if (m[x, y]) 0xFF101017.toInt() else 0xFFFFFFFF.toInt()
    }
    Bitmap.createBitmap(px, m.width, m.height, Bitmap.Config.ARGB_8888)
}.getOrNull()

@Composable
private fun NoNetworkCard(onRefresh: () -> Unit) {
    InfoCard(
        "Belum ada WiFi atau hotspot",
        "Nyalakan hotspot HP ini atau sambungkan ke WiFi kelas, lalu tekan Periksa ulang.",
        Vc.Yellow
    )
    Button(
        onClick = onRefresh,
        colors = ButtonDefaults.buttonColors(containerColor = Vc.Accent, contentColor = Vc.Ink),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().height(52.dp)
    ) { Text("Periksa ulang", style = vcText(15.sp, bold = true, color = Vc.Ink)) }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(Vc.Surface).padding(14.dp)) {
        Text(label, style = vcText(13.sp, color = Vc.Muted))
        Spacer(Modifier.height(4.dp))
        Text(value, style = vcText(18.sp, bold = true))
    }
}

@Composable
private fun InfoCard(title: String, body: String, color: androidx.compose.ui.graphics.Color) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Vc.Surface).padding(14.dp)) {
        Box(Modifier.padding(top = 6.dp).size(10.dp).clip(RoundedCornerShape(5.dp)).background(color))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = vcText(15.sp, bold = true))
            Spacer(Modifier.height(4.dp))
            Text(body, style = vcText(13.sp, color = Vc.TextSoft).copy(lineHeight = 19.sp))
        }
    }
}

fun formatBytes(b: Long): String = when {
    b < 1024 -> "$b B"
    b < 1024 * 1024 -> String.format(Locale("id"), "%.0f KB", b / 1024.0)
    else -> String.format(Locale("id"), "%.1f MB", b / (1024.0 * 1024.0))
}

fun formatDuration(s: Long): String = when {
    s < 60 -> "$s detik"
    else -> "${s / 60} menit ${s % 60} detik"
}
