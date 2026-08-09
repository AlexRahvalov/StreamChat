package by.rahvalov.streamchat.client.command;

import by.rahvalov.streamchat.client.config.ModConfig;
import by.rahvalov.streamchat.client.tiktok.TikTokLiveManager;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.network.chat.Component;

public class TikTokCommand {
    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
            dispatcher.register(ClientCommands.literal("tiktok")
                .then(ClientCommands.literal("connect")
                    .then(ClientCommands.argument("username", StringArgumentType.word())
                        .executes(context -> {
                            String username = StringArgumentType.getString(context, "username");
                            ModConfig.getInstance().tiktokUsername = username;
                            ModConfig.save();

                            context.getSource().sendFeedback(
                                Component.literal("§e[TikTok] Подключение к " + username + "...")
                            );
                            TikTokLiveManager.restart();
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("disconnect")
                    .executes(context -> {
                        TikTokLiveManager.stop();
                        context.getSource().sendFeedback(Component.literal("§c[TikTok] Отключено от стрима."));
                        return 1;
                    })
                )
            );
        });
    }
}
