package id.tg.vcv

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Browser
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import id.tg.vcv.data.Kind
import id.tg.vcv.ui.AboutScreen
import id.tg.vcv.ui.BrowserScreen
import id.tg.vcv.ui.PagePickerSheet
import id.tg.vcv.ui.DialogState
import id.tg.vcv.ui.HomeScreen
import id.tg.vcv.ui.ImportSheet
import id.tg.vcv.ui.MainViewModel
import id.tg.vcv.ui.ProjectMenuSheet
import id.tg.vcv.ui.QrScreen
import id.tg.vcv.ui.Screen
import id.tg.vcv.ui.SettingsScreen
import id.tg.vcv.ui.Target
import id.tg.vcv.ui.UiEvent
import id.tg.vcv.ui.Vc
import id.tg.vcv.ui.VcvDialogs
import id.tg.vcv.ui.VcvTheme
import id.tg.vcv.ui.vcText

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            VcvTheme { App() }
        }
        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Menangani: file dari WhatsApp/File Manager, pintasan layar utama, tombol notifikasi. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = streamExtra(intent) ?: return
                vm.onSharedIntent(uri, intent.type, isFromWhatsApp())
            }
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                vm.onSharedIntent(uri, intent.type, isFromWhatsApp())
            }
            ACTION_OPEN_PROJECT -> intent.getStringExtra(EXTRA_PROJECT_ID)?.let { vm.openById(it) }
            ACTION_OPEN_ACTIVE -> vm.reopenActive()
            ACTION_SAVE_ACTIVE -> vm.startSave(null)
        }
        // Cegah intent yang sama diproses ulang saat layar diputar
        setIntent(Intent(this, MainActivity::class.java))
    }

    @Suppress("DEPRECATION")
    private fun streamExtra(intent: Intent): Uri? =
        if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        else intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri

    private fun isFromWhatsApp(): Boolean {
        val ref = referrer?.toString()?.lowercase() ?: return false
        return "whatsapp" in ref
    }

    /** Buka alamat di Chrome (atau browser lain bila Chrome tidak ada). */
    fun launchBrowser(url: String) {
        val base = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addCategory(Intent.CATEGORY_BROWSABLE)
            .putExtra(Browser.EXTRA_APPLICATION_ID, packageName) // Chrome memakai ulang tab yang sama
        try {
            startActivity(Intent(base).setPackage("com.android.chrome"))
        } catch (e: ActivityNotFoundException) {
            try {
                startActivity(base)
            } catch (e2: ActivityNotFoundException) {
                Toast.makeText(this, "Tidak ada browser di HP ini", Toast.LENGTH_LONG).show()
            }
        }
    }

    @Composable
    private fun App() {
        val screen by vm.screen.collectAsStateWithLifecycle()
        val projects by vm.projects.collectAsStateWithLifecycle()
        val dialog by vm.dialog.collectAsStateWithLifecycle()
        val busy by vm.busy.collectAsStateWithLifecycle()
        val menuFor by vm.menuFor.collectAsStateWithLifecycle()
        val importSheet by vm.importSheet.collectAsStateWithLifecycle()
        val server by vm.server.collectAsStateWithLifecycle()
        val settings by vm.settings.collectAsStateWithLifecycle()
        val pagePick by vm.pagePick.collectAsStateWithLifecycle()

        // ---- pemilih file
        val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.onPicked(Kind.ZIP, it) }
        val pickHtml = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { vm.onPicked(Kind.HTML, it) }
        val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { vm.onPicked(Kind.FOLDER, it) }
        val saveZip = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { vm.onSaveTarget(it) }
        val saveHtml = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/html")) { vm.onSaveTarget(it) }
        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        LaunchedEffect(Unit) {
            vm.events.collect { e ->
                when (e) {
                    is UiEvent.LaunchBrowser -> launchBrowser(e.url)
                    UiEvent.PickZip -> safeLaunch {
                        pickZip.launch(arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream"))
                    }
                    UiEvent.PickHtml -> safeLaunch { pickHtml.launch(arrayOf("text/html", "application/xhtml+xml")) }
                    UiEvent.PickFolder -> safeLaunch { pickFolder.launch(null) }
                    is UiEvent.SaveZip -> safeLaunch { saveZip.launch(e.suggestedName) }
                    is UiEvent.SaveHtml -> safeLaunch { saveHtml.launch(e.suggestedName) }
                    is UiEvent.Toast -> Toast.makeText(this@MainActivity, e.message, Toast.LENGTH_SHORT).show()
                }
            }
        }

        Box(Modifier.fillMaxSize().background(Vc.Bg)) {
            when (screen) {
                Screen.HOME -> HomeScreen(
                    projects = projects,
                    onOpen = { vm.openProject(it, Target.DEFAULT) },
                    onMenu = { vm.showMenu(it) },
                    onImport = { vm.showImportSheet(true) },
                    onPick = { vm.requestPick(it) },
                    onSettings = { vm.go(Screen.SETTINGS) },
                )
                Screen.QR -> QrScreen(
                    st = server,
                    onBack = { vm.go(Screen.HOME) },
                    onStop = { vm.stopSharing() },
                    onRefresh = { vm.refreshLan() },
                    onToast = { Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show() },
                    onPin = { vm.setPin(it) },
                    onChangePin = { vm.changePin() },
                )
                Screen.BROWSER -> {
                    val p = server.project
                    if (server.running && p != null) {
                        BrowserScreen(
                            title = p.record.name,
                            url = server.localUrl,
                            onClose = { vm.go(Screen.HOME) },
                            onOpenChrome = { launchBrowser(it) },
                        )
                    } else {
                        LaunchedEffect(Unit) { vm.go(Screen.HOME) }
                    }
                }
                Screen.SETTINGS -> SettingsScreen(
                    s = settings,
                    onBack = { vm.go(Screen.HOME) },
                    onBrowser = { vm.setBrowser(it) },
                    onIdle = { vm.setIdle(it) },
                    onPort = { vm.setPort(it) },
                    onClear = { vm.showDialog(DialogState.ConfirmClear) },
                    onPageDefault = { vm.setPageDefault(it) },
                    onAbout = { vm.go(Screen.ABOUT) },
                )
                Screen.ABOUT -> AboutScreen(
                    onBack = { vm.go(Screen.SETTINGS) },
                    onOpenUrl = { launchBrowser(it) },
                )
            }

            if (importSheet) {
                ImportSheet(onDismiss = { vm.showImportSheet(false) }, onPick = { vm.requestPick(it) })
            }

            menuFor?.let { r ->
                ProjectMenuSheet(
                    r = r,
                    server = server,
                    onDismiss = { vm.showMenu(null) },
                    onChrome = { vm.openProject(r, Target.CHROME) },
                    onInternal = { vm.openProject(r, Target.INTERNAL) },
                    onQr = { vm.openProject(r, Target.QR) },
                    onShortcut = { vm.addShortcut(r) },
                    onRename = { vm.showMenu(null); vm.showDialog(DialogState.Rename(r)) },
                    onDelete = { vm.remove(r) },
                    onSave = { vm.startSave(r) },
                    onPickPage = { vm.pickPage(r) },
                )
            }

            pagePick?.let { pick ->
                PagePickerSheet(
                    pick = pick,
                    onPick = { vm.answerPagePick(pick, it) },
                    onDismiss = { vm.answerPagePick(pick, null) },
                )
            }

            dialog?.let { VcvDialogs(it, vm) }

            busy?.let { msg ->
                Box(
                    Modifier.fillMaxSize().background(Color(0xCC08080C)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                        CircularProgressIndicator(color = Vc.Accent)
                        Spacer(Modifier.height(16.dp))
                        Text(msg, style = vcText(15.sp))
                    }
                }
            }
        }
    }

    private inline fun safeLaunch(block: () -> Unit) {
        try {
            block()
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Pemilih file tidak tersedia di HP ini", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        const val ACTION_OPEN_PROJECT = "id.tg.vcv.action.OPEN_PROJECT"
        const val ACTION_OPEN_ACTIVE = "id.tg.vcv.action.OPEN_ACTIVE"
        const val ACTION_SAVE_ACTIVE = "id.tg.vcv.action.SAVE_ACTIVE"
        const val EXTRA_PROJECT_ID = "project_id"
    }
}
