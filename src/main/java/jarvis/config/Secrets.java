package jarvis.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * API keys, kept out of settings.json so the config file stays safe to
 * commit and share. config/secrets.json is gitignored.
 *
 * An environment variable of the same name wins, which is handy for
 * running without writing the key to disk at all.
 */
public class Secrets {

    private final JsonObject values;

    private Secrets(JsonObject values) {
        this.values = values;
    }

    public static Secrets load(Path path) {
        try {
            if (Files.exists(path)) {
                return new Secrets(JsonParser.parseString(Files.readString(path)).getAsJsonObject());
            }
        } catch (Exception e) {
            System.err.println("[config] could not read " + path + ": " + e.getMessage());
        }
        return new Secrets(new JsonObject());
    }

    /**
     * @param name    key in secrets.json, e.g. "geminiApiKey"
     * @param envName environment variable that overrides it, e.g. "GEMINI_API_KEY"
     */
    public String get(String name, String envName) {
        String fromEnv = System.getenv(envName);
        if (fromEnv != null && !fromEnv.isBlank()) return fromEnv.trim();
        if (values.has(name) && !values.get(name).isJsonNull()) {
            return values.get(name).getAsString().trim();
        }
        return "";
    }
}
