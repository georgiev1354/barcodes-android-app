# Качване на сървъра

## 1. Build

Нужен е Node.js 20+ (проверено с 22).

```bash
cd web
npm ci          # или npm install
npm test
npm run build   # → web/dist/
```

`dist/` е изцяло статичен (HTML, JS, CSS, WASM, иконки) и не изисква сървърна логика. Път `base: "./"` във `vite.config.ts` прави всички адреси относителни, така че сайтът работи както от корена на домейна, така и от подпапка (напр. `https://example.com/barcodes/`).

Ако build-ът се прави на сървъра, копирайте цялата папка `web/` без `node_modules/` и `dist/`. Иначе е достатъчно да качите съдържанието на `dist/`.

## 2. Изисквания към сървъра

- **HTTPS е задължителен.** Браузърите дават достъп до камерата само на `https://` (и `localhost`). През HTTP приложението показва съобщение и не стартира камерата.
- `.wasm` файловете трябва да се сервират с `Content-Type: application/wasm`. nginx ≥ 1.21 и Apache го правят по подразбиране. При по-стар сървър добавете типа ръчно (вижте по-долу).
- Препоръчително е кеширане: `assets/*` имат хеш в името и могат да се кешират завинаги. `index.html`, `sw.js` и `manifest.webmanifest` не трябва да се кешират дълго, иначе новата версия се вижда със закъснение.

### Пример: nginx

```nginx
server {
    listen 443 ssl http2;
    server_name barcodes.example.com;

    ssl_certificate     /etc/letsencrypt/live/barcodes.example.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/barcodes.example.com/privkey.pem;

    root /var/www/barcodes;   # съдържанието на dist/
    index index.html;

    # mime.types от nginx ≥ 1.21 вече съдържа application/wasm. При по-стар nginx добавете
    # "application/wasm wasm;" в /etc/nginx/mime.types.

    gzip on;
    gzip_types application/wasm application/javascript text/css application/manifest+json;

    location /assets/ {
        add_header Cache-Control "public, max-age=31536000, immutable";
    }
    location ~ ^/(index\.html|sw\.js|manifest\.webmanifest)?$ {
        add_header Cache-Control "no-cache";
    }
}
```

### Пример: Apache (`.htaccess` в папката на сайта)

```apache
AddType application/wasm .wasm
AddType application/manifest+json .webmanifest
<FilesMatch "^(index\.html|sw\.js|manifest\.webmanifest)$">
    Header set Cache-Control "no-cache"
</FilesMatch>
<If "%{REQUEST_URI} =~ m#/assets/#">
    Header set Cache-Control "public, max-age=31536000, immutable"
</If>
```

## 3. Обновяване

1. Вдигнете `version` в `web/package.json` (показва се в Настройки).
2. Изпълнете `npm run build` и качете новия `dist/`, като замените стария. Старите `assets/*` могат да се изтрият.
3. Потребителите получават новата версия при следващото отваряне: service worker-ът зарежда страницата от мрежата първо (network-first), а новите assets имат нови имена.

`CACHE` в `public/sw.js` се сменя само ако искате да изчистите изцяло кеша на всички потребители.

## Политика за поверителност

`public/privacy.html` е политиката за поверителност на Android приложението за Google Play и се качва заедно със сайта (`https://<домейн>/privacy.html`). Преди качване попълнете името и имейла в нея ([../../docs/play-store/privacy-policy.md](../../docs/play-store/privacy-policy.md)).

## 4. Проверка след качване

- Отворете сайта на телефон, разрешете камерата и сканирайте стикер.
- Настройки → Разпознаване показва кой декодер се ползва („вградено в браузъра“ или „ZXing“).
- В Chrome DevTools → Application: manifest и service worker без грешки. В Network `.wasm` трябва да е с `application/wasm`. Файлът се тегли само при ZXing, затова тествайте с `?decoder=zxing`.
- В Chrome на Android менюто трябва да предлага „Инсталиране на приложението“.
