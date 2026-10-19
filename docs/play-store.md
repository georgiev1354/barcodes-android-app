# Google Play: промени и публикуване

Този документ описва какво е променено, за да може приложението да се качи в Google Play, как се връща към оригинала (самообновяване от `apks.gg-webapp.com`) и какво се попълва в Play Console.

Свързани файлове:
- [play-store/listing.md](play-store/listing.md): текстове за магазина (BG/EN), категория, графики, снимки
- [play-store/privacy-policy.md](play-store/privacy-policy.md): политика за поверителност (страницата е `web/public/privacy.html`)
- [play-store/graphics/](play-store/graphics/): икона 512×512 и банер 1024×500
- [self-update.md](self-update.md): оригиналният механизъм за обновяване

## Защо се махна самообновяването

Политиката на Google Play [Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/9888379) забранява приложение от Play да се обновява само, извън Play. Освен това `REQUEST_INSTALL_PACKAGES` е ограничено разрешение: изисква декларация, а самообновяването не е позволена причина. Приложение с него се отхвърля.

Затова в тази версия:
- няма проверка за обновления, банер, диалог и известия;
- няма разрешения за инсталиране на пакети и за известия;
- кодът от `update/` изобщо не влиза в build-а. Сорс файловете **не са пипани**, само са изключени от компилация.

Обновленията идват от Google Play.

## Списък на промените

Всяка промяна е маркирана с коментар `PLAY-STORE`. Всички се намират с:

```bash
grep -rn "PLAY-STORE" app/
```

| # | Файл | Промяна | Тип |
|---|---|---|---|
| 1 | `app/src/main/AndroidManifest.xml` | Закоментирани `INTERNET`, `REQUEST_INSTALL_PACKAGES`, `UPDATE_PACKAGES_WITHOUT_USER_ACTION`, `POST_NOTIFICATIONS` | коментар |
| 2 | `app/src/main/AndroidManifest.xml` | Закоментирани receiver-ите `InstallResultReceiver` и `UpdateCompletedReceiver` | коментар |
| 3 | `MainActivity.kt` | Закоментирани import-ите на `update.*` и на `android.os.Build` | коментар |
| 4 | `MainActivity.kt` | Закоментиран `Updater.init(...)` | коментар |
| 5 | `MainActivity.kt` | Закоментирани `showUpdates`, `UpdateBanner()`, `UpdateDialog`. `BarcodesApp(onShowUpdates = {})` вместо `{ showUpdates = true }` | коментар + 1 ред |
| 6 | `MainActivity.kt` | Закоментирано искането на разрешение за известия (`POST_NOTIFICATIONS`) | коментар |
| 7 | `ui/SettingsScreen.kt` | Закоментиран елементът „Обновления“; **добавен** ред „Версия X“ и import на `BuildConfig` | коментар + добавено |
| 8 | `res/values/strings.xml` | **Добавен** низ `settings_version` | добавено |
| 9 | `app/build.gradle.kts` | Закоментирани двата `buildConfigField("String", "UPDATE_URL", …)` (и празният `debug {}` около втория) | коментар |
| 10 | `app/build.gradle.kts` | **Добавен** блок `tasks.withType<KotlinCompile>() { exclude("com/gg/barcodes/update/**") }`: изключва `update/` и теста му от компилация | добавено |
| 11 | `app/lint.xml` (**нов файл**) | Lint пропуска `update/`: той вече не се компилира, но lint го чете и отчита грешка, че известията нямат `POST_NOTIFICATIONS`. При връщане файлът се изтрива. | добавено |

Също: в `.gitignore` са добавени `*.jks` и `*.keystore`, за да не попадне ключът в репото (полезно и за оригинала).

Нови файлове (не влияят на оригинала, могат да останат):
- `docs/play-store.md`, `docs/play-store/*`
- `scripts/render-store-graphics.py`
- `web/public/privacy.html`

Непроменени: целият `app/src/main/java/com/gg/barcodes/update/`, `docs/self-update.md`, `app/src/test/.../update/UpdatePolicyTest.kt` (изключен от компилация заедно с `update/`). Низовете `settings_updates*` и иконата `ic_stat_barcodes` остават и не се ползват. Lint може да ги отбележи като unused; това е само предупреждение.

