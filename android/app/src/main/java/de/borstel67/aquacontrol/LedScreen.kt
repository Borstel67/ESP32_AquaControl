package de.borstel67.aquacontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

/** Bearbeitungsstand eines Kanals; Zeiten als [on1, off1, on2, off2]. */
private class ChannelForm(ch: JSONObject) {
    var mode by mutableIntStateOf(ch.optInt("mode"))
    var on by mutableStateOf(ch.optString("on"))
    var off by mutableStateOf(ch.optString("off"))
    val times = mutableStateListOf<String>().apply {
        val p = ch.optJSONArray("p")
        for (k in 0 until 2) {
            val o = p?.optJSONObject(k)
            add(o?.optString("on") ?: "00:00")
            add(o?.optString("off") ?: "00:00")
        }
    }

    fun toJson(): JSONObject = JSONObject()
        .put("mode", mode).put("on", on).put("off", off)
        .put("p", JSONArray()
            .put(JSONObject().put("on", times[0]).put("off", times[1]))
            .put(JSONObject().put("on", times[2]).put("off", times[3])))
}

private class LedForm(s: JSONObject) {
    private val ramp = s.getJSONObject("ramp")
    private val moon = s.getJSONObject("moon")

    var leds by mutableStateOf(s.optInt("leds").toString())
    var step by mutableStateOf(ramp.optInt("step").toString())
    val levels = mutableStateListOf<String>().apply {
        val a = ramp.getJSONArray("lv")
        for (i in 0 until a.length()) add(a.getInt(i).toString())
    }
    val channels = s.getJSONArray("ch").let { a -> List(a.length()) { ChannelForm(a.getJSONObject(it)) } }
    var moonMode by mutableIntStateOf(moon.optInt("mode"))
    var moonColor by mutableStateOf(moon.optString("color"))
    var moonRanges by mutableStateOf(moon.optString("ranges"))

    // Unlesbare Zahlen als -1 senden: das Gerät lehnt sie mit einer Fehlermeldung ab
    fun toJson(): JSONObject = JSONObject()
        .put("leds", leds.toIntOrNull() ?: -1)
        .put("ramp", JSONObject()
            .put("step", step.toIntOrNull() ?: -1)
            .put("lv", JSONArray(levels.map { it.toIntOrNull() ?: -1 })))
        .put("ch", JSONArray(channels.map { it.toJson() }))
        .put("moon", JSONObject().put("mode", moonMode).put("color", moonColor).put("ranges", moonRanges.trim()))
}

