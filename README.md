# ESP32_AquaControl

Umfassender WLAN-Manager für ESP32 mit Captive-Portal, OTA (Firmware + LittleFS), Web-UI und automatischer Fallback-Logik. Provisionierung erfolgt wahlweise per SoftAP/Captive-Portal oder über die STA-UI, Daten liegen in LittleFS. OTA wird über ElegantOTA bereitgestellt.

## Features
- **Captive Portal (AP + DNS-Catch-All)** für Erstkonfiguration / Re-Provisionierung.
- **STA-Modus** mit DHCP oder statischer IP, DNS, Hostname, NTP.
- **Web-UI** (LittleFS, `data/*.html`, `WifiManager.css`) und **REST-API** für STA-Config (`/api/sta/config`).
- **OTA-Updates**:
  - Firmware OTA via `App-OTA.ps1` (espota.py, WLAN).
  - LittleFS seriell via `LittleFS.ps1` oder OTA via `LittleFS-OTA.ps1`.
  - ElegantOTA unter `/update` (AP + STA-Server).
- **WiFi-Scan** (`/scan`, `/scan.json`).
- **Fallback-Strategien**: ON_FAIL, NO_CREDENTIALS_ONLY, SMART_RETRIES, BUTTON_ONLY, NEVER.
- **Auto-Reconnect** mit Backoff/Fenster; optionale Internetprüfung (HTTP 204).
- **LED-/Button-Handling**, **mDNS** (`<hostname>.local`), **NTP**.

Ausführliche Dokumentation (Hardware, Bedienung, API, Fehlerbehebung): [docs/DOKUMENTATION.md](docs/DOKUMENTATION.md)

## Aquarium-LED-Steuerung
Läuft unabhängig vom WLAN (auch im Portal-Modus); bedient wird sie im STA-Modus über `/led` und `/config`.

- **Hardware:** WS2812B-Datenleitung an `LED_STRIP_PIN` (Standard GPIO16), DS18B20 an `ONEWIRE_PIN` (Standard GPIO4, 4,7 kΩ Pull-up nach 3,3 V).
- **4 Farbkanäle:** LEDs reihum verteilt (Kanal 1 = LED 1, 5, 9, …). Je Kanal Farbe EIN/AUS, Betriebsart UHR/EIN/AUS und zwei Schaltzeiträume (gleiche EIN-/AUS-Zeit = deaktiviert, über Mitternacht möglich).
- **Sonnenauf-/-untergang (Dimmung):** ab EIN-Zeit 3 Stufen hoch, vor AUS-Zeit 3 Stufen herunter; Dauer je Stufe und Helligkeit je Stufe (%) einstellbar, 0 min = hart schalten.
- **Anzahl LEDs:** 1–400.
- **Mondbeleuchtung:** LED-Bereiche `von-bis;einzeln;von-bis` (max. 5), eigene Farbe. AUTO: an, wenn alle Kanäle außerhalb ihrer Schaltzeiten sind; Helligkeit = 2 × Tage bis/seit Neumond (/256). EIN/AUS: alle Kanäle aus, Mond an (Vollmond-Helligkeit) bzw. aus.
- **Uhr:** NTP (Server einstellbar, leer = `de.pool.ntp.org`), Zeitzone GMT-12 … GMT+13, EU-Sommerzeit abschaltbar, manuelle Zeiteinstellung, NTP-Status.
- **Sonne/Mond:** Sonnenaufgang/-untergang und Mondalter aus Breiten-/Längengrad (Bibliothek *sunMoon*).
- **Temperatur:** DS18B20, Anzeige jede Sekunde; `999.99` bei Sensorfehler.
- **Wasserqualität:** ADS1115 (I2C, GPIO8/9): AIN0 pH (DFRobot Gravity), AIN1 TDS (CQRobot); Seite `/water` mit Kalibrierung.
- **API:** `GET /api/aqua/status`, `GET/POST /api/aqua/settings` (JSON, Teil-Updates; zusätzlich `ntp` und `setTime`).
- **Android-App:** `android/` (Kotlin + Compose). In Android Studio öffnen oder per Kommandozeile bauen (`cd android`, `gradlew assembleDebug`, JDK 17+ über `JAVA_HOME`; APK unter `app/build/outputs/apk/debug/`), in den App-Einstellungen die IP des ESP32 eintragen. Zeigt Dashboard (Temp, pH, TDS, Kanäle, Mond) und stellt Kanal-/Mond-Betriebsart, Farben und Mond-Bereiche ein.
- **Bibliotheken:** Adafruit NeoPixel, OneWire, DallasTemperature, sunMoon, Time (TimeLib).
- **Speicher:** Einstellungen als JSON in NVS (Namespace `aqua`), NTP-Server als `ntp1` in der WLAN-Config.

