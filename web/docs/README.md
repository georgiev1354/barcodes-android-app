# Баркодове: уеб версия

Уеб (PWA) версия на Android приложението „Баркодове“. Сканира с камерата стикери с 2–4 баркода един до друг. Когато всеки баркод е разчетен сигурно, показва замразен кадър с номерирани баркодове и стойностите за копиране.

Папката `web/` е самостоятелен проект (собствени `package.json`, `.gitignore`, тестове). Може да се копира и разработва отделно от Android кода.

- [deploy.md](deploy.md): как се качва на сървъра
- [development.md](development.md): локална разработка, тестване на телефон, известни ограничения, следващи стъпки

## Какво е направено

| Функция | Статус |
|---|---|
| Сканиране с камерата, няколко баркода наведнъж | ✅ |
| Консенсус по кадри (същият алгоритъм като в приложението) | ✅ 1:1 порт + същите тестове |
| Подсилване на контраста на всеки втори кадър (избледнял печат) | ✅ |
| Брой баркодове: Авто / 2 / 3 / 4 | ✅ |
| Zoom: бутони 1×/2×/4×, плъзгач, pinch | ✅ където браузърът го позволява |
| Фенерче | ✅ където браузърът го позволява |
| Резултат: анотиран кадър, номера, копиране на една или всички стойности | ✅ |
| Wi-Fi QR кодове: мрежа, парола, защита поотделно | ✅ |
| Настройки: формати, премахване на „S/N:“ етикети, Wi-Fi показване (пазят се в `localStorage`) | ✅ |
| Офлайн работа и инсталиране на началния екран (PWA) | ✅ |
| Самообновяване (`update/` в приложението) | ➖ не е нужно: сайтът винаги е последната версия |

Проверено: unit тестове (20, `npm test`) и сканиране от край до край в Chrome с фалшива камера и синтетичен стикер (2× Code 128, EAN-13, Wi-Fi QR). И двата декодера дават 4/4 правилни стойности. На истински телефон все още **не е тествано**, вижте [development.md](development.md#тестване-на-телефон).

## Архитектура

```
<video> (getUserMedia, 1080p, задна камера)
   │  createImageBitmap, по един кадър наведнъж (back-pressure)
   ▼
decoder.worker.ts (Web Worker)
   │  BarcodeDetector (вграден) или ZXing-C++ WASM
   │  всеки втори кадър: контрастно подсилен сив
   ▼
session.ts → ConsensusTracker → ScanResult (анотиран <canvas> + стойности)
   ▼
ui/*Screen.ts
```

Декодер:
- **BarcodeDetector** (Shape Detection API) се ползва, ако браузърът поддържа всички избрани формати. В Chrome на Android работи със същия ML Kit модел като приложението, а в Chrome на macOS с Apple Vision.
- **ZXing-C++ (zxing-wasm)** е резервният вариант: iOS Safari, Firefox, Chrome на Windows/Linux. WASM файлът (~970 KB, ~420 KB gzip) се качва заедно със сайта, без CDN, и се зарежда само ако е нужен.
- `?decoder=zxing` в адреса налага ZXing (за сравнение и тестове).
- Кой декодер се ползва, се вижда в Настройки → Приложение → Разпознаване.

## Съответствие с Android кода

| Уеб | Android | Бележка |
|---|---|---|
| `src/scan/consensus.ts` | `scan/ConsensusTracker.kt` | 1:1, същите параметри (`requiredVotes = 6` и т.н.) |
| `src/scan/value.ts` | `scan/BarcodeValue.kt` | 1:1 |
| `src/scan/wifi.ts` | `scan/WifiCode.kt` | 1:1 |
| `src/scan/formats.ts` | `scan/ScanFormat.kt` | + имена за ZXing и BarcodeDetector |
| `src/scan/settings.ts` | `scan/ScanSettings.kt` | `localStorage` вместо SharedPreferences |
| `src/scan/enhance.ts` + `decoder.worker.ts` | `scan/BarcodeAnalyzer.kt` | |
| `src/scan/session.ts` | `scan/ScanViewModel.kt` | |
| `src/ui/scanScreen.ts` | `ui/ScanScreen.kt` | |
| `src/ui/resultScreen.ts` | `ui/ResultScreen.kt` | |
| `src/ui/settingsScreen.ts` | `ui/SettingsScreen.kt` | без обновления; показва версия и декодер |
| `src/main.ts` | `MainActivity.kt` | разрешение за камера, смяна на екрани, бутон „назад“ |
| `src/strings.ts` | `res/values/strings.xml` | същите текстове + уеб-специфични |
| `test/*.test.ts` | `app/src/test/.../scan/*Test.kt` | същите случаи |

При промяна на алгоритъма в едната версия пренесете я и в другата, заедно с тестовете.

## Структура

```
web/
├── index.html
├── package.json, tsconfig.json, vite.config.ts
├── public/                  копира се 1:1 в dist/
│   ├── manifest.webmanifest
│   ├── sw.js                service worker (офлайн)
│   └── icons/               генерирани от иконата на приложението
├── scripts/render-icons.py  прегенерира icons/ (macOS)
├── src/
│   ├── main.ts, strings.ts, styles.css
│   ├── scan/                логика (без DOM, освен session.ts)
│   └── ui/                  екрани, камера, clipboard
├── test/                    Vitest
└── docs/                    тази документация
```

Без UI framework: три екрана с малък DOM helper (`ui/dom.ts`). Единствената runtime зависимост е `zxing-wasm`.
