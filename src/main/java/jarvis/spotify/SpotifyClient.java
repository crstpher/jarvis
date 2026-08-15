package jarvis.spotify;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * The slice of the Spotify Web API Jarvis needs: find something, play it,
 * and control what's already playing.
 *
 * Playback control requires Spotify Premium and an active device — the
 * desktop app must be open (playing or recently paused). When no device
 * is active the API answers 404, which is translated here into a message
 * the assistant can say out loud rather than an exception.
 */
public class SpotifyClient {

    /** Raised for conditions worth telling the user about verbatim. */
    public static class SpotifyUnavailable extends Exception {
        public SpotifyUnavailable(String message) { super(message); }
    }

    private static final String API = "https://api.spotify.com/v1";

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final SpotifyAuth auth;

    public SpotifyClient(SpotifyAuth auth) {
        this.auth = auth;
    }

    /**
     * Search for something and start playing it.
     *
     * @param query what the user asked for, e.g. "bohemian rhapsody"
     * @param type  one of track, album, artist, playlist
     * @return what Jarvis should say back
     */
    public String play(String query, String type) throws Exception {
        String kind = switch (type == null ? "track" : type.toLowerCase()) {
            case "album" -> "album";
            case "artist" -> "artist";
            case "playlist" -> "playlist";
            default -> "track";
        };

        JsonObject results = get("/search?q=" + enc(query) + "&type=" + kind + "&limit=1");
        JsonArray items = results.getAsJsonObject(kind + "s").getAsJsonArray("items");
        if (items.isEmpty()) {
            return "I couldn't find " + query + " on Spotify";
        }

        JsonObject item = items.get(0).getAsJsonObject();
        String uri = item.get("uri").getAsString();
        String name = item.get("name").getAsString();

        JsonObject body = new JsonObject();
        if (kind.equals("track")) {
            JsonArray uris = new JsonArray();
            uris.add(uri);
            body.add("uris", uris);
        } else {
            // Albums, artists and playlists are played as a context.
            body.addProperty("context_uri", uri);
        }
        put("/me/player/play", body.toString());

        String who = "";
        if (item.has("artists") && item.getAsJsonArray("artists").size() > 0) {
            who = " by " + item.getAsJsonArray("artists").get(0)
                    .getAsJsonObject().get("name").getAsString();
        }
        return "Playing " + name + who;
    }

    public String pause() throws Exception {
        put("/me/player/pause", null);
        return "Paused";
    }

    public String resume() throws Exception {
        put("/me/player/play", null);
        return "Resuming";
    }

    public String next() throws Exception {
        post("/me/player/next");
        return "Skipping";
    }

    public String previous() throws Exception {
        post("/me/player/previous");
        return "Going back";
    }

    public String volume(int percent) throws Exception {
        int clamped = Math.max(0, Math.min(100, percent));
        put("/me/player/volume?volume_percent=" + clamped, null);
        return "Volume " + clamped + " percent";
    }

    /** What's playing right now, phrased for speech. */
    public String nowPlaying() throws Exception {
        JsonObject state = get("/me/player/currently-playing");
        if (state == null || !state.has("item") || state.get("item").isJsonNull()) {
            return "Nothing is playing right now";
        }
        JsonObject item = state.getAsJsonObject("item");
        String name = item.get("name").getAsString();
        String who = item.getAsJsonArray("artists").get(0)
                .getAsJsonObject().get("name").getAsString();
        return name + " by " + who;
    }

    // --- HTTP plumbing -------------------------------------------------

    private JsonObject get(String path) throws Exception {
        HttpResponse<String> resp = send(HttpRequest.newBuilder(URI.create(API + path)).GET());
        if (resp.statusCode() == 204 || resp.body().isBlank()) return null;
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    private void put(String path, String body) throws Exception {
        send(HttpRequest.newBuilder(URI.create(API + path))
                .header("Content-Type", "application/json")
                .PUT(body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body)));
    }

    private void post(String path) throws Exception {
        send(HttpRequest.newBuilder(URI.create(API + path))
                .POST(HttpRequest.BodyPublishers.noBody()));
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        HttpRequest req = builder
                .header("Authorization", "Bearer " + auth.accessToken())
                .timeout(Duration.ofSeconds(15))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());

        switch (resp.statusCode()) {
            case 404 -> throw new SpotifyUnavailable(
                    "I can't reach an active Spotify device. Open Spotify and play something first.");
            case 403 -> throw new SpotifyUnavailable(
                    "Spotify refused that. Playback control needs a Premium account.");
            case 401 -> throw new SpotifyUnavailable(
                    "My Spotify login expired. Run setup-spotify to reconnect.");
            case 429 -> throw new SpotifyUnavailable("Spotify is rate limiting me. Try again shortly.");
            default -> {
                if (resp.statusCode() >= 400) {
                    throw new SpotifyUnavailable("Spotify error " + resp.statusCode());
                }
            }
        }
        return resp;
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
