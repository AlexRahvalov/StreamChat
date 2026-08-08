package by.rahvalov.streamchat.client.tiktok;

import by.rahvalov.streamchat.client.config.ModConfig;
import by.rahvalov.streamchat.StreamChatMod;
import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.live.LiveClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class TikTokLiveManager {

    private static volatile LiveClient client;
    private static volatile Thread liveThread;
    private static volatile long connectionStartTime = 0;

    private static boolean isRecentEvent() {
        // Ignore events received within the first 1.5 seconds of connecting (backlog)
        return connectionStartTime > 0 && (System.currentTimeMillis() - connectionStartTime) > 1500;
    }

    public static void start() {
        if (ModConfig.getInstance().tiktokUsername == null || ModConfig.getInstance().tiktokUsername.trim().isEmpty()) {
            return; // No username configured
        }

        liveThread = new Thread(() -> {
            try {
                client = TikTokLive.newClient(ModConfig.getInstance().tiktokUsername)
                    .onConnected((liveClient, event) -> {
                        connectionStartTime = System.currentTimeMillis();
                        sendMessage("§a[TikTok] Успешно подключено к стриму " + ModConfig.getInstance().tiktokUsername + "!");
                    })
                    .onDisconnected((liveClient, event) -> {
                        connectionStartTime = 0;
                        sendMessage("§c[TikTok] Отключено от стрима.");
                    })
                    .onError((liveClient, event) -> {
                        sendMessage("§c[TikTok] Ошибка: " + event.getException().getMessage());
                    })
                    .onComment((liveClient, event) -> {
                        if (isRecentEvent()) {
                            displayEvent("§f[TikTok] §b" + event.getUser().getProfileName() + "§f: " + event.getText(), ModConfig.getInstance().displayComments);
                        }
                    })
                    .onGift((liveClient, event) -> {
                        if (isRecentEvent()) {
                            displayEvent("§f[TikTok] §d" + event.getUser().getProfileName() + " отправил(а) " + event.getGift().getName() + " x" + event.getCombo(), ModConfig.getInstance().displayGifts);
                        }
                    })
                    .onLike((liveClient, event) -> {
                        if (isRecentEvent()) {
                            displayEvent("§f[TikTok] §c" + event.getUser().getProfileName() + " поставил(-а) лайк трансляции", ModConfig.getInstance().displayLikes);
                        }
                    })
                    .onFollow((liveClient, event) -> {
                        if (isRecentEvent()) {
                            displayEvent("§f[TikTok] §e" + event.getUser().getProfileName() + " теперь подписчик", ModConfig.getInstance().displayNewFollowers);
                        }
                    })
                    .onShare((liveClient, event) -> {
                        if (isRecentEvent()) {
                            displayEvent("§f[TikTok] §9" + event.getUser().getProfileName() + " поделился(лась) трансляцией", ModConfig.getInstance().displayShares);
                        }
                    })
                    .onJoin((liveClient, event) -> {
                        if (isRecentEvent()) {
                            displayEvent("§f[TikTok] §7" + event.getUser().getProfileName() + " теперь с нами", ModConfig.getInstance().displayNewViewers);
                        }
                    })
                    .onSubscribe((liveClient, event) -> {
                        if (isRecentEvent()) {
                            displayEvent("§f[TikTok] §6" + event.getUser().getProfileName() + " оформил(а) платную подписку!", ModConfig.getInstance().displaySuperfanSubscriptions);
                        }
                    })
                    .buildAndConnect();
            } catch (Exception e) {
                StreamChatMod.LOGGER.error("TikTokLive Error", e);
            }
        }, "TikTokLive-Thread");
        liveThread.setDaemon(true);
        liveThread.start();
    }

    public static void stop() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
        // Wait for the background thread to fully terminate before allowing
        // a new connection to start — prevents two clients from being alive
        // simultaneously and delivering duplicate events to the game chat.
        if (liveThread != null && liveThread.isAlive()) {
            try {
                liveThread.join(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
            liveThread = null;
        }
    }

    private static void sendMessage(String message) {
        if (message == null) message = "";
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            var player = mc.player;
            if (player != null) {
                final String finalMessage = message;
                mc.execute(() -> player.sendSystemMessage(Component.literal(finalMessage)));
            }
        }
    }

    private static void displayEvent(String message, ModConfig.DisplayMode mode) {
        if (mode == ModConfig.DisplayMode.HIDDEN) return;
        if (message == null) message = "";
        Minecraft mc = Minecraft.getInstance();
        if (mc != null) {
            var player = mc.player;
            var gui = mc.gui;
            if (player != null) {
                String processedMessage = message;
                if (mode == ModConfig.DisplayMode.ACTION_BAR || mode == ModConfig.DisplayMode.TITLE) {
                    processedMessage = processedMessage.replaceFirst("^§.\\[TikTok\\] ", "");
                }
                final String finalMessage = processedMessage;
                mc.execute(() -> {
                    Component text = Component.literal(finalMessage);
                    if (mode == ModConfig.DisplayMode.CHAT) {
                        player.sendSystemMessage(text);
                    } else if (mode == ModConfig.DisplayMode.ACTION_BAR && gui != null) {
                        gui.setOverlayMessage(text, false);
                    } else if (mode == ModConfig.DisplayMode.TITLE && gui != null) {
                        gui.setTimes(10, 70, 20); 
                        gui.setTitle(text);
                        gui.setSubtitle(Component.empty());
                    }
                });
            }
        }
    }
}