@Composable
fun LedScreen(vm: AquaViewModel) {
    val st = vm.status
    val s = vm.settings
    if (st == null && s == null) { EmptyState(vm); return }

    if (st != null) {
        Column(Modifier.alpha(if (vm.error != null) 0.5f else 1f)) {
            Section("Status") {
                TileRow {
                    Tile("Uhrzeit", st.optString("time"), if (st.optBoolean("valid")) {
                        if (st.optBoolean("dstActive")) "Sommerzeit" else "Normalzeit"
                    } else "Uhr nicht gesetzt")
                    val t = tempOf(st)
                    Tile("Wassertemperatur", if (t == null) "Sensorfehler" else fmt(t, 2, "°C"), "DS18B20", error = t == null)
                }
                TileRow {
                    Tile(
                        "Sonne",
                        "${st.optString("sunrise").ifEmpty { "--:--" }} – ${st.optString("sunset").ifEmpty { "--:--" }}",
                        "Aufgang – Untergang",
                    )
                    Tile(
                        "Mond", if (st.optBoolean("valid")) "${st.optInt("moonAge")} Tage" else "–",
                        when {
                            !st.optBoolean("valid") -> "Uhr nicht gesetzt"
                            st.optBoolean("moonLit") -> "Helligkeit ${st.optInt("moonBright")}/256"
                            else -> "aus"
                        },
                    )
                }
                ChannelChips(st, offColorAware = true)
            }
        }
    }
    if (s == null) { Hint("Lade Einstellungen …", Modifier.padding(4.dp)); return }

    // Neu aufbauen, sobald das Gerät andere Einstellungen liefert (z. B. nach dem Speichern)
    val form = remember(s.toString()) { LedForm(s) }
    val live = st?.optJSONArray("ch")

    AquaCard {
        Sec("LED-Strip") {
            AquaField(form.leds, { form.leds = it }, label = "Anzahl LEDs", keyboard = KeyboardType.Number, suffix = "max. 400")
            Hint("Die LEDs werden reihum auf die 4 Kanäle verteilt: Kanal 1 = LED 1, 5, 9, … · Kanal 2 = LED 2, 6, 10, … usw.")
        }
        Sec("Sonnenaufgang / -untergang (Dimmung)", divider = true) {
            AquaField(
                form.step, { form.step = it }, label = "Dauer je Stufe",
                keyboard = KeyboardType.Number, suffix = "min (0 = hart schalten)",
            )
            FieldLabel("Stufen")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                form.levels.forEachIndexed { i, v ->
                    AquaField(
                        v, { form.levels[i] = it }, Modifier.weight(1f),
                        keyboard = KeyboardType.Number, suffix = "%",
                    )
                }
            }
            Hint(
                "Ab der EIN-Zeit wird in 3 Stufen hochgedimmt (Stufe 1 → 2 → 3), vor der AUS-Zeit in umgekehrter " +
                    "Reihenfolge herunter. Zwischen den Stufen wird von der AUS-Farbe zur EIN-Farbe gemischt.",
            )
        }
    }

    AquaCard {
        form.channels.forEachIndexed { i, ch ->
            val liveColor = live?.optJSONObject(i)?.optString("color")?.let { parseHex(it) }
            Sec("Kanal ${i + 1}", divider = i > 0, trailing = { Swatch(liveColor) }) {
                FieldLabel("Betriebsart")
                ModeRow(listOf("UHR", "EIN", "AUS"), ch.mode) { ch.mode = it }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ColorField("Farbe EIN", ch.on, { ch.on = it })
                    ColorField("Farbe AUS", ch.off, { ch.off = it })
                }
                for (k in 0 until 2) {
                    FieldLabel("Zeitraum ${k + 1}")
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TimeField(ch.times[2 * k], { ch.times[2 * k] = it }, Modifier.weight(1f))
                        Text("bis", fontSize = 14.sp, color = AquaColors.Muted)
                        TimeField(ch.times[2 * k + 1], { ch.times[2 * k + 1] = it }, Modifier.weight(1f))
                    }
                }
                Hint("LED ${i + 1}, ${i + 5}, ${i + 9}, … · Gleiche EIN- und AUS-Zeit = Zeitraum deaktiviert.")
            }
        }
    }

    AquaCard {
        val moonLive = st?.optBoolean("moonLit") == true
        Sec("Mondbeleuchtung", trailing = { Swatch(if (moonLive) parseHex(form.moonColor) else Color.Transparent) }) {
            FieldLabel("Betriebsart")
            ModeRow(listOf("AUTO", "EIN", "AUS"), form.moonMode) { form.moonMode = it }
            ColorField("Farbe", form.moonColor, { form.moonColor = it })
            AquaField(form.moonRanges, { form.moonRanges = it }, label = "LEDs", placeholder = "37-48;67-78;107-118")
            Hint(
                "Format von-bis;einzeln;von-bis, max. 5 Einträge.\n" +
                    "AUTO: Mond an, wenn alle Kanäle außerhalb ihrer Schaltzeiten sind; Helligkeit nach Mondphase.\n" +
                    "EIN / AUS: alle 4 Kanäle sind aus, der Mond leuchtet (feste Vollmond-Helligkeit) bzw. ist aus.",
            )
        }
    }

    AquaButton("Speichern", { vm.save(form.toJson()) }, Modifier.fillMaxWidth(), primary = true)
}
