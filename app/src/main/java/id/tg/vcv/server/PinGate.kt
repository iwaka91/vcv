package id.tg.vcv.server

import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap

/**
 * Kunci PIN untuk berbagi (opsional).
 * - Hanya pengunjung dari HP lain yang diminta PIN; HP guru (127.0.0.1) tidak.
 * - PIN benar → diberi tanda masuk (cookie) yang tetap berlaku walau PIN diganti.
 * - Salah [MAX_WRONG] kali → alamat itu dikunci [LOCK_MS].
 * Ini pencegah iseng, BUKAN enkripsi.
 */
class PinGate {
    private val rnd = SecureRandom()
    private val tokens = ConcurrentHashMap.newKeySet<String>()
    private val wrong = ConcurrentHashMap<String, Int>()
    private val lockedUntil = ConcurrentHashMap<String, Long>()

    @Volatile var enabled: Boolean = false
        private set
    @Volatile var pin: String = newPin()
        private set

    fun enable(on: Boolean) {
        if (on && !enabled) pin = newPin()
        enabled = on
    }

    /** PIN baru: QR/PIN lama tidak berlaku, siswa yang sudah masuk tidak terputus. */
    fun change() {
        var p = newPin()
        while (p == pin) p = newPin()
        pin = p
        wrong.clear(); lockedUntil.clear()
    }

    /** Sesi berbagi baru (proyek lain / berbagi dihentikan): semua tanda masuk dihapus. */
    fun reset() {
        tokens.clear(); wrong.clear(); lockedUntil.clear()
        pin = newPin()
    }

    fun isAllowed(token: String?): Boolean = token != null && token in tokens

    fun lockLeftMs(ip: String): Long =
        ((lockedUntil[ip] ?: 0L) - System.currentTimeMillis()).coerceAtLeast(0L)

    sealed class Check {
        data class Ok(val token: String) : Check()
        data class Wrong(val left: Int) : Check()
        data class Locked(val seconds: Long) : Check()
    }

    fun check(ip: String, input: String): Check {
        val lockLeft = lockLeftMs(ip)
        if (lockLeft > 0) return Check.Locked((lockLeft + 999) / 1000)
        if (input == pin) {
            wrong.remove(ip)
            val t = ByteArray(16).also { rnd.nextBytes(it) }.joinToString("") { "%02x".format(it) }
            tokens += t
            return Check.Ok(t)
        }
        val n = (wrong[ip] ?: 0) + 1
        if (n >= MAX_WRONG) {
            wrong.remove(ip)
            lockedUntil[ip] = System.currentTimeMillis() + LOCK_MS
            return Check.Locked(LOCK_MS / 1000)
        }
        wrong[ip] = n
        return Check.Wrong(MAX_WRONG - n)
    }

    private fun newPin(): String = "%04d".format(rnd.nextInt(10_000))

    companion object {
        const val COOKIE = "vcv_pass"
        const val MAX_WRONG = 5
        const val LOCK_MS = 60_000L
    }
}
