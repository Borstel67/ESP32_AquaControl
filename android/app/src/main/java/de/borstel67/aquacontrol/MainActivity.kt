package de.borstel67.aquacontrol

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONObject

class MainActivity : ComponentActivity() {
    private val vm: AquaViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AquaTheme(vm.theme) {
                // Status- und Navigationsleiste in Hintergrundfarbe, Symbole passend hell/dunkel
                val bar = AquaColors.Bg.toArgb()
                val light = AquaColors.isLight
                SideEffect {
                    window.statusBarColor = bar
                    window.navigationBarColor = AquaColors.Card.toArgb()
                    WindowCompat.getInsetsController(window, window.decorView).apply {
                        isAppearanceLightStatusBars = light
                        isAppearanceLightNavigationBars = light
                    }
                }
                AquaApp(vm)
            }
        }
    }

    // Messwerte nur abfragen, solange die App sichtbar ist
    override fun onStart() {
        super.onStart()
        vm.startPolling()
    }

    override fun onStop() {
        vm.stopPolling()
        super.onStop()
    }
}

private enum class Tab(val label: String, val icon: ImageVector) {
    Dashboard("Übersicht", Icons.Filled.Dashboard),
    Led("LED", Icons.Filled.Lightbulb),
    Water("Wasser", Icons.Filled.WaterDrop),
    Config("App", Icons.Filled.Settings),
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AquaApp(vm: AquaViewModel) {
    // Alle Tabs per horizontalem Wischen (Übersicht ↔ LED ↔ Wasser ↔ App);
    // ohne gespeicherte Adresse direkt die Konfiguration öffnen
    val pager = rememberPagerState(initialPage = if (vm.host.isBlank()) Tab.Config.ordinal else 0) { Tab.entries.size }
    val scope = rememberCoroutineScope()
    val current = Tab.entries[pager.currentPage]
    val snack = remember { SnackbarHostState() }

    fun select(index: Int) {
        scope.launch { pager.animateScrollToPage(index) }
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { snack.showSnackbar(it); vm.consumeMessage() }
    }

    Scaffold(
        containerColor = AquaColors.Bg,
        snackbarHost = { SnackbarHost(snack) { AquaToast(it.visuals.message) } },
        topBar = {
            // Kopfzeile „schwebt“ über dem Inhalt und blendet nach unten aus (header in Luxtronik)
            Column(
                Modifier
                    .background(Brush.verticalGradient(0.7f to AquaColors.Bg, 1f to AquaColors.Bg.copy(alpha = 0f)))
                    .statusBarsPadding()
                    .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            ) {
                AppHeader(
                    title = if (current == Tab.Config) "Einstellungen" else if (current == Tab.Led) "Aquarium LED" else
                        if (current == Tab.Water) "Wasserqualität" else "AquaControl",
                    subtitle = subtitle(current, vm),
                    icon = current.icon,
                    link = when {
                        vm.host.isBlank() -> LinkState.None
                        vm.busy -> LinkState.Busy
                        vm.error != null -> LinkState.Err
                        vm.status != null -> LinkState.Ok
                        else -> LinkState.Busy
                    },
                    onRefresh = if (vm.host.isBlank()) null else vm::refreshNow,
                )
            }
        },
        bottomBar = {
            AquaTabBar(Tab.entries.map { it.label to it.icon }, current.ordinal, ::select)
        },
    ) { pad ->
        HorizontalPager(
            state = pager,
            modifier = Modifier.padding(pad).fillMaxSize(),
            verticalAlignment = Alignment.Top,
        ) { page ->
            TabPage(Tab.entries[page], vm, Modifier) { select(Tab.Config.ordinal) }
        }
    }
}

/** Inhalt eines Tabs: eigene vertikale Scroll-Position, Verbindungs-Banner und Fußzeile. */
@Composable
private fun TabPage(tab: Tab, vm: AquaViewModel, modifier: Modifier, openConfig: () -> Unit) {
    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        vm.error?.let {
            Banner("Keine Verbindung", "$it\nZiel: http://${vm.host} – das Handy muss im selben WLAN sein.") {
                AquaButton("Erneut verbinden", vm::refreshNow, primary = true)
                if (tab != Tab.Config) AquaButton("Einstellungen", openConfig)
            }
        }
        when (tab) {
            Tab.Dashboard -> DashboardScreen(vm)
            Tab.Led -> LedScreen(vm)
            Tab.Water -> WaterScreen(vm)
            Tab.Config -> ConfigScreen(vm)
        }
        vm.lastUpdate?.let {
            Text(
                "Stand ${SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date(it))} · alle 2 s",
                Modifier.fillMaxWidth(), fontSize = 12.sp, color = AquaColors.Muted, textAlign = TextAlign.Center,
            )
        }
        Copyright()
    }
}

private fun subtitle(tab: Tab, vm: AquaViewModel): String {
    val s = vm.status
    if (vm.host.isBlank()) return "keine Adresse eingetragen"
    if (s == null) return if (vm.error != null) "nicht erreichbar" else "verbinde …"
    return when (tab) {
        Tab.Water -> when (vm.water?.optBoolean("ads")) {
            true -> "ADS1115 verbunden"
            false -> "ADS1115 nicht gefunden"
            null -> "${s.optString("date")} · ${s.optString("time")}"
        }
        Tab.Led -> "${s.optString("date")} · NTP: ${s.optString("ntpText")}"
        else -> "${s.optString("date")} · ${s.optString("time")} · ${vm.host}"
    }
}

@Composable
private fun Copyright() {
    val ctx = LocalContext.current
    val version = remember {
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull().orEmpty()
    }
    Text(
        "© 2026 R.Wisser · v$version",
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        fontSize = 11.sp, color = AquaColors.Muted, textAlign = TextAlign.Center,
    )
}

