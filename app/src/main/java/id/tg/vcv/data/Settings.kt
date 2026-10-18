package id.tg.vcv.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class BrowserChoice { CHROME, INTERNAL }

/** Halaman yang dibuka saat proyek diketuk: terakhir dipilih, atau selalu index.html. */
enum class PageDefault { LAST, INDEX }

data class AppSettings(
    val browser: BrowserChoice = BrowserChoice.CHROME,
    val idleMinutes: Int = 15,
    val port: Int = 8080,
    val pageDefault: PageDefault = PageDefault.LAST,
    /** Ingat pilihan "Kunci dengan PIN" di layar berbagi. */
    val sharePin: Boolean = false,
)

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("vcv_settings", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<AppSettings> = _state.asStateFlow()
    val current: AppSettings get() = _state.value

    private fun read() = AppSettings(
        browser = runCatching { BrowserChoice.valueOf(prefs.getString("browser", "CHROME")!!) }
            .getOrDefault(BrowserChoice.CHROME),
        idleMinutes = prefs.getInt("idle", 15),
        port = prefs.getInt("port", 8080),
        pageDefault = runCatching { PageDefault.valueOf(prefs.getString("page_default", "LAST")!!) }
            .getOrDefault(PageDefault.LAST),
        sharePin = prefs.getBoolean("share_pin", false),
    )

    fun setBrowser(b: BrowserChoice) = write(current.copy(browser = b))
    fun setIdle(m: Int) = write(current.copy(idleMinutes = m))
    fun setPageDefault(p: PageDefault) = write(current.copy(pageDefault = p))
    fun setSharePin(on: Boolean) = write(current.copy(sharePin = on))
    fun setPort(p: Int) {
        if (p in 1024..65535) write(current.copy(port = p))
    }

    private fun write(s: AppSettings) {
        prefs.edit()
            .putString("browser", s.browser.name)
            .putInt("idle", s.idleMinutes)
            .putInt("port", s.port)
            .putString("page_default", s.pageDefault.name)
            .putBoolean("share_pin", s.sharePin)
            .apply()
        _state.value = s
    }

    companion object {
        val IDLE_OPTIONS = listOf(5, 10, 15, 30)
    }
}
