package de.borstel67.aquacontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ── Wasser (water.html) ──────────────────────────────────────────────────────

@Composable
fun WaterScreen(vm: AquaViewModel) {
    val w = vm.water
    if (w == null) { EmptyState(vm); return }
    var tdsRef by remember { mutableStateOf("1000") }
    var confirmReset by remember { mutableStateOf(false) }
    val cal = w.optJSONObject("cal")
    val ads = w.optBoolean("ads", false)
    val phEn = w.optBoolean("phEn", true)
    val tdsEn = w.optBoolean("tdsEn", true)
    val phTc = w.optBoolean("phTc", false)
    val tdsTc = w.optBoolean("tdsTc", false)
    val tcOk = w.optBoolean("tcOk", false)
    val ph = w.dbl("ph")
    val tds = w.dbl("tds")
    // Eingabefelder werden neu befüllt, sobald sich der gespeicherte Wert im Gerät ändert
    val num = { v: Double?, d: Int -> v?.let { String.format(Locale.US, "%.${d}f", it) } ?: "" }
    var v7 by remember(cal?.dbl("ph7")) { mutableStateOf(num(cal?.dbl("ph7"), 1)) }
    var v4 by remember(cal?.dbl("ph4")) { mutableStateOf(num(cal?.dbl("ph4"), 1)) }
    var phOff by remember(cal?.dbl("phOff")) { mutableStateOf(num(cal?.dbl("phOff"), 2)) }

    Column(Modifier.alpha(if (vm.error != null) 0.5f else 1f)) {
        Section("Messwerte") {
            TileRow {
                Tile(
                    "pH-Wert", fmt(ph, 2), when {
                        !phEn -> "nicht angeschlossen"
                        !ads -> "kein ADS1115"
                        ph == null -> "kein Sensorsignal"
                        else -> "DFRobot Gravity"
                    },
                    error = phEn && (ph == null || ph < 0 || ph > 14),
                )
                Tile(
                    "TDS", fmt(tds, 0, "ppm"), when {
                        !tdsEn -> "nicht angeschlossen"
                        !ads -> "kein ADS1115"
                        tds == null -> "kein Sensorsignal"
                        else -> "CQRobot"
                    },
                )
            }
            TileRow {
                Tile("Leitwert", fmt(w.dbl("ec"), 0, "µS/cm"), "TDS × 2")
                Tile(
                    "Wassertemperatur", w.dbl("temp")?.let { fmt(it, 2, "°C") } ?: "–", when {
                        !phTc && !tdsTc -> "nur Anzeige"
                        tcOk -> "für Kompensation"
                        else -> "ungültig – keine Kompensation"
                    },
                )
            }
        }
    }

    AquaCard {
        Sec("Sensoren") {
            SwitchRow("pH-Sensor angeschlossen (AIN0)", phEn) { vm.calibrate(JSONObject().put("phEn", it)) }
            SwitchRow("TDS-Sensor angeschlossen (AIN1)", tdsEn) { vm.calibrate(JSONObject().put("tdsEn", it)) }
            Hint("Nicht angeschlossene Sensoren abschalten: ein offener ADS1115-Eingang liefert sonst zufällige Werte.")
        }
        Sec("Temperaturkompensation", divider = true) {
            SwitchRow("pH temperaturkompensiert", phTc) { vm.calibrate(JSONObject().put("phTc", it)) }
            SwitchRow("TDS / Leitwert auf 25 °C umrechnen", tdsTc) { vm.calibrate(JSONObject().put("tdsTc", it)) }
            Hint(
                "Nutzt die Wassertemperatur (DS18B20). pH: Bezug ist die Temperatur bei der Puffer-Kalibrierung " +
                    "(${fmt(cal?.dbl("phT"), 1, "°C")}). TDS/Leitwert: 2 %/°C, Bezug 25 °C. " +
                    "Ohne gültige Temperatur wird nicht kompensiert." +
                    if ((phTc || tdsTc) && !tcOk) " ⚠️ Temperatur derzeit ungültig." else "",
            )
        }
        Sec("pH-Kalibrierung", divider = true) {
            Hint("Sensor spülen, in Pufferlösung tauchen, ca. 1 Minute warten bis der Wert stabil ist, dann übernehmen.")
            CalRow("pH 7,00", fmt(cal?.dbl("ph7"), 0, "mV"), "Puffer pH 7 übernehmen", phEn) {
                vm.calibrate(JSONObject().put("ph7", true), "pH 7 übernommen.")
            }
            CalRow("pH 4,00", fmt(cal?.dbl("ph4"), 0, "mV"), "Puffer pH 4 übernehmen", phEn) {
                vm.calibrate(JSONObject().put("ph4", true), "pH 4 übernommen.")
            }
            Hint("Zuerst pH 7, dann pH 4. Aktuelle Sensorspannung: ${fmt(w.dbl("phMv"), 0, "mV")}")
        }
        Sec("pH manuell einstellen", divider = true) {
            AquaField(v7, { v7 = it }, label = "Spannung pH 7", keyboard = KeyboardType.Decimal, suffix = "mV")
            AquaField(v4, { v4 = it }, label = "Spannung pH 4", keyboard = KeyboardType.Decimal, suffix = "mV")
            AquaButton("Spannungen übernehmen", {
                val a = v7.replace(',', '.').toDoubleOrNull()
                val b = v4.replace(',', '.').toDoubleOrNull()
                if (a == null || b == null) vm.message = "Beide Spannungen eingeben."
                else vm.calibrate(JSONObject().put("phV7", a).put("phV4", b), "Spannungen gespeichert.")
            }, Modifier.fillMaxWidth())
            AquaField(phOff, { phOff = it }, label = "pH-Offset", keyboard = KeyboardType.Text, suffix = "pH")
            AquaButton("Offset übernehmen", {
                val o = phOff.replace(',', '.').toDoubleOrNull()
                if (o == null) vm.message = "Offset eingeben (0 = aus)."
                else vm.calibrate(JSONObject().put("phOff", o), "Offset gespeichert.")
            }, Modifier.fillMaxWidth())
            Hint(
                "Die Spannungen ersetzen die Kalibrierpunkte (mindestens 100 mV Abstand). Der Offset wird zum " +
                    "berechneten pH-Wert addiert, z. B. +0,50 wenn das Gerät 0,5 zu niedrig anzeigt.",
            )
        }
        Sec("TDS-Kalibrierung", divider = true) {
            AquaField(tdsRef, { tdsRef = it }, label = "Referenzlösung", keyboard = KeyboardType.Number, suffix = "ppm")
            AquaButton("Übernehmen", {
                vm.calibrate(JSONObject().put("tds", tdsRef.toIntOrNull() ?: 0), "TDS kalibriert.")
            }, Modifier.fillMaxWidth(), enabled = tdsEn)
            Hint(
                "Sensor in die Referenzlösung (z. B. 1000 ppm) tauchen, warten bis der Wert stabil ist. " +
                    "Korrekturfaktor: ${cal?.dbl("tdsK")?.let { String.format(Locale.GERMANY, "%.3f", it) } ?: "–"} · " +
                    "Sensorspannung: ${fmt(w.dbl("tdsMv"), 0, "mV")}",
            )
        }
        Sec(null, divider = true) {
            AquaButton("Kalibrierung zurücksetzen", { confirmReset = true }, Modifier.fillMaxWidth())
        }
    }

    if (confirmReset) {
        ConfirmSheet(
            "Kalibrierung zurücksetzen", "Kalibrierung auf Werkseinstellung zurücksetzen?", "Zurücksetzen",
            { confirmReset = false },
        ) {
            confirmReset = false
            vm.calibrate(JSONObject().put("reset", true), "Zurückgesetzt.")
        }
    }
}

