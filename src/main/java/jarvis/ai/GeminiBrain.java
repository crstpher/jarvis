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
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The online brain: Google's Gemini drives the conversation and calls
 * the same tools as the local model, but with far better judgement and
 * general knowledge. Free tier; the trade-off is that the conversation
 * text goes to Google, and it needs a working connection.
 *
 * The separate answer_question lookup tool is deliberately absent here:
 * Gemini already knows things, so it answers factual questions directly
 * instead of paying a second round trip to itself.
 */
public class GeminiBrain implements Brain {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";
    private static final int MAX_TOOL_ROUNDS = 4;
    private static final int MAX_HISTORY_ENTRIES = 24;

    /**
     * HTTP/1.1 forced: the JDK client stalls negotiating HTTP/2 with this
     * endpoint (same fault worked around in GeminiClient).
     */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String apiKey;
    private final String model;
    private final ToolBox toolBox;
    private volatile String persona;
    private final Deque<JsonObject> history = new ArrayDeque<>();

    public GeminiBrain(String apiKey, String model, ToolBox toolBox, String persona) {
        this.apiKey = apiKey;
        this.model = model;
        this.toolBox = toolBox;
        this.persona = persona;
    }

    @Override
    public Optional<String> handle(String userText) {
        List<ToolSpec> tools = toolBox.specs();
        // Gemini answers factual questions itself; see class comment.
        tools.removeIf(t -> t.name().equals(ToolBox.KNOWLEDGE_TOOL));

        history.addLast(textContent("user", userText));
        trimHistory();

        try {
            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                JsonObject content = call(tools);

                JsonArray parts = content.has("parts")
                        ? content.getAsJsonArray("parts") : new JsonArray();
                List<JsonObject> calls = new java.util.ArrayList<>();
                StringBuilder text = new StringBuilder();
                for (var p : parts) {
                    JsonObject po = p.getAsJsonObject();
                    if (po.has("functionCall")) calls.add(po.getAsJsonObject("functionCall"));
                    if (po.has("text")) text.append(po.get("text").getAsString());
                }

                if (calls.isEmpty()) {
                    String answer = text.toString().replaceAll("\\s+", " ").trim();
                    if (!answer.isEmpty()) {
                        history.addLast(textContent("model", answer));
                        trimHistory();
                        return Optional.of(answer);
                    }
                    return Optional.of("I'm not sure what to say to that, sir.");
                }

                // Record the model's tool request, run the tools, feed results back.
                history.addLast(content);
                JsonArray responses = new JsonArray();
                for (JsonObject fc : calls) {
                    String name = fc.get("name").getAsString();
                    JsonObject args = fc.has("args") && fc.get("args").isJsonObject()
                            ? fc.getAsJsonObject("args") : new JsonObject();
                    String result = runTool(tools, name, args);
                    System.out.printf("[tool] %s -> %s%n", name, result);

                    JsonObject payload = new JsonObject();
                    payload.addProperty("result", result);
                    JsonObject fr = new JsonObject();
                    fr.addProperty("name", name);
                    fr.add("response", payload);
                    JsonObject part = new JsonObject();
                    part.add("functionResponse", fr);
                    responses.add(part);
                }
                JsonObject toolTurn = new JsonObject();
                toolTurn.addProperty("role", "user");
                toolTurn.add("parts", responses);
                history.addLast(toolTurn);
                trimHistory();
            }
            return Optional.of("That took more steps than I expected, sir.");
        } catch (java.net.ConnectException | java.net.http.HttpTimeoutException e) {
            return Optional.of("I'm afraid I can't reach my online brain, sir. Check the connection.");
        } catch (IllegalStateException e) {
            return Optional.of(e.getMessage());
        } catch (Exception e) {
            System.err.println("[brain] " + e);
            return Optional.of("Something went wrong thinking about that, sir.");
        }
    }

    /** One generateContent round-trip; returns the model's content object. */
    private JsonObject call(List<ToolSpec> tools) throws Exception {
        JsonArray contents = new JsonArray();
        for (JsonObject c : history) contents.add(c);

        JsonObject sysPart = new JsonObject();
        sysPart.addProperty("text", persona);
        JsonArray sysParts = new JsonArray();
        sysParts.add(sysPart);
        JsonObject systemInstruction = new JsonObject();
        systemInstruction.add("parts", sysParts);

        JsonArray declarations = new JsonArray();
        for (ToolSpec t : tools) declarations.add(t.toGeminiJson());
        JsonObject toolWrapper = new JsonObject();
        toolWrapper.add("functionDeclarations", declarations);
        JsonArray toolArray = new JsonArray();
        toolArray.add(toolWrapper);

        JsonObject generationConfig = new JsonObject();
        generationConfig.addProperty("maxOutputTokens", 800);
        generationConfig.addProperty("temperature", 0.6);

        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("systemInstruction", systemInstruction);
        body.add("tools", toolArray);
        body.add("generationConfig", generationConfig);

        HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(ENDPOINT, model)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        switch (resp.statusCode()) {
            case 200 -> { }
            case 429 -> throw new IllegalStateException("I've hit my daily thinking limit, sir.");
            case 404 -> throw new IllegalStateException(
                    "My online model " + model + " no longer exists, sir. The settings need updating.");
            case 503 -> throw new IllegalStateException("My online brain is busy at the moment, sir.");
            default -> throw new IllegalStateException(
                    "My online brain returned error " + resp.statusCode() + ", sir.");
        }

        JsonObject root = JsonParser.parseString(resp.body()).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()
                || !candidates.get(0).getAsJsonObject().has("content")) {
            throw new IllegalStateException("I couldn't come up with a response to that, sir.");
        }
        return candidates.get(0).getAsJsonObject().getAsJsonObject("content");
    }

    private String runTool(List<ToolSpec> tools, String name, JsonObject args) {
        for (ToolSpec spec : tools) {
            if (spec.name().equals(name)) {
                try {
                    return spec.handler().run(args);
                } catch (Exception e) {
                    return "Failed: " + e.getMessage();
                }
            }
        }
        return "No such tool: " + name;
    }

    private static JsonObject textContent(String role, String text) {
        JsonObject part = new JsonObject();
        part.addProperty("text", text);
        JsonArray parts = new JsonArray();
        parts.add(part);
        JsonObject content = new JsonObject();
        content.addProperty("role", role);
        content.add("parts", parts);
        return content;
    }

    private void trimHistory() {
        // Never let the history start mid tool-exchange after trimming.
        while (history.size() > MAX_HISTORY_ENTRIES) history.removeFirst();
        while (!history.isEmpty() && !"user".equals(history.peekFirst().get("role").getAsString())) {
            history.removeFirst();
        }
    }

    @Override
    public void forget() {
        history.clear();
    }

    @Override
    public void setPersona(String persona) {
        this.persona = persona;
    }

    @Override
    public String name() {
        return "Gemini (" + model + ", online)";
    }
}
