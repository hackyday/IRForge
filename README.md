# IRForge

**A universal, fully-customizable infrared remote for Android.** Auto-detects every way your phone can blast IR — built-in emitter, USB dongle, or 3.5 mm AUX blaster — lets you import IR signal files (native **Flipper Zero / Bruce `.ir`** support), and design your own remotes from templates with a drag-and-drop editor.

![platform](https://img.shields.io/badge/platform-Android%207.0%2B-green) ![license](https://img.shields.io/badge/license-MIT-blue)

## Features

- **IR hardware auto-detection** — the Hardware tab shows every transmitter:
  - 📱 **Built-in IR blaster** via `ConsumerIrManager` (with carrier range info)
  - 🔌 **USB IR blasters** — USB Infrared Toy v2, FTDI FT232/245, CH340/CH341, CP210x, generic CDC-ACM. No drivers needed: serial protocols implemented in-app. **Learn/capture** from an existing remote with an IR Toy.
  - 🎧 **AUX (3.5 mm jack) IR blasters** — envelope mode (dongles with a decoder IC) and raw-carrier mode (96 kHz), left/right channel swap, built-in test-tone wizard.
  - Per-remote and global transmitter preference; auto-fallback built-in → USB → AUX.
- **Import everything**
  - Flipper Zero / **Bruce firmware `.ir`** files (raw **and** parsed protocols — NEC, NECext, SAMSUNG, SONY/SIRC, RC5, RC6 are decoded in-app)
  - LIRC `.lircd.conf` (RAW_CODES), **Pronto hex**, CSV, JSON
  - Import via file picker, batch-import a whole folder, or just *share* a `.ir` file into the app
  - Bundled **SmartLim demo** (16 buttons) — one tap to load
- **Full remote editor**
  - Templates: TV, set-top box, audio/receiver, fan, projector, **AC climate**, numbered grids, blank canvas
  - Drag, drop and **resize** every button; grid snap
  - Per-button: label, emoji icon, shape (rounded/square/circle/pill), solid or gradient fill, text color, border, opacity, font size, haptics, repeat count, **hold-to-repeat**, long-press secondary action
  - Per-remote: background color/gradient, multiple **pages**, transmitter override
  - **Auto-fill from library**: imported codes snap onto template slots by name
- **Macros & scenes** — chain codes and delays, run with progress, assign to buttons
- **Code library** — search, test-fire, rename, tag, dedupe, export back to Flipper `.ir`
- **Home-screen widget** — 4 favorite buttons, one-tap send
- **Backup & restore** — everything as JSON; dark/light/system theme

## Supported USB hardware

| Device | IDs | Transmit | Capture/Learn |
|---|---|---|---|
| USB Infrared Toy v2+ | `04d8:fd08` | ✅ (v2 protocol, variable carrier) | ✅ |
| FTDI FT232/245 | `0403:*` | ✅ (DIY text protocol) | — |
| CH340 / CH341 | `1a86:7523` / `5523` | ✅ (DIY text protocol) | — |
| CP210x | `10c4:ea60` | ✅ (DIY text protocol) | — |
| CDC-ACM serial | class 02/0A | ✅ (DIY text protocol) | — |
| Prolific PL2303 | `067b:*` | detected, unsupported | — |

### DIY serial blaster (Arduino/ESP32)

Any USB-serial IR blaster running this trivial protocol works:

```
IR <freqHz> <us> <us> <us> ...\n     → reply "OK" (optional)
```
Values are alternating mark/space durations in microseconds, exactly like a Flipper `data:` line.

Minimal ATmega sketch (IR LED on pin 3 via transistor, 38 kHz carrier):

```cpp
void setup() { Serial.begin(115200); }
void loop() {
  if (!Serial.available()) return;
  String line = Serial.readStringUntil('\n');
  if (!line.startsWith("IR ")) return;
  int f = line.substring(3).toInt();          // simplify: parse tokens
  // tokenize freq + durations, emit with your favorite IR library, then:
  Serial.println("OK");
}
```

## Importing the SmartLim files

1. Put your `.ir` files anywhere on the phone (or just try the bundled demo).
2. IRForge → **Codes** → ➕ → *Pick files* or *Pick folder*.
3. Review, uncheck duplicates, **Import selected** — with *auto-create remote* checked you get a ready TV-template remote with your codes assigned to the matching slots (Power, Vol +, …).
4. Alternatively *share* any `.ir` file to IRForge from your file manager.

## Building

```bash
./gradlew assembleDebug          # debug APK
./gradlew assembleRelease        # needs keystore.properties (see below)
./gradlew test                   # unit tests (parser/encoder suites)
```

`app` requires the Android SDK (compileSdk 34). For a signed release, create
`keystore.properties` in the repo root (gitignored):

```properties
storeFile=/absolute/path/to/keystore.jks
storePassword=***
keyAlias=irforge
keyPassword=***
```

Without it, release builds fall back to the debug key.

Releases are built automatically by GitHub Actions on `v*` tags — the workflow attaches the APKs to the GitHub release.

## License

MIT

## CI (GitHub Actions)

The build workflow lives at `.github/workflows/build.yml` (kept out of the initial push because
the repo token lacked the `workflow` scope). To enable automatic APK releases on `v*` tags:

```bash
gh auth refresh -s workflow          # one-time browser login to add the scope
git add .github && git commit -m "ci: add build workflow" && git push
git tag v1.0.1 && git push origin v1.0.1
```
