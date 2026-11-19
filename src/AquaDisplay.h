// SPDX-License-Identifier: MIT
// OLED-Anzeige 1,5" (128x128, SH1107) per I2C:
// zeigt die Dashboard-Werte (Uhrzeit, Temperatur, pH, TDS, Kanäle, Mond, WLAN).
// Teilt sich den I2C-Bus mit dem ADS1115; Aktualisierung 1x pro Sekunde.
#pragma once
#include <Arduino.h>
#include <U8g2lib.h>
#include "AquaController.h"

namespace aqua {

class Display {
public:
    Display(uint8_t sda, uint8_t scl, uint8_t addr = 0x3C);

    void begin();
    // ph/tds: NAN = nicht verfügbar
    void loop(const Controller::Overview& o, float ph, float tds, bool wifiConnected);

private:
    bool probe();
    void scan();
    void draw(const Controller::Overview& o, float ph, float tds, bool wifi);

    // 128x128-Monochrom-OLED meist SH1107; Alternativen: U8G2_SH1107_SEEED_128X128_*, U8G2_SH1107_PIMORONI_128X128_*
    // (Graustufen-Module: U8G2_SSD1327_WS_128X128_*)
    U8G2_SH1107_128X128_F_HW_I2C _u8g2;
    uint8_t  _addr;
    bool     _ok = false;
    bool     _scanned = false;
    uint32_t _lastDraw  = 0;
    uint32_t _lastProbe = 0;
    uint32_t _lastPage  = 0;
    uint8_t  _page      = 0;   // 0 = Temperatur, 1 = pH, 2 = TDS
};

} // namespace aqua
