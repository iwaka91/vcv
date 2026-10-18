package id.tg.vcv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Semua dialog aplikasi, sesuai mockup layar 6. */
@Composable
fun VcvDialogs(d: DialogState, vm: MainViewModel) {
    when (d) {
        is DialogState.Info -> VDialog(
            mark = "!", tint = Color(0xFF3A1E1A), ink = Vc.Danger,
            title = d.title, body = d.body,
            primary = "Oke", onPrimary = { vm.dismissDialog() },
            onDismiss = { vm.dismissDialog() }
        )
        is DialogState.AltEntry -> {
            val file = d.pending.inspection.entry?.substringAfterLast('/') ?: "file HTML"
            VDialog(
                mark = "?", tint = Color(0xFF3A2A12), ink = Vc.Yellow,
                title = "index.html tidak ada",
                body = "Ditemukan $file di dalam proyek ini. Buka file tersebut?",
                primary = "Buka", onPrimary = { vm.answerAltEntry(d.pending, true) },
                secondary = "Batal", onSecondary = { vm.answerAltEntry(d.pending, false) },
                onDismiss = { vm.answerAltEntry(d.pending, false) }
            )
        }
        is DialogState.Replace -> VDialog(
            mark = "=", tint = Color(0xFF1E2A3A), ink = Color(0xFF7CC4FF),
            title = "Nama proyek sama",
            body = "${d.existing.name} sudah ada di daftar. Timpa dengan file baru?",
            primary = "Timpa", onPrimary = { vm.answerReplace(d.pending, d.existing, true) },
            secondary = "Abaikan", onSecondary = { vm.answerReplace(d.pending, d.existing, false) },
            onDismiss = { vm.answerReplace(d.pending, d.existing, false) }
        )
        is DialogState.SuggestFolder -> VDialog(
            mark = "?", tint = Color(0xFF3A2A12), ink = Vc.Yellow,
            title = "Ada file pendamping",
            body = "HTML ini memanggil ${d.pending.relativeRefs} file lain (gambar/CSS/JS) yang tidak bisa dibaca dari file tunggal. Gunakan Import Folder agar tampil lengkap?",
            primary = "Import Folder", onPrimary = { vm.answerSuggestFolder(d.pending, true) },
            secondary = "Tetap buka", onSecondary = { vm.answerSuggestFolder(d.pending, false) },
            onDismiss = { vm.answerSuggestFolder(d.pending, false) }
        )
        is DialogState.SourceMissing -> VDialog(
            mark = "×", tint = Color(0xFF3A1E1A), ink = Vc.Danger,
            title = "File asli tidak ditemukan",
            body = "File sudah dipindah, diganti nama, atau dihapus. Hapus proyek ini dari daftar?",
            primary = "Hapus", primaryColor = Vc.Danger, onPrimary = { vm.answerSourceMissing(d.record, true) },
            secondary = "Biarkan", onSecondary = { vm.answerSourceMissing(d.record, false) },
            onDismiss = { vm.answerSourceMissing(d.record, false) }
        )
        is DialogState.SharedGone -> VDialog(
            mark = "i", tint = Color(0xFF1E2A22), ink = Vc.TagShared,
            title = "Salinan sudah dihapus",
            body = "Salinan sudah dihapus untuk menghemat memori. Buka WhatsApp lalu bagikan ulang file ini ke VcV.",
            primary = "Oke", onPrimary = { vm.dismissDialog() },
            secondary = "Hapus dari daftar", onSecondary = { vm.dismissDialog(); vm.remove(d.record) },
            onDismiss = { vm.dismissDialog() }
        )
        is DialogState.StopSharing -> VDialog(
            mark = "!", tint = Color(0xFF3A2A12), ink = Vc.Yellow,
            title = "Hentikan berbagi?",
            body = "${d.current.name} sedang dibagikan ke HP siswa. Hentikan dan buka ${d.next.name}?",
            primary = "Hentikan & buka", onPrimary = { vm.answerStopSharing(d, true) },
            secondary = "Batal", onSecondary = { vm.answerStopSharing(d, false) },
            onDismiss = { vm.answerStopSharing(d, false) }
        )
        is DialogState.Rename -> RenameDialog(d.record.name, onSave = { vm.rename(d.record, it) }, onDismiss = { vm.dismissDialog() })
        DialogState.ConfirmClear -> VDialog(
            mark = "×", tint = Color(0xFF3A1E1A), ink = Vc.Danger,
            title = "Kosongkan daftar?",
            body = "Semua proyek hilang dari daftar terbaru. File asli di HP tidak ikut terhapus.",
            primary = "Kosongkan", primaryColor = Vc.Danger, onPrimary = { vm.clearAll() },
            secondary = "Batal", onSecondary = { vm.dismissDialog() },
            onDismiss = { vm.dismissDialog() }
        )
    }
}

@Composable
private fun VDialog(
    mark: String,
    tint: Color,
    ink: Color,
    title: String,
    body: String,
    primary: String,
    onPrimary: () -> Unit,
    onDismiss: () -> Unit,
    secondary: String? = null,
    onSecondary: (() -> Unit)? = null,
    primaryColor: Color = Vc.Accent,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vc.Surface,
        shape = RoundedCornerShape(28.dp),
        icon = {
            Box(Modifier.size(40.dp).background(tint, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                Text(mark, style = vcText(20.sp, bold = true, color = ink))
            }
        },
        title = { Text(title, style = vcText(20.sp, bold = true)) },
        text = { Text(body, style = vcText(15.sp, color = Vc.TextSoft).copy(lineHeight = 22.sp)) },
        confirmButton = {
            Button(
                onClick = onPrimary,
                colors = ButtonDefaults.buttonColors(containerColor = primaryColor, contentColor = Vc.Ink),
                shape = RoundedCornerShape(22.dp),
            ) { Text(primary, style = vcText(15.sp, bold = true, color = Vc.Ink)) }
        },
        dismissButton = {
            if (secondary != null && onSecondary != null) {
                TextButton(onClick = onSecondary) { Text(secondary, style = vcText(15.sp, color = Vc.TextSoft)) }
            }
        },
    )
}

@Composable
private fun RenameDialog(current: String, onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Vc.Surface,
        shape = RoundedCornerShape(28.dp),
        title = { Text("Ganti nama tampilan", style = vcText(20.sp, bold = true)) },
        text = {
            Column {
                Text("Nama file asli tidak berubah.", style = vcText(14.sp, color = Vc.Muted))
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it.take(60) },
                    singleLine = true,
                    textStyle = vcText(16.sp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Vc.Accent, unfocusedBorderColor = Vc.Border, cursorColor = Vc.Accent
                    ),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(text) },
                enabled = text.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = Vc.Accent, contentColor = Vc.Ink),
                shape = RoundedCornerShape(22.dp),
            ) { Text("Simpan", style = vcText(15.sp, bold = true, color = Vc.Ink)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal", style = vcText(15.sp, color = Vc.TextSoft)) } },
    )
}
