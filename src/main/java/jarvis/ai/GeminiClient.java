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

/**
 * Asks Google's Gemini a general-knowledge question.
 *
 * The local model is good at deciding what to do; it is not good at
 * knowing things. This covers that gap without giving up the offline
 * design: nothing is sent to Google unless the user actually asks a
 * question, and the assistant still works with no network at all.
 */
public class GeminiClient {

    private static final String ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent";

    /** Answers are spoken, so they must be short. */
    private static final String SYSTEM_INSTRUCTION =
            "You are answering a question that will be read aloud by a voice assistant. "
            + "Reply in at most two short sentences of plain spoken English. "
            + "No markdown, no lists, no formatting, no preamble. "
            + "If the answer is a number, date or name, lead with it. "
            + "If you genuinely do not know, say so in one sentence.";

    /**
     * HTTP/1.1 is forced: negotiating HTTP/2 against this endpoint makes
     * the JDK client hang until the request timeout, while the identical
     * request over 1.1 returns in about two seconds.
     */
    private final HttpClient http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final String apiKey;
    private final String model;

    public GeminiClient(String apiKey, String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public boolean configured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Ask a question, retrying briefly if Google is momentarily busy.
     *
     * @param question what the user asked
     * @return the answer, phrased for speech
     */
    public String ask(String question) throws Exception {
        IllegalStateException last = null;
        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                return askOnce(question);
            } catch (IllegalStateException e) {
                last = e;
                // Only "busy" is worth retrying; a bad key or model will not fix itself.
                if (!e.getMessage().contains("busy")) throw e;
                Thread.sleep(400L * attempt);
            }
        }
        throw last;
    }

    private String askOnce(String question) throws Exception {
        JsonObject part = new JsonObject();
        part.addProperty("text", question);
        JsonArray parts = new JsonArray();
        parts.add(part);

        JsonObject content = new JsonObject();
        content.addProperty("role", "user");
        content.add("parts", parts);
        JsonArray contents = new JsonArray();
        contents.add(content);

        JsonObject sysPart = new JsonObject();
        sysPart.addProperty("text", SYSTEM_INSTRUCTION);
        JsonArray sysParts = new JsonArray();
        sysParts.add(sysPart);
        JsonObject systemInstruction = new JsonObject();
        systemInstruction.add("parts", sysParts);

        JsonObject generationConfig = new JsonObject();
        // Current Flash models reason before answering, and those thinking
        // tokens come out of this budget. At 200 the model spent the lot
        // thinking and returned a single word; 800 leaves room to answer.
        generationConfig.addProperty("maxOutputTokens", 800);
        generationConfig.addProperty("temperature", 0.4);

        JsonObject body = new JsonObject();
        body.add("contents", contents);
        body.add("systemInstruction", systemInstruction);
        body.add("generationConfig", generationConfig);

        HttpRequest req = HttpRequest.newBuilder(URI.create(String.format(ENDPOINT, model)))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();

        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        return switch (resp.statusCode()) {
            case 200 -> extractText(resp.body());
            case 400 -> throw new IllegalStateException("Google rejected the request; the API key may be wrong");
            case 403 -> throw new IllegalStateException("Google refused the API key");
            // Google retires model names while still listing them, so a 404
            // means the configured model, not a bad URL.
            case 404 -> throw new IllegalStateException(
                    "Google has no model called " + model + "; try gemini-flash-latest");
            case 429 -> throw new IllegalStateException("I've hit Google's daily question limit");
            case 503 -> throw new IllegalStateException("Google's AI is busy at the moment");
            default -> throw new IllegalStateException("Google returned error " + resp.statusCode());
        };
    }

    private static String extractText(String json) {
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray candidates = root.getAsJsonArray("candidates");
        if (candidates == null || candidates.isEmpty()) {
            // Usually means the prompt was filtered rather than answered.
            return "I couldn't get an answer to that one.";
        }
        JsonObject first = candidates.get(0).getAsJsonObject();
        if (!first.has("content")) return "I couldn't get an answer to that one.";

        StringBuilder text = new StringBuilder();
        JsonArray parts = first.getAsJsonObject("content").getAsJsonArray("parts");
        if (parts != null) {
            for (var p : parts) {
                JsonObject po = p.getAsJsonObject();
                if (po.has("text")) text.append(po.get("text").getAsString());
            }
        }
        String answer = text.toString().replaceAll("\\s+", " ").trim();
        return answer.isEmpty() ? "I couldn't get an answer to that one." : answer;
    }
}
