package by.rahvalov.streamchat.client.youtube;

import by.rahvalov.streamchat.StreamChatMod;
import by.rahvalov.streamchat.client.config.EventKind;
import by.rahvalov.streamchat.client.config.ModConfig;
import by.rahvalov.streamchat.client.display.StreamEventPresenter;

public class YouTubeLiveManager {

    private static final Object LOCK = new Object();
    private static final String PLATFORM = "YouTube";
    private static final long STOP_JOIN_MILLIS = 5000L;

    private static final YouTubeApiClient API = new YouTubeApiClient();

    private static volatile Thread liveThread;
    private static StopFlag stopFlag = new StopFlag();

    private static final class StopFlag {
        volatile boolean stop;
    }

    public static boolean isRunning() {
        Thread thread = liveThread;
        return thread != null && thread.isAlive();
    }

    public static void start() {
        ModConfig config = ModConfig.getInstance();
        String apiKey = config.youtubeApiKey;
        String target = config.youtubeVideoId;
        if (apiKey == null || apiKey.isBlank()) {
            return;
        }
        if (target == null || target.isBlank()) {
            return;
        }

        var videoIdOpt = API.extractVideoId(target);
        if (videoIdOpt.isEmpty()) {
            StreamEventPresenter.systemMessage("§c[YouTube] Не удалось распознать video id / URL.");
            return;
        }
        final String videoId = videoIdOpt.get();
        final String key = apiKey.trim();

        synchronized (LOCK) {
            if (isRunning()) {
                StreamChatMod.LOGGER.warn("YouTube live client already running; call stop()/restart() first");
                return;
            }
            stopFlag = new StopFlag();
            final StopFlag flag = stopFlag;
            liveThread = new Thread(() -> runLoop(key, videoId, flag), "YouTubeLive-Thread");
            liveThread.setDaemon(true);
            liveThread.start();
        }
    }

    public static void stop() {
        stopInternal(false);
    }

    public static void restart() {
        Thread worker = new Thread(() -> {
            stopInternal(true);
            start();
        }, "YouTubeLive-Restart");
        worker.setDaemon(true);
        worker.start();
    }

    private static void stopInternal(boolean awaitThread) {
        Thread toJoin;
        synchronized (LOCK) {
            stopFlag.stop = true;
            toJoin = liveThread;
            if (!awaitThread && toJoin != null && !toJoin.isAlive()) {
                liveThread = null;
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
                            StreamChatMod.LOGGER.warn(
                                "YouTube live thread did not exit within {}ms; refusing to clear handle",
                                STOP_JOIN_MILLIS
                            );
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

    private static void runLoop(String apiKey, String videoId, StopFlag flag) {
        final Thread self = Thread.currentThread();
        boolean connected = false;
        try {
            String liveChatId = API.fetchActiveLiveChatId(apiKey, videoId);
            if (flag.stop || Thread.interrupted()) {
                return;
            }
            connected = true;
            StreamEventPresenter.systemMessage("§a[YouTube] Подключено к чату трансляции " + videoId);

            String pageToken = null;
            boolean skipHistory = true;

            while (!flag.stop && !Thread.currentThread().isInterrupted()) {
                YouTubeApiClient.ChatPage page = API.fetchChatPage(apiKey, liveChatId, pageToken);
                pageToken = page.nextPageToken();

                if (!skipHistory) {
                    for (YouTubeApiClient.ChatMessage message : page.messages()) {
                        if (flag.stop) {
                            break;
                        }
                        emit(message);
                    }
                } else {
                    skipHistory = false;
                }

                try {
                    Thread.sleep(page.pollingIntervalMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            StreamChatMod.LOGGER.error("YouTubeLive Error", e);
            StreamEventPresenter.systemMessage("§c[YouTube] Ошибка: " + safeMessage(e));
        } finally {
            if (connected) {
                StreamEventPresenter.systemMessage("§c[YouTube] Отключено от стрима.");
            }
            synchronized (LOCK) {
                if (liveThread == self) {
                    liveThread = null;
                }
            }
        }
    }

    private static void emit(YouTubeApiClient.ChatMessage message) {
        String author = sanitize(message.authorName());
        String text = sanitize(message.text());

        switch (message.kind()) {
            case COMMENT -> present(
                EventKind.COMMENT,
                "§b" + author + "§f: " + text
            );
            case SUPER_CHAT -> present(
                EventKind.GIFT,
                "§d" + author + " Super Chat " + text
            );
            case SUPER_STICKER -> present(
                EventKind.GIFT,
                "§d" + author + " Super Sticker " + text
            );
            case MEMBERSHIP -> present(
                EventKind.SUBSCRIBE,
                "§6" + author + (text.isEmpty() ? " оформил(а) членство канала!" : " §f" + text)
            );
        }
    }

    private static void present(EventKind kind, String body) {
        StreamEventPresenter.present(PLATFORM, ModConfig.getInstance().modeFor(kind), body);
    }

    private static String sanitize(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('§', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null || message.isBlank() ? e.getClass().getSimpleName() : message;
    }
}
