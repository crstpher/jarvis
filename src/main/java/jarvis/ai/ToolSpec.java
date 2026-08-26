package jarvis.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Describes one capability the model is allowed to invoke, in the JSON
 * schema shape Ollama expects (the OpenAI "function calling" format).
 *
 * Building these by hand keeps the dependency surface small: the whole
 * AI layer needs nothing beyond Gson and the JDK's HTTP client.
 */
public class ToolSpec {

    /** One parameter of a tool. */
    private record Param(String name, String type, String description, boolean required) {}

    private final String name;
    private final String description;
    private final Map<String, Param> params = new LinkedHashMap<>();
    private final ToolHandler handler;

    public ToolSpec(String name, String description, ToolHandler handler) {
        this.name = name;
        this.description = description;
        this.handler = handler;
    }

    public ToolSpec param(String name, String type, String description, boolean required) {
        params.put(name, new Param(name, type, description, required));
        return this;
    }

    public String name() {
        return name;
    }

    public ToolHandler handler() {
        return handler;
    }

    /** Render as the JSON the model sees. */
    public JsonObject toJson() {
        JsonObject properties = new JsonObject();
        JsonArray required = new JsonArray();
        for (Param p : params.values()) {
            JsonObject spec = new JsonObject();
            spec.addProperty("type", p.type());
            spec.addProperty("description", p.description());
            properties.add(p.name(), spec);
            if (p.required()) required.add(p.name());
        }

        JsonObject parameters = new JsonObject();
        parameters.addProperty("type", "object");
        parameters.add("properties", properties);
        parameters.add("required", required);

        JsonObject function = new JsonObject();
        function.addProperty("name", name);
        function.addProperty("description", description);
        function.add("parameters", parameters);

        JsonObject tool = new JsonObject();
        tool.addProperty("type", "function");
        tool.add("function", function);
        return tool;
    }

    /**
     * The same declaration in the shape Gemini's function calling wants:
     * no outer wrapper, and schema types as uppercase enums.
     */
    public JsonObject toGeminiJson() {
        JsonObject fn = toJson().getAsJsonObject("function").deepCopy();
        JsonObject parameters = fn.getAsJsonObject("parameters");
        parameters.addProperty("type", "OBJECT");
        JsonObject properties = parameters.getAsJsonObject("properties");
        for (String key : properties.keySet()) {
            JsonObject p = properties.getAsJsonObject(key);
            p.addProperty("type", p.get("type").getAsString().toUpperCase(java.util.Locale.ROOT));
        }
        return fn;
    }

    /** What actually runs when the model picks this tool. */
    @FunctionalInterface
    public interface ToolHandler {
        /** @return a short result string that is fed back to the model. */
        String run(JsonObject arguments) throws Exception;
    }
}