@Composable
private fun CalRow(label: String, stored: String, button: String, enabled: Boolean, onClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f)) {
            Text(label, fontSize = 14.sp, color = AquaColors.Label)
            Text(stored, fontSize = 12.sp, color = AquaColors.Muted)
        }
        AquaButton(button, onClick, enabled = enabled)
    }
}

// ── Konfiguration (config.html + mqtt.html) ──────────────────────────────────

private val timezones = (-12..13).map { h ->
    h to "GMT" + if (h == 0) "" else (if (h > 0) "+" else "−") + kotlin.math.abs(h)
}

@Composable
fun ConfigScreen(vm: AquaViewModel) {
    ConnectionCard(vm)

    val s = vm.settings
    val st = vm.status
    if (s == null) {
        if (vm.host.isNotBlank()) Hint("Lade Einstellungen …", Modifier.padding(4.dp))
        return
    }

    // Formular neu füllen, sobald das Gerät andere Werte liefert
    var tz by remember(s.optInt("tz")) { mutableStateOf(s.optInt("tz")) }
    var dst by remember(s.optBoolean("dst")) { mutableStateOf(s.optBoolean("dst")) }
    var lat by remember(s.optDouble("lat")) { mutableStateOf(s.optDouble("lat").toString()) }
    var lon by remember(s.optDouble("lon")) { mutableStateOf(s.optDouble("lon").toString()) }
    var ntp by remember(s.optString("ntp")) { mutableStateOf(s.optString("ntp")) }

    AquaCard {
        Sec("Uhr") {
            val ntpState = st?.optInt("ntp") ?: 0   // 2 = synchron, 1 = Fehler
            val last = st?.optString("lastSync").orEmpty()
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("NTP-Status", fontSize = 14.sp, color = AquaColors.Label)
                Text(
                    (st?.optString("ntpText") ?: "…") + if (last.isNotEmpty()) " · letzte Sync $last" else "",
                    fontSize = 14.sp,
                    color = when (ntpState) { 2 -> AquaColors.Ok; 1 -> AquaColors.Bad; else -> AquaColors.Muted },
                )
            }
            AquaField(ntp, { ntp = it }, label = "NTP-Server", placeholder = "de.pool.ntp.org")
            AquaDropdown(
                timezones.firstOrNull { it.first == tz }?.second ?: "GMT$tz", timezones, { tz = it },
                label = "Zeitzone",
            )
            SwitchRow("Sommer-/Winterzeit (EU-Regel)", dst) { dst = it }
            Hint("Leerer NTP-Server = de.pool.ntp.org. Sommerzeit: letzter Sonntag im März bis letzter Sonntag im Oktober.")
        }
        Sec("Standort (Sonne & Mond)", divider = true) {
            AquaField(lat, { lat = it }, label = "Breitengrad", keyboard = KeyboardType.Decimal)
            AquaField(lon, { lon = it }, label = "Längengrad", keyboard = KeyboardType.Decimal)
            Hint("Dezimalgrad, Nord/Ost positiv (z. B. Berlin 52.52 / 13.40).")
        }
    }
    AquaButton("Speichern", {
        vm.save(
            JSONObject()
                .put("ntp", ntp.trim())
                .put("tz", tz)
                .put("dst", dst)
                // 999 = ungültig: das Gerät meldet den Fehler
                .put("lat", lat.replace(',', '.').toDoubleOrNull() ?: 999.0)
                .put("lon", lon.replace(',', '.').toDoubleOrNull() ?: 999.0),
        )
    }, Modifier.fillMaxWidth(), primary = true)

    ManualTimeCard(vm, s.optString("now"))
    WifiCards(vm)
    MqttCards(vm)
    FactoryResetCard(vm)
}

