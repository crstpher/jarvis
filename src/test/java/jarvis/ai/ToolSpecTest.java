package jarvis.ai;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The tool schema is what the model reads to decide what it can do, so a
 * malformed one fails silently and confusingly at runtime. These tests
 * pin the shape Ollama expects.
 */
class ToolSpecTest {

    private ToolSpec sample() {
        return new ToolSpec("spotify_play", "Play music", args -> "ok")
                .param("query", "string", "What to search for", true)
                .param("type", "string", "track or album", false);
    }

    @Test
    void rendersFunctionCallingSchema() {
        JsonObject json = sample().toJson();
        assertEquals("function", json.get("type").getAsString());

        JsonObject fn = json.getAsJsonObject("function");
        assertEquals("spotify_play", fn.get("name").getAsString());
        assertEquals("Play music", fn.get("description").getAsString());

        JsonObject params = fn.getAsJsonObject("parameters");
        assertEquals("object", params.get("type").getAsString());
        assertTrue(params.getAsJsonObject("properties").has("query"));
        assertTrue(params.getAsJsonObject("properties").has("type"));
    }

    @Test
    void onlyRequiredParamsAreMarkedRequired() {
        var required = sample().toJson()
                .getAsJsonObject("function")
                .getAsJsonObject("parameters")
                .getAsJsonArray("required");

        assertEquals(1, required.size());
        assertEquals("query", required.get(0).getAsString());
    }

    @Test
    void handlerIsInvokedWithArguments() throws Exception {
        ToolSpec spec = new ToolSpec("echo", "Echo it back",
                args -> "got " + args.get("value").getAsString());

        JsonObject args = new JsonObject();
        args.addProperty("value", "hello");

        assertEquals("got hello", spec.handler().run(args));
    }

    @Test
    void parameterDescriptionsSurvive() {
        JsonObject props = sample().toJson()
                .getAsJsonObject("function")
                .getAsJsonObject("parameters")
                .getAsJsonObject("properties");

        assertEquals("What to search for",
                props.getAsJsonObject("query").get("description").getAsString());
    }
}
