package by.rahvalov.streamchat.client.command;

import by.rahvalov.streamchat.client.config.ModConfig;
import by.rahvalov.streamchat.client.youtube.YouTubeApiClient;
import by.rahvalov.streamchat.client.youtube.YouTubeLiveManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.network.chat.Component;

public class YouTubeCommand {
    private static final YouTubeApiClient API = new YouTubeApiClient();

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("youtube")
                .then(ClientCommands.literal("apikey")
                    .then(ClientCommands.argument("key", StringArgumentType.greedyString())
                        .executes(context -> {
                            String key = StringArgumentType.getString(context, "key").trim();
                            if (key.isEmpty()) {
                                context.getSource().sendFeedback(Component.literal("§c[YouTube] API key пустой."));
                                return 0;
                            }
                            ModConfig.getInstance().youtubeApiKey = key;
                            ModConfig.save();
                            context.getSource().sendFeedback(
                                Component.literal("§a[YouTube] API key сохранён в streamchat.json")
                            );
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("connect")
                    .then(ClientCommands.argument("target", StringArgumentType.greedyString())
                        .executes(context -> {
                            String target = StringArgumentType.getString(context, "target").trim();
                            var videoId = API.extractVideoId(target);
                            if (videoId.isEmpty()) {
                                context.getSource().sendFeedback(
                                    Component.literal("§c[YouTube] Укажите video id или URL трансляции.")
                                );
                                return 0;
                            }
                            if (ModConfig.getInstance().youtubeApiKey == null
                                || ModConfig.getInstance().youtubeApiKey.isBlank()) {
                                context.getSource().sendFeedback(
                                    Component.literal("§c[YouTube] Сначала задайте ключ: /youtube apikey <KEY>")
                                );
                                return 0;
                            }

                            ModConfig.getInstance().youtubeVideoId = videoId.get();
                            ModConfig.save();

                            context.getSource().sendFeedback(
                                Component.literal("§e[YouTube] Подключение к " + videoId.get() + "...")
                            );
                            YouTubeLiveManager.restart();
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("disconnect")
                    .executes(context -> {
                        YouTubeLiveManager.stop();
                        context.getSource().sendFeedback(Component.literal("§c[YouTube] Отключено от стрима."));
                        return 1;
                    })
                )
            );
        });
    }
}
