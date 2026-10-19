package by.rahvalov.streamchat.client.display;

import by.rahvalov.streamchat.StreamChatMod;
import by.rahvalov.streamchat.client.config.ModConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Routes stream event text to Minecraft chat / action bar / title.
 * Expects a prefix-free body; chat prefix is applied only for {@link ModConfig.DisplayMode#CHAT}.
 * Action bar and title moved from {@code Gui} to {@code Gui.hud} in 26.2/26.3, so those calls are resolved at runtime.
 */
public final class StreamEventPresenter {
    private static final Field HUD;
    private static final Method SET_OVERLAY;
    private static final Method SET_TITLE;
    private static final Method SET_SUBTITLE;

    static {
        Field hud = null;
        Method overlay;
        Method title;
        Method subtitle;
        try {
            hud = Gui.class.getField("hud");
            Class<?> type = hud.getType();
            overlay = type.getMethod("setOverlayMessage", Component.class, boolean.class);
            title = type.getMethod("setTitle", Component.class);
            subtitle = type.getMethod("setSubtitle", Component.class);
        } catch (NoSuchFieldException ignored) {
            try {
                overlay = Gui.class.getMethod("setOverlayMessage", Component.class, boolean.class);
                title = Gui.class.getMethod("setTitle", Component.class);
                subtitle = Gui.class.getMethod("setSubtitle", Component.class);
            } catch (NoSuchMethodException e) {
                throw new ExceptionInInitializerError(e);
            }
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }
        HUD = hud;
        SET_OVERLAY = overlay;
        SET_TITLE = title;
        SET_SUBTITLE = subtitle;
        StreamChatMod.LOGGER.info("Stream event HUD target: {}", hud == null ? "Gui" : "Gui.hud");
    }

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
            if (player == null || gui == null) {
                return;
            }

            switch (mode) {
                case CHAT -> player.sendSystemMessage(Component.literal("§f[" + tag + "] " + text));
                case ACTION_BAR -> showOverlay(gui, text);
                case TITLE -> showTitle(gui, text);
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

    private static void showOverlay(Gui gui, String text) {
        try {
            SET_OVERLAY.invoke(hudTarget(gui), Component.literal(text), false);
        } catch (ReflectiveOperationException e) {
            StreamChatMod.LOGGER.error("Failed to show stream action bar", e);
        }
    }

    private static void showTitle(Gui gui, String text) {
        try {
            Object target = hudTarget(gui);
            // Do not call setTimes — it mutates global title timings for vanilla titles too.
            SET_TITLE.invoke(target, Component.literal(text));
            SET_SUBTITLE.invoke(target, Component.empty());
        } catch (ReflectiveOperationException e) {
            StreamChatMod.LOGGER.error("Failed to show stream title", e);
        }
    }

    private static Object hudTarget(Gui gui) throws ReflectiveOperationException {
        return HUD == null ? gui : HUD.get(gui);
    }
}
