package id.tg.vcv

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import id.tg.vcv.data.Kind
import id.tg.vcv.data.ProjectPreparer
import id.tg.vcv.data.ProjectStore
import id.tg.vcv.data.SettingsStore
import id.tg.vcv.server.ServerController
import id.tg.vcv.server.VcvServerService
import java.io.File

class VcvApp : Application() {

    lateinit var store: ProjectStore
        private set
    lateinit var settings: SettingsStore
        private set
    lateinit var preparer: ProjectPreparer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        store = ProjectStore(this)
        settings = SettingsStore(this)
        preparer = ProjectPreparer(this)

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                VcvServerService.CHANNEL_ID,
                getString(R.string.channel_server),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Status server saat proyek dibuka atau dibagikan" }
        )

        // Proyek ditutup → hapus salinan sementara file WhatsApp, tapi tetap tampil di daftar.
        ServerController.onProjectClosed = { project ->
            val r = store.get(project.record.id)
            if (r != null && r.kind == Kind.SHARED && r.copyPath.isNotEmpty()) {
                runCatching { File(r.copyPath).delete() }
                store.update(r.copy(copyPath = "", copyDeleted = true))
            }
            // Hapus folder ekstrak milik proyek yang ditutup (bukan milik proyek baru)
            if (ServerController.state.value.project?.record?.id != project.record.id) {
                runCatching { File(File(cacheDir, "serve"), project.record.id).deleteRecursively() }
            }
        }

        // Sisa folder sementara dari sesi sebelumnya (mis. aplikasi dimatikan paksa)
        if (!ServerController.state.value.running) {
            preparer.clearServeDir()
            cleanOrphanCopies()
        }
    }

    /** Salinan WhatsApp yang tertinggal (aplikasi ditutup paksa) dianggap sudah ditutup. */
    private fun cleanOrphanCopies() {
        store.items.value.filter { it.kind == Kind.SHARED && it.copyPath.isNotEmpty() }.forEach { r ->
            runCatching { File(r.copyPath).delete() }
            store.update(r.copy(copyPath = "", copyDeleted = true))
        }
        // File di folder shared yang tidak tercatat
        val known = store.items.value.map { it.copyPath }.toSet()
        File(filesDir, "shared").listFiles()?.forEach { if (it.absolutePath !in known) it.delete() }
    }

    companion object {
        lateinit var instance: VcvApp
            private set
    }
}