## Връщане към оригинала (самообновяване)

**Най-лесно с git.** Ако Play промените са в отделен commit (препоръчително, вижте по-долу):

```bash
git revert <commit-на-play-промените>
```

**Ръчно:**
1. `grep -rn "PLAY-STORE" app/` и за всеки резултат:
   - разкоментирайте закоментирания код под маркера (Kotlin: махнете `// `, XML: махнете `<!-- PLAY-STORE … ` и съответното `-->`);
   - изтрийте добавеното (промени 7, 8, 10 и файла `app/lint.xml` от 11) и самите `PLAY-STORE` коментари.
2. В `MainActivity.kt` върнете `BarcodesApp(onShowUpdates = { showUpdates = true })`.
3. В `AndroidManifest.xml` пояснителните редове „Android 12+: …“ и „Android 13+: …“ отново стават `<!-- … -->` коментари.
4. Проверка: `./gradlew testDebugUnitTest lint assembleRelease`. `UpdatePolicyTest` трябва отново да се изпълнява, а в Настройки отново да има „Обновления“.

**Важно за ключа:** APK за собствения сървър трябва да е подписан със **същия keystore** като досега ([self-update.md](self-update.md)), иначе вече инсталираните копия не могат да се обновят.

### Препоръка: отделен commit

```bash
git add -A && git commit -m "Prepare for Google Play: disable self-update"
```

Така двете версии се разделят чисто. Ако в бъдеще са нужни **и двата** канала едновременно (Play + собствен сървър), по-доброто решение са два product flavor-а (`play` и `direct`) с `update/` само в `direct`. Това е по-голяма промяна в build-а и не е направена тук.

## Build за Play

Play приема само **Android App Bundle** (`.aab`), подписан с ключ за качване (upload key).

1. Android Studio → **Build → Generate Signed App Bundle / APK → Android App Bundle**, вариант `release`.
2. Ключ (вижте следващата секция).
3. Резултат: `app/release/app-release.aab`.

Без Android Studio: `./gradlew bundleRelease` дава неподписан `app/build/outputs/bundle/release/app-release.aab`, който се подписва с `jarsigner`.

**versionCode** (`app/build.gradle.kts`, сега `5`) трябва да расте с всяко качване в Play. Play отказва вече използван код. `versionName` (`3.2`) е свободен текст, видим за потребителите.

### Ключ за подписване (важно решение)

Play ползва **Play App Signing**: вие качвате с upload key, а Google преподписва за потребителите с app signing key.

| Вариант | Как | Последствие |
|---|---|---|
| **А. Същият ключ като за собствения сървър** (препоръчително, ако има хора със самообновяващата се версия) | При първото качване: Play Console → App integrity → App signing → „Use existing app signing key“ / „Export and upload a key from Java keystore“ (инструментът PEPK). Качвате текущия keystore. | Версията от Play има същия подпис. Потребителите на старата версия могат да минат към Play без деинсталиране. |
| Б. Нов ключ, генериран от Google | По подразбиране, нищо не се прави. | Play версията е с друг подпис. Хората със старата версия трябва да я **деинсталират**, преди да инсталират от Play. |

Изборът е еднократен: app signing key-ят не може да се смени по-късно. Upload key-ят може да се смени през поддръжката на Play.

Пазете keystore файла и паролите извън git. `.gitignore` вече изключва `*.jks` и `*.keystore` (добавено с тези промени); пазете резервно копие на сигурно място.

## Play Console: стъпка по стъпка

