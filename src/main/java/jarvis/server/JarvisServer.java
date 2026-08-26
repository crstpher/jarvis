package jarvis.server;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import jarvis.core.Jarvis;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Set;
import java.util.concurrent.Executors;

/**
 * The dashboard: a small local web app for controlling Jarvis - whether
 * he is listening, which voice he speaks with, and his personality.
 *
 * Security: bound to 127.0.0.1 explicitly, so it is reachable only from
 * this machine - nothing on the network can see or control the
 * assistant. There is deliberately no remote-access option.
 */
public final class JarvisServer {

    private static final Set<String> KOKORO_VOICES =
            Set.of("bm_george", "bm_lewis", "bm_daniel", "bm_fable");

    private JarvisServer() {}

    public static void start(Jarvis jarvis, int port) {
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress("127.0.0.1", port), 0);
            server.setExecutor(Executors.newFixedThreadPool(2));

            server.createContext("/", ex -> serveIndex(ex));
            server.createContext("/api/status", ex -> handle(ex, () -> status(jarvis)));
            server.createContext("/api/listening", ex -> handle(ex, () -> {
                JsonObject body = readBody(ex);
                jarvis.setListening(body.get("enabled").getAsBoolean());
                return ok();
            }));
            server.createContext("/api/voice", ex -> handle(ex, () -> {
                JsonObject body = readBody(ex);
                String name = body.get("name").getAsString();
                if (!KOKORO_VOICES.contains(name)) {
                    return error("unknown voice " + name);
                }
                String failure = jarvis.switchVoice(name);
                return failure == null ? ok() : error(failure);
            }));
            server.createContext("/api/persona", ex -> handle(ex, () -> {
                JsonObject body = readBody(ex);
                String text = body.get("text").getAsString();
                if (text.isBlank()) return error("personality can't be empty");
                jarvis.setPersona(text);
                return ok();
            }));

            server.start();
            System.out.println("[dashboard] http://127.0.0.1:" + port + " (local machine only)");
        } catch (IOException e) {
            System.err.println("[dashboard] failed to start: " + e.getMessage());
        }
    }

    // --- handlers ------------------------------------------------------

    private interface Api {
        JsonObject run() throws Exception;
    }

    /** Uniform JSON endpoint plumbing with error containment. */
    private static void handle(HttpExchange ex, Api api) throws IOException {
        JsonObject result;
        int code = 200;
        try {
            result = api.run();
        } catch (Exception e) {
            code = 500;
            result = error(e.getMessage() == null ? e.toString() : e.getMessage());
        }
        byte[] out = new Gson().toJson(result).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.sendResponseHeaders(code, out.length);
        try (var os = ex.getResponseBody()) {
            os.write(out);
        }
    }

    private static JsonObject status(Jarvis jarvis) {
        var settings = jarvis.settings();
        JsonObject json = new JsonObject();
        json.addProperty("listening", jarvis.isListening());
        json.addProperty("voice", settings.kokoroVoiceName);
        json.addProperty("brain", jarvis.brainName());
        json.addProperty("persona", settings.persona);
        json.addProperty("spotify", Files.exists(java.nio.file.Path.of("config/spotify-tokens.json")));
        JsonArray recent = new JsonArray();
        for (String line : jarvis.recentEvents()) recent.add(line);
        json.add("recent", recent);
        return json;
    }

    private static void serveIndex(HttpExchange ex) throws IOException {
        if (!"/".equals(ex.getRequestURI().getPath())) {
            ex.sendResponseHeaders(404, -1);
            return;
        }
        byte[] html;
        try (InputStream in = JarvisServer.class.getResourceAsStream("/webui/index.html")) {
            html = in == null
                    ? "<h1>Dashboard page missing from build</h1>".getBytes(StandardCharsets.UTF_8)
                    : in.readAllBytes();
        }
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, html.length);
        try (var os = ex.getResponseBody()) {
            os.write(html);
        }
    }

    private static JsonObject readBody(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static JsonObject ok() {
        JsonObject json = new JsonObject();
        json.addProperty("ok", true);
        return json;
    }

    private static JsonObject error(String message) {
        JsonObject json = new JsonObject();
        json.addProperty("ok", false);
        json.addProperty("error", message);
        return json;
    }
}