@Composable
private fun FactoryResetCard(vm: AquaViewModel) {
    var confirm by remember { mutableStateOf(false) }
    Section("Werkseinstellung") {
        Hint("Löscht die komplette Konfiguration des Geräts: WLAN-Zugangsdaten und Netzwerk, LED-Kanäle und Zeiten, Uhr und Standort, pH/TDS-Kalibrierung sowie MQTT. Danach startet das Gerät im AP-Modus (Captive-Portal unter 192.168.4.1) und muss neu eingerichtet werden.")
        AquaButton("Auf Werkseinstellung zurücksetzen", { confirm = true }, Modifier.fillMaxWidth())
    }
    if (confirm) {
        ConfirmSheet(
            "Werkseinstellung",
            "Wirklich die gesamte Konfiguration löschen? Das Gerät startet im AP-Modus neu und ist für die App erst nach der Neueinrichtung wieder erreichbar.",
            "Zurücksetzen",
            { confirm = false },
        ) {
            confirm = false
            vm.factoryReset()
        }
    }
}

@Composable
private fun ConnectionCard(vm: AquaViewModel) {
    var host by remember(vm.host) { mutableStateOf(vm.host) }
    Section("Verbindung zum Gerät") {
        AquaField(host, { host = it }, label = "ESP32-Adresse", placeholder = "192.168.1.50")
        AquaButton("Speichern & verbinden", { vm.updateHost(host) }, Modifier.fillMaxWidth(), primary = true)
        Hint("Die App spricht per HTTP direkt mit dem ESP32 im selben WLAN (IP oder Hostname).")
    }
    Section("Darstellung") {
        val themes = listOf("auto" to "System (hell/dunkel automatisch)", "light" to "Hell", "dark" to "Dunkel")
        AquaDropdown(
            themes.firstOrNull { it.first == vm.theme }?.second ?: themes[0].second, themes, vm::updateTheme,
        )
    }
}

