# StreamChat 🎮📱

**StreamChat** — клиентский мод Minecraft (Fabric), который выводит чат **TikTok Live** и **YouTube Live** прямо в игру!

Подходит стримерам, которые хотят видеть комментарии, подарки/Super Chat, лайки, подписки/членства и другие события без второго монитора.

[🇺🇸 Read in English](README.md)

## 🚀 Суть проекта
Бесшовный мост между стримером в Minecraft и зрителями в TikTok / YouTube. Важные события чата показываются поверх геймплея.

## ⚙️ Принцип работы

### TikTok
Неофициальная библиотека `TikTokLive` (JWDeveloper) и WebSocket трансляции.
1. `/tiktok connect <ник>` (или `tiktokUsername` в конфиге)
2. Фоновый поток слушает комментарии, подарки, лайки, фолловы, репосты, входы, подписки
3. Фильтр бэклога ~1.5 с отсекает историю сразу после коннекта
4. События идут в чат / action bar / title по `displayModes`

### YouTube
Официальный **YouTube Data API v3** по API-ключу (для публичного live-чата OAuth не нужен).
1. Создайте ключ в [Google Cloud Console](https://console.cloud.google.com/) с включённым **YouTube Data API v3**
2. `/youtube apikey <ВАШ_КЛЮЧ>`
3. `/youtube connect <videoId|url>` пока идёт трансляция
4. Мод получает `activeLiveChatId`, пропускает первую страницу истории и дальше поллит новые сообщения (комменты, Super Chat, Super Stickers, membership)

## 🛠️ Команды

| Команда | Описание |
|---------|----------|
| `/tiktok connect <ник>` | Подключиться к TikTok Live |
| `/tiktok disconnect` | Отключить TikTok |
| `/youtube apikey <KEY>` | Сохранить API-ключ в `streamchat.json` |
| `/youtube connect <videoId\|url>` | Подключиться к YouTube Live чату |
| `/youtube disconnect` | Отключить YouTube |

Поддерживаются: video id, `youtube.com/watch?v=…`, `youtu.be/…`, `/live/…`, `/embed/…`.

## ⚙️ Конфиг (`config/streamchat.json`)
- `tiktokUsername` — ник TikTok
- `youtubeApiKey` — ключ YouTube Data API v3
- `youtubeVideoId` — id последней YouTube-трансляции
- `displayModes` — режим показа для `COMMENT`, `GIFT`, `LIKE`, `FOLLOW`, `SHARE`, `JOIN`, `SUBSCRIBE`: `HIDDEN` / `CHAT` / `ACTION_BAR` / `TITLE`

Сопоставление YouTube:
- комментарии → `COMMENT`
- Super Chat / Super Sticker → `GIFT`
- членства → `SUBSCRIBE`

**Безопасность:** ключ хранится открытым текстом в `streamchat.json`. Не публикуйте файл; ограничьте ключ в Google Cloud (API restrictions / квоты).

## 📥 Установка
1. Установите [Fabric Loader](https://fabricmc.net/) для Minecraft 26.1.2–26.3
2. Положите jar мода и Fabric API в `mods`
3. Запустите игру — конфиг создастся сам

## История версий

### 0.3.0
- Один jar на Minecraft 26.1.2–26.3. Сборка идёт на 26.3, Fabric Loader 0.19.5 и Fabric API 0.163.0+26.3. Action bar и title на 26.1.2 идут через `Gui`, на 26.2/26.3 — через `Gui.hud`.
- Подключение к TikTok больше не ждёт один недоступный адрес. Локальный прокси перебирает IPv4-адреса имени и берёт первый, который принимает соединение.
- Поток подключения живёт, пока сессия стрима реально не откроется. Если стрим не ответил за 20 секунд, в чат пишется ошибка, а не вечное «Подключение».
- Повторный `/tiktok connect` больше не пишет «Отключено» для сессии, которую команда только что остановила.
- Сбои соединения пишутся в лог игры и показываются в чате короткой фразой.

### 0.2.0
- Чат YouTube Live через YouTube Data API v3 рядом с TikTok.

## Раскрытие содержания
В проекте есть материалы, созданные или отредактированные с помощью ИИ.
- **Код, созданный или доработанный с помощью ИИ.** Часть мода, включая подключение к TikTok и совместимость HUD с Minecraft 26.1.2–26.3, написана с помощью Cursor.
- **Текст, созданный или доработанный с помощью ИИ.** Английское и русское описание проекта и перевод в этих README написаны или отредактированы с помощью Cursor.

## 👨‍💻 Автор
- **AlexRahvalov** (Illdiesooon / ALEX BY-3)
- **Лицензия:** CC0-1.0
