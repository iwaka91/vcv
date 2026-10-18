package id.tg.vcv.server

import android.annotation.SuppressLint
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import id.tg.vcv.MainActivity
import id.tg.vcv.R
import id.tg.vcv.VcvApp
import id.tg.vcv.data.Kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground service yang menjaga server tetap hidup saat Bapak pindah ke Chrome.
 * - Notifikasi berisi status + tombol Hentikan / Buka lagi / Simpan Proyek.
 * - Berhenti otomatis bila tidak ada permintaan selama N menit.
 * - Saat berbagi, HP dijaga tetap "terjaga" (wake lock + WiFi lock).
 */
class VcvServerService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            ServerController.stop()
            stopForegroundCompat()
            stopSelf()
            return START_NOT_STICKY
        }
        val st = ServerController.state.value
        if (!st.running) {
            stopSelf()
            return START_NOT_STICKY
        }
        startForegroundCompat(buildNotification(st))
        if (!started) {
            started = true
            observe()
            idleWatch()
        }
        return START_NOT_STICKY
    }

    private fun observe() {
        scope.launch {
            ServerController.state.collectLatest { st ->
                if (!st.running) {
                    releaseLocks()
                    stopForegroundCompat()
                    stopSelf()
                    return@collectLatest
                }
                if (st.sharing) acquireLocks() else releaseLocks()
                notifySafe(buildNotification(st))
            }
        }
    }

    private fun idleWatch() {
        scope.launch {
            while (true) {
                delay(15_000)
                val st = ServerController.state.value
                if (!st.running) break
                val idleMs = VcvApp.instance.settings.current.idleMinutes * 60_000L
                if (System.currentTimeMillis() - ServerController.lastActivity.get() > idleMs) {
                    ServerController.stop()
                    break
                }
                // Perbarui teks "berhenti otomatis ... lagi"
                notifySafe(buildNotification(st))
            }
        }
    }

    private fun buildNotification(st: ServerState): Notification {
        val name = st.project?.record?.name ?: "Proyek"
        val idleMin = VcvApp.instance.settings.current.idleMinutes
        val idleLeftMs = idleMin * 60_000L - (System.currentTimeMillis() - ServerController.lastActivity.get())
        val leftMin = ((idleLeftMs + 59_999) / 60_000).coerceIn(0, idleMin.toLong())
        val text = if (st.sharing) {
            "Berbagi ke HP siswa · ${st.devices} perangkat · berhenti otomatis $leftMin menit lagi jika tidak dipakai"
        } else {
            "Berhenti otomatis $leftMin menit lagi jika tidak dipakai"
        }

        val piFlags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val contentIntent = PendingIntent.getActivity(
            this, 10, Intent(this, MainActivity::class.java), piFlags
        )
        val stopIntent = PendingIntent.getService(
            this, 11, Intent(this, VcvServerService::class.java).setAction(ACTION_STOP), piFlags
        )
        val reopenIntent = PendingIntent.getActivity(
            this, 12, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_OPEN_ACTIVE), piFlags
        )

        val b = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vcv)
            .setColor(0xFFFF7A1A.toInt())
            .setContentTitle("Server aktif · $name")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent)
            .addAction(0, "Hentikan", stopIntent)
            .addAction(0, "Buka lagi", reopenIntent)

        val record = st.project?.record
        if (record != null && record.kind == Kind.SHARED && !record.copyDeleted) {
            val saveIntent = PendingIntent.getActivity(
                this, 13, Intent(this, MainActivity::class.java).setAction(MainActivity.ACTION_SAVE_ACTIVE), piFlags
            )
            b.addAction(0, "Simpan Proyek", saveIntent)
        }
        return b.build()
    }

    @SuppressLint("MissingPermission")
    private fun notifySafe(n: Notification) {
        runCatching { NotificationManagerCompat.from(this).notify(NOTIF_ID, n) }
    }

    private fun startForegroundCompat(n: Notification) {
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun stopForegroundCompat() {
        if (Build.VERSION.SDK_INT >= 24) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
    }

    @Suppress("DEPRECATION")
    private fun acquireLocks() {
        if (wakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "vcv:server").apply {
                setReferenceCounted(false); acquire(4 * 60 * 60 * 1000L)
            }
        }
        if (wifiLock == null) {
            val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "vcv:server").apply {
                setReferenceCounted(false); acquire()
            }
        }
    }

    private fun releaseLocks() {
        wakeLock?.let { runCatching { if (it.isHeld) it.release() } }
        wakeLock = null
        wifiLock?.let { runCatching { if (it.isHeld) it.release() } }
        wifiLock = null
    }

    override fun onDestroy() {
        releaseLocks()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val CHANNEL_ID = "vcv_server"
        const val NOTIF_ID = 7
        const val ACTION_START = "id.tg.vcv.action.START_SERVER"
        const val ACTION_STOP = "id.tg.vcv.action.STOP_SERVER"

        fun start(context: Context) {
            val i = Intent(context, VcvServerService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, i)
        }

        fun stop(context: Context) {
            val i = Intent(context, VcvServerService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(i) }
        }
    }
}
