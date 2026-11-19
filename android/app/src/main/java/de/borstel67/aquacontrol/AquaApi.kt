package de.borstel67.aquacontrol

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** HTTP-Zugriff auf die REST-API des ESP32 (/api/aqua/…). */
class AquaApi(private val host: () -> String) {

    private fun baseUrl(): String {
        val h = host().trim().trimEnd('/')
        return if (h.startsWith("http://") || h.startsWith("https://")) h else "http://$h"
    }

    suspend fun status(): JSONObject = get("/api/aqua/status")
    suspend fun water(): JSONObject = get("/api/aqua/water")
    suspend fun settings(): JSONObject = get("/api/aqua/settings")

    /** Teil-Update der Einstellungen; wirft bei HTTP-Fehler mit der Meldung des Geräts. */
    suspend fun saveSettings(patch: JSONObject) {
        post("/api/aqua/settings", patch)
    }

    suspend fun mqtt(): JSONObject = get("/api/aqua/mqtt")
    suspend fun saveMqtt(patch: JSONObject) { post("/api/aqua/mqtt", patch) }

    /** Kalibrieraktion, z. B. {"ph7":true}, {"ph4":true}, {"tds":1413}, {"reset":true}, {"phEn":false}. */
    suspend fun calibrate(action: JSONObject) { post("/api/aqua/water/cal", action) }

    /** WLAN-Parameter des STA-Modus (SSID, RSSI, DHCP/statisch, IP, Gateway, Subnetz, DNS, Hostname, NTP). */
    suspend fun wifi(): JSONObject = get("/api/sta/config")

    /** Der WiFi-Manager erwartet Formulardaten statt JSON (wie settings.html). */
    suspend fun saveWifi(fields: Map<String, String>) { postForm("/api/sta/config", fields) }

    /** Löscht die WLAN-Zugangsdaten; das Gerät startet danach im AP-Modus neu. */
    suspend fun eraseWifi() { postForm("/erase", mapOf("confirm" to "BESTÄTIGEN")) }

    /** Werkseinstellung: löscht WLAN-, LED-/Uhr-, Kalibrier- und MQTT-Konfiguration; Neustart im AP-Modus. */
    suspend fun factoryReset() { postForm("/factory", mapOf("confirm" to "WERKSEINSTELLUNG")) }

    private suspend fun get(path: String): JSONObject = request("GET", path, null, null)

    private suspend fun post(path: String, body: JSONObject): JSONObject =
        request("POST", path, body.toString(), "application/json")

    private suspend fun postForm(path: String, fields: Map<String, String>): JSONObject =
        request(
            "POST", path,
            fields.entries.joinToString("&") { (k, v) -> "$k=${URLEncoder.encode(v, "UTF-8")}" },
            "application/x-www-form-urlencoded",
        )

    private suspend fun request(method: String, path: String, body: String?, contentType: String?): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl() + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 4000
                conn.readTimeout = 6000
                if (body != null) {
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", contentType)
                    conn.outputStream.use { it.write(body.toByteArray()) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (code !in 200..299) {
                    val msg = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
                    throw java.io.IOException(msg.ifEmpty { "HTTP $code" })
                }
                if (text.isBlank()) JSONObject() else JSONObject(text)
            } finally {
                conn.disconnect()
            }
        }
}
