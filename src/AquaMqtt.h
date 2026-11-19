// SPDX-License-Identifier: MIT
// MQTT-Anbindung (PubSubClient):
// - veröffentlicht alle Werte als einzelne Topics: <base>/status/..., <base>/water/..., <base>/settings/...
//   (JSON-Struktur wird flach gelegt, z. B. <base>/status/ch/0/level, <base>/water/ph)
// - <base>/online: "1" retained, Last Will "0"
// - Verbindung nicht-blockierend im loop() mit Wiederholung alle 10 s
// - Konfiguration in NVS (Namespace "mqtt"), API GET/POST /api/aqua/mqtt
#pragma once
#include <Arduino.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <ESPAsyncWebServer.h>
#include "AquaController.h"
#include "AquaWater.h"

namespace aqua {

class Mqtt {
public:
    Mqtt(Controller& ctl, Water& water);

    void begin();                               // Konfiguration laden
    void loop(bool wifiConnected);              // im loop() aufrufen
    void registerRoutes(AsyncWebServer& server);

private:
    // POD, unter Spinlock kopierbar (HTTP-Task ↔ loop())
    struct Cfg {
        bool     enabled;
        char     host[64];
        uint16_t port;
        char     user[32];
        char     pass[64];
        char     base[48];      // Topic-Präfix ohne führenden/abschließenden "/"
        uint16_t intervalS;     // Veröffentlichungsintervall der Messwerte
    };

    Cfg  getCfg() const;
    void setCfg(const Cfg& c);
    bool loadCfg();
    bool saveCfg(const Cfg& c);

    bool connectBroker(const Cfg& c);
    void publishAll(const Cfg& c, bool withSettings);
    void publishDoc(const Cfg& c, const char* group, JsonDocument& doc, bool retain);
    void publishFlat(const String& topic, JsonVariantConst v, bool retain);

    void handleGet(AsyncWebServerRequest* r);
    void handlePost(AsyncWebServerRequest* r, JsonVariant& json);

    Controller& _ctl;
    Water&      _water;

    WiFiClient   _net;
    PubSubClient _mqtt;

    mutable portMUX_TYPE _mux = portMUX_INITIALIZER_UNLOCKED;
    Cfg      _cfg = {};
    uint32_t _cfgVersion     = 0;
    uint32_t _appliedVersion = UINT32_MAX;

    uint32_t _lastTry      = 0;
    uint32_t _lastPublish  = 0;
    uint32_t _lastSettings = 0;
    bool     _wasConnected = false;
    String   _lastError;
    volatile bool _connected = false;           // für HTTP-Status
};

} // namespace aqua
