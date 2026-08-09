package by.rahvalov.streamchat.client.config;

import by.rahvalov.streamchat.StreamChatMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("streamchat.json").toFile();
    private static final int CURRENT_VERSION = 3;

    private static ModConfig instance = new ModConfig();

    public static ModConfig getInstance() {
        return instance;
    }

    public enum DisplayMode {
        HIDDEN, CHAT, ACTION_BAR, TITLE
    }

    public int configVersion = CURRENT_VERSION;
    public String tiktokUsername = "";
    public Map<EventKind, DisplayMode> displayModes = defaultDisplayModes();

    public static DisplayMode defaultMode(EventKind kind) {
        return switch (kind) {
            case COMMENT, FOLLOW, SHARE -> DisplayMode.CHAT;
            case LIKE, JOIN -> DisplayMode.ACTION_BAR;
            case GIFT, SUBSCRIBE -> DisplayMode.TITLE;
        };
    }

    private static Map<EventKind, DisplayMode> defaultDisplayModes() {
        Map<EventKind, DisplayMode> modes = new EnumMap<>(EventKind.class);
        for (EventKind kind : EventKind.values()) {
            modes.put(kind, defaultMode(kind));
        }
        return modes;
    }

    public DisplayMode modeFor(EventKind kind) {
        if (displayModes == null) {
            return defaultMode(kind);
        }
        DisplayMode mode = displayModes.get(kind);
        return mode != null ? mode : defaultMode(kind);
    }

    public void validate() {
        if (tiktokUsername == null) {
            tiktokUsername = "";
        }
        if (displayModes == null) {
            displayModes = defaultDisplayModes();
            return;
        }
        Map<EventKind, DisplayMode> normalized = new EnumMap<>(EventKind.class);
        for (EventKind kind : EventKind.values()) {
            DisplayMode mode = displayModes.get(kind);
            normalized.put(kind, mode != null ? mode : defaultMode(kind));
        }
        displayModes = normalized;
        if (configVersion < 1) {
            configVersion = CURRENT_VERSION;
        }
    }

    public static void load() {
        if (!CONFIG_FILE.exists()) {
            save();
            return;
        }

        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            JsonElement rootElement = JsonParser.parseReader(reader);
            if (rootElement == null || !rootElement.isJsonObject()) {
                StreamChatMod.LOGGER.error("StreamChat config is not a JSON object; keeping in-memory defaults (file left untouched)");
                return;
            }

            JsonObject root = rootElement.getAsJsonObject();
            ModConfig loaded = GSON.fromJson(root, ModConfig.class);
            if (loaded == null) {
                StreamChatMod.LOGGER.error("StreamChat config parsed to null; keeping in-memory defaults (file left untouched)");
                return;
            }

            loaded.validate();
            boolean dirty = migrate(root, loaded);
            if (loaded.configVersion != CURRENT_VERSION) {
                loaded.configVersion = CURRENT_VERSION;
                dirty = true;
            }

            instance = loaded;
            if (dirty) {
                save();
            }
        } catch (Exception e) {
            // Fail-soft: keep last good in-memory instance and do not overwrite the on-disk file.
            StreamChatMod.LOGGER.error("Failed to load StreamChat config; keeping previous/defaults (file left untouched): {}", e.getMessage());
        }
    }

    /**
     * Migrates v1 {@code show*} booleans and v2 named {@code display*} fields into {@link #displayModes}.
     *
     * @return true if the in-memory config was changed and should be rewritten
     */
    private static boolean migrate(JsonObject root, ModConfig cfg) {
        if (root.has("displayModes") && root.get("displayModes").isJsonObject()) {
            return hasLegacyKeys(root);
        }

        if (hasNamedDisplayFields(root)) {
            applyNamedDisplayField(root, cfg, "displayComments", EventKind.COMMENT);
            applyNamedDisplayField(root, cfg, "displayGifts", EventKind.GIFT);
            applyNamedDisplayField(root, cfg, "displayLikes", EventKind.LIKE);
            applyNamedDisplayField(root, cfg, "displayNewFollowers", EventKind.FOLLOW);
            applyNamedDisplayField(root, cfg, "displayShares", EventKind.SHARE);
            applyNamedDisplayField(root, cfg, "displayNewViewers", EventKind.JOIN);
            applyNamedDisplayField(root, cfg, "displaySuperfanSubscriptions", EventKind.SUBSCRIBE);
            return true;
        }

        if (hasShowBooleans(root)) {
            // v1 only supported chat toggles: false → HIDDEN, true → CHAT.
            applyShowBoolean(root, cfg, "showComments", EventKind.COMMENT);
            applyShowBoolean(root, cfg, "showGifts", EventKind.GIFT);
            applyShowBoolean(root, cfg, "showLikes", EventKind.LIKE);
            applyShowBoolean(root, cfg, "showNewFollowers", EventKind.FOLLOW);
            applyShowBoolean(root, cfg, "showShares", EventKind.SHARE);
            applyShowBoolean(root, cfg, "showNewViewers", EventKind.JOIN);
            applyShowBoolean(root, cfg, "showSuperfanSubscriptions", EventKind.SUBSCRIBE);
            return true;
        }

        return false;
    }

    private static boolean hasLegacyKeys(JsonObject root) {
        return hasNamedDisplayFields(root) || hasShowBooleans(root);
    }

    private static boolean hasNamedDisplayFields(JsonObject root) {
        return root.has("displayGifts")
            || root.has("displayComments")
            || root.has("displayLikes")
            || root.has("displayNewFollowers")
            || root.has("displayNewViewers")
            || root.has("displayShares")
            || root.has("displaySuperfanSubscriptions");
    }

    private static boolean hasShowBooleans(JsonObject root) {
        return root.has("showGifts")
            || root.has("showComments")
            || root.has("showLikes")
            || root.has("showNewFollowers")
            || root.has("showNewViewers")
            || root.has("showShares")
            || root.has("showSuperfanSubscriptions");
    }

    private static void applyNamedDisplayField(JsonObject root, ModConfig cfg, String key, EventKind kind) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return;
        }
        try {
            DisplayMode mode = DisplayMode.valueOf(root.get(key).getAsString());
            cfg.displayModes.put(kind, mode);
        } catch (RuntimeException e) {
            StreamChatMod.LOGGER.warn("Invalid display mode for {}: {}", key, root.get(key));
        }
    }

    private static void applyShowBoolean(JsonObject root, ModConfig cfg, String key, EventKind kind) {
        if (!root.has(key) || root.get(key).isJsonNull()) {
            return;
        }
        try {
            boolean enabled = root.get(key).getAsBoolean();
            cfg.displayModes.put(kind, enabled ? DisplayMode.CHAT : DisplayMode.HIDDEN);
        } catch (RuntimeException e) {
            StreamChatMod.LOGGER.warn("Invalid boolean for {}: {}", key, root.get(key));
        }
    }

    public static void save() {
        try {
            File parent = CONFIG_FILE.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            StreamChatMod.LOGGER.error("Failed to save StreamChat config: {}", e.getMessage());
        }
    }
}
