# Текстове за Google Play

Готови за поставяне в Play Console → **Grow users → Store presence → Main store listing**. В скоби са лимитите на Play. Основният език е български (bg-BG); английският е по желание (Add translation → en-US).

## Български (bg-BG)

**Име на приложението** (≤ 30 знака, 29):

```
Баркодове – скенер за стикери
```

**Кратко описание** (≤ 80 знака, 74):

```
Сканира всички баркодове от стикера наведнъж и ги дава готови за копиране.
```

**Пълно описание** (≤ 4000 знака):

```
Баркодове разчита с камерата стикери с няколко баркода един до друг, например серийни и продуктови номера върху кутии, устройства и рутери, и ви дава стойностите готови за копиране.

ВСИЧКИ БАРКОДОВЕ НАВЕДНЪЖ
Насочвате камерата към стикера и приложението разпознава всички баркодове на него едновременно, без да ги снимате един по един.

БЕЗ ГРЕШНИ СТОЙНОСТИ
Всеки баркод се чете многократно и се приема само когато резултатът е сигурен. Справя се и с избледнял или нискоконтрастен печат.

ЯСЕН РЕЗУЛТАТ
Виждате кадър на стикера с номерирани баркодове и списък със стойностите. Копирате една стойност с едно докосване или всички наведнъж.

ЧИСТИ СТОЙНОСТИ
По желание премахва етикети като „S/N:“ и „P/N:“ и копирате само самия номер.

WI-FI QR КОДОВЕ
Показва името на мрежата, паролата и вида защита поотделно, всяко с бутон за копиране.

НАСТРОЙКИ
• Брой баркодове на стикера: 2, 3, 4 или автоматично
• Формати: Code 128, Code 39, ITF, EAN-13, QR (по подразбиране), както и EAN-8, UPC-A, UPC-E, Code 93, Codabar, Data Matrix, PDF417, Aztec
• Увеличение (zoom) и фенерче за малки стикери и слабо осветление

ПОВЕРИТЕЛНОСТ
Разпознаването става изцяло на телефона. Снимките от камерата не се запазват и не се изпращат никъде. Няма реклами и регистрация.
```

## English (en-US), по желание

**App name** (≤ 30, 26):

```
Barcodes – Sticker Scanner
```

**Short description** (≤ 80, 76):

```
Scans every barcode on a sticker at once and lists the values ready to copy.
```

**Full description**:

```
Barcodes reads stickers that carry several barcodes side by side, such as serial and part numbers on boxes, devices and routers, and lists the values ready to copy.

ALL BARCODES AT ONCE
Point the camera at the sticker and every barcode on it is recognised together. No need to scan them one by one.

NO WRONG VALUES
Each barcode is read many times and accepted only once the result is certain. Faded or low-contrast print works too.

CLEAR RESULT
You get a frame of the sticker with numbered barcodes and the list of values. Copy one value with a tap, or all of them at once.

CLEAN VALUES
Optionally removes labels such as "S/N:" and "P/N:", so only the number is copied.

WI-FI QR CODES
Shows the network name, password and security type separately, each with its own copy button.

SETTINGS
• Number of barcodes on the sticker: 2, 3, 4 or automatic
• Formats: Code 128, Code 39, ITF, EAN-13, QR (default), plus EAN-8, UPC-A, UPC-E, Code 93, Codabar, Data Matrix, PDF417, Aztec
• Zoom and flashlight for small stickers and low light

PRIVACY
Recognition runs entirely on the phone. Camera images are never saved or sent anywhere. No ads, no sign-up.
```

## Останалите полета

| Поле | Стойност |
|---|---|
| Категория | Apps → **Tools** (Инструменти) |
| Тагове | Barcode scanner, Productivity |
| Имейл за контакт | задължителен; показва се публично в Play |
| Уебсайт | по желание, напр. адресът на уеб версията |
| Политика за поверителност | URL към `privacy.html` (вижте [privacy-policy.md](privacy-policy.md)) |

## Графики

| Файл | Изискване на Play | Статус |
|---|---|---|
| `graphics/icon-512.png` | 512×512 PNG | ✅ генериран |
| `graphics/feature-graphic.png` | 1024×500 PNG/JPG | ✅ генериран |
| Снимки на екрана (телефон) | 2–8 бр., 16:9 или 9:16, страна 320–3840 px | ❌ направете ги на телефон, вижте по-долу |

Прегенериране след смяна на иконата: `python3 scripts/render-store-graphics.py` (macOS).

**Снимки на екрана.** Най-добре е да са с истински стикер. Предложение за 4 снимки:
1. Сканиране: стикер в кадъра, зелени и жълти рамки.
2. Резултат: номерираните баркодове и списъкът.
3. Резултат с Wi-Fi QR код (рутер).
4. Настройки.

Снимане: на телефона (бутон за звук + захранване) или с `adb exec-out screencap -p > shot.png`. Снимките на телефон 1080×2400 стават директно.
