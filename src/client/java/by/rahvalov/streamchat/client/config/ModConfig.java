package by.rahvalov.streamchat.client.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import by.rahvalov.streamchat.StreamChatMod;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

public class ModConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final File CONFIG_FILE = FabricLoader.getInstance().getConfigDir().resolve("streamchat.json").toFile();

    private static ModConfig instance = new ModConfig();

    public static ModConfig getInstance() {
        return instance;
    }

    public enum DisplayMode {
        HIDDEN, CHAT, ACTION_BAR, TITLE
    }

    public int configVersion = 2;
    public String tiktokUsername = "";
    
    public DisplayMode displayGifts = DisplayMode.TITLE;
    public DisplayMode displayNewFollowers = DisplayMode.CHAT;
    public DisplayMode displayNewViewers = DisplayMode.ACTION_BAR;
    public DisplayMode displaySuperfanSubscriptions = DisplayMode.TITLE;
    public DisplayMode displayComments = DisplayMode.CHAT;
    public DisplayMode displayLikes = DisplayMode.ACTION_BAR;
    public DisplayMode displayShares = DisplayMode.CHAT;

    public void validate() {
        if (displayGifts == null) displayGifts = DisplayMode.TITLE;
        if (displayNewFollowers == null) displayNewFollowers = DisplayMode.CHAT;
        if (displayNewViewers == null) displayNewViewers = DisplayMode.ACTION_BAR;
        if (displaySuperfanSubscriptions == null) displaySuperfanSubscriptions = DisplayMode.TITLE;
        if (displayComments == null) displayComments = DisplayMode.CHAT;
        if (displayLikes == null) displayLikes = DisplayMode.ACTION_BAR;
        if (displayShares == null) displayShares = DisplayMode.CHAT;
    }

    @SuppressWarnings("null")
    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    instance.validate();
                    if (instance.configVersion < 2) {
                        instance.configVersion = 2;
                        save();
                    }
                }
            } catch (Exception e) {
                StreamChatMod.LOGGER.error("Failed to load StreamChat config, recreating defaults: {}", e.getMessage());
                instance = new ModConfig();
                save();
            }
        } else {
            save();
        }
    }

    public static void save() {
        try {
            CONFIG_FILE.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            StreamChatMod.LOGGER.error("Failed to save StreamChat config: {}", e.getMessage());
        }
    }
}