## MQTT
Veröffentlicht alle Werte als einzelne Topics (Bibliothek *PubSubClient* erforderlich). Konfiguration über die Seite `/mqtt` (`data/mqtt.html`, Menü „📡 MQTT“) oder per API, gespeichert in NVS (Namespace `mqtt`):

```
curl -X POST http://<ip>/api/aqua/mqtt -H "Content-Type: application/json" \
  -d '{"enabled":true,"host":"192.168.1.10","port":1883,"user":"","pass":"","base":"aquacontrol","interval":10}'
```
`GET /api/aqua/mqtt` liefert Konfiguration (ohne Passwort), Verbindungsstatus und letzten Fehler.

| Topic | Inhalt |
|---|---|
| `<base>/online` | `1` (retained); Last Will `0` |
| `<base>/status/…` | Uhrzeit, NTP, Sonne/Mond, `temp`, `ch/0..3/{on,level,color}` (alle `interval` s) |
| `<base>/water/…` | `ph`, `tds`, `ec`, `temp`, `phMv`, `tdsMv`, `ads`, `cal/…` (alle `interval` s) |
| `<base>/settings/…` | komplette Konfiguration (retained, beim Verbinden und alle 60 s) |

Nicht verfügbare Werte (Sensorfehler) werden mit leerem Payload gesendet. Die Einstellungen sind nur lesend; geändert wird weiterhin über die Web-UI/HTTP-API.

## Projektstruktur (relevant)
- `ESP32_AquaControl.ino` — Beispiel-Sketch (Startlogik, LED/Button, OTA-Init).
- `src/ESPWiFiManagerAP.*` — AP/Captive-Portal, Scan, OTA, mDNS, Fallback.
- `src/ESPWiFiManagerSTA.*` — STA-Server, `/api/sta/config`, optional Captive-Redirect.
- `src/ESPWiFiManagerCommon.*` — Utilities (Credentials, `config.json`, NTP, File-Streaming).
- `src/AquaConfig.*` — LED-/Uhr-Konfiguration (Datenmodell, JSON, NVS).
- `src/AquaClock.*` — Zeitzone, EU-Sommerzeit, NTP-Status, manuelle Zeit.
- `src/AquaController.*` — LED-Rendering, Dimm-Rampe, Mond/Sonne, DS18B20, HTTP-API.
- `src/AquaWater.*` — ADS1115, pH/TDS, Kalibrierung, `/water`-API.
- `src/AquaMqtt.*` — MQTT-Client, veröffentlicht alle Werte, `/api/aqua/mqtt`.
- `data/led.html`, `data/water.html`, `data/config.html` — LED-Steuerung, Wasserqualität und Konfiguration (STA).
- `data/` — Web-Assets (LittleFS-Inhalt: `indexAP.html`, `indexSTA.html`, `settings.html`, `success.html`, `error.html`, `WifiManager.css`).
- `LittleFS.ps1`, `LittleFS-OTA.ps1`, `App-OTA.ps1` — Flash/OTA-Skripte.

## Abhängigkeiten
- Arduino Core ESP32 (3.x empfohlen).
- Bibliotheken: ArduinoJson, ESPAsyncWebServer, AsyncTCP, ElegantOTA, LittleFS (im Core enthalten).
- Tools: `espota.py` (kommt mit Core), `mklittlefs`.

