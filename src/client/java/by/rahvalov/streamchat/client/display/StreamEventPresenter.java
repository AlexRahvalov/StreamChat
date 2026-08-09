package by.rahvalov.streamchat.client.display;

import by.rahvalov.streamchat.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Routes stream event text to Minecraft chat / action bar / title.
 * Expects a prefix-free body; chat prefix is applied only for {@link ModConfig.DisplayMode#CHAT}.
 */
public final class StreamEventPresenter {
    private StreamEventPresenter() {
    }

    public static void present(String platform, ModConfig.DisplayMode mode, String body) {
        if (mode == null || mode == ModConfig.DisplayMode.HIDDEN) {
            return;
        }
        final String tag = platform == null || platform.isBlank() ? "Stream" : platform;
        final String text = body == null ? "" : body;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }

        mc.execute(() -> {
            var player = mc.player;
            var gui = mc.gui;
            if (player == null) {
                return;
            }

            switch (mode) {
                case CHAT -> player.sendSystemMessage(Component.literal("§f[" + tag + "] " + text));
                case ACTION_BAR -> {
                    if (gui != null) {
                        gui.setOverlayMessage(Component.literal(text), false);
                    }
                }
                case TITLE -> {
                    if (gui != null) {
                        // Do not call setTimes — it mutates global title timings for vanilla titles too.
                        gui.setTitle(Component.literal(text));
                        gui.setSubtitle(Component.empty());
                    }
                }
                case HIDDEN -> {
                    // unreachable after early return
                }
            }
        });
    }

    /** Lifecycle / status lines (already fully formatted for chat). */
    public static void systemMessage(String message) {
        final String text = message == null ? "" : message;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) {
            return;
        }
        mc.execute(() -> {
            var player = mc.player;
            if (player != null) {
                player.sendSystemMessage(Component.literal(text));
            }
        });
    }
}
