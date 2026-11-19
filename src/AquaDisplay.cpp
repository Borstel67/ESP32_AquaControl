// SPDX-License-Identifier: MIT
// OLED-Anzeige: Dashboard-Werte auf SH1107 128x128 (1,5")
#include "AquaDisplay.h"
#include "AWM_Logging.h"
#include <Wire.h>

namespace aqua {

static const uint32_t kDrawEveryMs  = 1000;
static const uint32_t kProbeEveryMs = 30000;
static const uint32_t kPageEveryMs  = 3000;    // Wechsel der angezeigten Messwerte
static const uint8_t  kContrast     = 255;     // maximal (0–255); niedriger schont das OLED (Einbrennen)
static const float    kTempError    = 999.0f;
static const uint8_t  kColOffset    = 0;       // Spaltenversatz des Moduls (Pixel); 0 = kein Versatz

Display::Display(uint8_t sda, uint8_t scl, uint8_t addr)
    : _u8g2(U8G2_R0, U8X8_PIN_NONE, scl, sda), _addr(addr) {}

bool Display::probe() {
    Wire.beginTransmission(_addr);
    return Wire.endTransmission() == 0;
}

// Diagnose: alle Geräte am I2C-Bus auflisten (einmalig, wenn das OLED fehlt)
void Display::scan() {
    uint8_t found = 0;
    for (uint8_t a = 1; a < 127; ++a) {
        Wire.beginTransmission(a);
        if (Wire.endTransmission() == 0) {
            AWM_LOGI("ℹ️ I2C-Scan: Gerät an 0x%02X", a);
            ++found;
        }
    }
    if (!found) AWM_LOGW("⚠️ I2C-Scan: kein Gerät am Bus (Pins, Versorgung, Pull-ups prüfen)");
}

void Display::begin() {
    // Der I2C-Bus wird von Water::begin() gestartet; hier nur Anzeige suchen
    _lastProbe = millis();
    // U8g2 wird immer gestartet: der Wire-Probe ist nur Diagnose, das Display läuft auch,
    // wenn er fehlschlägt
    if (!probe()) {
        AWM_LOGW("⚠️ OLED an 0x%02X antwortet nicht auf Wire-Probe, starte U8g2 trotzdem", _addr);
        if (!_scanned) { _scanned = true; scan(); }
    }

    _u8g2.setI2CAddress(_addr << 1);
    _u8g2.begin();
    _u8g2.getU8x8()->x_offset = kColOffset;   // Spaltenversatz des Moduls ausgleichen
    _u8g2.setBusClock(400000);
    _u8g2.setContrast(kContrast);
    _ok = true;
    AWM_LOGI("✅ OLED SH1107 gestartet");
}

void Display::loop(const Controller::Overview& o, float ph, float tds, bool wifi) {
    const uint32_t now = millis();

    if (!_ok) {
        if (now - _lastProbe >= kProbeEveryMs) begin();   // evtl. nachträglich angesteckt
        return;
    }
    if (now - _lastDraw < kDrawEveryMs) return;
    _lastDraw = now;
    draw(o, ph, tds, wifi);
}

void Display::draw(const Controller::Overview& o, float ph, float tds, bool wifi) {
    char buf[32];
    _u8g2.clearBuffer();

    // Kopfzeile: Uhrzeit links, WLAN rechts
    _u8g2.setFont(u8g2_font_8x13_tf);
    if (o.timeValid) snprintf(buf, sizeof(buf), "%02u:%02u", o.hour, o.minute);
    else             snprintf(buf, sizeof(buf), "--:--");
    _u8g2.drawStr(0, 12, buf);
    const char* net = wifi ? "WLAN" : "kein WLAN";
    _u8g2.drawStr(128 - _u8g2.getStrWidth(net), 12, net);
    _u8g2.drawHLine(0, 15, 128);

    // Messwerte: abwechselnd (Temperatur, pH, TDS) groß in der Mitte
    const uint32_t now = millis();
    if (now - _lastPage >= kPageEveryMs) {
        _lastPage = now;
        _page = (_page + 1) % 3;
    }
    const char* label;
    const char* unit = "";
    switch (_page) {
        case 0:
            label = "Temperatur";
            unit  = "\xb0" "C";
            if (o.temp < kTempError) snprintf(buf, sizeof(buf), "%.2f", o.temp);
            else                     snprintf(buf, sizeof(buf), "--");
            break;
        case 1:
            label = "pH";
            if (!isnan(ph)) snprintf(buf, sizeof(buf), "%.2f", ph);
            else            snprintf(buf, sizeof(buf), "--");
            break;
        default:
            label = "TDS";
            unit  = "ppm";
            if (!isnan(tds)) snprintf(buf, sizeof(buf), "%.0f", tds);
            else             snprintf(buf, sizeof(buf), "--");
            break;
    }
    _u8g2.setFont(u8g2_font_10x20_tf);
    _u8g2.drawStr((128 - _u8g2.getStrWidth(label)) / 2, 46, label);

    // Wert groß, Einheit daneben (mittlere Schrift)
    _u8g2.setFont(u8g2_font_logisoso28_tf);
    const uint8_t wVal = _u8g2.getStrWidth(buf);
    _u8g2.setFont(u8g2_font_10x20_tf);
    const uint8_t wUnit = unit[0] ? _u8g2.getStrWidth(unit) : 0;
    const uint8_t gap   = unit[0] ? 4 : 0;
    const int x0 = (128 - (wVal + gap + wUnit)) / 2;
    _u8g2.drawStr(x0 + wVal + gap, 88, unit);
    _u8g2.setFont(u8g2_font_logisoso28_tf);
    _u8g2.drawStr(x0, 88, buf);

    // Fußzeile: Kanalhelligkeit in %, Mond
    _u8g2.drawHLine(0, 108, 128);
    _u8g2.setFont(u8g2_font_7x13_tf);
    int n = 0;
    buf[0] = '\0';
    for (uint8_t i = 0; i < kChannels && n < (int)sizeof(buf); ++i)
        n += snprintf(buf + n, sizeof(buf) - n, i ? " %u" : "%u", o.level[i]);
    _u8g2.drawStr(0, 125, buf);
    if (o.moonLit) _u8g2.drawStr(128 - _u8g2.getStrWidth("Mond"), 125, "Mond");

    _u8g2.sendBuffer();
}

} // namespace aqua
