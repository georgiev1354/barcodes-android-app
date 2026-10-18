# Самообновяване на Android приложения (стандарт)

Документът е **единният стандарт** за това как приложенията ни се обновяват сами, без Play Store
и без потребителят ръчно да тегли нова версия. Всички приложения ползват **един и същ код**, **един
и същ договор със сървъра** и **едно и също поведение**. Целта е, ако знаеш как работи в едно
приложение, да знаеш как работи във всички.

Референтната имплементация е в SMS Паркиране (`com.gg.smsparking`). Там е тествана на емулатор с
Android 16 (API 36). Пълният код е в [раздел 9](#9-пълен-код). Той се генерира от реалните файлове
със `scripts/build-self-update-doc.py`, така че документът и работещият код не могат да се
разминат.

**Съдържание**
1. [Принцип „1:1“](#1-принцип-11) — какво е еднакво и какво се различава
2. [Поведение за потребителя](#2-поведение-за-потребителя)
3. [Архитектура](#3-архитектура)
4. [Сървър — договор](#4-сървър--договор)
5. [Сървър — инсталация](#5-сървър--инсталация)
6. [Добавяне в ново приложение](#6-добавяне-в-ново-приложение) — чеклист
7. [Издаване на нова версия](#7-издаване-на-нова-версия)
8. [Тестване](#8-тестване)
9. [Пълен код](#9-пълен-код)
10. [Подводни камъни и решения](#10-подводни-камъни-и-решения)

---

## 1. Принцип „1:1“

**Еднакво във всяко приложение (не се пипа):**

| Какво | Къде |
|---|---|
| целият пакет `update/` (8 файла) | `app/src/main/java/<package>/update/` — сменя се **само редът `package`** |
| тестовете | `app/src/test/java/<package>/update/UpdatePolicyTest.kt` |
| debug ресурсите | `app/src/debug/AndroidManifest.xml`, `app/src/debug/res/xml/network_security_config.xml` |
| R8 правилото за Kotlin (за release) | `app/src/main/keepRules/kotlin-multifile.keep` — раздел 6, стъпка 2 |
| растерната иконка за сървъра | `mipmap-xxxhdpi/ic_launcher.webp` 192×192, генерирана със `scripts/render-launcher-icons.py` — раздел 6, стъпка 7 |
| разрешенията и receiver-ите в манифеста | раздел 6, стъпка 3 |
| текстовете за потребителя (на български) | в кода |
| ендпойнтът | един `check-update.php` за всички приложения (`server/public/`) |
| Apache2 конфигурацията | един VirtualHost за всички приложения (`server/apache-apks.conf`) |
| публикуването от админ панела | `publishUpdate()` (`server/admin/publish-update.php`) |
| мястото на файловете на сървъра | `<project_folder>/storage/apks/<applicationId>/` |
| скриптът за издаване | `scripts/make-update-manifest.py` |
| копирането в друго приложение | `scripts/copy-self-update.sh` (само в референтното) |
| адресът | `https://apks.gg-webapp.com/<applicationId>/check-update` (от `applicationId`) |

**Специфично за всяко приложение (само тези 4 неща):**

| Какво | Пример от SMS Паркиране |
|---|---|
| `applicationId` (оттам идва и папката на сървъра) | `com.gg.smsparking` |
| ключът за подпис (keystore) | собствен за всяко приложение, **никога не се сменя** |
| иконка за известия (монохромна) | `R.drawable.ic_stat_parking` |
| `isBusy` — кога приложението не бива да се рестартира | „изпраща се SMS“ |

Ако някое приложение има нужда от промяна в общия код, промяната се прави **в стандарта** (тук и
в референтното приложение) и се разнася до всички. Не се правят локални варианти.

> Ако приложенията станат много, следващата стъпка е пакетът `update/` да стане отделен Gradle
> модул или библиотека, споделена между проектите. До тогава копирането 1:1 е по-просто.

---

## 2. Поведение за потребителя

| Ситуация | Какво се случва |
|---|---|
| Отваряне на приложението (или връщане към него) | Проверка към сървъра, **най-много веднъж на час**. Няма периодична фонова проверка. |
| Има нова версия | Изтегля се във фонов режим и се проверява (размер, SHA-256, пакет, versionCode, подпис). |
| Изтеглена е, приложението е на екрана | Карта отгоре „Налична е версия X“ + бележките + бутон **„Обнови сега“**. Нищо не прекъсва потребителя. |
| Потребителят излиза от приложението (Home или смяна на приложение) | Инсталира се **тихо**, без въпроси (Android 12+, след първото обновление). |
| Приложението е „заето“ (`isBusy`) | Не се инсталира. Опитва пак при следващото излизане. |
| Липсва „Инсталиране на неизвестни приложения“ | Картата показва „Разреши“ → системните настройки → след връщане бутонът става „Обнови сега“. |
| Android изисква потвърждение (първото обновление; Android 9–11 винаги) | Ако приложението е на екрана → системният диалог „Update“. Ако не е → известие „Налична е версия X — Докоснете, за да я инсталирате“ (по веднъж на версия). Докосването отваря приложението и показва диалога. |
| Потребителят натисне Cancel в системния диалог | Картата „Обнови сега“ остава. Нищо не се губи. |
| След обновление | Известие „<Име на приложението> — Обновено до версия X.“, изтегленият файл се трие. |
| Диалог „Обновления“ (бутон в приложението) | Версия, превключвател „Автоматично обновяване“, последна проверка, статус/грешка, „Провери сега“. |
| „Автоматично обновяване“ изключено | Не проверява и не инсталира само. Само от „Провери сега“ → „Обнови сега“. |
| Debug версия | Обновяването е изключено (друг ключ за подпис), освен с `-PupdateUrl` за локален тест. |

---

## 3. Архитектура

```
 Приложение                                              apks.gg-webapp.com
 ──────────                                              ──────────────────
 Activity стартира (Updater.Lifecycle, ≥1 ч от последната)
   UpdateClient.fetch ── GET /<pkg>/check-update?versionCode=N&sdk=S ──► public/check-update.php
                                                                         чете storage/apks/<pkg>/latest.json
                      ◄── 204 (нищо ново) / 200 + JSON
   UpdateClient.download ── GET /<pkg>/apk/<pkg>-M.apk ───────────────► storage/apks/<pkg>/apk/ (статичен)
                      ◄── APK → cacheDir/self-update/update-M.apk (SHA-256 по време на тегленето)
   UpdateInstaller.verify (пакет, versionCode, подпис)
   state = Ready → UpdateBanner „Обнови сега“
 Последната Activity спира (излизане) или потребителят натисне „Обнови сега“
   UpdateInstaller.install → PackageInstaller session (USER_ACTION_NOT_REQUIRED)
   InstallResultReceiver:
     PENDING_USER_ACTION → системен диалог (на екрана) или известие (извън него)
     FAILURE_ABORTED     → обратно в Ready
     друга грешка        → Failed(съобщение)
 Новата версия стартира
   UpdateCompletedReceiver (MY_PACKAGE_REPLACED) → трие APK-то, известие „Обновено до версия X“
```

**Файлове на пакета `update/`:**

| Файл | Роля |
|---|---|
| `UpdateInfo.kt` | `UpdateInfo` (отговорът на сървъра) + `UpdatePolicy` — чиста логика (по-нова ли е, URL, SHA формат), unit-тествана |
| `UpdateClient.kt` | HTTP: проверката и изтеглянето (`HttpURLConnection`, без библиотеки), размер + SHA-256 |
| `UpdateInstaller.kt` | проверка на APK (пакет, versionCode, подпис) и `PackageInstaller` сесия |
| `InstallResultReceiver.kt` | резултатът от инсталацията (диалог, известие, отказ, грешка) |
| `UpdateCompletedReceiver.kt` | `MY_PACKAGE_REPLACED` — почистване + известие „обновено“ |
| `UpdateNotifier.kt` | собствен канал „Обновления“ и известията (независими от приложението) |
| `Updater.kt` | оркестрацията, състоянието (`StateFlow<UpdateState>`), настройките (SharedPreferences `self_update`), следене на отваряне/излизане (`ActivityLifecycleCallbacks`) |
| `UpdateUi.kt` | Compose: `UpdateBanner()` и `UpdateDialog(onDismiss)` |

**Състояния (`UpdateState`):** `Idle` → `Checking` → `UpToDate` | `Downloading(percent)` → `Ready` →
`Installing` → (рестарт) | `NeedsPermission` | `Failed(message)`.

**Зависимости:** само AndroidX core + activity (+ Compose Material3 за `UpdateUi.kt`). Няма
WorkManager, Retrofit, DI и т.н. Пакетът **не импортира нищо от приложението**.

**Данни, които пакетът пази:**

| Къде | Какво |
|---|---|
| `SharedPreferences "self_update"` | `auto` (вкл./изкл.), `lastCheck` (ms), `notice` (последното известие „versionCode:kind“), `icon` (res id за известията) |
| `cacheDir/self-update/` | изтегленото APK (трие се след обновление или ако вече не е нужно) |
| канал за известия `self_update` | „Обновления“ |

---

## 4. Сървър — договор

Сървърът е **статичен + един малък PHP скрипт на приложение**. Няма база данни, вход или
информация за потребителите. Сигурността е в клиента (SHA-256 + подпис).

### 4.1 Проверка за нова версия

```
GET https://apks.gg-webapp.com/<applicationId>/check-update?versionCode=<int>&sdk=<int>
```

Адресът е зашит в приложението и **трябва да работи завинаги** (старите версии питат него). При
преместване → 301/302 към новия, пак по HTTPS.

| Параметър | Тип | Описание |
|---|---|---|
| `versionCode` | цяло ≥ 0 | инсталираната версия (приложението винаги го праща) |
| `sdk` | цяло ≥ 1 | Android API level на телефона |

Липсващи параметри → връща пълния манифест (удобно за проверка в браузър). Невалидни → `400`.

Заглавия на заявката: `User-Agent: <applicationId>/<versionName> (<versionCode>; Android <sdk>)`,
`Accept: application/json`. Без бисквитки и токени.

| Код | Кога | Тяло |
|---|---|---|
| **200** | има версия с по-висок `versionCode` и `minSdk` ≤ `sdk` | JSON (4.2) |
| **204** | няма по-нова, или телефонът е с твърде стар Android | празно |
| 400 | невалидни параметри / пакет | `{"error":"bad_parameters"}` / `{"error":"bad_package"}` |
| 404 | няма такова приложение (папка) на сървъра | `{"error":"unknown_app"}` |
| 405 | метод ≠ GET/HEAD | `{"error":"method_not_allowed"}` |
| 503 | приложението съществува, но `latest.json` липсва или е повреден | `{"error":"manifest_missing"}` / `{"error":"manifest_invalid"}` |

Всички отговори: `Cache-Control: no-store`. Всеки код, различен от 200/204, клиентът приема като
временна грешка и опитва при следващото отваряне. Клиентът сравнява `versionCode` и сам, така
че сървър, който винаги връща 200, също работи.

### 4.2 JSON при 200 (`latest.json`)

```json
{
  "versionCode": 4,
  "versionName": "1.3",
  "apkUrl": "https://apks.gg-webapp.com/com.gg.smsparking/apk/com.gg.smsparking-4.apk",
  "sha256": "5f92b045b626b54e6450dd92ccf5c15b4c44296de2f7c69725852586c5c78456",
  "size": 2853017,
  "minSdk": 28,
  "changelog": "Какво е новото.",
  "publishedAt": "2026-10-15T18:46:20Z"
}
```

| Поле | Задължително | Описание |
|---|---|---|
| `versionCode` | **да**, цяло ≥ 1 | трябва да е равно на `versionCode` в APK файла |
| `versionName` | препоръчително | показва се на потребителя |
| `apkUrl` | **да** | абсолютен `https://` или относителен спрямо check-update (`apk/x.apk`). `http://` се отхвърля |
| `sha256` | **да**, 64 hex, **малки букви** | SHA-256 на APK файла |
| `size` | препоръчително | байтове. За прогреса и проверка за непълно изтегляне. `0`/липса = неизвестен |
| `minSdk` | не (0) | по-старите телефони получават 204 |
| `changelog` | не | кратко, на български, `\n` за нов ред |
| `publishedAt` | не | само информативно |

Непознати полета се игнорират, така че форматът може да се разширява без да чупи старите версии.

### 4.3 APK файлът

```
GET https://apks.gg-webapp.com/<applicationId>/apk/<applicationId>-<versionCode>.apk
```

- `Content-Type: application/vnd.android.package-archive`, `Content-Length` (желателно), код 200.
  Без Range: при прекъсване се тегли наново.
- Пренасочвания само HTTPS → HTTPS. Може да е на друг хост/CDN (с абсолютен `apkUrl`).
- Имената съдържат `versionCode` и **никога не се презаписват** → `Cache-Control: public,
  max-age=31536000, immutable`.
- Максимален размер, който клиентът приема: 200 MB.

---

## 5. Сървър — инсталация

**Стек:** Apache2 + PHP 8.1+ (каквото вече ползва сървърът за `.php`: `libapache2-mod-php` или
php-fpm). HTTPS с валиден сертификат (Let's Encrypt), TLS 1.2+.

### 5.1 Структура

```
<project_folder>/
├── public/                         ← DocumentRoot на apks.gg-webapp.com
│   ├── … админ панелът …
│   └── check-update.php            ← server/public/check-update.php (един за всички приложения)
└── storage/apks/                   ← APKS_ROOT: НЕ е публична; пише я админ панелът
    ├── com.gg.smsparking/
    │   ├── latest.json
    │   └── apk/
    │       ├── com.gg.smsparking-3.apk
    │       └── com.gg.smsparking-4.apk
    └── com.gg.другоприложение/
        └── … същото …
```

Ново приложение = нова папка в `storage/apks/` (създава я `publishUpdate()` при първото качване).
Apache конфигурацията и `check-update.php` не се променят.

### 5.2 Apache

`server/apache-apks.conf` е **един** VirtualHost за всички приложения. Пътят се задава на **едно**
място:

```apache
Define PROJECT_ROOT /home/USER/project_folder
```

| Адрес | Какво става |
|---|---|
| `/<applicationId>/check-update` | вътрешно → `public/check-update.php?package=<applicationId>` (versionCode/sdk се запазват) |
| `/<applicationId>/apk/<файл>.apk` | `AliasMatch` → `storage/apks/<applicationId>/apk/<файл>.apk`, `application/vnd.android.package-archive`, кеш 1 година |
| всичко останало | админ панелът (`public/`), непроменен |
| порт 80 | 301 към HTTPS |

От `storage/apks` се отдават **само** `.apk` файлове (`Require all denied` + изключение за `*.apk`).
`latest.json`, листинги и всичко друго в `storage` не са достъпни отвън. Адресите на админ панела
не съвпадат с правилата, защото applicationId задължително съдържа точка (`/admin/...` не е
пакет). Затова панелът може да си ползва свой `.htaccess`/front controller (`AllowOverride All`
за `public/`).

Инсталация (Debian/Ubuntu):

```sh
sudo cp server/apache-apks.conf /etc/apache2/sites-available/apks.conf
sudo nano /etc/apache2/sites-available/apks.conf          # PROJECT_ROOT = истинският път
sudo a2enmod rewrite headers ssl alias
sudo a2ensite apks && sudo apachectl configtest && sudo systemctl reload apache2
sudo certbot --apache -d apks.gg-webapp.com                # HTTPS сертификат
cp server/public/check-update.php <project_folder>/public/
```

Ако сертификатът още не съществува, `configtest` ще се оплаче от `SSLCertificateFile`. Тогава
първо пуснете само `<VirtualHost *:80>` (или `certbot certonly --webroot`), вземете сертификата и
после включете `*:443`.

Ако админ панелът е на фреймуърк с `public/index.php` front controller (напр. Laravel), той
продължава да работи. `check-update.php` е отделен файл в `public/` и правилото на VirtualHost-а
се изпълнява преди `.htaccess` на панела. Ако предпочитате ендпойнтът да е маршрут на
фреймуърка, той трябва да връща **точно** същите отговори като `check-update.php` (раздел 4).

### 5.3 Права

- Apache (`www-data`) трябва да **влиза** във всяка папка по пътя до `storage/apks` и да **чете**
  APK файловете.
- Админ панелът (обикновено също `www-data`) трябва да **пише** в `storage/apks`.

```sh
sudo chgrp -R www-data <project_folder>/storage/apks
sudo chmod -R g+rwX <project_folder>/storage/apks
sudo find <project_folder>/storage/apks -type d -exec chmod g+s {} +   # новите файлове наследяват групата
namei -l <project_folder>/storage/apks                                 # x за www-data по целия път
```

### 5.4 Админ панел — какво трябва да прави

Панелът е ваш. Стандартът изисква само **файловете, които той записва**, да са точно като в
раздел 4. За това има готова функция:

```php
require '/path/to/publish-update.php';            // server/admin/publish-update.php

$latest = publishUpdate(
    apksRoot: '/home/USER/project_folder/storage/apks',
    host: 'https://apks.gg-webapp.com',
    package: $_POST['package'],                   // напр. com.gg.smsparking
    apkPath: $_FILES['apk']['tmp_name'],          // подписаното release APK
    versionCode: (int)$_POST['versionCode'],      // = versionCode в APK-то
    versionName: $_POST['versionName'],
    changelog: $_POST['changelog'] ?? '',
    minSdk: (int)($_POST['minSdk'] ?? 0),
);
```

Какво гарантира `publishUpdate()` (не го заобикаляйте с ръчен запис):

| Правило | Защо |
|---|---|
| applicationId се проверява по регулярен израз | без `../` и чужди папки |
| файлът започва с `PK\x03\x04` (zip) | грешно качен файл |
| `versionCode` > публикувания | Android не приема по-ниска версия |
| име `<applicationId>-<versionCode>.apk`, отказ ако съществува | публикуваните файлове никога не се сменят (кеш 1 година) |
| първо APK-то, после `latest.json`, и двете през `.tmp` + `rename` | телефон никога не вижда `latest.json` към непълен файл |
| `sha256` и `size` се изчисляват от записания файл | точно каквото проверява клиентът |
| грешка → изключение със съобщение на български, нищо не е публикувано | за показване в панела |

Какво **не** може да провери (проверява го телефонът и отказва да инсталира):

- Дали `versionCode` в APK-то е същият като въведения. При разминаване телефонът показва
  „Версията в APK (…) не съвпада с обявената (…)“. Затова въвеждайте го от
  `app/release/output-metadata.json` или качвайте `output-metadata.json` заедно с APK-то.
- Дали APK-то е подписано със същия ключ. Ако не е, телефонът показва „Обновлението е подписано
  с друг ключ…“. Ако на сървъра има Android build-tools, панелът може да провери предварително с
  `apksigner verify --print-certs` и да сравни отпечатъка.

Панелът трябва да е зад вход (само администратори). Качването на APK е най-чувствителната
операция в цялата система, макар че без keystore-а качено чуждо APK не може да се инсталира.

**Само статичен хостинг (без PHP):** сервирайте `latest.json` на `/<applicationId>/check-update`
с `Content-Type: application/json` и `Cache-Control: no-store`. Винаги 200, а клиентът решава
сам. Губи се само филтърът по `minSdk` на сървъра; клиентът пак проверява `minSdk`.

Конфигурацията е тествана на Apache 2.4 + php-fpm с няколко пакета и фалшив админ панел с
front controller `.htaccess`. Пускани са всички кодове от раздел 4. Съдържанието на `storage`
(`latest.json`, други файлове, `../`) не се отдава, а адресите на панела стигат до панела.

**Проверка след инсталация:**

```sh
H=https://apks.gg-webapp.com; P=com.gg.smsparking
curl -i "$H/$P/check-update?versionCode=1&sdk=35"     # 200 + JSON (ако има > 1)
curl -i "$H/$P/check-update?versionCode=999&sdk=35"   # 204
curl -sI "$H/$P/apk/$P-4.apk"                         # 200, application/vnd.android.package-archive
curl -s  "$H/$P/apk/$P-4.apk" | shasum -a 256         # = sha256 от latest.json
curl -s  "$H/$P/latest.json"                          # НЕ трябва да върне JSON-а (стига до админ панела)
```

---

## 6. Добавяне в ново приложение

Предпоставки: minSdk ≥ 28, Kotlin + Compose (за `UpdateUi.kt`; без Compose — виж стъпка 6),
AndroidX `core-ktx` и `activity` (ако е Compose приложение, ги има).

**1. Копирай кода 1:1** — от SMS Паркиране с една команда:

```sh
scripts/copy-self-update.sh ~/AndroidStudioProjects/<НовПроект>            # пакетът = namespace от app/build.gradle.kts
scripts/copy-self-update.sh ~/AndroidStudioProjects/<НовПроект> com.gg.app  # или изрично
```

Скриптът копира файловете по-долу, сменя реда `package`, не презаписва различен debug файл (казва
какво да се слее) и накрая изброява стъпки 2–8. Пускай го отново след всяка промяна в `update/`,
за да обновиш другите приложения. Ръчно — от [раздел 9](#9-пълен-код) или от референтното приложение:
- `app/src/main/java/<package>/update/*.kt` (8 файла) — смени **само** първия ред `package …`
- `app/src/test/java/<package>/update/UpdatePolicyTest.kt` — същото
- `app/src/debug/AndroidManifest.xml` и `app/src/debug/res/xml/network_security_config.xml`
  (ако приложението вече има `networkSecurityConfig` в debug, слей ги)

**2. `app/build.gradle.kts`:**

```kotlin
android {
    defaultConfig {
        // Every release published to the update server needs a higher versionCode (docs/self-update.md).
        // -PversionCode=N overrides it, for testing updates between two local builds.
        versionCode = providers.gradleProperty("versionCode").map(String::toInt).getOrElse(1)

        // One folder per package on the update server, so several apps (or variants) never mix.
        buildConfigField("String", "UPDATE_URL", "\"https://apks.gg-webapp.com/$applicationId/check-update\"")
    }
    buildTypes {
        debug {
            // A debug build is signed with the debug key and can't update from the release server.
            // Pass -PupdateUrl=http://10.0.2.2:8000/<applicationId>/check-update to test against a local one.
            buildConfigField("String", "UPDATE_URL", "\"${providers.gradleProperty("updateUrl").getOrElse("")}\"")
        }
    }
    buildFeatures {
        buildConfig = true
    }
}
```

`applicationId` трябва да е зададен **преди** реда с `buildConfigField` в `defaultConfig`.

**Release с R8 (`optimization { packageScope = … }`):** добави файла
`app/src/main/keepRules/kotlin-multifile.keep` (всички `.keep` файлове в папката се сливат):

```proguard
# Kotlin stdlib facades like CollectionsKt are split into a chain of package-private classes
# (CollectionsKt__IterablesKt <- CollectionsKt__MutableCollectionsJVMKt <- ...). With packageScope,
# R8 repackaged some links of that chain but not others, so a subclass ended up outside the package
# of its package-private superclass and the release build crashed on start with
# "IllegalAccessError: Class kotlin.collections.h extended by class ru is inaccessible".
# Keeping the names' package (members can still be shrunk, optimized and renamed) prevents that.
-keep,allowshrinking,allowoptimization,allowobfuscation class kotlin.**Kt__*
```

Без него release версията пада още при стартиране, а debug работи нормално. **Не махайте**
`packageScope`, за да „оправите“ срива: тогава R8 оптимизира и ML Kit/Google Play services и те
се чупят (`NullPointerException` в `BarcodeScanning.getClient` в Баркодове). Проверявайте release
версията на устройство или емулатор (подписана с debug ключа, ако трябва), не само че се компилира.

**3. `AndroidManifest.xml`:**

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />
<!-- Android 12+: lets updates installed by this app (as installer of record) skip the confirmation. -->
<uses-permission android:name="android.permission.UPDATE_PACKAGES_WITHOUT_USER_ACTION" />
<!-- Android 13+: for the "update ready" / "updated" notifications; request it at runtime as usual. -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

<application …>
    <receiver
        android:name=".update.InstallResultReceiver"
        android:exported="false" />

    <receiver
        android:name=".update.UpdateCompletedReceiver"
        android:exported="false">
        <intent-filter>
            <action android:name="android.intent.action.MY_PACKAGE_REPLACED" />
        </intent-filter>
    </receiver>
</application>
```

Launcher активността трябва да е `ComponentActivity` (Compose/AndroidX го дава). Препоръчително е
`android:launchMode="singleTop"`; работи и без него.

**4. Инициализация.** Едно извикване в `onCreate` на launcher активността (след `super.onCreate`).
Това е **единственият** код в приложението. Отварянето и излизането се следят автоматично.

```kotlin
Updater.init(
    this,
    checkUrl = BuildConfig.UPDATE_URL,
    smallIcon = R.drawable.ic_stat_<app>,   // монохромна иконка за известия
    isBusy = { false },                     // true докато не бива да се рестартира (по избор)
)
```

Пример от SMS Паркиране: `isBusy = { repository.sessions.any { it.status == PENDING } }` — не се
обновява, докато се изпраща SMS.

**5. UI (Compose):**
- `UpdateBanner()` — най-отгоре в съдържанието, над всички табове/екрани (показва се само при
  нужда).
- `UpdateDialog(onDismiss = { … })` — от бутон в приложението (в SMS Паркиране: „⟳“ в TopAppBar).

```kotlin
var showUpdates by remember { mutableStateOf(false) }
Scaffold(
    topBar = { TopAppBar(title = { … }, actions = { IconButton(onClick = { showUpdates = true }) { Text("⟳") } }) },
) { padding ->
    Column(Modifier.padding(padding).fillMaxSize()) {
        UpdateBanner()
        Box(Modifier.weight(1f)) { /* съдържанието на приложението */ }
    }
}
if (showUpdates) UpdateDialog(onDismiss = { showUpdates = false })
```

**6. Без Compose (View система):** не копирайте `UpdateUi.kt`. Направете същите два елемента с
View-та, като слушате `Updater.state`, `Updater.autoUpdate` и `Updater.lastCheck` (StateFlow) и
викате `Updater.installNow(context)`, `Updater.check(context)` и `Updater.setAutoUpdate(context, on)`.
Текстовете и поведението трябва да са същите като в `UpdateUi.kt`.

**7. Подпис и сървър:**
- Издайте първата версия с механизма със **собствен keystore** (Android Studio → Generate Signed
  APK). Запишете SHA-256 отпечатъка на сертификата.
- Качете първата версия през админ панела (`publishUpdate()`). Той създава
  `storage/apks/<applicationId>/`. Apache и `check-update.php` не се променят.
- Първата инсталация на телефона е ръчна (браузър/файл). Всичко след нея е автоматично.
- **Иконка за сървъра.** При качване сървърът взема иконката на приложението от APK-то
  (`app/Support/Apk/ApkReader.php`, `findIcon`):
  1. тръгва от `android:icon` в манифеста и взема **първия** растерен файл зад него (PNG или WebP,
     но не `.9.png`);
  2. ако иконата е адаптивна (`<adaptive-icon>`), търси растер в слоя `<foreground>`;
  3. накрая търси `res/mipmap-*/ic_launcher.png|webp` по име.

  Векторни (XML) рисунки не може да рендерира. Затова до адаптивната иконка трябва да има **точно
  един** растер, и то голям:

  ```
  app/src/main/res/
    mipmap-anydpi/ic_launcher.xml            ← адаптивната; на телефона винаги се ползва тя (minSdk ≥ 26)
    mipmap-xxxhdpi/ic_launcher.webp          ← 192×192, само за сървъра
    mipmap-xxxhdpi/ic_launcher_round.webp    ← 192×192, ако има android:roundIcon
  ```

  Генерирай ги от векторните слоеве със `scripts/render-launcher-icons.py` (macOS, нужен е Pillow).
  Пускай го отново след всяка смяна на иконката. **Не добавяй другите плътности** (mdpi…xxhdpi).
  R8 преименува файловете в release (`res/sK.webp`), затова първият растер в таблицата на ресурсите
  става mdpi, 48×48, и сървърът показва малка размазана иконка. По същата причина стъпка 3 (търсене
  по име) не работи с release APK. Проверка:
  ```sh
  aapt2 dump resources app/release/app-release.apk | grep -A3 "mipmap/ic_launcher$"
  # (xxxhdpi) (file) res/….webp   ← трябва да е първият ред
  # (anydpi)  (file) res/….xml
  ```

**8. Проверка:** `./gradlew testDebugUnitTest lintDebug` + локалният тест от раздел 8.

---

## 7. Издаване на нова версия

1. Увеличи `versionCode` (с поне 1) и смени `versionName` в `app/build.gradle.kts`.
2. Android Studio → **Build → Generate Signed App Bundle / APK → APK**, release, **същият keystore**
   → `app/release/app-release.apk` + `output-metadata.json`.
   (`app-release-unsigned.apk` от `./gradlew assembleRelease` **не** става.)
3. Качете `app-release.apk` през **админ панела** (applicationId, versionCode и versionName от
   `app/release/output-metadata.json`, плюс changelog). Панелът вика `publishUpdate()`.

   Без панел (ръчно) — същият резултат:
   ```sh
   scripts/make-update-manifest.py app/release/app-release.apk --changelog "Какво е новото" --out build/update
   ```
   Скриптът чете всичко от `output-metadata.json`, проверява подписа (`apksigner`) и отпечатва
   отпечатъка на сертификата. **Сравни го със записания!** После прави
   `build/update/<applicationId>/` със същото съдържание като на сървъра. Качи първо APK-то,
   после `latest.json`:
   ```sh
   P=com.gg.smsparking
   R=/home/USER/project_folder/storage/apks/$P
   scp build/update/$P/apk/*.apk server:$R/apk/
   scp build/update/$P/latest.json server:$R/latest.json.tmp
   ssh server "mv $R/latest.json.tmp $R/latest.json"
   ```
   Добре е и при качване през панела да пуснеш скрипта локално, само заради проверката на подписа.
4. Проверка с `curl` (раздел 5).

**Връщане назад:** не може (по-нисък `versionCode` се отхвърля). Издай поправка с по-висок
`versionCode`.
**Спиране на обновленията:** върни в `latest.json` текущата версия (всички получават 204) или
махни файла (503 → телефоните опитват по-късно).

---

## 8. Тестване

**Unit:** `./gradlew testDebugUnitTest` (`UpdatePolicyTest`).

**Локален сървър + емулатор** (debug ↔ debug, същият debug ключ). `server/dev-router.php` повтаря
правилата на Apache за вградения PHP сървър, така че адресите са **същите** като на истинския
сървър. Инсталирай с `adb -s`, защото `installDebug` инсталира на **всички** свързани устройства,
включително личния телефон.

```sh
P=com.gg.smsparking                                   # applicationId
U=http://10.0.2.2:8000/$P/check-update                # 10.0.2.2 = компютърът, гледан от емулатора
W=build/devserver                                     # = <project_folder> на сървъра
ADB="adb -s emulator-5554"

mkdir -p $W/public $W/storage/apks && cp server/public/check-update.php $W/public/

# „нова“ версия → публикувана с publishUpdate(), както от админ панела
./gradlew assembleDebug -PversionCode=2 -PupdateUrl=$U
php -r 'require "server/admin/publish-update.php"; publishUpdate("'$W'/storage/apks", "http://10.0.2.2:8000", "'$P'", "app/build/outputs/apk/debug/app-debug.apk", 2, "test2", "Тестово обновление", 28);'
php -S 0.0.0.0:8000 -t $W/public server/dev-router.php &

# „стара“ версия на емулатора
./gradlew assembleDebug -PversionCode=1 -PupdateUrl=$U
$ADB install -r app/build/outputs/apk/debug/app-debug.apk
```

Полезни команди:

| Цел | Команда |
|---|---|
| разрешение „неизвестни приложения“ без UI | `$ADB shell appops set $P REQUEST_INSTALL_PACKAGES allow` |
| приложението да е „инсталатор“ на себе си (тихи обновления) | `$ADB install -r -i $P <apk>` |
| кой е инсталаторът | `$ADB shell dumpsys package $P \| grep installerPackageName` |
| нулиране на „последна проверка“ (иначе чака 1 ч) | `$ADB shell run-as $P rm shared_prefs/self_update.xml` (при спряно приложение) |
| Play Protect „App scan recommended“ на емулатора | `$ADB shell settings put global package_verifier_enable 0` (после `1`) |
| симулиране на докосване на известието | `$ADB shell am start -f 0x14000000 -a self_update.action.INSTALL -n $P/.MainActivity` |

**Сценарии, които трябва да минат** (✓ = проверено в SMS Паркиране на емулатор с API 36):

| # | Сценарий | Очаквано | |
|---|---|---|---|
| 1 | Отваряне, има нова версия | 1 GET check-update + 1 GET apk. Карта „Налична е версия…“. Без рестарт. | ✓ |
| 2 | Без разрешение → „Обнови сега“ | Бутонът става „Разреши“ → Settings → назад → пак „Обнови сега“ | ✓ |
| 3 | Първо обновление (инсталатор ≠ приложението) | Системен диалог „Update“. Cancel → картата остава. Update → нова версия, `installerPackageName` = приложението, известие „Обновено“. | ✓ |
| 4 | Отваряне → карта → Home | Тихо обновление след ~1–3 s, без диалог | ✓ |
| 5 | Нужно потвърждение, а приложението е затворено | Известие „Докоснете, за да я инсталирате“ | ✓ |
| 6 | Стартиране с действието на известието (процесът жив / убит) | Приложението се отваря и инсталира (диалог, ако е нужен) | ✓ |
| 7 | Повторно отваряне до 1 ч | Няма заявка към сървъра | ✓ |
| 8 | Грешен `sha256` в `latest.json` | „Изтегленият файл не съвпада с контролната сума“, APK-то е изтрито | |
| 9 | APK, подписано с друг ключ | „Обновлението е подписано с друг ключ…“ | |
| 10 | Сървърът е спрян | Тиха грешка в диалога, приложението работи нормално | |

Сценарии 8–10 са покрити от кода, но още не са пускани на устройство. Пуснете ги при първото
внедряване в ново приложение.

---

## 9. Пълен код

Генерирано от файловете в референтното приложение (`com.gg.smsparking`) със
`scripts/build-self-update-doc.py`. **Не редактирайте ръчно** — променете кода и пуснете скрипта.

<!-- CODE:BEGIN -->

### `app/src/main/java/com/gg/smsparking/update/UpdateInfo.kt`

_данните от сървъра + чистата логика_

```kotlin
package com.gg.smsparking.update

import java.net.URI

/** The server's answer to check-update; the format is in docs/self-update.md. */
data class UpdateInfo(
    val versionCode: Long,
    val versionName: String,
    val apkUrl: String,
    /** Lower-case hex SHA-256 of the APK file. */
    val sha256: String,
    /** APK size in bytes; 0 when the server didn't say. */
    val size: Long = 0,
    val minSdk: Int = 0,
    val changelog: String = "",
)

/** Pure decisions about an update, kept out of the Android/org.json code so they can be unit-tested. */
object UpdatePolicy {
    fun isNewer(info: UpdateInfo, currentVersionCode: Long, sdkInt: Int): Boolean =
        info.versionCode > currentVersionCode && sdkInt >= info.minSdk

    fun checkUrl(base: String, currentVersionCode: Long, sdkInt: Int): String =
        base + (if ('?' in base) '&' else '?') + "versionCode=$currentVersionCode&sdk=$sdkInt"

    /**
     * Absolute URL of the APK; a relative [apkUrl] resolves against [checkUrl].
     * Null unless it is HTTPS (or HTTP when [allowHttp], for local tests with a debug build).
     */
    fun resolveApkUrl(checkUrl: String, apkUrl: String, allowHttp: Boolean = false): String? {
        val uri = runCatching { URI(checkUrl).resolve(apkUrl.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase()
        val schemeOk = scheme == "https" || (allowHttp && scheme == "http")
        return if (schemeOk && !uri.host.isNullOrEmpty()) uri.toString() else null
    }

    fun isSha256(hex: String): Boolean = hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }

    fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }
}
```

### `app/src/main/java/com/gg/smsparking/update/UpdateClient.kt`

_HTTP: проверка и изтегляне_

```kotlin
package com.gg.smsparking.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.coroutineContext

/** HTTP side of updates: the version check and the APK download. */
object UpdateClient {
    private const val CONNECT_TIMEOUT_MS = 15_000
    private const val READ_TIMEOUT_MS = 30_000
    private const val MAX_APK_BYTES = 200L * 1024 * 1024

    /** The newer version the server offers, or null when it answers 204 (nothing new). */
    suspend fun fetch(checkUrl: String, currentVersionCode: Long, sdkInt: Int, userAgent: String): UpdateInfo? =
        withContext(Dispatchers.IO) {
            val conn = open(UpdatePolicy.checkUrl(checkUrl, currentVersionCode, sdkInt), userAgent)
            conn.setRequestProperty("Accept", "application/json")
            try {
                when (val code = conn.responseCode) {
                    HttpURLConnection.HTTP_NO_CONTENT -> null
                    HttpURLConnection.HTTP_OK -> parse(conn.inputStream.bufferedReader().use { it.readText() })
                    else -> throw IOException("Сървърът за обновления върна код $code")
                }
            } finally {
                conn.disconnect()
            }
        }

    private fun parse(text: String): UpdateInfo {
        val o = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw IOException("Невалиден отговор от сървъра за обновления", e)
        }
        val info = UpdateInfo(
            versionCode = o.optLong("versionCode", -1),
            versionName = o.optString("versionName"),
            apkUrl = o.optString("apkUrl"),
            sha256 = o.optString("sha256").lowercase(),
            size = o.optLong("size", 0),
            minSdk = o.optInt("minSdk", 0),
            changelog = o.optString("changelog"),
        )
        if (info.versionCode < 1 || info.apkUrl.isBlank() || !UpdatePolicy.isSha256(info.sha256)) {
            throw IOException("Непълен отговор от сървъра за обновления")
        }
        return info
    }

    /**
     * Downloads [url] into [target], checking size and SHA-256. A file already there with the right
     * hash (from an earlier run) is reused. [onProgress] gets 0–100, or null while the size is unknown.
     */
    suspend fun download(url: String, info: UpdateInfo, target: File, userAgent: String, onProgress: (Int?) -> Unit) =
        withContext(Dispatchers.IO) {
            if (target.exists() && sha256(target) == info.sha256) return@withContext
            target.parentFile?.mkdirs()
            val part = File(target.path + ".part")
            val conn = open(url, userAgent)
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("Изтеглянето върна код ${conn.responseCode}")
                }
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.size
                if (total > MAX_APK_BYTES) throw IOException("Файлът за обновление е твърде голям")
                val digest = MessageDigest.getInstance("SHA-256")
                var read = 0L
                var lastPercent = -1
                conn.inputStream.use { input ->
                    part.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            coroutineContext.ensureActive()
                            val n = input.read(buffer)
                            if (n < 0) break
                            read += n
                            if (read > MAX_APK_BYTES) throw IOException("Файлът за обновление е твърде голям")
                            digest.update(buffer, 0, n)
                            output.write(buffer, 0, n)
                            val percent = if (total > 0) (read * 100 / total).toInt().coerceAtMost(100) else null
                            if (percent == null || percent != lastPercent) {
                                lastPercent = percent ?: -1
                                onProgress(percent)
                            }
                        }
                    }
                }
                if (info.size > 0 && read != info.size) throw IOException("Изтегленият файл е непълен")
                if (UpdatePolicy.hex(digest.digest()) != info.sha256) {
                    throw IOException("Изтегленият файл не съвпада с контролната сума (SHA-256)")
                }
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            } finally {
                conn.disconnect()
                part.delete()
            }
        }

    private fun open(url: String, userAgent: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("User-Agent", userAgent)
        }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return UpdatePolicy.hex(digest.digest())
    }
}
```

### `app/src/main/java/com/gg/smsparking/update/UpdateInstaller.kt`

_проверка на APK и PackageInstaller_

```kotlin
package com.gg.smsparking.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File

/** Checks a downloaded APK and hands it to [PackageInstaller]; the outcome arrives in [InstallResultReceiver]. */
object UpdateInstaller {
    const val EXTRA_VERSION_NAME = "versionName"
    const val EXTRA_VERSION_CODE = "versionCode"

    /** "Install unknown apps" for this app, which the user grants in system Settings. */
    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Null if [apk] is this app, at [expectedVersionCode], signed with the installed app's key;
     * otherwise why not. Android enforces the signature anyway — this just fails early, with a reason.
     */
    fun verify(context: Context, apk: File, expectedVersionCode: Long): String? {
        val pm = context.packageManager
        val archive = archiveInfo(pm, apk) ?: return "Изтегленият файл не е валидно APK"
        if (archive.packageName != context.packageName) return "Обновлението е за друго приложение (${archive.packageName})"
        if (archive.longVersionCode != expectedVersionCode) {
            return "Версията в APK (${archive.longVersionCode}) не съвпада с обявената ($expectedVersionCode)"
        }
        val installed = installedInfo(pm, context.packageName)
        if (signers(archive).intersect(signers(installed)).isEmpty()) {
            return "Обновлението е подписано с друг ключ и не може да се инсталира"
        }
        return null
    }

    fun install(context: Context, apk: File, info: UpdateInfo) {
        val installer = context.packageManager.packageInstaller
        // A session left over from an earlier, unconfirmed attempt would only confuse the user.
        installer.mySessions.forEach { runCatching { installer.abandonSession(it.sessionId) } }

        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setSize(apk.length())
            setInstallReason(PackageManager.INSTALL_REASON_USER)
            // Silent once this app is the installer of record (i.e. after the first update through it).
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                apk.inputStream().use { input ->
                    session.openWrite("base.apk", 0, apk.length()).use { output ->
                        input.copyTo(output, 64 * 1024)
                        session.fsync(output)
                    }
                }
                val result = Intent(context, InstallResultReceiver::class.java)
                    .putExtra(EXTRA_VERSION_NAME, info.versionName)
                    .putExtra(EXTRA_VERSION_CODE, info.versionCode)
                // Mutable: the installer fills in the status extras.
                val pending = PendingIntent.getBroadcast(
                    context, sessionId, result,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveInfo(pm: PackageManager, apk: File): PackageInfo? =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageArchiveInfo(apk.path, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageArchiveInfo(apk.path, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    @Suppress("DEPRECATION")
    private fun installedInfo(pm: PackageManager, packageName: String): PackageInfo =
        if (Build.VERSION.SDK_INT >= 33) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong()))
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        }

    /** All certificates the package may be signed with, including earlier ones after a key rotation. */
    private fun signers(info: PackageInfo): Set<Signature> {
        val signing = info.signingInfo ?: return emptySet()
        val certs = if (signing.hasMultipleSigners()) signing.apkContentsSigners else signing.signingCertificateHistory
        return certs?.toSet().orEmpty()
    }
}
```

### `app/src/main/java/com/gg/smsparking/update/InstallResultReceiver.kt`

_резултатът от инсталацията_

```kotlin
package com.gg.smsparking.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat

/** Result of a [PackageInstaller] session started by [UpdateInstaller]. On success the process is replaced. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val versionName = intent.getStringExtra(UpdateInstaller.EXTRA_VERSION_NAME).orEmpty()
        val versionCode = intent.getLongExtra(UpdateInstaller.EXTRA_VERSION_CODE, 0)
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null && Updater.foreground) {
                    context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    Updater.awaitingUser()
                } else {
                    // Can't show the system dialog from the background: ask via a notification instead.
                    Updater.onNeedsConfirmation(context, versionCode, versionName)
                }
            }
            PackageInstaller.STATUS_SUCCESS -> Unit
            // The user closed the system dialog: keep offering the update.
            PackageInstaller.STATUS_FAILURE_ABORTED -> Updater.awaitingUser()
            else -> Updater.onInstallFailed(
                intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                    ?.let { "Неуспешно инсталиране: $it" } ?: "Неуспешно инсталиране (код $status)"
            )
        }
    }
}
```

### `app/src/main/java/com/gg/smsparking/update/UpdateCompletedReceiver.kt`

_след обновление_

```kotlin
package com.gg.smsparking.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Runs in the new version right after an update: cleans up and says what happened. */
class UpdateCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        if (!Updater.onPackageReplaced(context)) return
        val pm = context.packageManager
        val label = pm.getApplicationLabel(context.applicationInfo).toString()
        val version = pm.getPackageInfo(context.packageName, 0).versionName.orEmpty()
        UpdateNotifier.show(context, label, "Обновено до версия $version.", install = false)
    }
}
```

### `app/src/main/java/com/gg/smsparking/update/UpdateNotifier.kt`

_известията_

```kotlin
package com.gg.smsparking.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/** The update notifications; independent of the app's own notification code. */
internal object UpdateNotifier {
    const val ACTION_INSTALL = "self_update.action.INSTALL"
    private const val CHANNEL_ID = "self_update"
    private const val NOTIFICATION_ID = 0x5e1f

    fun ensureChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_DEFAULT)
                .setName("Обновления")
                .setDescription("Нова версия на приложението")
                .build()
        )
    }

    /** Tapping opens the app; with [install], the app then installs the downloaded update. */
    @SuppressLint("MissingPermission")
    fun show(context: Context, title: String, text: String, install: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
        if (install) launch.action = ACTION_INSTALL
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val open = PendingIntent.getActivity(
            context, NOTIFICATION_ID, launch, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val icon = Updater.prefs(context).getInt(Updater.KEY_ICON, 0).takeIf { it != 0 } ?: context.applicationInfo.icon
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
```

### `app/src/main/java/com/gg/smsparking/update/Updater.kt`

_оркестрация, състояние, настройки, отваряне/излизане_

```kotlin
package com.gg.smsparking.update

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.annotation.DrawableRes
import androidx.core.content.edit
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int?) : UpdateState
    /** Downloaded and verified; installs when the user leaves the app, or taps "Обнови". */
    data class Ready(val info: UpdateInfo) : UpdateState
    /** The user must allow "Install unknown apps" for this app first. */
    data class NeedsPermission(val info: UpdateInfo) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String) : UpdateState
}

/**
 * Self-update: check → download → verify → install. The same code in every app (docs/self-update.md);
 * the only app-specific input is [init].
 *
 * Checks when the app comes to the foreground (at most hourly, no background polling). Installing
 * restarts the app, so it happens silently once the user leaves it, or right away on the user's tap.
 */
object Updater {
    /** Small check interval, so switching back and forth between apps doesn't hit the server every time. */
    private const val CHECK_INTERVAL_MS = 60 * 60 * 1000L
    private const val DIR = "self-update"
    private const val PREFS = "self_update"
    private const val KEY_AUTO = "auto"
    private const val KEY_LAST_CHECK = "lastCheck"
    private const val KEY_NOTICE = "notice"
    internal const val KEY_ICON = "icon"

    private var checkUrl = ""
    private var isBusy: () -> Boolean = { false }
    private var initialized = false

    val enabled: Boolean get() = checkUrl.isNotBlank()

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _autoUpdate = MutableStateFlow(true)
    /** Check on open and install on leaving the app; off = only from the "Провери сега" button. */
    val autoUpdate: StateFlow<Boolean> = _autoUpdate.asStateFlow()

    private val _lastCheck = MutableStateFlow(0L)
    val lastCheck: StateFlow<Long> = _lastCheck.asStateFlow()

    private val mutex = Mutex()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile
    private var ready: Pair<UpdateInfo, File>? = null

    @Volatile
    internal var foreground = false
        private set

    /**
     * Call once from the launcher activity's onCreate (before its onStart).
     *
     * @param checkUrl `https://<host>/<applicationId>/check-update`; blank disables updates (debug builds).
     * @param smallIcon monochrome notification icon.
     * @param isBusy true while the app must not be restarted (e.g. an SMS is being sent).
     */
    fun init(
        activity: Activity,
        checkUrl: String,
        @DrawableRes smallIcon: Int,
        isBusy: () -> Boolean = { false },
    ) {
        this.checkUrl = checkUrl
        this.isBusy = isBusy
        if (initialized) return
        initialized = true
        val app = activity.application
        val sp = prefs(app)
        sp.edit { putInt(KEY_ICON, smallIcon) }
        _autoUpdate.value = sp.getBoolean(KEY_AUTO, true)
        _lastCheck.value = sp.getLong(KEY_LAST_CHECK, 0)
        UpdateNotifier.ensureChannel(app)
        val lifecycle = Lifecycle(app)
        app.registerActivityLifecycleCallbacks(lifecycle)
        // This activity's onActivityCreated has already passed (init runs inside its onCreate).
        lifecycle.attach(activity)
    }

    fun setAutoUpdate(context: Context, on: Boolean) {
        prefs(context).edit { putBoolean(KEY_AUTO, on) }
        _autoUpdate.value = on
    }

    /** Checks and downloads; installs right away if the user has left the app meanwhile. */
    suspend fun check(context: Context) {
        if (!enabled || !mutex.tryLock()) return
        val app = context.applicationContext
        try {
            _state.value = UpdateState.Checking
            val current = currentVersionCode(app)
            val info = UpdateClient.fetch(checkUrl, current, Build.VERSION.SDK_INT, userAgent(app))
            val now = System.currentTimeMillis()
            prefs(app).edit { putLong(KEY_LAST_CHECK, now) }
            _lastCheck.value = now
            if (info == null || !UpdatePolicy.isNewer(info, current, Build.VERSION.SDK_INT)) {
                ready = null
                clearDownloads(app)
                _state.value = UpdateState.UpToDate
                return
            }
            val url = UpdatePolicy.resolveApkUrl(checkUrl, info.apkUrl, allowHttp = isDebuggable(app))
                ?: throw IOException("Невалиден адрес на обновлението (нужен е HTTPS)")
            val file = File(dir(app), "update-${info.versionCode}.apk")
            _state.value = UpdateState.Downloading(info, null)
            UpdateClient.download(url, info, file, userAgent(app)) { _state.value = UpdateState.Downloading(info, it) }
            UpdateInstaller.verify(app, file, info.versionCode)?.let {
                file.delete()
                throw IOException(it)
            }
            dir(app).listFiles()?.filter { it != file }?.forEach { it.delete() }
            ready = info to file
            _state.value = UpdateState.Ready(info)
        } catch (e: CancellationException) {
            _state.value = UpdateState.Idle
            throw e
        } catch (e: Exception) {
            _state.value = UpdateState.Failed(e.message ?: e.javaClass.simpleName)
            return
        } finally {
            mutex.unlock()
        }
        if (!foreground) installIfAuto(app)
    }

    /** The user tapped "Обнови" (in the app or in a notification). */
    suspend fun installNow(context: Context) {
        if (ready == null && mutex.isLocked) {
            // A check is already running (e.g. the one on app start): wait for it instead.
            mutex.lock()
            mutex.unlock()
        }
        if (ready == null) check(context)
        install(context.applicationContext, userInitiated = true)
    }

    private suspend fun checkIfDue(context: Context) {
        if (_autoUpdate.value && System.currentTimeMillis() - _lastCheck.value >= CHECK_INTERVAL_MS) check(context)
    }

    private suspend fun installIfAuto(context: Context) {
        if (_autoUpdate.value && _state.value is UpdateState.Ready) install(context, userInitiated = false)
    }

    private suspend fun install(context: Context, userInitiated: Boolean) {
        val (info, file) = ready ?: return
        if (!file.exists()) {
            ready = null
            _state.value = UpdateState.Idle
            return
        }
        if (!UpdateInstaller.canInstall(context)) {
            // The banner asks for the permission next time the app is open.
            _state.value = UpdateState.NeedsPermission(info)
            return
        }
        // Installing restarts the app; don't cut off work in progress.
        if (isBusy()) {
            if (userInitiated) _state.value = UpdateState.Failed("Изчакайте текущата операция да приключи и опитайте пак")
            return
        }
        _state.value = UpdateState.Installing(info)
        try {
            withContext(Dispatchers.IO) { UpdateInstaller.install(context, file, info) }
        } catch (e: Exception) {
            _state.value = UpdateState.Failed("Неуспешно инсталиране: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    internal fun awaitingUser() {
        ready?.let { _state.value = UpdateState.Ready(it.first) }
    }

    internal fun onNeedsConfirmation(context: Context, versionCode: Long, versionName: String) {
        awaitingUser()
        val key = "$versionCode:confirm"
        val sp = prefs(context)
        if (sp.getString(KEY_NOTICE, null) == key) return
        sp.edit { putString(KEY_NOTICE, key) }
        UpdateNotifier.show(context, "Налична е версия $versionName", "Докоснете, за да я инсталирате.", install = true)
    }

    internal fun onInstallFailed(message: String) {
        _state.value = UpdateState.Failed(message)
    }

    /** After this app was replaced: removes the APK; true if it was an update we downloaded. */
    internal fun onPackageReplaced(context: Context): Boolean {
        val files = dir(context).listFiles().orEmpty()
        files.forEach { it.delete() }
        return files.any { it.name.endsWith(".apk") }
    }

    internal fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    internal fun currentVersionCode(context: Context): Long =
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(context.packageName, 0))

    private fun userAgent(context: Context): String {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return "${context.packageName}/${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)}; Android ${Build.VERSION.SDK_INT})"
    }

    private fun isDebuggable(context: Context) = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0

    private fun clearDownloads(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }

    private fun dir(context: Context) = File(context.cacheDir, DIR)

    /** Foreground = at least one started activity. Replaces any onStart/onStop code in the app. */
    private class Lifecycle(private val app: Application) : Application.ActivityLifecycleCallbacks {
        private var started = 0

        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = attach(activity)

        fun attach(activity: Activity) {
            handleIntent(activity.intent)
            (activity as? ComponentActivity)?.addOnNewIntentListener { handleIntent(it) }
        }

        override fun onActivityStarted(activity: Activity) {
            if (started++ == 0) {
                foreground = true
                scope.launch { checkIfDue(app) }
            }
        }

        override fun onActivityResumed(activity: Activity) {
            // Back from system Settings: the install permission may have been granted.
            val s = _state.value
            if (s is UpdateState.NeedsPermission && UpdateInstaller.canInstall(app)) _state.value = UpdateState.Ready(s.info)
        }

        override fun onActivityStopped(activity: Activity) {
            if (--started == 0 && !activity.isChangingConfigurations) {
                foreground = false
                scope.launch { installIfAuto(app) }
            }
        }

        override fun onActivityPaused(activity: Activity) = Unit
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
        override fun onActivityDestroyed(activity: Activity) = Unit

        /** Tap on the "Докоснете, за да я инсталирате" notification. */
        private fun handleIntent(intent: Intent?) {
            if (intent?.action != UpdateNotifier.ACTION_INSTALL) return
            intent.action = null
            scope.launch { installNow(app) }
        }
    }
}
```

### `app/src/main/java/com/gg/smsparking/update/UpdateUi.kt`

_Compose: банер и диалог_

```kotlin
package com.gg.smsparking.update

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.net.toUri
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Put above the app's content: shown only while a downloaded update waits for the user. */
@Composable
fun UpdateBanner(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val (info, needsPermission) = when (val s = state) {
        is UpdateState.Ready -> s.info to false
        is UpdateState.NeedsPermission -> s.info to true
        else -> return
    }
    Card(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Налична е версия ${info.versionName}", fontWeight = FontWeight.Bold)
            info.changelog.takeIf { it.isNotBlank() }?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (needsPermission) {
                Text("Разрешете инсталирането от това приложение — след това обновленията ще стават сами.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = { openInstallPermission(context) }) { Text("Разреши") }
            } else {
                Button(onClick = { scope.launch { Updater.installNow(context) } }) { Text("Обнови сега") }
            }
        }
    }
}

/** "Обновления": version, auto-update switch, last check, status and "Провери сега". */
@Composable
fun UpdateDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val auto by Updater.autoUpdate.collectAsState()
    val lastCheck by Updater.lastCheck.collectAsState()
    val version = remember {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName} (${PackageInfoCompat.getLongVersionCode(info)})"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Обновления") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Версия $version")
                if (!Updater.enabled) {
                    Text("Обновленията са изключени в тази компилация.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    return@Column
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Автоматично обновяване", modifier = Modifier.weight(1f))
                    Switch(checked = auto, onCheckedChange = { Updater.setAutoUpdate(context, it) })
                }
                if (lastCheck > 0) {
                    Text("Последна проверка: ${dateTime.format(Instant.ofEpochMilli(lastCheck).atZone(ZoneId.systemDefault()))}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                when (val s = state) {
                    UpdateState.Idle -> Unit
                    UpdateState.Checking -> Text("Проверява се…")
                    UpdateState.UpToDate -> Text("Имате последната версия.")
                    is UpdateState.Downloading -> {
                        Text("Изтегля се версия ${s.info.versionName}…")
                        if (s.percent != null) LinearProgressIndicator(progress = { s.percent / 100f }, modifier = Modifier.fillMaxWidth())
                        else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    is UpdateState.Ready -> Text("Версия ${s.info.versionName} е изтеглена.")
                    is UpdateState.NeedsPermission -> Text("Версия ${s.info.versionName} е изтеглена, но инсталирането не е разрешено.")
                    is UpdateState.Installing -> Text("Инсталира се версия ${s.info.versionName}…")
                    is UpdateState.Failed -> Text(s.message, color = MaterialTheme.colorScheme.error)
                }
                Text(
                    "Проверява при отваряне на приложението; новата версия се инсталира сама, " +
                        "щом излезете от него. Първия път Android може да поиска потвърждение.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            when (state) {
                is UpdateState.Ready -> TextButton(onClick = { scope.launch { Updater.installNow(context) } }) { Text("Обнови сега") }
                is UpdateState.NeedsPermission -> TextButton(onClick = { openInstallPermission(context) }) { Text("Разреши") }
                UpdateState.Checking, is UpdateState.Downloading, is UpdateState.Installing -> Unit
                else -> if (Updater.enabled) {
                    TextButton(onClick = { scope.launch { Updater.check(context) } }) { Text("Провери сега") }
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Затвори") } },
    )
}

private val dateTime = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm")

private fun openInstallPermission(context: Context) {
    context.startActivity(
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}
```

### `app/src/test/java/com/gg/smsparking/update/UpdatePolicyTest.kt`

_unit тестове_

```kotlin
package com.gg.smsparking.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val base = "https://apks.gg-webapp.com/com.gg.smsparking/check-update"
    private fun info(code: Long, minSdk: Int = 0) = UpdateInfo(code, "1.$code", "x.apk", "a".repeat(64), minSdk = minSdk)

    @Test
    fun newerOnlyWhenHigherAndSupported() {
        assertTrue(UpdatePolicy.isNewer(info(2), 1, 35))
        assertFalse(UpdatePolicy.isNewer(info(1), 1, 35))
        assertFalse(UpdatePolicy.isNewer(info(2, minSdk = 36), 1, 35))
    }

    @Test
    fun checkUrlAppendsParameters() {
        assertEquals("$base?versionCode=3&sdk=35", UpdatePolicy.checkUrl(base, 3, 35))
        assertEquals("$base?ch=beta&versionCode=3&sdk=35", UpdatePolicy.checkUrl("$base?ch=beta", 3, 35))
    }

    @Test
    fun apkUrlMustBeHttps() {
        assertEquals("https://apks.gg-webapp.com/com.gg.smsparking/apk/com.gg.smsparking-2.apk", UpdatePolicy.resolveApkUrl(base, "apk/com.gg.smsparking-2.apk"))
        assertEquals("https://cdn.example.com/a.apk", UpdatePolicy.resolveApkUrl(base, "https://cdn.example.com/a.apk"))
        assertNull(UpdatePolicy.resolveApkUrl(base, "http://cdn.example.com/a.apk"))
        assertEquals("http://10.0.2.2:8000/a.apk", UpdatePolicy.resolveApkUrl("http://10.0.2.2:8000/x", "/a.apk", allowHttp = true))
        assertNull(UpdatePolicy.resolveApkUrl(base, "file:///sdcard/a.apk", allowHttp = true))
    }

    @Test
    fun sha256Format() {
        assertTrue(UpdatePolicy.isSha256("0123456789abcdef".repeat(4)))
        assertFalse(UpdatePolicy.isSha256("0123456789ABCDEF".repeat(4)))
        assertFalse(UpdatePolicy.isSha256("abc"))
        assertEquals("00ff10", UpdatePolicy.hex(byteArrayOf(0, -1, 16)))
    }
}
```

### `app/src/debug/AndroidManifest.xml`

_само debug_

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Debug only: allows a plain-HTTP local update server (see docs/update-server.md, "Локален тест"). -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <application android:networkSecurityConfig="@xml/network_security_config" />
</manifest>
```

### `app/src/debug/res/xml/network_security_config.xml`

_само debug_

```xml
<?xml version="1.0" encoding="utf-8"?>
<!-- Debug only, for a local HTTP update server; release builds keep the HTTPS-only default. -->
<network-security-config xmlns:tools="http://schemas.android.com/tools">
    <base-config
        cleartextTrafficPermitted="true"
        tools:ignore="InsecureBaseConfiguration" />
</network-security-config>
```

### `server/apache-apks.conf`

_сървър: Apache2 VirtualHost, един за всички приложения_

```apache
# Apache2: apks.gg-webapp.com — admin panel + the self-update server for every app (docs/self-update.md).
#
#   <project_folder>/
#   ├── public/                    ← DocumentRoot: the admin panel + check-update.php (server/public/)
#   └── storage/apks/              ← APKS_ROOT, not public; written by the admin panel
#       └── <applicationId>/
#           ├── latest.json
#           └── apk/<applicationId>-<versionCode>.apk
#
# Public URLs for the apps (everything else is the admin panel):
#   /<applicationId>/check-update      → public/check-update.php?package=<applicationId>
#   /<applicationId>/apk/<file>.apk    → storage/apks/<applicationId>/apk/<file>.apk
# Adding an app = adding a folder in storage/apks; this file doesn't change.
#
# Install (Debian/Ubuntu):
#   sudo cp server/apache-apks.conf /etc/apache2/sites-available/apks.conf
#   (edit PROJECT_ROOT below)
#   sudo a2enmod rewrite headers ssl alias
#   sudo a2ensite apks && sudo apachectl configtest && sudo systemctl reload apache2
#   sudo certbot --apache -d apks.gg-webapp.com        # HTTPS certificate (Let's Encrypt)
#
# PHP: whatever the server already uses for .php (libapache2-mod-php or php-fpm via a2enconf).

# The only path to adapt.
Define PROJECT_ROOT /home/USER/project_folder
Define APKS_ROOT ${PROJECT_ROOT}/storage/apks

<VirtualHost *:80>
    ServerName apks.gg-webapp.com
    Redirect permanent / https://apks.gg-webapp.com/
</VirtualHost>

<VirtualHost *:443>
    ServerName apks.gg-webapp.com
    DocumentRoot ${PROJECT_ROOT}/public

    SSLEngine on
    SSLCertificateFile /etc/letsencrypt/live/apks.gg-webapp.com/fullchain.pem
    SSLCertificateKeyFile /etc/letsencrypt/live/apks.gg-webapp.com/privkey.pem

    # The admin panel: keep whatever it needs (AllowOverride All for a framework's .htaccess).
    <Directory ${PROJECT_ROOT}/public>
        Options -Indexes +FollowSymLinks
        AllowOverride All
        Require all granted
    </Directory>

    # check-update.php finds the apps here.
    SetEnv APKS_ROOT ${APKS_ROOT}

    RewriteEngine On
    # /<applicationId>/check-update → /check-update.php?package=<applicationId> (versionCode/sdk kept)
    RewriteRule "^/([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+)/check-update$" "/check-update.php?package=$1" [PT,QSA,L]

    # /<applicationId>/apk/<file>.apk → storage/apks/<applicationId>/apk/<file>.apk
    AliasMatch "^/([A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+)/apk/([A-Za-z0-9._-]+\.apk)$" "${APKS_ROOT}/$1/apk/$2"

    # storage/apks: only the .apk files, nothing else (latest.json, listings).
    <Directory ${APKS_ROOT}>
        Options None
        AllowOverride None
        Require all denied
        <FilesMatch "\.apk$">
            Require all granted
            ForceType application/vnd.android.package-archive
            # File names contain the versionCode and never change: cache forever.
            Header set Cache-Control "public, max-age=31536000, immutable"
        </FilesMatch>
    </Directory>

    ErrorLog ${APACHE_LOG_DIR}/apks-error.log
    CustomLog ${APACHE_LOG_DIR}/apks-access.log combined
</VirtualHost>
```

### `server/public/check-update.php`

_сървър: ендпойнтът, в <project_folder>/public/_

```php
<?php
/**
 * GET /<applicationId>/check-update?versionCode=<int>&sdk=<int>
 * (Apache rewrites it to /check-update.php?package=<applicationId>&versionCode=…&sdk=…)
 *
 * 200 + JSON (the app's latest.json) when there is a newer version the device can install,
 * 204 when there is nothing to install. Contract: docs/self-update.md.
 * One file for every app; it reads <APKS_ROOT>/<applicationId>/latest.json.
 */
declare(strict_types=1);

header('Cache-Control: no-store');
header('X-Content-Type-Options: nosniff');

function fail(int $code, string $error): never
{
    http_response_code($code);
    header('Content-Type: application/json; charset=utf-8');
    echo json_encode(['error' => $error]);
    exit;
}

if ($_SERVER['REQUEST_METHOD'] !== 'GET' && $_SERVER['REQUEST_METHOD'] !== 'HEAD') {
    header('Allow: GET, HEAD');
    fail(405, 'method_not_allowed');
}

// Set by Apache (SetEnv APKS_ROOT); the default is the standard layout <project_folder>/storage/apks.
$root = $_SERVER['APKS_ROOT'] ?? getenv('APKS_ROOT') ?: dirname(__DIR__) . '/storage/apks';

// An Android applicationId: at least two segments, letters/digits/underscore, each starting with a letter.
$package = (string)($_GET['package'] ?? '');
if (!preg_match('/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/', $package)) {
    fail(400, 'bad_package');
}
if (!is_dir("$root/$package")) {
    fail(404, 'unknown_app');
}

$raw = @file_get_contents("$root/$package/latest.json");
if ($raw === false) {
    fail(503, 'manifest_missing');
}
$latest = json_decode($raw, true);
if (!is_array($latest)
    || !is_int($latest['versionCode'] ?? null)
    || !is_string($latest['apkUrl'] ?? null)
    || !preg_match('/^[0-9a-f]{64}$/', (string)($latest['sha256'] ?? ''))
) {
    fail(503, 'manifest_invalid');
}

// Missing parameters are allowed (the full manifest is returned); malformed ones are not.
$current = filter_input(INPUT_GET, 'versionCode', FILTER_VALIDATE_INT, ['options' => ['min_range' => 0]]);
$sdk = filter_input(INPUT_GET, 'sdk', FILTER_VALIDATE_INT, ['options' => ['min_range' => 1]]);
if ($current === false || $sdk === false) {
    fail(400, 'bad_parameters');
}

$nothingNew = $current !== null && $current >= $latest['versionCode'];
$unsupported = $sdk !== null && $sdk < (int)($latest['minSdk'] ?? 0);
if ($nothingNew || $unsupported) {
    http_response_code(204);
    exit;
}

header('Content-Type: application/json; charset=utf-8');
echo json_encode($latest, JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES);
```

### `server/admin/publish-update.php`

_сървър: публикуване от админ панела_

```php
<?php
/**
 * For the admin panel: publishes a new APK of an app exactly as the self-update standard expects
 * (docs/self-update.md). Framework-agnostic: require this file and call publishUpdate().
 *
 *   $latest = publishUpdate(
 *       apksRoot: '/home/USER/project_folder/storage/apks',
 *       host: 'https://apks.gg-webapp.com',
 *       package: 'com.gg.smsparking',
 *       apkPath: $_FILES['apk']['tmp_name'],     // the uploaded, signed release APK
 *       versionCode: 4,                          // must equal versionCode inside the APK
 *       versionName: '1.3',
 *       changelog: 'Какво е новото',
 *       minSdk: 28,
 *   );
 *
 * Writes <apksRoot>/<package>/apk/<package>-<versionCode>.apk, then <apksRoot>/<package>/latest.json
 * (both atomically, the APK first). Throws InvalidArgumentException / RuntimeException with a
 * message for the admin on any problem; nothing is published then.
 */
declare(strict_types=1);

const APPLICATION_ID_PATTERN = '/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/';

function publishUpdate(
    string $apksRoot,
    string $host,
    string $package,
    string $apkPath,
    int $versionCode,
    string $versionName,
    string $changelog = '',
    int $minSdk = 0,
): array {
    if (!preg_match(APPLICATION_ID_PATTERN, $package)) {
        throw new InvalidArgumentException("Невалиден applicationId: $package");
    }
    if ($versionCode < 1) {
        throw new InvalidArgumentException('versionCode трябва да е ≥ 1');
    }
    if (!is_file($apkPath) || filesize($apkPath) === 0) {
        throw new InvalidArgumentException('Липсва APK файл');
    }
    $magic = (string)file_get_contents($apkPath, false, null, 0, 4);
    if ($magic !== "PK\x03\x04") {
        throw new InvalidArgumentException('Файлът не е APK');
    }

    $appDir = rtrim($apksRoot, '/') . "/$package";
    $latestPath = "$appDir/latest.json";
    if (is_file($latestPath)) {
        $current = json_decode((string)file_get_contents($latestPath), true);
        $currentCode = is_array($current) ? (int)($current['versionCode'] ?? 0) : 0;
        if ($versionCode <= $currentCode) {
            throw new InvalidArgumentException("versionCode $versionCode не е по-голям от публикувания ($currentCode)");
        }
    }

    // Published files never change (clients and caches rely on it).
    $name = "$package-$versionCode.apk";
    $apkDir = "$appDir/apk";
    $target = "$apkDir/$name";
    if (file_exists($target)) {
        throw new RuntimeException("$name вече съществува");
    }
    if (!is_dir($apkDir) && !mkdir($apkDir, 0775, true) && !is_dir($apkDir)) {
        throw new RuntimeException("Папката $apkDir не може да се създаде");
    }

    // 1. The APK, atomically (same filesystem → rename is atomic).
    $tmp = "$target.tmp";
    if (!copy($apkPath, $tmp)) {
        throw new RuntimeException('APK файлът не може да се запише');
    }
    chmod($tmp, 0664);
    if (!rename($tmp, $target)) {
        @unlink($tmp);
        throw new RuntimeException('APK файлът не може да се запише');
    }

    // 2. latest.json, atomically — only now do phones see the new version.
    $latest = [
        'versionCode' => $versionCode,
        'versionName' => $versionName,
        'apkUrl' => rtrim($host, '/') . "/$package/apk/$name",
        'sha256' => hash_file('sha256', $target),
        'size' => filesize($target),
        'minSdk' => $minSdk,
        'changelog' => $changelog,
        'publishedAt' => gmdate('Y-m-d\TH:i:s\Z'),
    ];
    $json = json_encode($latest, JSON_PRETTY_PRINT | JSON_UNESCAPED_UNICODE | JSON_UNESCAPED_SLASHES) . "\n";
    $tmp = "$latestPath.tmp";
    if (file_put_contents($tmp, $json) === false || !chmod($tmp, 0664) || !rename($tmp, $latestPath)) {
        @unlink($tmp);
        throw new RuntimeException('latest.json не може да се запише');
    }
    return $latest;
}
```

### `server/dev-router.php`

_само за локален тест с php -S_

```php
<?php
/**
 * Local stand-in for apache-apks.conf, for tests with PHP's built-in server only:
 *
 *   php -S 0.0.0.0:8000 -t <project_folder>/public server/dev-router.php
 *
 * Same public URLs as production: /<applicationId>/check-update and /<applicationId>/apk/<file>.apk,
 * with the files in <project_folder>/storage/apks. Everything else → 404.
 */
declare(strict_types=1);

const PKG = '[A-Za-z][A-Za-z0-9_]*(?:\.[A-Za-z][A-Za-z0-9_]*)+';
$apksRoot = dirname(rtrim($_SERVER['DOCUMENT_ROOT'], '/')) . '/storage/apks';
$path = (string)parse_url($_SERVER['REQUEST_URI'], PHP_URL_PATH);

if (preg_match('#^/(' . PKG . ')/check-update$#', $path, $m)) {
    $_GET['package'] = $m[1];
    $_SERVER['APKS_ROOT'] = $apksRoot;
    require $_SERVER['DOCUMENT_ROOT'] . '/check-update.php';
    return true;
}
if (preg_match('#^/(' . PKG . ')/apk/([A-Za-z0-9._-]+\.apk)$#', $path, $m) && is_file($file = "$apksRoot/$m[1]/apk/$m[2]")) {
    header('Content-Type: application/vnd.android.package-archive');
    header('Content-Length: ' . filesize($file));
    readfile($file);
    return true;
}
http_response_code(404);
return true;
```

### `scripts/make-update-manifest.py`

_издаване на версия_

```python
#!/usr/bin/env python3
"""Prepares a signed release APK for the self-update server (docs/self-update.md). Works for any app.

Reads applicationId/versionCode/versionName from the output-metadata.json next to the APK, checks the
signature with apksigner (if the Android SDK is found), and writes into OUT_DIR:

    OUT_DIR/<applicationId>/apk/<applicationId>-<versionCode>.apk
    OUT_DIR/<applicationId>/latest.json

i.e. exactly the layout of the app's folder on the server.

Example:
    scripts/make-update-manifest.py app/release/app-release.apk --changelog "Какво е новото" --out build/update
"""
import argparse
import glob
import hashlib
import json
import os
import shutil
import subprocess
import sys
from datetime import datetime, timezone

DEFAULT_HOST = "https://apks.gg-webapp.com"


def find_apksigner():
    sdk = os.environ.get("ANDROID_HOME") or os.environ.get("ANDROID_SDK_ROOT") or os.path.expanduser("~/Library/Android/sdk")
    found = sorted(glob.glob(os.path.join(sdk, "build-tools", "*", "apksigner")))
    return found[-1] if found else shutil.which("apksigner")


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    p.add_argument("apk", help="signed release APK, e.g. app/release/app-release.apk")
    p.add_argument("--out", default="build/update", help="output directory (default: build/update)")
    p.add_argument("--host", default=DEFAULT_HOST, help="update server (default: %(default)s)")
    p.add_argument("--changelog", default="", help="short text shown to the user")
    p.add_argument("--min-sdk", type=int, default=None, help="minSdk of the build (default: from output-metadata.json)")
    a = p.parse_args()

    if "unsigned" in os.path.basename(a.apk):
        sys.exit("Това APK не е подписано. Използвайте Build → Generate Signed App Bundle / APK със същия keystore.")

    meta_path = os.path.join(os.path.dirname(os.path.abspath(a.apk)), "output-metadata.json")
    with open(meta_path, encoding="utf-8") as f:
        meta = json.load(f)
    package = meta["applicationId"]
    element = next(e for e in meta["elements"] if e["outputFile"] == os.path.basename(a.apk))
    version_code, version_name = int(element["versionCode"]), element["versionName"]
    min_sdk = a.min_sdk if a.min_sdk is not None else int(meta.get("minSdkVersionForDexing", 0))

    apksigner = find_apksigner()
    if apksigner:
        r = subprocess.run([apksigner, "verify", "--print-certs", a.apk], capture_output=True, text=True)
        if r.returncode != 0:
            sys.exit("apksigner: подписът не е валиден\n" + r.stdout + r.stderr)
        certs = [l.split(":", 1)[1].strip() for l in r.stdout.splitlines() if "certificate SHA-256 digest" in l]
        print("Подписващ сертификат (SHA-256):", ", ".join(certs) or "?")
        print("  → трябва да е един и същ за всички версии на", package)
    else:
        print("ВНИМАНИЕ: apksigner не е намерен, подписът не е проверен.", file=sys.stderr)

    folder = os.path.join(a.out, package)
    name = f"{package}-{version_code}.apk"
    os.makedirs(os.path.join(folder, "apk"), exist_ok=True)
    target = os.path.join(folder, "apk", name)
    shutil.copyfile(a.apk, target)

    digest = hashlib.sha256()
    with open(target, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 20), b""):
            digest.update(chunk)

    latest = {
        "versionCode": version_code,
        "versionName": version_name,
        "apkUrl": f"{a.host.rstrip('/')}/{package}/apk/{name}",
        "sha256": digest.hexdigest(),
        "size": os.path.getsize(target),
        "minSdk": min_sdk,
        "changelog": a.changelog,
        "publishedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
    }
    with open(os.path.join(folder, "latest.json"), "w", encoding="utf-8") as f:
        json.dump(latest, f, ensure_ascii=False, indent=2)
        f.write("\n")

    print(json.dumps(latest, ensure_ascii=False, indent=2))
    print(f"\nКачете първо {target}, после {os.path.join(folder, 'latest.json')}.")


if __name__ == "__main__":
    main()
```

<!-- CODE:END -->

---

## 10. Подводни камъни и решения

Неща, които излязоха при реалния тест и вече са решени в кода. Не ги „оптимизирайте“ обратно.

| Проблем | Решение в кода |
|---|---|
| `File.renameTo` за `.part` → `.apk` връщаше `false` без причина | `Files.move(..., REPLACE_EXISTING)` |
| Без `UPDATE_PACKAGES_WITHOUT_USER_ACTION` Android пита при **всяко** обновление | разрешението е в манифеста |
| `USER_ACTION_NOT_REQUIRED` работи само ако приложението е „инсталатор“ на себе си | първото обновление минава с потвърждение, после е тихо. Нищо не може да го заобиколи |
| Инсталирането рестартира приложението | тихо само след излизане (`onActivityStopped` на последната активност, без config change) или при натискане на бутона; `isBusy` за критични операции |
| `Updater.init` се вика вътре в `onCreate`, т.е. след `onActivityCreated` на същата активност | `init` закача ръчно и текущата активност (`lifecycle.attach(activity)`). Иначе докосването на известието не прави нищо |
| Известие от BroadcastReceiver не може да отвори системния диалог от фонов режим | `PENDING_USER_ACTION` извън екрана → известие → то отваря приложението с `ACTION_INSTALL` → нова сесия на преден план |
| Повтарящи се известия | по веднъж на версия (`notice` = „versionCode:confirm“) |
| JobScheduler фонова проверка | премахната: проверка само при отваряне (иначе трябва и `ACCESS_NETWORK_STATE`, без което се crash-ва) |
| Debug ключ ≠ release ключ | в debug обновяването е изключено, освен с `-PupdateUrl` |
| HTTP за локален тест | позволен само в debug (`src/debug/…/network_security_config.xml`); release е само HTTPS, и клиентът отхвърля `http://` `apkUrl` |
| **Google Play Protect** може да покаже „App scan recommended“ за приложение извън Play Store и да спре тихото инсталиране | не може да се заобиколи от сървъра или кода. Потребителят избира „Scan app“ или го изключва. Ако е масов проблем — Play Store |
| **Загубен keystore** | няма решение: нито една инсталация не може да се обнови. Пазете го офлайн + резервно копие; никога в git или на сървъра |
| Старите версии без механизма | обновяват се веднъж ръчно до първата версия с механизма |
| Сървърът показва иконка 48×48 или никаква | векторната адаптивна иконка не се рендерира, а при всички плътности първият растер е mdpi. Решение: само `mipmap-xxxhdpi/ic_launcher.webp` 192×192 (раздел 6, стъпка 7). Проверено в Баркодове (`com.gg.barcodes`) |
| **Release версията не стартира** (`IllegalAccessError: Class kotlin.collections.h extended by class ru is inaccessible`), а debug работи | R8 с `packageScope` премества част от пакетно-видимите Kotlin класове `*Kt__*` в друг пакет. Решение: `kotlin-multifile.keep` (раздел 6, стъпка 2). Без `packageScope` се чупи ML Kit, затова той остава. Проверено в Баркодове (`com.gg.barcodes`) на емулатор с API 36 |