## Build (Visual Studio + vMicro)
1. Board/FQBN wählen (z. B. ESP32-WROOM-DA).  
2. Partitionen: benötigt 2 OTA-Slots + 1 DATA (LittleFS). Werte in `LittleFS.ps1`/`partitions.csv` prüfen (Offset/Size).  
3. Abhängigkeiten installieren (s.o.).  
4. LittleFS-Inhalt hochladen (seriell) oder per Skript/OTA (siehe unten).  
5. Sketch build/flash über vMicro (USB) oder OTA.

## Flash-/OTA-Skripte
- LittleFS seriell:  
  `.\LittleFS.ps1 -Port COM4`  (Port bei Bedarf anpassen)
- LittleFS OTA:  
  `.\LittleFS-OTA.ps1 -Ip <ESP-IP> [-Port 3232] [-Password <ota-pass>]`
- Firmware OTA:  
  `.\App-OTA.ps1 -Ip <ESP-IP> -Bin .\build\firmware.bin [-Port 3232] [-Password <ota-pass>]`

## Laufzeitverhalten (Beispiel-Sketch)
- Boot: versucht STA-Verbindung mit gespeicherten Credentials.
- Fallback: öffnet Captive-Portal (AP + DNS-Catch-All) bei Fehlschlag.
- Button (`BTN_PIN`, LOW-aktiv):  
  - Kurzer Druck (~≥700 ms): Portal öffnen.  
  - 2–5 s halten: Portal öffnen (Sketch-Logik).  
  - ≥5 s halten: Credentials löschen, Neustart.
- LED (`LED_PIN`): OFF/ON/BLINK_SLOW/BLINK_FAST (Sketch); interne AWM-LED-Patterns für Portal/Scan/Status.

## HTTP-Routen (Auszug)
- AP: `/`, `/settings`, `/scan`, `/scan.json`, `/save` (POST), `/erase` (POST), `/WifiManager.css`
- STA: `/`, `/settings`, `/api/sta/config` (GET/POST), `/erase` (POST), `/WifiManager.css`, `/led`, `/water`, `/config`, `/api/aqua/status`, `/api/aqua/settings` (GET/POST), `/api/aqua/water` (GET), `/api/aqua/water/cal` (POST)
- Captive-Redirects: `/generate_204`, `/gen_204`, `/hotspot-detect.html`, `/connecttest.txt`, `/ncsi.txt`, `/fwlink`
- OTA: `/update` (ElegantOTA)

## Persistenz
- `config.json`/NVS: SSID, Passwort, DHCP/static (IP/GW/SN/DNS), Hostname, NTP.  
- Geschützte JSONs können via `setProtectedJsons({...})` von Löschroutinen ausgenommen werden.

## Hardware-Annahmen
- WS2812B-Daten: `LED_STRIP_PIN` (Standard GPIO16).
- DS18B20: `ONEWIRE_PIN` (Standard GPIO4, 4,7 kΩ Pull-up nach 3,3 V).
- ADS1115: `I2C_SDA_PIN` / `I2C_SCL_PIN` (Standard GPIO8 / GPIO9), Adresse `ADS1115_ADDR` (0x48); AIN0 = pH, AIN1 = TDS.
- OLED M242-12864-4-V2.0 (SSD1309, U8g2): I2C am selben Bus, Adresse `OLED_ADDR` (0x3C); zeigt Uhrzeit, Temperatur, pH, TDS, Kanäle.
- LED: `LED_PIN` (Standard GPIO2).
- Taster: `BTN_PIN` (Standard GPIO0, LOW-aktiv, -1 zum Deaktivieren).
- Vollständige Pinbelegung, Anschlussplan, Stromversorgung und Stückliste: [docs/DOKUMENTATION.md](docs/DOKUMENTATION.md#hardware--verdrahtung)
- Board: ESP32; Sleep ist deaktiviert, Power-Save aus.

## Hinweise zu Partitionen
- LittleFS-Offset/-Size müssen zu `partitions.csv` passen (siehe `LittleFS.ps1`: `offset`, `siz`).  
- Für OTA werden zwei App-Slots benötigt; sicherstellen, dass das Partitionsschema das vorsieht.

## Lizenz
MIT (siehe SPDX-Header in den Quelldateien).
