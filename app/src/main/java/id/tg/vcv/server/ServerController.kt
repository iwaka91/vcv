package id.tg.vcv.server

import android.net.Uri
import id.tg.vcv.data.PreparedProject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

data class ServerState(
    val running: Boolean = false,
    val project: PreparedProject? = null,
    val port: Int = 0,
    val sharing: Boolean = false,
    val lanIps: List<String> = emptyList(),
    val devices: Int = 0,
    /** Perkiraan byte yang diunduh tiap siswa (sesudah gzip). */
    val transferBytes: Long = 0,
    /** Kunci PIN aktif untuk berbagi. */
    val pinOn: Boolean = false,
    val pin: String = "",
) {
    val localUrl: String
        get() = project?.let { "http://127.0.0.1:$port/" + Uri.encode(it.entryName, "/") } ?: ""

    /** Alamat untuk HP siswa, tanpa PIN (untuk tombol Salin). */
    fun lanUrl(ip: String): String =
        project?.let { "http://$ip:$port/" + Uri.encode(it.entryName, "/") } ?: ""

    /** Alamat di dalam QR: PIN ikut tertanam, jadi scan langsung masuk. */
    fun qrUrl(ip: String): String =
        lanUrl(ip).let { if (pinOn && it.isNotEmpty()) "$it?pin=$pin" else it }
}

/** Satu-satunya server aktif di aplikasi (satu proyek dalam satu waktu). */
object ServerController {

    private val _state = MutableStateFlow(ServerState())
    val state: StateFlow<ServerState> = _state.asStateFlow()

    private var server: VcvHttpServer? = null
    private val gate = PinGate()
    private val devices = ConcurrentHashMap.newKeySet<String>()
    val lastActivity = AtomicLong(System.currentTimeMillis())

    /** Dipanggil saat sebuah proyek "ditutup" (server berhenti atau diganti proyek lain). */
    var onProjectClosed: ((PreparedProject) -> Unit)? = null

    @Synchronized
    @Throws(IOException::class)
    fun start(project: PreparedProject, preferredPort: Int, sharing: Boolean, pinOn: Boolean = false): ServerState {
        val previous = _state.value.project
        val keepPinSession = sharing && _state.value.sharing && previous?.record?.id == project.record.id
        stopInternal()
        if (!keepPinSession) gate.reset()
        gate.enable(sharing && pinOn)
        if (previous != null && previous.record.id != project.record.id) {
            onProjectClosed?.invoke(previous)
        }
        devices.clear()
        lastActivity.set(System.currentTimeMillis())

        var lastError: IOException? = null
        // Port cadangan melompat 100 agar tidak memakai port (= data) milik proyek lain.
        val candidates = (0..20).map { preferredPort + it * 100 }.filter { it in 1024..65535 }
        for (port in candidates) {
            val host = if (sharing) null else "127.0.0.1"
            val s = VcvHttpServer(host, port, project.rootDir, project.entryName, ::onRequest, gate)
            try {
                s.start(VcvHttpServer.SOCKET_TIMEOUT_MS, true)
                server = s
                val transfer = if (sharing) s.warmUp() else 0L
                val st = ServerState(
                    running = true,
                    project = project,
                    port = port,
                    sharing = sharing,
                    lanIps = if (sharing) NetUtil.lanAddresses() else emptyList(),
                    devices = 0,
                    transferBytes = transfer,
                    pinOn = gate.enabled,
                    pin = gate.pin,
                )
                _state.value = st
                return st
            } catch (e: IOException) {
                lastError = e
                runCatching { s.stop() }
            }
        }
        throw lastError ?: IOException("Port tidak tersedia")
    }

    /** Hidupkan/matikan mode berbagi untuk proyek yang sedang aktif. */
    @Synchronized
    @Throws(IOException::class)
    fun setSharing(sharing: Boolean, preferredPort: Int): ServerState {
        val st = _state.value
        val p = st.project ?: throw IOException("Tidak ada proyek aktif")
        if (st.running && st.sharing == sharing) return st
        return start(p, if (st.port > 0) st.port else preferredPort, sharing, st.pinOn)
    }

    /** Nyalakan/matikan kunci PIN tanpa menghentikan server. */
    fun setPin(on: Boolean) {
        val st = _state.value
        gate.enable(on && st.sharing)
        _state.value = st.copy(pinOn = gate.enabled, pin = gate.pin)
    }

    /** Ganti PIN: QR lama tidak berlaku, siswa yang sudah masuk tetap tersambung. */
    fun changePin() {
        gate.change()
        _state.value = _state.value.copy(pin = gate.pin)
    }

    /** Perbarui data proyek aktif (mis. setelah "Simpan Proyek" atau ganti nama). */
    fun updateRecord(record: id.tg.vcv.data.ProjectRecord) {
        val st = _state.value
        val p = st.project ?: return
        if (p.record.id == record.id) _state.value = st.copy(project = p.copy(record = record))
    }

    fun refreshLanIps() {
        val st = _state.value
        if (st.running && st.sharing) _state.value = st.copy(lanIps = NetUtil.lanAddresses())
    }

    /** Hentikan server sepenuhnya (proyek dianggap ditutup). */
    @Synchronized
    fun stop() {
        val previous = _state.value.project
        stopInternal()
        gate.reset(); gate.enable(false)
        _state.value = ServerState()
        if (previous != null) onProjectClosed?.invoke(previous)
    }

    private fun stopInternal() {
        server?.let { runCatching { it.stop() } }
        server = null
    }

    private fun onRequest(remoteIp: String) {
        lastActivity.set(System.currentTimeMillis())
        if (remoteIp.isEmpty() || remoteIp == "127.0.0.1" || remoteIp.startsWith("0:0:0:0:0:0:0:1") || remoteIp == "::1") return
        if (devices.add(remoteIp)) {
            val st = _state.value
            _state.value = st.copy(devices = devices.size)
        }
    }
}
