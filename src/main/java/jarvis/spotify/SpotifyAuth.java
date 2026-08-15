package jarvis.spotify;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Spotify OAuth using the PKCE flow (RFC 7636).
 *
 * PKCE is the right choice for a desktop app: it needs only the public
 * Client ID, never a client secret, so nothing confidential is stored on
 * disk. The one-time consent happens in your browser; the resulting
 * refresh token is saved to config/spotify-tokens.json and renewed
 * automatically from then on.
 */
public class SpotifyAuth {

    private static final String REDIRECT_URI = "http://127.0.0.1:8888/callback";
    private static final String SCOPES = String.join(" ",
            "user-read-playback-state",
            "user-modify-playback-state",
            "user-read-currently-playing");

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();
    private final String clientId;
    private final Path tokenFile;

    private String accessToken;
    private String refreshToken;
    private Instant expiresAt = Instant.EPOCH;

    public SpotifyAuth(String clientId, Path tokenFile) {
        this.clientId = clientId;
        this.tokenFile = tokenFile;
        load();
    }

    public boolean hasRefreshToken() {
        return refreshToken != null && !refreshToken.isBlank();
    }

    /** A valid access token, refreshing or prompting for consent as needed. */
    public synchronized String accessToken() throws Exception {
        if (accessToken != null && Instant.now().isBefore(expiresAt.minusSeconds(60))) {
            return accessToken;
        }
        if (hasRefreshToken()) {
            refresh();
        } else {
            authorizeInteractively();
        }
        return accessToken;
    }

    /**
     * One-time browser consent. Opens Spotify's approval page and waits for
     * the redirect back to a tiny local web server.
     */
    public void authorizeInteractively() throws Exception {
        String verifier = randomUrlSafe(64);
        String challenge = base64Url(MessageDigest.getInstance("SHA-256")
                .digest(verifier.getBytes(StandardCharsets.US_ASCII)));

        CompletableFuture<String> codeFuture = new CompletableFuture<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 8888), 0);
        server.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String code = null, error = null;
            for (String pair : query == null ? new String[0] : query.split("&")) {
                int eq = pair.indexOf('=');
                if (eq < 0) continue;
                String k = pair.substring(0, eq), v = pair.substring(eq + 1);
                if (k.equals("code")) code = v;
                if (k.equals("error")) error = v;
            }
            String page = code != null
                    ? "<h2>Jarvis is connected to Spotify.</h2><p>You can close this tab.</p>"
                    : "<h2>Authorisation failed.</h2><p>" + error + "</p>";
            byte[] out = page.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, out.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(out);
            }
            if (code != null) codeFuture.complete(code);
            else codeFuture.completeExceptionally(new IllegalStateException("Spotify denied: " + error));
        });
        server.start();

        String authUrl = "https://accounts.spotify.com/authorize"
                + "?client_id=" + enc(clientId)
                + "&response_type=code"
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&code_challenge_method=S256"
                + "&code_challenge=" + challenge
                + "&scope=" + enc(SCOPES);

        System.out.println("[spotify] opening browser for one-time authorisation...");
        new ProcessBuilder("cmd", "/c", "start", "", authUrl).start();

        try {
            String code = codeFuture.get(3, TimeUnit.MINUTES);
            exchangeCode(code, verifier);
            System.out.println("[spotify] connected.");
        } finally {
            server.stop(0);
        }
    }

    private void exchangeCode(String code, String verifier) throws Exception {
        String form = "grant_type=authorization_code"
                + "&code=" + enc(code)
                + "&redirect_uri=" + enc(REDIRECT_URI)
                + "&client_id=" + enc(clientId)
                + "&code_verifier=" + enc(verifier);
        postToken(form);
    }

    private void refresh() throws Exception {
        String form = "grant_type=refresh_token"
                + "&refresh_token=" + enc(refreshToken)
                + "&client_id=" + enc(clientId);
        postToken(form);
    }

    private void postToken(String form) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create("https://accounts.spotify.com/api/token"))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form))
                .build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Spotify token request failed (" + resp.statusCode() + "): " + resp.body());
        }
        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        accessToken = json.get("access_token").getAsString();
        expiresAt = Instant.now().plusSeconds(json.get("expires_in").getAsLong());
        if (json.has("refresh_token")) {
            refreshToken = json.get("refresh_token").getAsString();
        }
        save();
    }

    private void load() {
        try {
            if (!Files.exists(tokenFile)) return;
            JsonObject json = JsonParser.parseString(Files.readString(tokenFile)).getAsJsonObject();
            if (json.has("refresh_token")) refreshToken = json.get("refresh_token").getAsString();
        } catch (Exception e) {
            System.err.println("[spotify] could not read saved tokens: " + e.getMessage());
        }
    }

    private void save() {
        try {
            Files.createDirectories(tokenFile.getParent());
            JsonObject json = new JsonObject();
            json.addProperty("refresh_token", refreshToken);
            Files.writeString(tokenFile, json.toString());
        } catch (Exception e) {
            System.err.println("[spotify] could not save tokens: " + e.getMessage());
        }
    }

    private static String randomUrlSafe(int bytes) {
        byte[] buf = new byte[bytes];
        new SecureRandom().nextBytes(buf);
        return base64Url(buf);
    }

    private static String base64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
