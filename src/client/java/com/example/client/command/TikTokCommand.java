package com.example.client.command;

import com.example.client.config.ModConfig;
import com.example.client.tiktok.TikTokLiveManager;
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
                            ModConfig.INSTANCE.tiktokUsername = username;
                            ModConfig.save();
                            
                            context.getSource().sendFeedback(Component.literal("§e[TikTok] Connecting to " + username + "..."));
                            
                            // Stop existing connection if any
                            TikTokLiveManager.stop();
                            // Start new connection
                            TikTokLiveManager.start();
                            
                            return 1;
                        })
                    )
                )
                .then(ClientCommands.literal("disconnect")
                    .executes(context -> {
                        TikTokLiveManager.stop();
                        context.getSource().sendFeedback(Component.literal("§c[TikTok] Disconnected from stream."));
                        return 1;
                    })
                )
            );
        });
    }
}
