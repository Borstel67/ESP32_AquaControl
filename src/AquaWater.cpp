// SPDX-License-Identifier: MIT
// Wasserqualität: ADS1115, pH (DFRobot Gravity), TDS (CQRobot), HTTP-API
#include "AquaWater.h"
#include "AWM_Logging.h"
#include "ESPWiFiManagerCommon.h"
#include <AsyncJson.h>
#include <ArduinoJson.h>
#include <Preferences.h>
#include <Wire.h>

namespace aqua {

// ADS1115-Register
static const uint8_t  kRegConversion = 0x00;
static const uint8_t  kRegConfig     = 0x01;
// OS=1 (Start) | PGA ±4,096 V | Einzelmessung | 128 SPS | Komparator aus; MUX kommt dazu
static const uint16_t kCfgBase       = 0x8000 | (0x1 << 9) | 0x0100 | (0x4 << 5) | 0x0003;
static const float    kMvPerLsb      = 0.125f;     // ±4,096 V / 32768

static const uint32_t kConvWaitMs    = 9;          // 128 SPS ≈ 7,8 ms
static const uint32_t kProbeEveryMs  = 30000;
static const float    kSmooth        = 0.05f;      // EMA je Messung (≈ 25 Messungen/s je Kanal)
static const float    kMinSignalMv   = 50.0f;      // darunter: Sensor nicht angeschlossen
static const float    kTempError     = 999.99f;
static const float    kMinSpanMv     = 100.0f;     // Mindestabstand der pH-Kalibrierpunkte
static const float    kMaxPhOffset   = 3.0f;       // pH-Offset: ±3
static const float    kMaxCalMv      = 3300.0f;    // manuelle Kalibrierspannung: 0–3300 mV
static const float    kRefTemp       = 25.0f;      // Bezugstemperatur der Kompensation (°C)
static const float    kTdsTempCoef   = 0.02f;      // TDS/Leitwert: 2 % je °C
static const float    kKelvin        = 273.15f;

// Wassertemperatur plausibel? (999.99 = unbekannt, -127 = DS18B20-Fehler)
static bool tempOk(float t) { return t > -10.0f && t < 60.0f; }

static const char* kNvsNs = "aqua";

// DFRobot-Standardwerte (Sensor ohne eigene Kalibrierung)
static const float kDefaultV7 = 1500.0f;
static const float kDefaultV4 = 2032.0f;

static void sendJson(AsyncWebServerRequest* r, int code, JsonDocument& doc) {
    String out;
    serializeJson(doc, out);
    r->send(code, "application/json", out);
}

// Zahl als JSON-Text, "null" wenn nicht verfügbar
static String numOrNull(bool ok, float v, int decimals) {
    return ok ? String(v, decimals) : String("null");
}

static void sendError(AsyncWebServerRequest* r, int code, const String& msg) {
    JsonDocument doc;
    doc["ok"]    = false;
    doc["error"] = msg;
    sendJson(r, code, doc);
}

// ── Lebenszyklus ──────────────────────────────────────────────────────────────

Water::Water(uint8_t sda, uint8_t scl, uint8_t addr) : _sda(sda), _scl(scl), _addr(addr) {
    _cal = { kDefaultV7, kDefaultV4, 1.0f, true, true, 0.0f, false, false, kRefTemp };
    _rd.temp = kTempError;
}

void Water::begin() {
    if (loadCal()) AWM_LOGI("✅ Wasser-Kalibrierung geladen");
    else           AWM_LOGI("ℹ️ Wasser-Kalibrierung: Standardwerte");

    Wire.begin(_sda, _scl);
    Wire.setClock(400000);
    Wire.setTimeOut(20);
    _adsOk = probe();
    if (!_adsOk) AWM_LOGW("⚠️ Kein ADS1115 an 0x%02X gefunden", _addr);
    _lastProbe = millis();
}

bool Water::probe() {
    Wire.beginTransmission(_addr);
    return Wire.endTransmission() == 0;
}

// Einzelmessung gegen GND an AIN0/AIN1 starten
bool Water::startConversion(uint8_t channel) {
    const uint16_t cfg = kCfgBase | ((uint16_t)(0x4 + channel) << 12);
    Wire.beginTransmission(_addr);
    Wire.write(kRegConfig);
    Wire.write(cfg >> 8);
    Wire.write(cfg & 0xFF);
    return Wire.endTransmission() == 0;
}

// ready = false, solange der ADS1115 noch wandelt
bool Water::readConversion(float& mv, bool& ready) {
    Wire.beginTransmission(_addr);
    Wire.write(kRegConfig);
    if (Wire.endTransmission(false) != 0) return false;
    if (Wire.requestFrom(_addr, (uint8_t)2) != 2) return false;
    const uint16_t cfg = ((uint16_t)Wire.read() << 8) | Wire.read();
    ready = cfg & 0x8000;
    if (!ready) return true;

    Wire.beginTransmission(_addr);
    Wire.write(kRegConversion);
    if (Wire.endTransmission(false) != 0) return false;
    if (Wire.requestFrom(_addr, (uint8_t)2) != 2) return false;
    const int16_t raw = ((uint16_t)Wire.read() << 8) | Wire.read();
    mv = (raw < 0 ? 0 : raw) * kMvPerLsb;   // Single-Ended: kleine negative Werte = 0
    return true;
}

void Water::loop(float tempC) {
    const uint32_t now = millis();
    const Cal cal = getCal();
    const bool en[2] = { cal.phEn, cal.tdsEn };

    if (!_adsOk) {
        if (now - _lastProbe >= kProbeEveryMs) {
            _lastProbe = now;
            _adsOk = probe();
            _converting = false;
            if (_adsOk) AWM_LOGI("✅ ADS1115 gefunden");
        }
    } else {
        if (!_converting) {
            // Abgeschaltete Kanäle (kein Sensor angeschlossen) überspringen
            if (!en[_channel]) { _have[_channel] = false; _channel ^= 1; }
            if (en[_channel]) {
                _adsOk = startConversion(_channel);
                _converting = _adsOk;
                _startedAt = now;
            }
        } else if (now - _startedAt >= kConvWaitMs) {
            float mv = 0;
            bool ready = false;
            if (!readConversion(mv, ready)) {
                _adsOk = false;
                _converting = false;
                _have[0] = _have[1] = false;
                _lastProbe = now;
                AWM_LOGW("⚠️ ADS1115 antwortet nicht");
            } else if (ready) {
                _mv[_channel] = _have[_channel] ? _mv[_channel] + (mv - _mv[_channel]) * kSmooth : mv;
                _have[_channel] = true;
                _channel ^= 1;
                _converting = false;   // nächster Kanal im nächsten Durchlauf
            } else if (now - _startedAt > 100) {
                _converting = false;   // hängende Wandlung verwerfen
            }
        }
    }

    portENTER_CRITICAL(&_mux);
    _rd.adsOk    = _adsOk;
    _rd.phMv     = _mv[0];
    _rd.tdsMv    = _mv[1];
    _rd.phValid  = _adsOk && en[0] && _have[0] && _mv[0] >= kMinSignalMv;
    _rd.tdsValid = _adsOk && en[1] && _have[1] && _mv[1] >= kMinSignalMv;
    _rd.temp     = tempC;
    portEXIT_CRITICAL(&_mux);
}

// ── Berechnung ────────────────────────────────────────────────────────────────

// pH aus Sensorspannung: Gerade durch (V7, pH 7) und (V4, pH 4); danach wird der feste Offset addiert.
// Temperaturkompensation (optional): die Elektrodensteilheit ist proportional zur absoluten
// Temperatur (Nernst), Drehpunkt pH 7. Die bei der Kalibrierung gemessene Steigung wird daher
// mit (T + 273,15) / (T_kal + 273,15) skaliert. Ohne gültige Temperatur: keine Kompensation.
float Water::calcPh(const Cal& c, float mv, float tempC) {
    float slope = (c.phV4 - c.phV7) / (4.0f - 7.0f);                  // mV je pH
    if (c.phTc && tempOk(tempC) && tempOk(c.phCalT))
        slope *= (tempC + kKelvin) / (c.phCalT + kKelvin);
    return 7.0f + (mv - c.phV7) / slope + c.phOff;
}

// TDS in ppm (Faktor 0,5): Polynom aus dem Hersteller-Beispiel.
// Temperaturkompensation (optional, wie im Hersteller-Beispiel): Spannung auf 25 °C umrechnen,
// V25 = V / (1 + 0,02 · (T − 25)). Ohne gültige Temperatur: keine Kompensation.
float Water::calcTds(const Cal& c, float mv, float tempC) {
    if (c.tdsTc && tempOk(tempC)) mv /= 1.0f + kTdsTempCoef * (tempC - kRefTemp);
    const float v  = mv / 1000.0f;
    const float ppm = (133.42f * v * v * v - 255.86f * v * v + 857.39f * v) * 0.5f;
    return ppm * c.tdsK;
}

// ── Config / Kalibrierung (thread-sicher) ─────────────────────────────────────

Water::Reading Water::getReading() const {
    portENTER_CRITICAL(&_mux);
    const Reading r = _rd;
    portEXIT_CRITICAL(&_mux);
    return r;
}

Water::Cal Water::getCal() const {
    portENTER_CRITICAL(&_mux);
    const Cal c = _cal;
    portEXIT_CRITICAL(&_mux);
    return c;
}

void Water::setCal(const Cal& c) {
    portENTER_CRITICAL(&_mux);
    _cal = c;
    portEXIT_CRITICAL(&_mux);
}

bool Water::loadCal() {
    Preferences p;
    if (!p.begin(kNvsNs, true)) return false;
    const bool has = p.isKey("phv7") && p.isKey("phv4") && p.isKey("tdsk");
    Cal c = { p.getFloat("phv7", kDefaultV7), p.getFloat("phv4", kDefaultV4), p.getFloat("tdsk", 1.0f),
              p.getBool("phen", true), p.getBool("tdsen", true), p.getFloat("phoff", 0.0f),
              p.getBool("phtc", false), p.getBool("tdstc", false), p.getFloat("phcalt", kRefTemp) };
    p.end();
    const bool valid = has && fabsf(c.phV4 - c.phV7) >= kMinSpanMv && c.tdsK > 0.1f && c.tdsK < 10.0f;
    if (!valid) { c.phV7 = kDefaultV7; c.phV4 = kDefaultV4; c.tdsK = 1.0f; c.phCalT = kRefTemp; }   // Schalter bleiben erhalten
    if (!(fabsf(c.phOff) <= kMaxPhOffset)) c.phOff = 0.0f;                     // auch bei NAN
    if (!tempOk(c.phCalT)) c.phCalT = kRefTemp;
    setCal(c);
    return valid;
}

bool Water::saveCal(const Cal& c) {
    Preferences p;
    if (!p.begin(kNvsNs, false)) return false;
    const bool ok = p.putFloat("phv7", c.phV7) && p.putFloat("phv4", c.phV4) && p.putFloat("tdsk", c.tdsK)
                    && p.putBool("phen", c.phEn) && p.putBool("tdsen", c.tdsEn) && p.putFloat("phoff", c.phOff)
                    && p.putBool("phtc", c.phTc) && p.putBool("tdstc", c.tdsTc) && p.putFloat("phcalt", c.phCalT);
    p.end();
    return ok;
}

void Water::values(float& ph, float& tds) const {
    const Reading rd = getReading();
    const Cal     c  = getCal();
    ph  = rd.phValid  ? calcPh(c, rd.phMv, rd.temp)   : NAN;
    tds = rd.tdsValid ? calcTds(c, rd.tdsMv, rd.temp) : NAN;
}

// ── HTTP ──────────────────────────────────────────────────────────────────────

void Water::registerRoutes(AsyncWebServer& server) {
    server.on("/water", HTTP_GET, [](AsyncWebServerRequest* r) {
        if (!serveFileFromLittleFS(r, "/water.html", "text/html"))
            r->send(500, "text/html", "<h1>water.html nicht gefunden</h1>");
    });
    server.on("/api/aqua/water", HTTP_GET, [this](AsyncWebServerRequest* r) { handleGet(r); });

    auto* post = new AsyncCallbackJsonWebHandler("/api/aqua/water/cal",
        [this](AsyncWebServerRequest* r, JsonVariant& json) { handleCalibrate(r, json); });
    post->setMethod(HTTP_POST);
    server.addHandler(post);
}

// GET /api/aqua/water: Messwerte (null = nicht verfügbar) und Kalibrierung
void Water::handleGet(AsyncWebServerRequest* r) {
    JsonDocument doc;
    buildValues(doc);
    sendJson(r, 200, doc);
}

void Water::buildValues(JsonDocument& doc) const {
    const Reading rd = getReading();
    const Cal     c  = getCal();

    doc["ads"]  = rd.adsOk;
    const float tds = calcTds(c, rd.tdsMv, rd.temp);
    doc["phEn"]  = c.phEn;
    doc["tdsEn"] = c.tdsEn;
    doc["phTc"]  = c.phTc;
    doc["tdsTc"] = c.tdsTc;
    doc["tcOk"]  = tempOk(rd.temp);   // Temperatur gültig → Kompensation aktiv (sofern eingeschaltet)
    doc["phMv"]  = serialized(numOrNull(rd.adsOk && c.phEn, rd.phMv, 1));
    doc["tdsMv"] = serialized(numOrNull(rd.adsOk && c.tdsEn, rd.tdsMv, 1));
    doc["ph"]    = serialized(numOrNull(rd.phValid, calcPh(c, rd.phMv, rd.temp), 2));
    doc["tds"]   = serialized(numOrNull(rd.tdsValid, tds, 0));
    doc["ec"]    = serialized(numOrNull(rd.tdsValid, tds * 2.0f, 0));
    doc["temp"]  = serialized(numOrNull(rd.temp < 900.0f, rd.temp, 2));

    JsonObject cal = doc["cal"].to<JsonObject>();
    cal["ph7"]  = serialized(String(c.phV7, 1));
    cal["ph4"]  = serialized(String(c.phV4, 1));
    cal["tdsK"] = serialized(String(c.tdsK, 3));
    cal["phOff"] = serialized(String(c.phOff, 2));
    cal["phT"]  = serialized(String(c.phCalT, 1));
}

// POST /api/aqua/water/cal (JSON), genau eine Aktion:
//   {"ph7":true}  aktuelle pH-Spannung als pH 7 übernehmen (Sensor in Pufferlösung pH 7)
//   {"ph4":true}  aktuelle pH-Spannung als pH 4 übernehmen
//   {"tds":1413}  Referenzlösung in ppm: TDS-Faktor so setzen, dass der Messwert passt
//   {"phV7":1500,"phV4":2032}  pH-Spannungen (mV) manuell setzen; auch nur einer der beiden Werte
//   {"phOff":-0.5}  fester pH-Offset (±3), wird zum pH-Wert addiert
//   {"reset":true} Werkseinstellung der Kalibrierung (pH-Spannungen, Offset, TDS-Faktor)
//   {"phEn":false,"tdsEn":true}  Sensor als angeschlossen/nicht angeschlossen markieren (einzeln oder beide)
//   {"phTc":true,"tdsTc":false}  Temperaturkompensation pH / TDS ein- oder ausschalten (einzeln oder beide)
// Die Puffer-Kalibrierung (ph7/ph4) merkt sich die aktuelle Wassertemperatur als Bezug der
// pH-Kompensation; manuelle Spannungen und Reset beziehen sich auf 25 °C.
void Water::handleCalibrate(AsyncWebServerRequest* r, JsonVariant& json) {
    if (!json.is<JsonObject>()) { sendError(r, 400, "JSON-Objekt erwartet"); return; }
    JsonObjectConst o = json.as<JsonObjectConst>();

    const Reading rd = getReading();
    Cal c = getCal();
    const float calT = tempOk(rd.temp) ? rd.temp : kRefTemp;

    if (!o["phEn"].isNull() || !o["tdsEn"].isNull() || !o["phTc"].isNull() || !o["tdsTc"].isNull()) {
        if (!o["phEn"].isNull())  c.phEn  = o["phEn"]  | true;
        if (!o["tdsEn"].isNull()) c.tdsEn = o["tdsEn"] | true;
        if (!o["phTc"].isNull())  c.phTc  = o["phTc"]  | false;
        if (!o["tdsTc"].isNull()) c.tdsTc = o["tdsTc"] | false;
    } else if (o["reset"] | false) {
        c.phV7 = kDefaultV7; c.phV4 = kDefaultV4; c.tdsK = 1.0f; c.phOff = 0.0f;   // Schalter bleiben erhalten
        c.phCalT = kRefTemp;
    } else if (!o["phOff"].isNull()) {
        const float off = o["phOff"] | NAN;
        if (!(fabsf(off) <= kMaxPhOffset)) { sendError(r, 400, "pH-Offset muss zwischen -3 und +3 liegen"); return; }
        c.phOff = off;
    } else if (!o["phV7"].isNull() || !o["phV4"].isNull()) {
        const float v7 = o["phV7"].isNull() ? c.phV7 : (o["phV7"] | NAN);
        const float v4 = o["phV4"].isNull() ? c.phV4 : (o["phV4"] | NAN);
        if (!(v7 > 0 && v7 <= kMaxCalMv && v4 > 0 && v4 <= kMaxCalMv)) { sendError(r, 400, "Spannung muss zwischen 1 und 3300 mV liegen"); return; }
        if (fabsf(v4 - v7) < kMinSpanMv) { sendError(r, 400, "pH-7- und pH-4-Spannung müssen mindestens 100 mV auseinanderliegen"); return; }
        c.phV7 = v7;
        c.phV4 = v4;
        c.phCalT = kRefTemp;
    } else if (o["ph7"] | false) {
        if (!rd.phValid) { sendError(r, 400, "Kein pH-Signal (Sensor/ADS1115 prüfen)"); return; }
        if (fabsf(c.phV4 - rd.phMv) < kMinSpanMv) { sendError(r, 400, "pH 7 und pH 4 liegen zu dicht beieinander – erst pH 4 zurücksetzen"); return; }
        c.phV7 = rd.phMv;
        c.phCalT = calT;
    } else if (o["ph4"] | false) {
        if (!rd.phValid) { sendError(r, 400, "Kein pH-Signal (Sensor/ADS1115 prüfen)"); return; }
        if (fabsf(rd.phMv - c.phV7) < kMinSpanMv) { sendError(r, 400, "Spannung zu nahe an pH 7 – Sensor in pH-4-Lösung? Erst pH 7 kalibrieren"); return; }
        c.phV4 = rd.phMv;
        c.phCalT = calT;   // die Steigung entsteht erst mit pH 4 → dessen Temperatur zählt
    } else if (!o["tds"].isNull()) {
        const float ref = o["tds"] | 0.0f;
        if (ref < 10 || ref > 5000) { sendError(r, 400, "Referenzwert muss 10–5000 ppm sein"); return; }
        if (!rd.tdsValid) { sendError(r, 400, "Kein TDS-Signal (Sensor/ADS1115 prüfen)"); return; }
        Cal raw = c; raw.tdsK = 1.0f;
        const float measured = calcTds(raw, rd.tdsMv, rd.temp);   // ggf. temperaturkompensiert
        if (measured < 1.0f) { sendError(r, 400, "TDS-Messwert zu klein für Kalibrierung"); return; }
        const float k = ref / measured;
        if (k < 0.2f || k > 5.0f) { sendError(r, 400, "Faktor außerhalb 0,2–5 – Sensor/Lösung prüfen"); return; }
        c.tdsK = k;
    } else {
        sendError(r, 400, "Unbekannte Aktion");
        return;
    }

    if (!saveCal(c)) { sendError(r, 500, "Speichern fehlgeschlagen (NVS)"); return; }
    setCal(c);

    JsonDocument ok;
    ok["ok"] = true;
    sendJson(r, 200, ok);
}

} // namespace aqua
