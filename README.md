# StreamChat 🎮📱

**StreamChat** is a client-side Minecraft (Fabric) mod that integrates TikTok Live chat directly into your game!

Perfect for streamers who want to communicate with their TikTok audience without getting distracted from the gameplay. The mod broadcasts comments, gifts, likes, subscriptions, and new viewer information in real-time right to the Minecraft in-game chat.

[🇷🇺 Читать на русском](README_RU.md)

## 🚀 Project Overview
The core idea of StreamChat is to create a seamless bridge between a Minecraft streamer and their TikTok viewers. Instead of using a second monitor or getting distracted by checking a phone to read the chat, the streamer can see all important events right in the game chat, overlaid on top of the gameplay.

## ⚙️ How It Works
The mod uses the unofficial `TikTokLive` library (by JWDeveloper) to connect to TikTok Live WebSockets.
1. **Connection:** You enter your TikTok username in the configuration or use the `/tiktok connect <your_nickname>` command.
2. **Background Process:** The mod launches a separate lightweight thread that connects to TikTok servers and starts listening for events (messages, gifts, likes, etc.).
3. **Smart Filtering:** A built-in "history filter" ignores old events (backlog) that arrive within the first 1.5 seconds after connecting, ensuring your in-game chat isn't spammed with old messages.
4. **Game Integration:** All new events that are enabled in the settings are converted into Minecraft text components (`Component`) with appropriate color formatting (e.g., gold for paid subscriptions, pink for gifts) and are sent directly to the local player (`mc.player.sendSystemMessage`).

## 🛠️ Features:
- **Quick Controls:** Use the `/tiktok connect <your_nickname>` command to start and `/tiktok disconnect` to stop the event broadcast.
- **Flexible Configuration:** Full control over what you want to see. You can specifically enable or disable the display of:
  - Comments
  - Gifts
  - Likes
  - New Followers
  - Shares
  - New Viewers (Joins)
  - Superfan Subscriptions
- **Full Localization:** The mod integrates naturally with Minecraft's chat style.

## 📥 Installation
1. Install [Fabric Loader](https://fabricmc.net/) for your Minecraft version (version ~26.1.2 is supported).
2. Place the compiled `.jar` file of the mod (as well as necessary dependencies, including Fabric API) into the `mods` folder of your game.
3. Launch the game. The configuration file will be created automatically, after which you can customize the mod to your liking.

## 👨‍💻 Author
- **AlexRahvalov** (Illdiesooon / ALEX BY-3)
- **License:** CC0-1.0
