// Same texts as app/src/main/res/values/strings.xml, plus the web-only ones at the end.

export const S = {
  appName: "Баркодове",

  permissionRationale: "За да разчита баркодове, приложението се нуждае от достъп до камерата.",
  permissionGrant: "Разреши камерата",

  expectedAuto: "Авто",
  expectedLabel: "Брой:",
  torch: "Фенерче",
  scanHint: "Насочете камерата към стикера и задръжте неподвижно",
  scanProgress: (confirmed: number, total: number) => `Потвърдени: ${confirmed} от ${total} засечени`,
  scanRestart: "Отначало",

  resultTitle: "Разчетени баркодове",
  resultImageDescription: "Кадър от камерата с отбелязани баркодове",
  copy: "Копирай",
  copyAll: "Копирай всички",
  copied: "Копирано",
  scanAgain: "Ново сканиране",

  wifiTitle: "Wi‑Fi мрежа",
  wifiSsid: "Мрежа",
  wifiPassword: "Парола",
  wifiSecurity: "Защита",
  wifiOpen: "Без парола",
  wifiHidden: "Скрита мрежа",
  wifiHiddenYes: "Да",
  wifiRaw: "Оригинал",

  back: "Назад",
  settingsTitle: "Настройки",
  settingsFormats: "Какво да се сканира",
  settingsFormatsHint:
    "Изберете само форматите, които има по стикерите. Колкото по-малко са, толкова по-бързо се разчита и по-малък е рискът избледнял код да бъде прочетен грешно.",
  settingsFormatWeak: "Без контролна цифра: всеки код се потвърждава с повече еднакви прочитания",
  settingsFormatsDefault: "Върни по подразбиране (Code 128, Code 39, ITF, EAN-13, QR)",
  settingsResult: "Резултат",
  settingsStripLabels: "Премахвай етикети пред стойността",
  settingsStripLabelsSummary: "„S/N: 2430011117002865“ → „2430011117002865“. Също SN:, P/N:, PN:.",
  settingsReadableWifi: "Показвай Wi‑Fi кодовете разчетени",
  settingsReadableWifiSummary:
    "Име на мрежата и парола поотделно, всяко за копиране. Оригиналът също остава за копиране.",
  settingsApp: "Приложение",

  // Web only
  permissionDenied:
    "Достъпът до камерата е отказан. Разрешете го от настройките на браузъра за този сайт и презаредете страницата.",
  noCamera: "Не е намерена камера на това устройство.",
  insecureContext: "Камерата работи само през защитена връзка (https://).",
  cameraError: "Камерата не можа да бъде стартирана.",
  tryAgain: "Опитай отново",
  zoom: "Увеличение",
  settingsVersion: (version: string) => `Версия ${version}`,
  settingsDecoder: "Разпознаване",
  decoderNative: "вградено в браузъра",
  decoderZxing: "ZXing (WebAssembly)",
  decoderUnknown: "ще се определи при първото сканиране",
};
