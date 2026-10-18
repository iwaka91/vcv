package id.tg.vcv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tg.vcv.data.AppSettings
import id.tg.vcv.data.BrowserChoice
import id.tg.vcv.data.PageDefault
import id.tg.vcv.data.SettingsStore

@Composable
fun SettingsScreen(
    s: AppSettings,
    onBack: () -> Unit,
    onBrowser: (BrowserChoice) -> Unit,
    onIdle: (Int) -> Unit,
    onPort: (Int) -> Unit,
    onClear: () -> Unit,
    onPageDefault: (PageDefault) -> Unit,
    onAbout: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(Vc.Bg).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(64.dp).padding(start = 4.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.size(48.dp)) { VIcon(Ic.BACK, Vc.Text) }
            Text("Pengaturan", style = vcText(20.sp, bold = true))
        }
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Section("MEMBUKA PROYEK") {
                Text("Peramban bawaan", style = vcText(16.sp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Vc.Surface).padding(4.dp),
                ) {
                    Segment("Chrome", s.browser == BrowserChoice.CHROME, Modifier.weight(1f)) { onBrowser(BrowserChoice.CHROME) }
                    Segment("Internal", s.browser == BrowserChoice.INTERNAL, Modifier.weight(1f)) { onBrowser(BrowserChoice.INTERNAL) }
                }
                Spacer(Modifier.height(6.dp))
                Text("Halaman yang dibuka", style = vcText(16.sp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Vc.Surface).padding(4.dp),
                ) {
                    Segment("Terakhir dipilih", s.pageDefault == PageDefault.LAST, Modifier.weight(1f)) { onPageDefault(PageDefault.LAST) }
                    Segment("Selalu index.html", s.pageDefault == PageDefault.INDEX, Modifier.weight(1f)) { onPageDefault(PageDefault.INDEX) }
                }
                Text("Untuk proyek dengan banyak halaman. Pilih ulang lewat tekan lama atau titik tiga, lalu Pilih halaman.", style = vcText(13.sp, color = Vc.Muted))
            }

            Section("SERVER") {
                Text("Berhenti otomatis jika tidak dipakai", style = vcText(16.sp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsStore.IDLE_OPTIONS.forEach { m ->
                        val sel = s.idleMinutes == m
                        OutlinedButton(
                            onClick = { onIdle(m) },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.5.dp, if (sel) Vc.Accent else Vc.Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = if (sel) androidx.compose.ui.graphics.Color(0xFF2A1C10) else androidx.compose.ui.graphics.Color.Transparent,
                                contentColor = if (sel) Vc.AccentSoft else Vc.TextSoft
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                            modifier = Modifier.weight(1f).height(44.dp)
                        ) { Text("$m mnt", style = vcText(14.sp, bold = sel, color = if (sel) Vc.AccentSoft else Vc.TextSoft)) }
                    }
                }
                PortField(s.port, onPort)
                Text("Port dasar. Tiap proyek memakai port sendiri (port dasar + nomor urut) agar datanya terpisah.", style = vcText(13.sp, color = Vc.Muted))
            }

            Section("DAFTAR TERBARU") {
                TextButton(
                    onClick = onClear,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.textButtonColors(containerColor = Vc.Surface, contentColor = Vc.Danger),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) { Text("Kosongkan daftar", style = vcText(15.sp, color = Vc.Danger), modifier = Modifier.fillMaxWidth()) }
                Text("File asli di HP tidak ikut terhapus.", style = vcText(13.sp, color = Vc.Muted))
            }

            Section("TENTANG") {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Vc.Surface)
                        .clickable(onClick = onAbout).padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VcvWordmark(22.sp)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Tentang Vibe Coding Viewer", style = vcText(15.sp))
                        Text("Versi, lisensi, pembuat", style = vcText(12.sp, color = Vc.Muted))
                    }
                    Text("›", style = vcText(22.sp, color = Vc.Muted))
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = vcText(13.sp, bold = true, color = Vc.Accent).copy(letterSpacing = 1.sp))
        content()
    }
}

@Composable
private fun Segment(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val long = label.length > 10
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Vc.Accent else androidx.compose.ui.graphics.Color.Transparent,
            contentColor = if (selected) Vc.Ink else Vc.TextSoft
        ),
        elevation = null,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (long) 4.dp else 16.dp),
        modifier = modifier.height(44.dp)
    ) {
        Text(
            label, maxLines = 1, softWrap = false,
            style = vcText(if (long) 13.sp else 15.sp, bold = selected, color = if (selected) Vc.Ink else Vc.TextSoft)
        )
    }
}

@Composable
private fun PortField(port: Int, onPort: (Int) -> Unit) {
    var text by remember(port) { mutableStateOf(port.toString()) }
    val valid = text.toIntOrNull()?.let { it in 1024..65535 } == true
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = text,
            onValueChange = { v -> text = v.filter { it.isDigit() }.take(5) },
            label = { Text("Port server") },
            singleLine = true,
            isError = !valid,
            textStyle = TextStyle(fontFamily = VcFonts.mono, fontSize = 15.sp, color = Vc.AccentSoft),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Vc.Accent, unfocusedBorderColor = Vc.Border, cursorColor = Vc.Accent,
                focusedLabelColor = Vc.Accent, unfocusedLabelColor = Vc.Muted
            ),
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(10.dp))
        Button(
            onClick = { text.toIntOrNull()?.let(onPort) },
            enabled = valid && text != port.toString(),
            colors = ButtonDefaults.buttonColors(containerColor = Vc.Accent, contentColor = Vc.Ink),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(52.dp)
        ) { Text("Simpan", style = vcText(14.sp, bold = true, color = Vc.Ink)) }
    }
}
