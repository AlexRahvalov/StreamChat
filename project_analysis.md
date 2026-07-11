# 📊 Анализ проекта: StreamChat

## Общее описание

**StreamChat** — клиентский мод для Minecraft на платформе **Fabric**, который транслирует события TikTok Live (чат, лайки, подарки, подписки и т.д.) прямо в игровой чат Minecraft.

| Параметр | Значение |
|---|---|
| Minecraft | `26.1.2` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.150.0+26.1.2` |
| Java | `25` |
| Лицензия | CC0-1.0 |
| Версия мода | `1.1.0` |
| Автор | AlexRahvalov |

---

## 🏗️ Архитектура проекта

```
StreamChat/
├── src/
│   ├── main/                         ← Серверная сторона (точка входа + пустой mixin)
│   │   ├── java/.../StreamChatMod.java
│   │   └── java/.../mixin/StreamChatMixin.java
│   └── client/                       ← Вся логика (клиентская сторона)
│       └── java/.../client/
│           ├── StreamChatModClient.java     ← Точка входа клиента
│           ├── config/ModConfig.java        ← Конфигурация (Gson + JSON)
│           ├── command/TikTokCommand.java   ← /tiktok connect|disconnect
│           └── tiktok/TikTokLiveManager.java ← Ядро интеграции TikTok
```

### Поток инициализации
```
onInitializeClient()
  ├── ModConfig.load()           ← Загружает streamchat.json
  ├── TikTokCommand.register()   ← Регистрирует /tiktok команды
  └── TikTokLiveManager.start()  ← Запускает фоновый поток TikTokLive-Thread
```

---

## 📦 Зависимости (build.gradle)

| Библиотека | Версия | Назначение |
|---|---|---|
| `TikTok-Live-Java:Client` | `1.11.0-Release` | TikTok WebSocket клиент |
| `TikTok-Live-Java:API` | `1.11.0-Release` | API модели событий |
| `DepenDance-Full` | `0.0.18-Release` | DI-контейнер для TikTokLive |
| `DepenDance:Injector` | `0.0.18-Release` | Инъекция зависимостей |
| `DepenDance:Decorator` | `0.0.18-Release` | Декораторы |
| `Java-WebSocket` | `1.5.5` | WebSocket транспорт |
| `protobuf-java` | `4.26.1` | Протокол сериализации TikTok |

Все зависимости включены в JAR через директиву `include` (fat-jar подход).

---

## 🔍 Анализ ключевых классов

### `TikTokLiveManager.java` — Ядро мода
- Подключается через `TikTokLive.newClient()` в отдельном потоке `TikTokLive-Thread`
- Поддерживает 8 типов событий: `onConnected`, `onDisconnected`, `onError`, `onComment`, `onGift`, `onLike`, `onFollow`, `onShare`, `onJoin`, `onSubscribe`
- **Фильтр бэклога:** события первых 1500ms после подключения игнорируются
- Безопасная отправка в UI через `mc.execute()` (главный поток Minecraft)

### `ModConfig.java` — Конфигурация
- Использует **Gson** для сериализации/десериализации JSON
- Файл: `%config_dir%/streamchat.json`
- 7 булевых переключателей отображения событий + TikTok никнейм

### `TikTokCommand.java` — Команды
- `/tiktok connect <username>` — сохраняет ник, перезапускает соединение
- `/tiktok disconnect` — разрывает соединение

### `StreamChatMixin.java` / `StreamChatClientMixin.java`
- Стандартные шаблонные миксины из Fabric template — **пустые**, не используются реально

### `StreamChatMod.java` (main)
- MOD_ID жёстко задан как `"modid"` — **несоответствие** с реальным ID `"streamchat"` из `fabric.mod.json`

---

## ⚠️ Выявленные проблемы

### 🔴 Критические

| # | Файл | Проблема |
|---|---|---|
| 1 | `StreamChatMod.java:9` | `MOD_ID = "modid"` — должно быть `"streamchat"`. Logger пишет в неправильную категорию |
| 2 | `TikTokLiveManager.java:75` | `e.printStackTrace()` вместо нормального логирования через `LOGGER` |

### 🟡 Значительные

| # | Файл | Проблема |
|---|---|---|
| 3 | `TikTokLiveManager.java` | `client` и `connectionStartTime` — **статические поля без синхронизации** (`volatile`/`synchronized`). Возможны race condition при быстром `/tiktok connect` + `/tiktok disconnect` |
| 4 | `TikTokLiveManager.java:80` | `stop()` зануляет `client = null`, но старый поток `TikTokLive-Thread` продолжает работать до реального отключения. Нет join/await завершения |
| 5 | `TikTokCommand.java:21` | Сообщение `"Connecting to"` на английском, а все игровые сообщения на русском — **несогласованность языка** |
| 6 | `ModConfig.java:40,54` | Использует `System.err.println` вместо LOGGER |
| 7 | `build.gradle:12` | Версия мода генерируется как `yyMMdd-HHmm-SNAPSHOT` вместо `mod_version` из `gradle.properties` |

### 🟢 Мелкие / улучшения

| # | Файл | Проблема |
|---|---|---|
| 8 | `StreamChatMixin.java` / `StreamChatClientMixin.java` | Пустые миксины от шаблона — можно удалить |
| 9 | `fabric.mod.json:16` | `"environment": "*"` — мод полностью клиентский, должно быть `"client"` |
| 10 | `ModConfig.java:16` | `INSTANCE` — не `final`, может быть заменён снаружи. Лучше сделать `private static` с геттером |
| 11 | Нет | Нет обработки случая, когда игрок **выходит из мира**, а TikTok соединение остаётся активным |
| 12 | `build.gradle:71,81` | Java 25 — пре-релизная версия на момент написания. Возможны проблемы совместимости |

---

## 📐 Схема архитектуры

```
[Minecraft Client]
       │
       ▼
