package de.borstel67.aquacontrol

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

class AquaViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("aqua", 0)

    var host by mutableStateOf(prefs.getString("host", "") ?: "")
        private set
    var status by mutableStateOf<JSONObject?>(null)
        private set
    var water by mutableStateOf<JSONObject?>(null)
        private set
    var settings by mutableStateOf<JSONObject?>(null)
        private set
    var mqtt by mutableStateOf<JSONObject?>(null)
        private set
    var wifi by mutableStateOf<JSONObject?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var message by mutableStateOf<String?>(null)

    /** Darstellung: "auto" (System), "light" oder "dark". */
    var theme by mutableStateOf(prefs.getString("theme", "auto") ?: "auto")
        private set
    /** Zeitpunkt der letzten erfolgreichen Abfrage (für die Kopfzeile). */
    var lastUpdate by mutableStateOf<Long?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    fun updateTheme(value: String) {
        theme = value
        prefs.edit().putString("theme", value).apply()
    }

    /** Sofort neu abfragen (Aktualisieren-Knopf in der Kopfzeile). */
    fun refreshNow() {
        if (host.isBlank() || busy) return
        viewModelScope.launch {
            if (poll()) message = "Aktualisiert"
            loadSettings()
        }
    }

    private suspend fun poll(): Boolean {
        busy = true
        return try {
            status = api.status()
            // Wasserwerte sind optional (Sensor evtl. nicht verbaut)
            water = runCatching { api.water() }.getOrNull()
            error = null
            lastUpdate = System.currentTimeMillis()
            true
        } catch (e: Exception) {
            error = e.message ?: e.javaClass.simpleName
            false
        } finally {
            busy = false
        }
    }

    /** Verlauf der letzten 5 min (150 × 2 s) für die Sparklines; null = kein Messwert. */
    var tempHist by mutableStateOf<List<Double?>>(emptyList())
        private set
    var phHist by mutableStateOf<List<Double?>>(emptyList())
        private set
    var tdsHist by mutableStateOf<List<Double?>>(emptyList())
        private set
    var ecHist by mutableStateOf<List<Double?>>(emptyList())
        private set

    private val api = AquaApi { host }
    private var pollJob: Job? = null
    private var loadingSettings = false

    init {
        if (host.isNotBlank()) loadSettings()
    }

    fun updateHost(value: String) {
        host = value.trim()
        prefs.edit().putString("host", host).apply()
        status = null; water = null; settings = null; mqtt = null; wifi = null
        tempHist = emptyList(); phHist = emptyList(); tdsHist = emptyList(); ecHist = emptyList()
        error = null; lastUpdate = null
        if (host.isNotBlank()) loadSettings()
    }

    /** Messwerte alle 2 s abfragen, solange die App sichtbar ist. */
    fun startPolling() {
        if (pollJob?.isActive == true) return
        pollJob = viewModelScope.launch {
            while (isActive) {
                if (host.isNotBlank() && poll()) {
                    pushHistory()
                    if (settings == null) loadSettings()
                }
                delay(2000)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
    }

    private fun pushHistory() {
        val temp = status?.dbl("temp")?.takeIf { it < 999 }   // 999 = Sensorfehler
        tempHist = (tempHist + temp).takeLast(HISTORY_POINTS)
        phHist = (phHist + water?.dbl("ph")).takeLast(HISTORY_POINTS)
        tdsHist = (tdsHist + water?.dbl("tds")).takeLast(HISTORY_POINTS)
        ecHist = (ecHist + water?.dbl("ec")).takeLast(HISTORY_POINTS)
    }

    fun loadSettings() {
        if (loadingSettings) return
        loadingSettings = true
        viewModelScope.launch {
            try {
                settings = api.settings()
                mqtt = runCatching { api.mqtt() }.getOrNull()
                wifi = runCatching { api.wifi() }.getOrNull()
                error = null
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            } finally {
                loadingSettings = false
            }
        }
    }

    fun saveMqtt(patch: JSONObject) {
        viewModelScope.launch {
            try {
                api.saveMqtt(patch)
                mqtt = api.mqtt()
                message = "Gespeichert."
            } catch (e: Exception) {
                message = "Fehler: ${e.message}"
            }
        }
    }

    fun refreshMqtt() {
        viewModelScope.launch { runCatching { mqtt = api.mqtt() } }
    }

    fun saveWifi(fields: Map<String, String>) {
        viewModelScope.launch {
            try {
                api.saveWifi(fields)
                wifi = api.wifi()
                message = "Gespeichert. Wirksam nach dem nächsten Neustart des Geräts."
            } catch (e: Exception) {
                message = "Fehler: ${e.message}"
            }
        }
    }

    fun eraseWifi() {
        viewModelScope.launch {
            try {
                api.eraseWifi()
                message = "WLAN-Daten gelöscht. Das Gerät startet im AP-Modus neu."
            } catch (e: Exception) {
                message = "Fehler: ${e.message}"
            }
        }
    }

    fun factoryReset() {
        viewModelScope.launch {
            try {
                api.factoryReset()
                message = "Werkseinstellung: Konfiguration gelöscht. Das Gerät startet im AP-Modus neu (WLAN „WiFi Manager“, http://192.168.4.1)."
            } catch (e: Exception) {
                message = "Fehler: ${e.message}"
            }
        }
    }

    fun calibrate(action: JSONObject, okText: String = "Gespeichert.") {
        viewModelScope.launch {
            try {
                api.calibrate(action)
                water = api.water()
                message = okText
            } catch (e: Exception) {
                message = "Fehler: ${e.message}"
            }
        }
    }

    /** Einstellungen speichern; bei einem Fehler bleiben die Eingaben im Formular erhalten. */
    fun save(patch: JSONObject, okText: String = "Gespeichert.") {
        viewModelScope.launch {
            try {
                api.saveSettings(patch)
                settings = api.settings()
                message = okText
                error = null
            } catch (e: Exception) {
                message = "Fehler: ${e.message ?: "Speichern fehlgeschlagen"}"
            }
        }
    }

    fun consumeMessage() { message = null }
}
