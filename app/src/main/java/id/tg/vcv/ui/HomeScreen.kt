package id.tg.vcv.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import id.tg.vcv.data.Kind
import id.tg.vcv.data.ProjectRecord
import id.tg.vcv.data.ProjectStore
import id.tg.vcv.server.ServerState

@Composable
fun HomeScreen(
    projects: List<ProjectRecord>,
    onOpen: (ProjectRecord) -> Unit,
    onMenu: (ProjectRecord) -> Unit,
    onImport: () -> Unit,
    onPick: (Kind) -> Unit,
    onSettings: () -> Unit,
) {
    Box(Modifier.fillMaxSize().background(Vc.Bg)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            HomeHeader(onSettings)
            if (projects.isEmpty()) {
                EmptyState(onPick)
            } else {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Text("Terbaru", style = vcText(18.sp, bold = true))
                    Text("${projects.size} dari ${ProjectStore.MAX}", style = vcText(13.sp, color = Vc.Muted))
                }
                LazyColumn(
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 140.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(projects, key = { it.id }) { r ->
                        ProjectRow(r, onClick = { onOpen(r) }, onMenu = { onMenu(r) })
                    }
                }
            }
        }
        if (projects.isNotEmpty()) {
            Button(
                onClick = onImport,
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Vc.Accent, contentColor = Vc.Ink),
                contentPadding = PaddingValues(start = 18.dp, end = 22.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(start = 20.dp, end = 20.dp, bottom = 40.dp)
                    .height(56.dp)
            ) {
                VIcon(Ic.PLUS, Vc.Ink, 22.dp)
                Spacer(Modifier.width(10.dp))
                Text("Import", style = vcText(16.sp, bold = true, color = Vc.Ink))
            }
        }
        VcvFooter(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Vc.Bg)
                .navigationBarsPadding()
                .padding(vertical = 10.dp)
        )
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(64.dp).padding(start = 20.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        VcvWordmark(30.sp)
        Spacer(Modifier.width(10.dp))
        Text("Vibe Coding Viewer", style = vcText(13.sp, color = Vc.Muted), modifier = Modifier.weight(1f))
        IconButton(onClick = onSettings, modifier = Modifier.size(48.dp)) {
            VIcon(Ic.SETTINGS, Vc.Text)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProjectRow(r: ProjectRecord, onClick: () -> Unit, onMenu: () -> Unit) {
    val sub = if (r.isSharedCopyGone) "Sekali buka · bagikan ulang dari WhatsApp"
    else "${r.sourceLabel} · ${relativeTime(r.lastOpened)}"
    Row(
        Modifier
            .fillMaxWidth()
            .height(72.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Vc.Surface)
            .combinedClickable(onClick = onClick, onLongClick = onMenu)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        KindBadge(r)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                r.name, style = vcText(16.sp, color = if (r.isSharedCopyGone) Vc.Muted else Vc.Text),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(sub, style = vcText(13.sp, color = Vc.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onMenu, modifier = Modifier.size(48.dp)) {
            VIcon(Ic.MORE, Vc.Muted, 20.dp)
        }
    }
}

@Composable
private fun EmptyState(onPick: (Kind) -> Unit) {
    Column(
        Modifier.fillMaxSize().navigationBarsPadding().padding(start = 28.dp, end = 28.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(Modifier.width(300.dp).height(230.dp), contentAlignment = Alignment.Center) {
            Sticker("JS", Vc.Yellow, -14f, Modifier.align(Alignment.TopStart).padding(start = 22.dp, top = 18.dp))
            Sticker("</>", Vc.Accent, 10f, Modifier.align(Alignment.TopEnd).padding(end = 26.dp, top = 10.dp))
            Sticker("CSS", Vc.Purple, 8f, Modifier.align(Alignment.BottomStart).padding(start = 30.dp, bottom = 22.dp), 15.sp)
            Sticker("{ }", Vc.Teal, -9f, Modifier.align(Alignment.BottomEnd).padding(end = 34.dp, bottom = 16.dp))
            VcvWordmark(84.sp)
        }
        Spacer(Modifier.height(28.dp))
        Text("Belum ada proyek", style = vcText(22.sp, bold = true))
        Spacer(Modifier.height(8.dp))
        Text(
            "Import file ZIP, HTML, atau folder hasil vibe coding untuk mulai.",
            style = vcText(15.sp, color = Vc.TextSoft), textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = { onPick(Kind.ZIP) },
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Vc.Accent, contentColor = Vc.Ink),
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) { Text("Import ZIP", style = vcText(16.sp, bold = true, color = Vc.Ink)) }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineAction("File HTML", Modifier.weight(1f)) { onPick(Kind.HTML) }
            OutlineAction("Folder", Modifier.weight(1f)) { onPick(Kind.FOLDER) }
        }
        Spacer(Modifier.height(28.dp))
        Text(
            "Bisa juga dari WhatsApp: tekan file lalu Bagikan, pilih VcV.",
            style = vcText(13.sp, color = Vc.Muted), textAlign = TextAlign.Center
        )
    }
}

@Composable
fun OutlineAction(label: String, modifier: Modifier = Modifier, color: Color = Vc.Text, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, if (color == Vc.Text) Vc.Border else color),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
        modifier = modifier.height(52.dp)
    ) { Text(label, style = vcText(15.sp, color = color)) }
}

