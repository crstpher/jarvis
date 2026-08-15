package jarvis.ai;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Minimal client for a locally running Ollama server (the same model
 * server PewDiePie's Odysseus workspace uses underneath).
 *
 * Only /api/chat is needed: it accepts the conversation so far plus the
 * list of tools the model may call, and returns either a spoken reply
 * or a request to run one or more tools.
 */
public class OllamaClient {

    /** What the model sent back: some text, and/or tool calls to run. */
    public record Reply(String text, List<ToolCall> toolCalls, JsonObject raw) {
        public boolean wantsTools() {
            return toolCalls != null && !toolCalls.isEmpty();
        }
    }

    public record ToolCall(String name, JsonObject arguments) {}

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String baseUrl;
    private final String model;
    private final int maxTokens;

    public OllamaClient(String baseUrl, String model, int maxTokens) {
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
        this.maxTokens = maxTokens;
    }

    /** Load the model into VRAM now, so the first real request isn't slow. */
    public void warmUp() {
        try {
            JsonObject body = new JsonObject();
            body.addProperty("model", model);
            body.addProperty("keep_alive", "24h");
            body.add("messages", new JsonArray());
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                    .timeout(Duration.ofSeconds(120))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                    .build();
            http.send(req, HttpResponse.BodyHandlers.discarding());
        } catch (Exception e) {
            System.err.println("[ai] warm-up failed: " + e.getMessage());
        }
    }

    /** True if the Ollama server is up and the configured model is present. */
    public boolean available() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return false;
            // Model names carry a tag ("qwen2.5:7b"); accept a bare name too.
            String bare = model.contains(":") ? model.substring(0, model.indexOf(':')) : model;
            return resp.body().contains(model) || resp.body().contains(bare);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * One round-trip to the model.
     *
     * @param messages conversation so far, in Ollama's message format
     * @param tools    capabilities the model is allowed to invoke
     */
    public Reply chat(JsonArray messages, List<ToolSpec> tools) throws Exception {
        JsonObject body = new JsonObject();
        body.addProperty("model", model);
        body.add("messages", messages);
        body.addProperty("stream", false);
        // Pin the model in VRAM. Cold start costs ~30s; staying resident
        // takes a tool decision from seconds down to ~350ms.
        body.addProperty("keep_alive", "24h");

        if (tools != null && !tools.isEmpty()) {
            JsonArray toolArray = new JsonArray();
            for (ToolSpec t : tools) toolArray.add(t.toJson());
            body.add("tools", toolArray);
        }

        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.6);
        options.addProperty("num_predict", maxTokens);
        body.add("options", options);

        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/api/chat"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new IllegalStateException("Ollama returned HTTP " + resp.statusCode() + ": " + resp.body());
        }

        JsonObject json = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonObject message = json.getAsJsonObject("message");

        String text = message.has("content") ? message.get("content").getAsString().trim() : "";

        List<ToolCall> calls = new java.util.ArrayList<>();
        if (message.has("tool_calls")) {
            for (var el : message.getAsJsonArray("tool_calls")) {
                JsonObject fn = el.getAsJsonObject().getAsJsonObject("function");
                JsonObject args = fn.has("arguments") && fn.get("arguments").isJsonObject()
                        ? fn.getAsJsonObject("arguments")
                        : new JsonObject();
                calls.add(new ToolCall(fn.get("name").getAsString(), args));
            }
        }
        return new Reply(text, calls, message);
    }
}
