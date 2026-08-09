package by.rahvalov.streamchat.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

public class StreamChatModClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		by.rahvalov.streamchat.client.config.ModConfig.load();
		by.rahvalov.streamchat.client.command.TikTokCommand.register();
		by.rahvalov.streamchat.client.command.YouTubeCommand.register();
		by.rahvalov.streamchat.client.tiktok.TikTokLiveManager.start();
		by.rahvalov.streamchat.client.youtube.YouTubeLiveManager.start();

		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
			by.rahvalov.streamchat.client.tiktok.TikTokLiveManager.stop();
			by.rahvalov.streamchat.client.youtube.YouTubeLiveManager.stop();
		});
	}
}
