# StreamChat 🎮📱

**StreamChat** is a client-side Minecraft (Fabric) mod that integrates **TikTok Live** and **YouTube Live** chat directly into your game!

Perfect for streamers who want to communicate with their audience without getting distracted from gameplay. The mod broadcasts comments, gifts/Super Chats, likes, subscriptions/memberships, and other events in real-time right into the Minecraft chat.

[🇷🇺 Читать на русском](README_RU.md)

## 🚀 Project Overview
StreamChat creates a seamless bridge between a Minecraft streamer and viewers on TikTok and YouTube. Instead of using a second monitor or checking a phone, important chat events appear in-game.

## ⚙️ How It Works

### TikTok
Uses the unofficial `TikTokLive` library (JWDeveloper) over TikTok Live WebSockets.
1. `/tiktok connect <nickname>` (or set `tiktokUsername` in config)
2. A background thread listens for comments, gifts, likes, follows, shares, joins, subscriptions
3. A 1.5s backlog filter drops history dumped right after connect
4. Events are shown via chat / action bar / title according to `displayModes`

### YouTube
Uses the official **YouTube Data API v3** with an API key (no OAuth for public live chats).
1. Create an API key in [Google Cloud Console](https://console.cloud.google.com/) with **YouTube Data API v3** enabled
2. `/youtube apikey <YOUR_KEY>`
3. `/youtube connect <videoId|url>` while the stream is live
4. The mod resolves `activeLiveChatId`, skips the first history page, then polls new messages (comments, Super Chat, Super Stickers, memberships)

## 🛠️ Commands

| Command | Description |
|---------|-------------|
| `/tiktok connect <nickname>` | Connect to a TikTok live |
| `/tiktok disconnect` | Stop TikTok |
| `/youtube apikey <KEY>` | Save YouTube Data API key to `streamchat.json` |
| `/youtube connect <videoId\|url>` | Connect to a YouTube live chat |
| `/youtube disconnect` | Stop YouTube |

Supported YouTube targets: raw video id, `youtube.com/watch?v=…`, `youtu.be/…`, `/live/…`, `/embed/…`.

## ⚙️ Configuration (`config/streamchat.json`)
- `tiktokUsername` — TikTok host nickname
- `youtubeApiKey` — YouTube Data API v3 key
- `youtubeVideoId` — last connected YouTube video id
- `displayModes` — per-event display: `HIDDEN`, `CHAT`, `ACTION_BAR`, `TITLE` for `COMMENT`, `GIFT`, `LIKE`, `FOLLOW`, `SHARE`, `JOIN`, `SUBSCRIBE`

YouTube mapping:
- comments → `COMMENT`
- Super Chat / Super Sticker → `GIFT`
- memberships → `SUBSCRIBE`

**Security note:** the API key is stored in plain text in `streamchat.json`. Do not share that file; restrict the key in Google Cloud (API restrictions / quotas).

## 📥 Installation
1. Install [Fabric Loader](https://fabricmc.net/) for Minecraft ~26.1.2
2. Place the mod jar and Fabric API into `mods`
3. Launch the game — config is created automatically

## 👨‍💻 Author
- **AlexRahvalov** (Illdiesooon / ALEX BY-3)
- **License:** CC0-1.0
