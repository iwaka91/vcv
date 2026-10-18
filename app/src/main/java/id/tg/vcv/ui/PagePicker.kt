package id.tg.vcv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Lembar "Pilih halaman": daftar semua halaman HTML, dikelompokkan per folder. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PagePickerSheet(pick: PagePick, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    var selected by remember(pick) { mutableStateOf(pick.selected) }
    var query by remember(pick) { mutableStateOf("") }
    val shown = remember(pick, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) pick.pages else pick.pages.filter { it.lowercase().contains(q) }
    }
    val groups = remember(shown) { shown.groupBy { it.substringBeforeLast('/', "") } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Vc.Surface,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
            Text("Pilih halaman", style = vcText(20.sp, bold = true), modifier = Modifier.padding(start = 8.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                "${pick.title} · ${pick.pages.size} halaman",
                style = vcText(13.sp, color = Vc.Muted), maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 8.dp)
            )
            Spacer(Modifier.height(12.dp))

            if (pick.pages.size > 8) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it.take(40) },
                    singleLine = true,
                    placeholder = { Text("Cari halaman…", style = vcText(15.sp, color = Vc.Muted)) },
                    textStyle = vcText(15.sp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Vc.Accent, unfocusedBorderColor = Vc.Border, cursorColor = Vc.Accent
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
            }

            LazyColumn(
                Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                if (shown.isEmpty()) {
                    item { Text("Tidak ada halaman yang cocok.", style = vcText(14.sp, color = Vc.Muted), modifier = Modifier.padding(8.dp)) }
                }
                groups.forEach { (folder, pages) ->
                    if (groups.size > 1 || folder.isNotEmpty()) {
                        item(key = "f:$folder") {
                            Text(
                                if (folder.isEmpty()) "/ (folder utama)" else "$folder/",
                                style = TextStyle(fontFamily = VcFonts.mono, fontSize = 12.sp, color = Vc.AccentSoft),
                                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 2.dp)
                            )
                        }
                    }
                    items(pages, key = { "p:$it" }) { page ->
                        PageRow(page, page == selected) { selected = page }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Button(
                onClick = { onPick(selected) },
                enabled = selected in pick.pages,
                colors = ButtonDefaults.buttonColors(containerColor = Vc.Accent, contentColor = Vc.Ink),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) { Text("Buka halaman ini", style = vcText(16.sp, bold = true, color = Vc.Ink)) }
        }
    }
}

@Composable
private fun PageRow(path: String, selected: Boolean, onClick: () -> Unit) {
    val name = path.substringAfterLast('/')
    val isIndex = name.lowercase().startsWith("index.")
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) Vc.Surface2 else Vc.Bg)
            .border(1.5.dp, if (selected) Vc.Accent else Vc.Bg, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(20.dp).clip(RoundedCornerShape(10.dp))
                .border(2.dp, if (selected) Vc.Accent else Vc.Border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(Vc.Accent))
        }
        Spacer(Modifier.width(12.dp))
        Text(
            name, style = vcText(15.sp, bold = selected),
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        if (isIndex) {
            Spacer(Modifier.width(8.dp))
            Text(
                "utama", style = vcText(12.sp, color = Vc.Ink),
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Vc.Yellow).padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
    }
}
