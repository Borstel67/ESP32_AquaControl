// SPDX-License-Identifier: MIT
// Wasserqualität über einen ADS1115 (I2C, 16 Bit):
// - Kanal 0 (AIN0): pH-Sensor DFRobot Gravity (Analog-pH, Ausgang 0–3 V)
// - Kanal 1 (AIN1): TDS-Sensor CQRobot (Ausgang 0–2,3 V)
// - nicht-blockierende Messung im loop(), Glättung per gleitendem Mittel
// - pH: 2-Punkt-Kalibrierung (pH 7 / pH 4), Spannungen auch manuell einstellbar,
//       zusätzlicher pH-Offset, optionale Temperaturkompensation (Nernst-Steigung)
// - TDS: Korrekturfaktor aus Referenzlösung, optionale Temperaturkompensation (2 %/°C, Bezug 25 °C)
// - HTTP-Seite /water, API /api/aqua/water (GET) und /api/aqua/water/cal (POST)
#pragma once
#include <Arduino.h>
#include <ArduinoJson.h>
#include <ESPAsyncWebServer.h>

namespace aqua {

class Water {
public:
    Water(uint8_t sda, uint8_t scl, uint8_t addr = 0x48);

    void begin();
    void loop(float tempC);                     // tempC: Wassertemperatur, >= 900 = unbekannt
    void registerRoutes(AsyncWebServer& server);
    void values(float& ph, float& tds) const;   // kalibrierte Werte; NAN = nicht verfügbar
    void buildValues(JsonDocument& doc) const;  // Messwerte + Kalibrierung als JSON (HTTP und MQTT)

private:
    // Kalibrierung (POD, unter Spinlock kopierbar)
    struct Cal {
        float phV7;     // Sensorspannung in mV bei pH 7
        float phV4;     // Sensorspannung in mV bei pH 4
        float tdsK;     // Korrekturfaktor TDS
        bool  phEn;     // pH-Sensor angeschlossen (aus = Kanal wird nicht gemessen)
        bool  tdsEn;    // TDS-Sensor angeschlossen
        float phOff;    // fester pH-Offset, wird zum berechneten pH addiert
        bool  phTc;     // Temperaturkompensation pH
        bool  tdsTc;    // Temperaturkompensation TDS/Leitwert
        float phCalT;   // Wassertemperatur bei der pH-Kalibrierung (°C)
    };

    struct Reading {
        bool  adsOk;
        bool  phValid;
        bool  tdsValid;
        float phMv;
        float tdsMv;
        float temp;     // °C, 999.99 = unbekannt
    };

    Reading getReading() const;
    Cal     getCal() const;
    void    setCal(const Cal& c);
    bool    loadCal();
    bool    saveCal(const Cal& c);

    static float calcPh(const Cal& c, float mv, float tempC);
    static float calcTds(const Cal& c, float mv, float tempC);

    bool     probe();
    bool     startConversion(uint8_t channel);
    bool     readConversion(float& mv, bool& ready);

    void handleGet(AsyncWebServerRequest* r);
    void handleCalibrate(AsyncWebServerRequest* r, JsonVariant& json);

    uint8_t _sda, _scl, _addr;

    mutable portMUX_TYPE _mux = portMUX_INITIALIZER_UNLOCKED;
    Cal      _cal = {};
    Reading  _rd  = {};

    // Messzustand (nur loop()-Task)
    bool     _adsOk      = false;
    uint8_t  _channel    = 0;
    bool     _converting = false;
    uint32_t _startedAt  = 0;
    uint32_t _lastProbe  = 0;
    float    _mv[2]      = {};
    bool     _have[2]    = {};
};

} // namespace aqua