@Composable
internal fun EmptyState(vm: AquaViewModel) {
    Hint(
        if (vm.host.isBlank()) "Bitte zuerst die Adresse des ESP32 eintragen (Tab „App“)." else "Lade …",
        Modifier.padding(4.dp),
    )
}

// ── Gemeinsame Status-Bausteine ──────────────────────────────────────────────

/** Kanal-Chips (K1…K4) und Mond. [offColorAware]: AUS-Farbe außerhalb der Schaltzeiten kennzeichnen. */
@Composable
internal fun ChannelChips(s: JSONObject, offColorAware: Boolean) {
    val moonLit = s.optBoolean("moonLit")
    Chips {
        val chs = s.optJSONArray("ch")
        if (chs != null) for (i in 0 until chs.length()) {
            val c = chs.getJSONObject(i)
            val on = c.optBoolean("on")
            val color = c.optString("color")
            val text = when {
                on -> "${c.optInt("level")} %"
                offColorAware && !color.equals("#000000", ignoreCase = true) -> "AUS-Farbe"
                else -> "aus"
            }
            Chip("K${i + 1} $text", on, parseHex(color))
        }
        Chip("🌙 " + if (moonLit) "an" else "aus", moonLit)
    }
}

private fun dstText(s: JSONObject) =
    if (s.optBoolean("valid")) (if (s.optBoolean("dstActive")) "Sommerzeit" else "Normalzeit") else "Uhr nicht gesetzt"

private fun moonSub(s: JSONObject) = when {
    !s.optBoolean("valid") -> "Uhr nicht gesetzt"
    s.optBoolean("moonLit") -> "Helligkeit ${s.optInt("moonBright")}/256"
    else -> "aus"
}

/** Wassertemperatur; 999 = Sensorfehler des DS18B20. */
internal fun tempOf(s: JSONObject): Double? = s.dbl("temp")?.takeIf { it < 999 }

// ── Dashboard (dashboard.html) ───────────────────────────────────────────────

@Composable
fun DashboardScreen(vm: AquaViewModel) {
    val s = vm.status
    if (s == null) { EmptyState(vm); return }
    val q = vm.water
    val stale = vm.error != null   // Werte veraltet
    val temp = tempOf(s)
    val tempErr = s.dbl("temp") != null && temp == null

    val phEn = q?.optBoolean("phEn", true) ?: true
    val tdsEn = q?.optBoolean("tdsEn", true) ?: true
    val ads = q?.optBoolean("ads", false) ?: false
    val ph = q?.dbl("ph")
    val tds = q?.dbl("tds")

    // Beleuchtungszustand für die rechte Seite der Hero-Kachel
    val chs = s.optJSONArray("ch")
    val total = chs?.length() ?: 0
    val onCount = (0 until total).count { chs!!.getJSONObject(it).optBoolean("on") }
    val light = when {
        onCount > 0 -> "$onCount von $total Kanälen an"
        s.optBoolean("moonLit") -> "Mondlicht"
        else -> "aus"
    }

    Column(Modifier.alpha(if (stale) 0.5f else 1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Hero(
            label = "Wassertemperatur",
            value = if (tempErr) "Fehler" else temp?.let { String.format(Locale.GERMANY, "%.1f", it) } ?: "–",
            unit = if (tempErr || temp == null) "" else "°C",
            stateLabel = "Beleuchtung",
            state = light,
            error = tempErr,
            history = vm.tempHist,
        )
        GroupTitle("Messwerte") { Hint("Verlauf: letzte 5 min") }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            TileRow {
                Tile(
                    "pH-Wert", fmt(ph, 2), when {
                        !phEn -> "nicht angeschlossen"
                        !ads -> "kein ADS1115"
                        ph == null -> "kein Sensorsignal"
                        else -> "DFRobot Gravity"
                    },
                    error = phEn && (ph == null || ph < 0 || ph > 14), history = vm.phHist,
                )
                Tile(
                    "TDS", fmt(tds, 0, "ppm"), when {
                        !tdsEn -> "nicht angeschlossen"
                        !ads -> "kein ADS1115"
                        tds == null -> "kein Sensorsignal"
                        else -> "CQRobot"
                    },
                    history = vm.tdsHist,
                )
            }
            TileRow {
                Tile("Leitwert", fmt(q?.dbl("ec"), 0, "µS/cm"), "TDS × 2", history = vm.ecHist)
                Tile(
                    "Wassertemperatur", if (tempErr) "Sensorfehler" else fmt(temp, 2, "°C"), "DS18B20",
                    error = tempErr,
                )
            }
        }

        GroupTitle("Status")
        AquaCard { Sec(null) {
            TileRow {
                Tile(
                    "Uhr", s.optString("time"),
                    dstText(s) + " · NTP: " + s.optString("ntpText"),
                )
                Tile(
                    "Sonne",
                    "${s.optString("sunrise").ifEmpty { "--:--" }} – ${s.optString("sunset").ifEmpty { "--:--" }}",
                    "Aufgang – Untergang",
                )
            }
            TileRow {
                Tile("Mond", if (s.optBoolean("valid")) "${s.optInt("moonAge")} Tage" else "–", moonSub(s))
                Tile("Sensoren", "", "DS18B20: " + if (tempErr) "Fehler" else "ok") {
                    Row(
                        Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        StatusDot(ads)
                        Text(
                            if (ads) "ADS1115 ok" else "ADS1115 fehlt",
                            fontSize = 20.sp, color = AquaColors.Text, fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
            ChannelChips(s, offColorAware = false)
        } }
    }
}