@Composable
private fun ManualTimeCard(vm: AquaViewModel, now: String) {
    val parts = now.split("T")
    var date by remember(now) { mutableStateOf(parts.getOrNull(0).orEmpty()) }
    var time by remember(now) { mutableStateOf(parts.getOrNull(1).orEmpty()) }
    Section("Manuelle Zeiteinstellung") {
        AquaField(date, { date = it }, label = "Datum", placeholder = "JJJJ-MM-TT")
        AquaField(time, { time = it }, label = "Uhrzeit", placeholder = "HH:MM:SS")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AquaButton("Handyzeit übernehmen", {
                val d = Date()
                date = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)
                time = SimpleDateFormat("HH:mm:ss", Locale.US).format(d)
            }, Modifier.weight(1f), ghost = true)
            AquaButton("Uhrzeit setzen", {
                if (date.isBlank() || time.isBlank()) vm.message = "Datum und Uhrzeit angeben."
                else vm.save(JSONObject().put("setTime", "${date.trim()}T${time.trim()}"), "Uhrzeit gesetzt.")
            }, Modifier.weight(1f))
        }
        Hint("Lokalzeit. Bei aktiver NTP-Verbindung wird die Uhr bei der nächsten Synchronisierung wieder überschrieben.")
    }
}

// ── WLAN (settings.html, STA-Teil) ───────────────────────────────────────────

@Composable
private fun WifiCards(vm: AquaViewModel) {
    val w = vm.wifi ?: return

    // Formular neu füllen, sobald das Gerät andere Werte liefert
    var dhcp by remember(w) { mutableStateOf(w.optBoolean("dhcp", true)) }
    var ip by remember(w) { mutableStateOf(w.optString("ip")) }
    var gw by remember(w) { mutableStateOf(w.optString("gw")) }
    var sn by remember(w) { mutableStateOf(w.optString("sn")) }
    var dns1 by remember(w) { mutableStateOf(w.optString("dns1")) }
    var dns2 by remember(w) { mutableStateOf(w.optString("dns2")) }
    var ntp1 by remember(w) { mutableStateOf(w.optString("ntp1")) }
    var ntp2 by remember(w) { mutableStateOf(w.optString("ntp2")) }
    var hostname by remember(w) { mutableStateOf(w.optString("hostname")) }
    var confirmErase by remember { mutableStateOf(false) }

    val ssid = w.optString("ssid")
    val rssi = w.optInt("rssi")

    AquaCard {
        Sec("WLAN – Netzwerk", trailing = {
            if (rssi != 0) Hint("$rssi dBm", color = if (rssi >= -67) AquaColors.Ok else AquaColors.Muted)
        }) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SSID", fontSize = 14.sp, color = AquaColors.Label)
                Text(ssid.ifEmpty { "–" }, fontSize = 14.sp, color = AquaColors.Text)
            }
            Hint("SSID und Passwort lassen sich nur im AP-Modus (Captive-Portal) ändern.")
            AquaField(hostname, { hostname = it }, label = "Hostname", placeholder = "mein-esp")
        }
        Sec("Adressierung", divider = true) {
            ModeRow(listOf("DHCP", "Statisch"), if (dhcp) 0 else 1) { dhcp = it == 0 }
            if (!dhcp) {
                AquaField(ip, { ip = it }, label = "IP-Adresse", placeholder = "192.168.1.50", keyboard = KeyboardType.Uri)
                AquaField(gw, { gw = it }, label = "Gateway", placeholder = "192.168.1.1", keyboard = KeyboardType.Uri)
                AquaField(sn, { sn = it }, label = "Subnetz", placeholder = "255.255.255.0", keyboard = KeyboardType.Uri)
                AquaField(dns1, { dns1 = it }, label = "DNS 1", placeholder = "1.1.1.1", keyboard = KeyboardType.Uri)
                AquaField(dns2, { dns2 = it }, label = "DNS 2", placeholder = "8.8.8.8", keyboard = KeyboardType.Uri)
                Hint("Nach einer Änderung der IP-Adresse die ESP32-Adresse oben anpassen.")
            }
        }
        Sec("Zeitserver", divider = true) {
            AquaField(ntp1, { ntp1 = it }, label = "NTP 1", placeholder = "de.pool.ntp.org")
            AquaField(ntp2, { ntp2 = it }, label = "NTP 2", placeholder = "ptbtime1.ptb.de")
            Hint("Wird in /config.json gespeichert und beim STA-Start (Neustart) übernommen.")
        }
    }
    AquaButton("WLAN speichern", {
        vm.saveWifi(
            mapOf(
                "dhcp" to if (dhcp) "1" else "0",
                "ip" to ip.trim(), "gw" to gw.trim(), "sn" to sn.trim(),
                "dns1" to dns1.trim(), "dns2" to dns2.trim(),
                "hostname" to hostname.trim(),
                "ntp1" to ntp1.trim(), "ntp2" to ntp2.trim(),
            ),
        )
    }, Modifier.fillMaxWidth(), primary = true)

    Section("WLAN – Gefährlicher Bereich") {
        Hint("Löscht die config.json mit den WLAN-Zugangsdaten und startet das Gerät neu. Danach ist es nur noch über den eigenen Access Point (192.168.4.1) erreichbar.")
        AquaButton("Anmeldedaten löschen und neu starten", { confirmErase = true }, Modifier.fillMaxWidth())
    }

    if (confirmErase) {
        ConfirmSheet(
            "WLAN-Daten löschen",
            "WLAN-Zugangsdaten wirklich löschen? Das Gerät startet im AP-Modus neu und ist für die App nicht mehr erreichbar.",
            "Löschen",
            { confirmErase = false },
        ) {
            confirmErase = false
            vm.eraseWifi()
        }
    }
}