// ------------------------------------------------------------------ Sheet: pilih jenis import

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(onDismiss: () -> Unit, onPick: (Kind) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vc.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Import proyek", style = vcText(20.sp, bold = true), modifier = Modifier.padding(start = 8.dp, bottom = 8.dp))
            ImportOption("ZIP", Vc.TagZip, "File ZIP", "Proyek lengkap: HTML, CSS, JS, gambar dalam satu file") { onPick(Kind.ZIP) }
            ImportOption("HTML", Vc.TagHtml, "File HTML tunggal", "Satu file .html yang berdiri sendiri") { onPick(Kind.HTML) }
            ImportOption("DIR", Vc.TagDir, "Folder proyek", "Folder berisi index.html dan file pendukungnya") { onPick(Kind.FOLDER) }
            Text(
                "VcV hanya mengingat lokasi file. File asli tetap di tempatnya.",
                style = vcText(13.sp, color = Vc.Muted),
                modifier = Modifier.padding(start = 8.dp, top = 10.dp)
            )
        }
    }
}

@Composable
private fun ImportOption(tag: String, color: Color, title: String, desc: String, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = ButtonDefaults.textButtonColors(containerColor = Vc.Surface2),
        contentPadding = PaddingValues(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            Modifier.size(48.dp).background(color, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) { Text(tag, style = vcText(13.sp, bold = true, color = Vc.Ink).copy(fontFamily = VcFonts.mono)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = vcText(16.sp, bold = true))
            Spacer(Modifier.height(4.dp))
            Text(desc, style = vcText(13.sp, color = Vc.TextSoft))
        }
    }
}

// ------------------------------------------------------------------ Sheet: menu tekan lama

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectMenuSheet(
    r: ProjectRecord,
    server: ServerState,
    onDismiss: () -> Unit,
    onChrome: () -> Unit,
    onInternal: () -> Unit,
    onQr: () -> Unit,
    onShortcut: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    onPickPage: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vc.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 28.dp)) {
            Row(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                KindBadge(r)
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(r.name, style = vcText(17.sp, bold = true), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (r.isSharedCopyGone) "Sekali buka · bagikan ulang dari WhatsApp" else r.sourceLabel,
                        style = vcText(13.sp, color = Vc.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2C2C3A)))
            Spacer(Modifier.height(8.dp))

            val canSave = r.kind == Kind.SHARED && !r.isSharedCopyGone
            if (canSave) MenuItem(Ic.SAVE, "Simpan Proyek", Vc.Yellow, bold = true, onClick = onSave)
            MenuItem(Ic.EXTERNAL, "Buka di Chrome", Vc.Accent, bold = true, onClick = onChrome)
            MenuItem(Ic.WINDOW, "Buka di peramban internal", Vc.Text, onClick = onInternal)
            MenuItem(Ic.QR, "Bagikan ke HP siswa (QR)", Vc.Text, onClick = onQr)
            if (r.canHaveManyPages && !r.isSharedCopyGone) MenuItem(Ic.PAGES, "Pilih halaman…", Vc.Text, onClick = onPickPage)
            MenuItem(Ic.HOME, "Tambah ke layar utama HP", Vc.Text, onClick = onShortcut)
            MenuItem(Ic.PENCIL, "Ganti nama tampilan", Vc.Text, onClick = onRename)
            MenuItem(Ic.TRASH, "Hapus dari daftar", Vc.Danger, onClick = onDelete)
        }
    }
}

@Composable
private fun MenuItem(icon: Ic, label: String, color: Color, bold: Boolean = false, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        contentPadding = PaddingValues(horizontal = 14.dp),
        modifier = Modifier.fillMaxWidth().height(56.dp)
    ) {
        VIcon(icon, color, 22.dp)
        Spacer(Modifier.width(16.dp))
        Text(label, style = vcText(16.sp, bold = bold, color = color), modifier = Modifier.weight(1f))
    }
}

fun relativeTime(ts: Long): String {
    val d = (System.currentTimeMillis() - ts).coerceAtLeast(0) / 1000
    return when {
        d < 60 -> "baru saja"
        d < 3600 -> "${d / 60} menit lalu"
        d < 86_400 -> "${d / 3600} jam lalu"
        d < 2 * 86_400 -> "kemarin"
        d < 7 * 86_400 -> "${d / 86_400} hari lalu"
        d < 30 * 86_400 -> "${d / (7 * 86_400)} minggu lalu"
        else -> "${d / (30 * 86_400)} bulan lalu"
    }
}
