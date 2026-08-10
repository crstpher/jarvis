package jarvis.config;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * User-tunable settings loaded from config/settings.json.
 * Missing fields fall back to the defaults below.
 */
public class Settings {

    public String wakeWord = "jarvis";
    public String modelPath = "models/vosk-model-small-en-us-0.15";
    public boolean clapEnabled = true;
    /** Gap pattern between claps, e.g. "short,short" = three quick claps. */
    public String clapPattern = "short,short";
    /** How many times louder than background noise a clap must be. */
    public double clapSensitivity = 6.0;
    /** Give up listening for a command after this long. */
    public long commandTimeoutMs = 6000;
    /** Fuzzy-match acceptance threshold (0 = exact only, 1 = accept anything). */
    public double matchThreshold = 0.4;

    public static Settings load(Path path) {
        if (!Files.exists(path)) {
            System.out.println("[config] " + path + " not found, using defaults");
            return new Settings();
        }
        try (Reader r = Files.newBufferedReader(path)) {
            Settings s = new Gson().fromJson(r, Settings.class);
            return s != null ? s : new Settings();
        } catch (IOException e) {
            System.err.println("[config] Failed to read " + path + ": " + e.getMessage() + " - using defaults");
            return new Settings();
        }
    }
}
