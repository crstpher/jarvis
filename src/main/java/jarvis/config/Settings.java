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
    /**
     * Larger model used only for free-form speech. Blank falls back to
     * modelPath. Vosk supports runtime grammars only on the small models,
     * so the compact one keeps the wake word and fast path while this one
     * handles conversation.
     */
    public String freeModelPath = "models/vosk-model-en-us-0.22";
    /** Substring of the microphone name to capture from; "" = Windows default device. */
    public String inputDevice = "";
    public boolean clapEnabled = true;
    /** Gap pattern between claps, e.g. "short,short" = three quick claps. */
    public String clapPattern = "short,short";
    /** How many times louder than background noise a clap must be. */
    public double clapSensitivity = 6.0;
    /** Give up listening for a command after this long. */
    public long commandTimeoutMs = 6000;
    /**
     * Stay listening after replying, so a conversation flows without
     * repeating the wake word every turn. Ends on silence or "goodbye".
     */
    public boolean conversationMode = true;
    /** How long to wait for a follow-up before returning to wake-word mode. */
    public long followUpMs = 9000;
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
    /**
     * "kokoro" (most natural), "piper" (lighter, no Python), or "sapi"
     * (built-in Windows voice). Each falls back to the next if unavailable.
     */
    public String voice = "kokoro";
    /** Substring of the speaker device to play through; "" = Windows default. */
    public String outputDevice = "";

    public String pythonExe = "python";
    public String kokoroScript = "tools/kokoro/kokoro_server.py";
    public String kokoroModel = "tools/kokoro/kokoro-v1.0.onnx";
    public String kokoroVoices = "tools/kokoro/voices-v1.0.bin";
    /** British male: bm_george, bm_lewis, bm_daniel, bm_fable. */
    public String kokoroVoiceName = "bm_george";
    public double kokoroSpeed = 1.0;

    public String piperExe = "tools/piper/piper.exe";
    public String piperModel = "tools/piper/en_GB-alan-medium.onnx";
    public int piperSampleRate = 22050;

    // --- online knowledge -----------------------------------------------
    /**
     * Let Jarvis consult Google's Gemini for general-knowledge questions
     * the local model can't answer well. The API key lives in
     * config/secrets.json, not here. Turn off to stay strictly offline.
     */
    public boolean geminiEnabled = true;
    /**
     * Flash-Lite, and an alias rather than a pinned version.
     *
     * Google retires specific model names while still listing them
     * (gemini-2.5-flash 404s), so "latest" avoids future breakage. Lite
     * is chosen for consistency: full Flash reasons before answering and
     * measured anywhere from 2 to 19 seconds, which is unusable when the
     * answer is spoken. Lite averages under a second.
     */
    public String geminiModel = "gemini-flash-lite-latest";
    /**
     * Speak looked-up answers exactly as returned. Turning this off sends
     * them back through the local model to be reworded in character, which
     * sounds more butler-ish but can distort the facts.
     */
    public boolean speakAnswersVerbatim = true;

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
