package by.rahvalov.streamchat.client.tiktok;

import by.rahvalov.streamchat.client.config.ModConfig;
import by.rahvalov.streamchat.StreamChatMod;
import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.live.LiveClient;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

public class TikTokLiveManager {

    private static volatile LiveClient client;
    private static volatile long connectionStartTime = 0;

    private static boolean isRecentEvent() {
        // Ignore events received within the first 1.5 seconds of connecting (backlog)
        return connectionStartTime > 0 && (System.currentTimeMillis() - connectionStartTime) > 1500;
    }

    public static void start() {
        if (ModConfig.getInstance().tiktokUsername == null || ModConfig.getInstance().tiktokUsername.trim().isEmpty()) {
            return; // No username configured
        }

        new Thread(() -> {
            try {
                client = TikTokLive.newClient(ModConfig.getInstance().tiktokUsername)
                    .onConnected((client, event) -> {
                        connectionStartTime = System.currentTimeMillis();
                        sendMessage("§a[TikTok] Успешно подключено к стриму " + ModConfig.getInstance().tiktokUsername + "!");
                    })
                    .onDisconnected((client, event) -> {
                        connectionStartTime = 0;
                        sendMessage("§c[TikTok] Отключено от стрима.");
                    })
                    .onError((client, event) -> {
                        sendMessage("§c[TikTok] Ошибка: " + event.getException().getMessage());
                    })
                    .onComment((client, event) -> {
                        if (isRecentEvent() && ModConfig.getInstance().showComments) {
                            sendMessage("§f[TikTok] §b" + event.getUser().getProfileName() + "§f: " + event.getText());
                        }
                    })
                    .onGift((client, event) -> {
                        if (isRecentEvent() && ModConfig.getInstance().showGifts) {
                            sendMessage("§f[TikTok] §d" + event.getUser().getProfileName() + " отправил(а) " + event.getGift().getName() + " x" + event.getCombo());
                        }
                    })
                    .onLike((client, event) -> {
                        if (isRecentEvent() && ModConfig.getInstance().showLikes) {
                            sendMessage("§f[TikTok] §c" + event.getUser().getProfileName() + " поставил(-а) лайк трансляции");
                        }
                    })
                    .onFollow((client, event) -> {
                        if (isRecentEvent() && ModConfig.getInstance().showNewFollowers) {
                            sendMessage("§f[TikTok] §e" + event.getUser().getProfileName() + " теперь подписчик");
                        }
                    })
                    .onShare((client, event) -> {
                        if (isRecentEvent() && ModConfig.getInstance().showShares) {
                            sendMessage("§f[TikTok] §9" + event.getUser().getProfileName() + " поделился(лась) трансляцией");
                        }
                    })
                    .onJoin((client, event) -> {
                        if (isRecentEvent() && ModConfig.getInstance().showNewViewers) {
                            sendMessage("§f[TikTok] §7" + event.getUser().getProfileName() + " теперь с нами");
                        }
                    })
                    .onSubscribe((client, event) -> {
                        if (isRecentEvent() && ModConfig.getInstance().showSuperfanSubscriptions) {
                            sendMessage("§f[TikTok] §6" + event.getUser().getProfileName() + " оформил(а) платную подписку!");
                        }
                    })
                    .buildAndConnect();
            } catch (Exception e) {
                StreamChatMod.LOGGER.error("TikTokLive Error", e);
            }
        }, "TikTokLive-Thread").start();
    }

    public static void stop() {
        if (client != null) {
            client.disconnect();
            client = null;
        }
    }

    private static void sendMessage(String message) {
        Minecraft mc = Minecraft.getInstance();
        if (mc != null && mc.player != null) {
            mc.execute(() -> mc.player.sendSystemMessage(Component.literal(message)));
        }
    }
}