StreamChatModClient.onInitializeClient()
       │
       ├──► ModConfig.load() ──► streamchat.json
       │
       ├──► TikTokCommand.register()
       │         └── /tiktok connect <nick> ──► TikTokLiveManager.start()
       │         └── /tiktok disconnect     ──► TikTokLiveManager.stop()
       │
       └──► TikTokLiveManager.start()
                  │
                  ▼
         [TikTokLive-Thread]
                  │
                  ▼
         TikTokLive.newClient() ──WebSocket──► TikTok Servers
                  │
                  ▼
         Event Handlers (Comment/Gift/Like/Follow/Share/Join/Subscribe)
                  │
                  ▼ mc.execute()
         mc.player.sendSystemMessage(Component.literal(...))
```

---

## ✅ Сильные стороны

- **Чистая архитектура**: логика чётко разделена на config / command / tiktok пакеты
- **Правильный threading**: сетевой поток отделён от UI-потока Minecraft через `mc.execute()`
- **Умный фильтр бэклога** — хорошее UX-решение
- **fat-jar через `include`** — пользователю не нужно устанавливать зависимости отдельно
- **Локализация** сообщений на русском для русскоязычной аудитории

---

## 🚀 Рекомендации по развитию

1. **Исправить `MOD_ID`** — привести к `"streamchat"`
2. **Добавить `volatile`** к `client` и `connectionStartTime`
3. **Унифицировать язык** сообщений (или добавить i18n через `.lang` файлы)
4. **Добавить команду `/tiktok status`** — показывает текущее состояние соединения
5. **Обработка выхода из мира** — отключать TikTok при `ClientLifecycleEvents.CLIENT_STOPPING`
6. **Переключить `environment`** в `fabric.mod.json` на `"client"`
7. **GUI для настроек** через Mod Menu / Screen API вместо ручного редактирования JSON
