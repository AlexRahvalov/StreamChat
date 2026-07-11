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

    public int configVersion = 1;
    public String tiktokUsername = "";
    public boolean showGifts = true;
    public boolean showNewFollowers = true;
    public boolean showNewViewers = true;
    public boolean showSuperfanSubscriptions = true;
    public boolean showComments = true;
    public boolean showLikes = true;
    public boolean showShares = true;

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ModConfig loaded = GSON.fromJson(reader, ModConfig.class);
                if (loaded != null) {
                    instance = loaded;
                    if (instance.configVersion < 1) {
                        instance.configVersion = 1;
                        save();
                    }
                }
            } catch (IOException e) {
                StreamChatMod.LOGGER.error("Failed to load StreamChat config: {}", e.getMessage());
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
