package by.rahvalov.streamchat.client.tiktok;

import by.rahvalov.streamchat.StreamChatMod;
import by.rahvalov.streamchat.client.config.EventKind;
import by.rahvalov.streamchat.client.config.ModConfig;
import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.live.LiveClient;

public class TikTokLiveManager {

    private static final Object LOCK = new Object();
    private static final long BACKLOG_MILLIS = 1500L;
    private static final long STOP_JOIN_MILLIS = 5000L;

    private static volatile LiveClient client;
    private static volatile Thread liveThread;
    private static volatile long connectionStartTime = 0;

    private static boolean isPastBacklogWindow() {
        return connectionStartTime > 0 && (System.currentTimeMillis() - connectionStartTime) > BACKLOG_MILLIS;
    }

    public static boolean isRunning() {
        Thread thread = liveThread;
        return thread != null && thread.isAlive();
    }

    public static void start() {
        String username = ModConfig.getInstance().tiktokUsername;
        if (username == null || username.trim().isEmpty()) {
            return;
        }
        final String host = username.trim();

        synchronized (LOCK) {
            if (isRunning()) {
                StreamChatMod.LOGGER.warn("TikTok live client already running; call stop()/restart() first");
                return;
            }

            liveThread = new Thread(() -> runClient(host), "TikTokLive-Thread");
            liveThread.setDaemon(true);
            liveThread.start();
        }
    }

    /**
     * Disconnect without blocking the caller (safe on the Minecraft client thread).
     * If the background thread is still alive, {@link #start()} will refuse until it exits
     * or {@link #restart()} awaits it off-thread.
     */
    public static void stop() {
        stopInternal(false);
    }

    /**
     * Stop on a worker thread (with join), then start again. Use for /tiktok connect.
     */
    public static void restart() {
        Thread worker = new Thread(() -> {
            stopInternal(true);
            start();
        }, "TikTokLive-Restart");
        worker.setDaemon(true);
        worker.start();
    }

    private static void stopInternal(boolean awaitThread) {
        LiveClient toDisconnect;
        Thread toJoin;

        synchronized (LOCK) {
            toDisconnect = client;
            client = null;
            connectionStartTime = 0;
            toJoin = liveThread;
            if (!awaitThread) {
                // Keep liveThread reference while alive so start() cannot race a duplicate client.
                if (toJoin != null && !toJoin.isAlive()) {
                    liveThread = null;
                }
            }
        }

        if (toDisconnect != null) {
            try {
                toDisconnect.disconnect();
            } catch (Exception e) {
                StreamChatMod.LOGGER.warn("Error disconnecting TikTok client: {}", e.getMessage());
            }
        }

        if (toJoin != null && toJoin.isAlive()) {
            toJoin.interrupt();
            if (awaitThread) {
                try {
                    toJoin.join(STOP_JOIN_MILLIS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                synchronized (LOCK) {
                    if (liveThread == toJoin) {
                        if (toJoin.isAlive()) {
                            StreamChatMod.LOGGER.warn("TikTok live thread did not exit within {}ms; refusing to clear handle", STOP_JOIN_MILLIS);
                        } else {
                            liveThread = null;
                        }
                    }
                }
            }
        } else if (awaitThread) {
            synchronized (LOCK) {
                if (liveThread == toJoin) {
                    liveThread = null;
                }
            }
        }
    }

    private static void runClient(String host) {
        final Thread self = Thread.currentThread();
        try {
            TikTokLive.newClient(host)
                .onConnected((liveClient, event) -> {
                    synchronized (LOCK) {
                        if (liveThread != self) {
                            try {
                                liveClient.disconnect();
                            } catch (Exception ignored) {
                                // superseded
                            }
                            return;
                        }
                        client = liveClient;
                        connectionStartTime = System.currentTimeMillis();
                    }
                    TikTokEventPresenter.systemMessage("§a[TikTok] Успешно подключено к стриму " + host + "!");
                })
                .onDisconnected((liveClient, event) -> {
                    connectionStartTime = 0;
                    TikTokEventPresenter.systemMessage("§c[TikTok] Отключено от стрима.");
                })
                .onError((liveClient, event) -> {
                    String detail = event.getException() != null ? event.getException().getMessage() : "unknown";
                    TikTokEventPresenter.systemMessage("§c[TikTok] Ошибка: " + detail);
                })
                .onComment((liveClient, event) -> emit(
                    EventKind.COMMENT,
                    "§b" + sanitize(event.getUser().getProfileName()) + "§f: " + sanitize(event.getText())
                ))
                .onGift((liveClient, event) -> emit(
                    EventKind.GIFT,
                    "§d" + sanitize(event.getUser().getProfileName()) + " отправил(а) "
                        + sanitize(event.getGift().getName()) + " x" + event.getCombo()
                ))
                .onLike((liveClient, event) -> emit(
                    EventKind.LIKE,
                    "§c" + sanitize(event.getUser().getProfileName()) + " поставил(-а) лайк трансляции"
                ))
                .onFollow((liveClient, event) -> emit(
                    EventKind.FOLLOW,
                    "§e" + sanitize(event.getUser().getProfileName()) + " теперь подписчик"
                ))
                .onShare((liveClient, event) -> emit(
                    EventKind.SHARE,
                    "§9" + sanitize(event.getUser().getProfileName()) + " поделился(лась) трансляцией"
                ))
                .onJoin((liveClient, event) -> emit(
                    EventKind.JOIN,
                    "§7" + sanitize(event.getUser().getProfileName()) + " теперь с нами"
                ))
                .onSubscribe((liveClient, event) -> emit(
                    EventKind.SUBSCRIBE,
                    "§6" + sanitize(event.getUser().getProfileName()) + " оформил(а) платную подписку!"
                ))
                .buildAndConnect();
        } catch (Exception e) {
            StreamChatMod.LOGGER.error("TikTokLive Error", e);
        } finally {
            synchronized (LOCK) {
                if (liveThread == self) {
                    client = null;
                    connectionStartTime = 0;
                    liveThread = null;
                }
            }
        }
    }

    private static void emit(EventKind kind, String body) {
        if (!isPastBacklogWindow()) {
            return;
        }
        TikTokEventPresenter.present(ModConfig.getInstance().modeFor(kind), body);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        // Strip section-sign formatting / control chars from untrusted TikTok strings.
        return value.replace('§', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
