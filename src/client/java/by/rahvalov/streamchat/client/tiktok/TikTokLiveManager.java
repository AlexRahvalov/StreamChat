package by.rahvalov.streamchat.client.tiktok;

import by.rahvalov.streamchat.StreamChatMod;
import by.rahvalov.streamchat.client.config.EventKind;
import by.rahvalov.streamchat.client.config.ModConfig;
import by.rahvalov.streamchat.client.display.StreamEventPresenter;
import io.github.jwdeveloper.tiktok.TikTokLive;
import io.github.jwdeveloper.tiktok.data.settings.ProxyClientSettings;
import io.github.jwdeveloper.tiktok.live.LiveClient;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.ProxySelector;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class TikTokLiveManager {

    private static final Object LOCK = new Object();
    private static final String PLATFORM = "TikTok";
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
        // connect() returns as soon as the socket thread starts. onConnected arrives later.
        CountDownLatch opened = new CountDownLatch(1);
        CountDownLatch closed = new CountDownLatch(1);
        AtomicBoolean accepted = new AtomicBoolean();
        try {
            int proxyPort = TikTokFailoverProxy.port();
            var live = TikTokLive.newClient(host)
                .configure(settings -> {
                    // Whole seconds: the library feeds only Duration.toMillisPart() to the
                    // websocket, so a fractional timeout would shrink that connect to <1s.
                    var http = settings.getHttpSettings();
                    http.setTimeout(Duration.ofSeconds(15));
                    // Java's client keeps a single resolved address and waits out the
                    // whole timeout when that address is dead. The local proxy retries
                    // the hostname's other IPv4 addresses.
                    http.onClientCreating(builder -> builder.proxy(
                        ProxySelector.of(new InetSocketAddress("127.0.0.1", proxyPort))
                    ));
                    ProxyClientSettings proxy = http.getProxyClientSettings();
                    proxy.setEnabled(true);
                    proxy.setType(Proxy.Type.HTTP);
                    proxy.setAllowWebsocket(true);
                    proxy.addProxy("127.0.0.1", proxyPort);
                })
                .onConnected((liveClient, event) -> {
                    synchronized (LOCK) {
                        if (liveThread != self) {
                            try {
                                liveClient.disconnect();
                            } catch (Exception ignored) {
                                // superseded
                            }
                            opened.countDown();
                            closed.countDown();
                            return;
                        }
                        client = liveClient;
                        connectionStartTime = System.currentTimeMillis();
                    }
                    accepted.set(true);
                    opened.countDown();
                    StreamEventPresenter.systemMessage("§a[TikTok] Успешно подключено к стриму " + host + "!");
                })
                .onDisconnected((liveClient, event) -> {
                    opened.countDown();
                    closed.countDown();
                    synchronized (LOCK) {
                        // stop()/restart() clears client first. That disconnect is not a drop.
                        if (client != liveClient) {
                            return;
                        }
                        connectionStartTime = 0;
                    }
                    StreamEventPresenter.systemMessage("§c[TikTok] Отключено от стрима.");
                })
                .onError((liveClient, event) -> {
                    Throwable error = event.getException();
                    StreamChatMod.LOGGER.error("TikTok connection error", error);
                    StreamEventPresenter.systemMessage("§c[TikTok] Ошибка: " + describe(error));
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
            if (!opened.await(20, TimeUnit.SECONDS) || !accepted.get()) {
                StreamEventPresenter.systemMessage("§c[TikTok] Ошибка: стрим не ответил за 20 секунд.");
                try {
                    live.disconnect();
                } catch (Exception ignored) {
                    // already closed
                }
                closed.await(5, TimeUnit.SECONDS);
            } else {
                closed.await();
            }
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
        StreamEventPresenter.present(PLATFORM, ModConfig.getInstance().modeFor(kind), body);
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "unknown";
        }
        String message = error.getMessage();
        Throwable cursor = error;
        while (cursor.getCause() != null && cursor.getCause() != cursor) {
            cursor = cursor.getCause();
            if (cursor.getMessage() != null) {
                message = cursor.getMessage();
            }
        }
        if (message != null && message.toLowerCase(Locale.ROOT).contains("timed out")) {
            return "сервер TikTok не ответил. С этой сети часть адресов TikTok недоступна.";
        }
        if (message != null && message.contains("502")) {
            return "адрес стрима TikTok не ответил. Повтори подключение.";
        }
        return message == null ? error.getClass().getSimpleName() : message;
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        // Strip section-sign formatting / control chars from untrusted TikTok strings.
        return value.replace('§', ' ').replace('\n', ' ').replace('\r', ' ');
    }
}
