package by.rahvalov.streamchat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class StreamChatModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// This entrypoint is suitable for setting up client-specific logic, such as rendering.
		by.rahvalov.streamchat.client.config.ModConfig.load();
		by.rahvalov.streamchat.client.command.TikTokCommand.register();
		by.rahvalov.streamchat.client.tiktok.TikTokLiveManager.start();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			by.rahvalov.streamchat.client.tiktok.TikTokLiveManager.stop();
		});
	}
}