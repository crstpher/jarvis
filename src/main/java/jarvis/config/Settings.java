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
    /** Substring of the microphone name to capture from; "" = Windows default device. */
    public String inputDevice = "";
    public boolean clapEnabled = true;
    /** Gap pattern between claps, e.g. "short,short" = three quick claps. */
    public String clapPattern = "short,short";
    /** How many times louder than background noise a clap must be. */
    public double clapSensitivity = 6.0;
    /** Give up listening for a command after this long. */
    public long commandTimeoutMs = 6000;
    /** Fuzzy-match acceptance threshold (0 = exact only, 1 = accept anything). */
    public double matchThreshold = 0.4;
    /**
     * Restrict the *fast path* recognizer to the phrases in commands.json.
     * A second, full-vocabulary recognizer always runs alongside it to
     * catch conversation, so this only controls how the fast path listens.
     */
    public boolean strictGrammar = true;

    // --- conversational AI ---------------------------------------------
    /** Route anything that isn't a known command to the local AI. */
    public boolean aiEnabled = true;
    public String ollamaUrl = "http://localhost:11434";
    public String ollamaModel = "qwen2.5:7b";
    /** Cap on reply length; keeps spoken answers short. */
    public int aiMaxTokens = 160;
    /** Who Jarvis is. Edit freely — this is the personality dial. */
    public String persona =
            "You are Jarvis, a witty, unflappable British AI assistant running on the user's "
            + "gaming PC. You control their apps, games and Spotify through the tools provided.\n"
            + "Rules:\n"
            + "- Your replies are spoken aloud, so keep them to one or two short sentences. "
            + "Never use markdown, lists, emoji or code.\n"
            + "- When the user wants something done, call the matching tool. Do not describe "
            + "what you would do — just do it, then confirm briefly.\n"
            + "- If a tool reports a failure, say what went wrong in plain words.\n"
            + "- For chat or questions, answer directly and briefly. Dry humour is welcome; "
            + "rambling is not.";

    // --- voice ----------------------------------------------------------
    /** "piper" for the neural voice, "sapi" for the built-in Windows one. */
    public String voice = "piper";
    public String piperExe = "tools/piper/piper.exe";
    public String piperModel = "tools/piper/en_GB-alan-medium.onnx";
    public int piperSampleRate = 22050;

    // --- spotify --------------------------------------------------------
    /** Client ID from developer.spotify.com. Blank disables Spotify. */
    public String spotifyClientId = "";

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