1. **Профил на разработчик** на [play.google.com/console](https://play.google.com/console) (еднократна такса $25, проверка на самоличност).
2. **Create app**: име „Баркодове – скенер за стикери“, език по подразбиране български, App, Free.
3. **Main store listing**: текстове и графики от [play-store/listing.md](play-store/listing.md).
4. **App content** (Policy → App content):
   - **Privacy policy**: URL към `privacy.html` ([play-store/privacy-policy.md](play-store/privacy-policy.md)).
   - **Ads**: No, the app doesn't contain ads.
   - **App access**: All functionality is available without special access.
   - **Content rating**: въпросник IARC, категория Utility/Productivity. Отговорът на всичко е „No“ → очаквано PEGI 3 / Everyone.
   - **Target audience**: 18+ (или 13+). Не избирайте групи под 13, иначе важат правилата Families.
   - **Data safety**: вижте по-долу.
   - **Government apps / Financial features / Health**: No / None.
5. **Testing → Closed testing**: за **личен** профил, създаден след 13.11.2023, Google изисква поне **12 тестери** да са включени в затворен тест **14 последователни дни**, преди да се отключи Production. Фирмен профил няма това изискване.
6. **Production → Create new release**: качвате `.aab`, release notes на български, държави (напр. България или всички), Review → Start rollout.

Първият преглед обикновено отнема от няколко часа до няколко дни.

## Data safety

Попълнено според това, което прави приложението, и според [ML Kit Android Data Disclosure](https://developers.google.com/ml-kit/android-data-disclosure). ML Kit изпраща метрики на Google и трябва да се декларират, защото приложението включва ML Kit.

| Въпрос | Отговор |
|---|---|
| Does your app collect or share any of the required user data types? | **Yes** (заради метриките на ML Kit) |
| Is all of the user data collected by your app encrypted in transit? | **Yes** |
| Do you provide a way for users to request that their data is deleted? | **No** (няма акаунти и данни, свързани с потребителя) |

Типове данни (Collected: Yes, Shared: No, Processed ephemerally: No, Required: Yes, т.е. потребителят не може да го изключи):

| Категория → тип | Цел |
|---|---|
| App info and performance → **Diagnostics** | Analytics |
| App info and performance → **Other app performance data** | Analytics |
| Device or other IDs → **Device or other IDs** | Analytics |

**Не** се събират: местоположение, лична информация, снимки и видео (кадрите остават на устройството), файлове, контакти, история.

Отговорността за формуляра е на разработчика. Ако добавите библиотеки (Firebase, Crashlytics, реклами), обновете формуляра и политиката.

## Технически проверки (направени)

| Изискване на Play | Статус |
|---|---|
| targetSdk ≥ 35 (изискване от 31.08.2025) | ✅ 37 |
| Поддръжка на 16 KB memory page size (за приложения с native код, задължително от 01.11.2025) | ✅ Всички `.so` (ML Kit `libbarhopper_v3`, CameraX) са с ELF LOAD alignment 16384 и `zipalign -c -P 16` минава |
| Няма `REQUEST_INSTALL_PACKAGES` | ✅ Проверено в release APK |
| Няма код за самообновяване | ✅ В dex файловете няма `com.gg.barcodes.update.*` |
| Release build-ът стартира | ✅ Release APK на емулатор (API 36): сканиране и настройки („Версия 3.1“), без срив и без искане за известия |
| Формат | ✅ `./gradlew bundleRelease` създава `.aab` |

Разрешения в крайния (merged) manifest:
- `CAMERA`: от приложението;
- `INTERNET`, `ACCESS_NETWORK_STATE`: добавят ги библиотеките на ML Kit (`play-services-mlkit` / `datatransport`) за метриките, макар че нашият ред `INTERNET` е закоментиран. Не са опасни разрешения и Play не изисква декларация за тях;
- `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`: вътрешно на AndroidX.

Не съм ги махал с `tools:node="remove"`, защото ML Kit не е тестван без тях.

## Чести грешки в Play Console

**„Не можете да разпространявате тази версия, защото съществуващите потребители няма да са в състояние да надстроят до новодобавените пакети“** и **„Тази публикувана версия не добавя и не премахва пакети“**

Двете грешки идват заедно, когато новата версия ползва пакет (`.aab`) с `versionCode`, който вече е качван: в същата или в друга писта (вътрешно тестване, затворено тестване). Решение:
1. Вдигнете `versionCode` в `app/build.gradle.kts` над **най-високия** код, качван досега в която и да е писта (App bundle explorer показва всички).
2. Генерирайте нов подписан `.aab`.
3. В черновата на версията махнете стария пакет (ако е добавен) и качете новия.
