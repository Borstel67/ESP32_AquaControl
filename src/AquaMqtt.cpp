// SPDX-License-Identifier: MIT
// MQTT: Konfiguration, Verbindung, Veröffentlichung aller Werte
#include "AquaMqtt.h"
#include "AWM_Logging.h"
#include "ESPWiFiManagerCommon.h"
#include <AsyncJson.h>
#include <Preferences.h>

namespace aqua {

static const char*    kNvsNs          = "mqtt";
static const uint32_t kRetryMs        = 10000;
static const uint32_t kSettingsEveryMs = 60000;
static const uint16_t kMinInterval    = 1;
static const uint16_t kMaxInterval    = 3600;

static void sendJson(AsyncWebServerRequest* r, int code, JsonDocument& doc) {
    String out;
    serializeJson(doc, out);
    r->send(code, "application/json", out);
}

static void sendError(AsyncWebServerRequest* r, int code, const String& msg) {
    JsonDocument doc;
    doc["ok"]    = false;
    doc["error"] = msg;
    sendJson(r, code, doc);
}

// Kopiert s in ein char-Array fester Größe; false, wenn zu lang
template <size_t N>
static bool copyStr(char (&dst)[N], const char* s) {
    if (strlen(s) >= N) return false;
    strcpy(dst, s);
    return true;
}

static void setDefaults(char* base, size_t n) {
    snprintf(base, n, "aquacontrol");
}

// ── Lebenszyklus ──────────────────────────────────────────────────────────────

Mqtt::Mqtt(Controller& ctl, Water& water) : _ctl(ctl), _water(water), _mqtt(_net) {
    _cfg.port      = 1883;
    _cfg.intervalS = 10;
    setDefaults(_cfg.base, sizeof(_cfg.base));
}

void Mqtt::begin() {
    if (loadCfg()) AWM_LOGI("✅ MQTT-Konfiguration geladen");
    else           AWM_LOGI("ℹ️ MQTT nicht konfiguriert");
    _mqtt.setSocketTimeout(2);
    _mqtt.setKeepAlive(30);
    _mqtt.setBufferSize(512);
}

Mqtt::Cfg Mqtt::getCfg() const {
    portENTER_CRITICAL(&_mux);
    const Cfg c = _cfg;
    portEXIT_CRITICAL(&_mux);
    return c;
}

void Mqtt::setCfg(const Cfg& c) {
    portENTER_CRITICAL(&_mux);
    _cfg = c;
    _cfgVersion++;
    portEXIT_CRITICAL(&_mux);
}

bool Mqtt::loadCfg() {
    Preferences p;
    if (!p.begin(kNvsNs, true)) return false;
    const bool has = p.isKey("host");
    Cfg c = getCfg();
    c.enabled   = p.getBool("en", false);
    c.port      = p.getUShort("port", 1883);
    c.intervalS = p.getUShort("int", 10);
    copyStr(c.host, p.getString("host", "").c_str());
    copyStr(c.user, p.getString("user", "").c_str());
    copyStr(c.pass, p.getString("pass", "").c_str());
    const String base = p.getString("base", "");
    if (base.length()) copyStr(c.base, base.c_str());
    p.end();
    if (c.intervalS < kMinInterval || c.intervalS > kMaxInterval) c.intervalS = 10;
    setCfg(c);
    return has;
}

bool Mqtt::saveCfg(const Cfg& c) {
    Preferences p;
    if (!p.begin(kNvsNs, false)) return false;
    const bool ok = p.putBool("en", c.enabled) && p.putUShort("port", c.port) && p.putUShort("int", c.intervalS)
                    && p.putString("host", c.host) && p.putString("user", c.user)
                    && p.putString("pass", c.pass) && p.putString("base", c.base);
    p.end();
    return ok;
}

// ── Verbindung ────────────────────────────────────────────────────────────────

bool Mqtt::connectBroker(const Cfg& c) {
    String id = WiFi.getHostname() ? String(WiFi.getHostname()) : String("aquacontrol");
    id += "-" + String((uint32_t)(ESP.getEfuseMac() & 0xFFFFFF), HEX);

    const String willTopic = String(c.base) + "/online";
    _mqtt.setServer(c.host, c.port);
    const bool ok = *c.user
        ? _mqtt.connect(id.c_str(), c.user, c.pass, willTopic.c_str(), 1, true, "0")
        : _mqtt.connect(id.c_str(), nullptr, nullptr, willTopic.c_str(), 1, true, "0");
    if (ok) {
        _mqtt.publish(willTopic.c_str(), "1", true);
        _lastError = "";
    } else {
        _lastError = "Verbindung fehlgeschlagen (rc=" + String(_mqtt.state()) + ")";
    }
    return ok;
}

void Mqtt::loop(bool wifiConnected) {
    const uint32_t now = millis();
    const Cfg c = getCfg();

    // Konfiguration geändert → bestehende Verbindung sauber beenden und neu aufbauen
    portENTER_CRITICAL(&_mux);
    const uint32_t ver = _cfgVersion;
    portEXIT_CRITICAL(&_mux);
    if (ver != _appliedVersion) {
        _appliedVersion = ver;
        if (_mqtt.connected()) {
            _mqtt.publish((String(c.base) + "/online").c_str(), "0", true);
            _mqtt.disconnect();
        }
        _lastTry = 0;
    }

    if (!c.enabled || !*c.host || !wifiConnected) {
        if (_mqtt.connected()) _mqtt.disconnect();
        _connected = false;
        _wasConnected = false;
        return;
    }

    if (!_mqtt.connected()) {
        _connected = false;
        _wasConnected = false;
        if (_lastTry && now - _lastTry < kRetryMs) return;
        _lastTry = now ? now : 1;
        if (!connectBroker(c)) { AWM_LOGW("⚠️ MQTT: %s", _lastError.c_str()); return; }
        AWM_LOGI("✅ MQTT verbunden: %s:%u", c.host, c.port);
    }

    _connected = true;
    _mqtt.loop();

    // Erst nach dem Verbinden sofort alles senden, danach im Intervall
    const bool first = !_wasConnected;
    if (first || now - _lastPublish >= (uint32_t)c.intervalS * 1000UL) {
        _lastPublish = now;
        const bool withSettings = first || now - _lastSettings >= kSettingsEveryMs;
        if (withSettings) _lastSettings = now;
        publishAll(c, withSettings);
    }
    _wasConnected = true;
}

// ── Veröffentlichung ──────────────────────────────────────────────────────────

// JSON rekursiv in Topics umsetzen: Objekt → /<key>, Array → /<index>, null → leerer Payload
void Mqtt::publishFlat(const String& topic, JsonVariantConst v, bool retain) {
    if (v.is<JsonObjectConst>()) {
        for (JsonPairConst kv : v.as<JsonObjectConst>())
            publishFlat(topic + "/" + kv.key().c_str(), kv.value(), retain);
    } else if (v.is<JsonArrayConst>()) {
        size_t i = 0;
        for (JsonVariantConst e : v.as<JsonArrayConst>())
            publishFlat(topic + "/" + String(i++), e, retain);
    } else {
        String payload;
        if (v.is<bool>())        payload = v.as<bool>() ? "true" : "false";
        else if (!v.isNull())    payload = v.as<String>();
        _mqtt.publish(topic.c_str(), payload.c_str(), retain);
    }
}

void Mqtt::publishDoc(const Cfg& c, const char* group, JsonDocument& doc, bool retain) {
    // serialized()-Rohwerte (Nachkommastellen) werden erst durch Rundlauf zu echten Zahlen
    String text;
    serializeJson(doc, text);
    JsonDocument flat;
    if (deserializeJson(flat, text)) return;
    publishFlat(String(c.base) + "/" + group, flat.as<JsonVariantConst>(), retain);
}

void Mqtt::publishAll(const Cfg& c, bool withSettings) {
    {
        JsonDocument doc;
        _ctl.buildStatus(doc);
        publishDoc(c, "status", doc, false);
    }
    {
        JsonDocument doc;
        _water.buildValues(doc);
        publishDoc(c, "water", doc, false);
    }
    if (withSettings) {
        JsonDocument doc;
        _ctl.buildSettings(doc);
        publishDoc(c, "settings", doc, true);
    }
}

// ── HTTP ──────────────────────────────────────────────────────────────────────

void Mqtt::registerRoutes(AsyncWebServer& server) {
    server.on("/mqtt", HTTP_GET, [](AsyncWebServerRequest* r) {
        if (!serveFileFromLittleFS(r, "/mqtt.html", "text/html"))
            r->send(500, "text/html", "<h1>mqtt.html nicht gefunden</h1>");
    });
    server.on("/api/aqua/mqtt", HTTP_GET, [this](AsyncWebServerRequest* r) { handleGet(r); });

    auto* post = new AsyncCallbackJsonWebHandler("/api/aqua/mqtt",
        [this](AsyncWebServerRequest* r, JsonVariant& json) { handlePost(r, json); });
    post->setMethod(HTTP_POST);
    server.addHandler(post);
}

// GET /api/aqua/mqtt: Konfiguration (Passwort wird nie ausgegeben) und Verbindungsstatus
void Mqtt::handleGet(AsyncWebServerRequest* r) {
    const Cfg c = getCfg();
    JsonDocument doc;
    doc["enabled"]   = c.enabled;
    doc["host"]      = c.host;
    doc["port"]      = c.port;
    doc["user"]      = c.user;
    doc["passSet"]   = *c.pass != 0;
    doc["base"]      = c.base;
    doc["interval"]  = c.intervalS;
    doc["connected"] = (bool)_connected;
    doc["error"]     = _lastError;
    sendJson(r, 200, doc);
}

// POST /api/aqua/mqtt (JSON, Teil-Updates): enabled, host, port, user, pass, base, interval
void Mqtt::handlePost(AsyncWebServerRequest* r, JsonVariant& json) {
    if (!json.is<JsonObject>()) { sendError(r, 400, "JSON-Objekt erwartet"); return; }
    JsonObjectConst o = json.as<JsonObjectConst>();
    Cfg c = getCfg();

    if (!o["enabled"].isNull()) c.enabled = o["enabled"] | false;
    if (!o["host"].isNull() && !copyStr(c.host, o["host"] | "")) { sendError(r, 400, "Host zu lang"); return; }
    if (!o["user"].isNull() && !copyStr(c.user, o["user"] | "")) { sendError(r, 400, "Benutzer zu lang"); return; }
    if (!o["pass"].isNull() && !copyStr(c.pass, o["pass"] | "")) { sendError(r, 400, "Passwort zu lang"); return; }
    if (!o["port"].isNull()) {
        const long p = o["port"] | 0L;
        if (p < 1 || p > 65535) { sendError(r, 400, "Port muss 1–65535 sein"); return; }
        c.port = (uint16_t)p;
    }
    if (!o["interval"].isNull()) {
        const long i = o["interval"] | 0L;
        if (i < kMinInterval || i > kMaxInterval) { sendError(r, 400, "Intervall muss 1–3600 s sein"); return; }
        c.intervalS = (uint16_t)i;
    }
    if (!o["base"].isNull()) {
        String b = o["base"] | "";
        b.trim();
        while (b.startsWith("/")) b.remove(0, 1);
        while (b.endsWith("/"))   b.remove(b.length() - 1);
        if (!b.length() || b.indexOf('#') >= 0 || b.indexOf('+') >= 0 || !copyStr(c.base, b.c_str())) {
            sendError(r, 400, "Ungültiges Topic-Präfix");
            return;
        }
    }
    if (c.enabled && !*c.host) { sendError(r, 400, "Host fehlt"); return; }

    if (!saveCfg(c)) { sendError(r, 500, "Speichern fehlgeschlagen (NVS)"); return; }
    setCfg(c);

    JsonDocument ok;
    ok["ok"] = true;
    sendJson(r, 200, ok);
}

} // namespace aqua