@Composable
private fun MqttCards(vm: AquaViewModel) {
    // Verbindungsstatus wie im Web alle 3 s aktualisieren, solange die Seite offen ist
    LaunchedEffect(vm.host) {
        while (vm.host.isNotBlank()) {
            delay(3000)
            vm.refreshMqtt()
        }
    }
    val m = vm.mqtt ?: return

    // Formular nur einmal füllen; der Status darf es nicht zurücksetzen
    var enabled by remember { mutableStateOf(m.optBoolean("enabled")) }
    var host by remember { mutableStateOf(m.optString("host")) }
    var port by remember { mutableStateOf(m.optInt("port").toString()) }
    var user by remember { mutableStateOf(m.optString("user")) }
    var pass by remember { mutableStateOf("") }
    var base by remember { mutableStateOf(m.optString("base")) }
    var interval by remember { mutableStateOf(m.optInt("interval").toString()) }

    val connected = m.optBoolean("connected")
    val state = when {
        !m.optBoolean("enabled") -> "deaktiviert"
        connected -> "verbunden"
        else -> "nicht verbunden" + m.optString("error").let { if (it.isNotEmpty()) " – $it" else "" }
    }
    val prefix = base.trim().trim('/').ifEmpty { "…" }

    AquaCard {
        Sec("MQTT – Broker", trailing = {
            Hint(state, color = if (connected) AquaColors.Ok else AquaColors.Muted)
        }) {
            SwitchRow("MQTT aktivieren", enabled) { enabled = it }
            AquaField(host, { host = it }, label = "Host", placeholder = "192.168.1.10")
            AquaField(port, { port = it }, label = "Port", keyboard = KeyboardType.Number)
            AquaField(user, { user = it }, label = "Benutzer")
            AquaField(pass, { pass = it }, label = "Passwort", password = true)
            Hint(
                if (m.optBoolean("passSet")) "Ein Passwort ist gespeichert. Leer lassen, um es beizubehalten."
                else "Kein Passwort gespeichert.",
            )
        }
        Sec("MQTT – Veröffentlichung", divider = true) {
            AquaField(base, { base = it }, label = "Topic-Präfix")
            AquaField(interval, { interval = it }, label = "Intervall", keyboard = KeyboardType.Number, suffix = "s")
            Hint("Topics: $prefix/status/…, $prefix/water/…, $prefix/settings/… (Einstellungen alle 60 s) und $prefix/online (1/0).")
        }
    }
    AquaButton("MQTT speichern", {
        val p = JSONObject()
            .put("enabled", enabled).put("host", host.trim())
            .put("port", port.toIntOrNull() ?: 0).put("user", user)
            .put("base", base).put("interval", interval.toIntOrNull() ?: 0)
        if (pass.isNotEmpty()) p.put("pass", pass)   // leer = unverändert
        vm.saveMqtt(p)
        pass = ""
    }, Modifier.fillMaxWidth(), primary = true)
}
