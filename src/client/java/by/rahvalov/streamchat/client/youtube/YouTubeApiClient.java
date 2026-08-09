package by.rahvalov.streamchat.client.youtube;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal YouTube Data API v3 client (API key only) for live chat polling.
 */
public final class YouTubeApiClient {
    private static final String API_ROOT = "https://www.googleapis.com/youtube/v3";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    private static final Pattern WATCH_V = Pattern.compile("[?&]v=([a-zA-Z0-9_-]{11})");
    private static final Pattern YOUTU_BE = Pattern.compile("youtu\\.be/([a-zA-Z0-9_-]{11})");
    private static final Pattern LIVE_PATH = Pattern.compile("youtube\\.com/live/([a-zA-Z0-9_-]{11})");
    private static final Pattern EMBED = Pattern.compile("youtube\\.com/embed/([a-zA-Z0-9_-]{11})");
    private static final Pattern SHORTS = Pattern.compile("youtube\\.com/shorts/([a-zA-Z0-9_-]{11})");
    private static final Pattern RAW_ID = Pattern.compile("^([a-zA-Z0-9_-]{11})$");

    private final HttpClient http = HttpClient.newBuilder()
        .connectTimeout(TIMEOUT)
        .build();

    public Optional<String> extractVideoId(String input) {
        if (input == null) {
            return Optional.empty();
        }
        String value = input.trim();
        if (value.isEmpty()) {
            return Optional.empty();
        }

        Optional<String> fromPattern = firstGroup(WATCH_V, value)
            .or(() -> firstGroup(YOUTU_BE, value))
            .or(() -> firstGroup(LIVE_PATH, value))
            .or(() -> firstGroup(EMBED, value))
            .or(() -> firstGroup(SHORTS, value))
            .or(() -> firstGroup(RAW_ID, value));
        return fromPattern;
    }

    public String fetchActiveLiveChatId(String apiKey, String videoId) throws IOException, InterruptedException {
        String url = API_ROOT + "/videos?part=liveStreamingDetails&id="
            + enc(videoId) + "&key=" + enc(apiKey);
        JsonObject root = getJson(url);
        assertNoApiError(root);

        JsonArray items = root.getAsJsonArray("items");
        if (items == null || items.isEmpty()) {
            throw new IOException("Видео не найдено или недоступно");
        }
        JsonObject details = items.get(0).getAsJsonObject().getAsJsonObject("liveStreamingDetails");
        if (details == null || !details.has("activeLiveChatId") || details.get("activeLiveChatId").isJsonNull()) {
            throw new IOException("У видео нет активного live-чата (трансляция не идёт?)");
        }
        return details.get("activeLiveChatId").getAsString();
    }

    public ChatPage fetchChatPage(String apiKey, String liveChatId, String pageToken)
        throws IOException, InterruptedException {
        StringBuilder url = new StringBuilder(API_ROOT)
            .append("/liveChat/messages?part=snippet,authorDetails&liveChatId=")
            .append(enc(liveChatId))
            .append("&key=")
            .append(enc(apiKey));
        if (pageToken != null && !pageToken.isBlank()) {
            url.append("&pageToken=").append(enc(pageToken));
        }

        JsonObject root = getJson(url.toString());
        assertNoApiError(root);

        long pollingInterval = root.has("pollingIntervalMillis")
            ? root.get("pollingIntervalMillis").getAsLong()
            : 5000L;
        String nextPageToken = root.has("nextPageToken") && !root.get("nextPageToken").isJsonNull()
            ? root.get("nextPageToken").getAsString()
            : null;

        List<ChatMessage> messages = new ArrayList<>();
        JsonArray items = root.getAsJsonArray("items");
        if (items != null) {
            for (JsonElement element : items) {
                parseMessage(element.getAsJsonObject()).ifPresent(messages::add);
            }
        }
        return new ChatPage(messages, nextPageToken, Math.max(1000L, pollingInterval));
    }

    private Optional<ChatMessage> parseMessage(JsonObject item) {
        JsonObject snippet = item.getAsJsonObject("snippet");
        JsonObject author = item.getAsJsonObject("authorDetails");
        if (snippet == null || author == null) {
            return Optional.empty();
        }

        String type = text(snippet, "type");
        String authorName = text(author, "displayName");
        if (authorName.isEmpty()) {
            authorName = "Unknown";
        }

        return switch (type) {
            case "textMessageEvent" -> Optional.of(new ChatMessage(
                ChatMessage.Kind.COMMENT,
                authorName,
                text(snippet, "displayMessage")
            ));
            case "superChatEvent" -> {
                JsonObject details = snippet.getAsJsonObject("superChatDetails");
                String amount = details != null ? text(details, "amountDisplayString") : "";
                String msg = details != null ? text(details, "userComment") : text(snippet, "displayMessage");
                String body = amount.isEmpty() ? msg : (amount + (msg.isEmpty() ? "" : ": " + msg));
                yield Optional.of(new ChatMessage(ChatMessage.Kind.SUPER_CHAT, authorName, body));
            }
            case "superStickerEvent" -> {
                JsonObject details = snippet.getAsJsonObject("superStickerDetails");
                String amount = details != null ? text(details, "amountDisplayString") : "";
                yield Optional.of(new ChatMessage(
                    ChatMessage.Kind.SUPER_STICKER,
                    authorName,
                    amount.isEmpty() ? "Super Sticker" : amount
                ));
            }
            case "newSponsorEvent", "memberMilestoneChatEvent",
                 "membershipGiftingEvent", "giftMembershipReceivedEvent" -> Optional.of(new ChatMessage(
                ChatMessage.Kind.MEMBERSHIP,
                authorName,
                text(snippet, "displayMessage")
            ));
            default -> Optional.empty();
        };
    }

    private JsonObject getJson(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(TIMEOUT)
            .header("Accept", "application/json")
            .GET()
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            String body = response.body() == null ? "" : response.body();
            throw new IOException("HTTP " + response.statusCode() + ": " + summarizeError(body));
        }
        JsonElement parsed = JsonParser.parseString(response.body());
        if (!parsed.isJsonObject()) {
            throw new IOException("Некорректный JSON-ответ YouTube API");
        }
        return parsed.getAsJsonObject();
    }

    private static void assertNoApiError(JsonObject root) throws IOException {
        if (!root.has("error")) {
            return;
        }
        throw new IOException(summarizeError(root.toString()));
    }

    private static String summarizeError(String body) {
        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (root.has("error") && root.get("error").isJsonObject()) {
                JsonObject error = root.getAsJsonObject("error");
                String message = text(error, "message");
                if (!message.isEmpty()) {
                    return message;
                }
            }
        } catch (RuntimeException ignored) {
            // fall through
        }
        if (body == null || body.isBlank()) {
            return "unknown error";
        }
        return body.length() > 180 ? body.substring(0, 180) + "…" : body;
    }

    private static Optional<String> firstGroup(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        if (matcher.find()) {
            return Optional.ofNullable(matcher.group(1));
        }
        return Optional.empty();
    }

    private static String text(JsonObject object, String key) {
        if (object == null || !object.has(key) || object.get(key).isJsonNull()) {
            return "";
        }
        return object.get(key).getAsString();
    }

    private static String enc(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record ChatPage(List<ChatMessage> messages, String nextPageToken, long pollingIntervalMillis) {
    }

    public record ChatMessage(Kind kind, String authorName, String text) {
        public enum Kind {
            COMMENT, SUPER_CHAT, SUPER_STICKER, MEMBERSHIP
        }
    }
}
