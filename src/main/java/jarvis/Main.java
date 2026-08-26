package jarvis;

import jarvis.actions.CommandRegistry;
import jarvis.config.Settings;
import jarvis.core.Jarvis;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {
        Settings settings = Settings.load(Path.of("config/settings.json"));

        if (args.length > 0 && args[0].equals("--check")) {
            check(settings);
            return;
        }

        if (args.length > 0 && args[0].equals("--mic")) {
            jarvis.util.MicCheck.run(settings, 25);
            return;
        }

        if (args.length > 0 && args[0].equals("--spotify-login")) {
            spotifyLogin(settings);
            return;
        }

        // Ask a question straight through to Gemini - for testing the key.
        if (args.length > 1 && args[0].equals("--ask")) {
            askOnce(settings, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            return;
        }

        // One text exchange with the configured brain - tests the whole AI
        // path (tool calling included) without the microphone or speech models.
        if (args.length > 1 && args[0].equals("--chat")) {
            CommandRegistry reg = CommandRegistry.load(Path.of("config/commands.json"));
            var matcher = new jarvis.nlu.CommandMatcher(reg, settings.matchThreshold);
            var brain = jarvis.core.Jarvis.createBrain(settings, reg, matcher,
                    new jarvis.actions.ActionExecutor());
            if (brain == null) {
                System.err.println("No brain available.");
                System.exit(1);
            }
            String text = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
            long start = System.currentTimeMillis();
            System.out.println("YOU:    " + text);
            System.out.println("JARVIS: " + brain.handle(text).orElse("(silence)"));
            System.out.println("(" + brain.name() + ", " + (System.currentTimeMillis() - start) + " ms)");
            return;
        }

        // Speak a line and exit - for auditioning voices without a microphone.
        if (args.length > 0 && args[0].equals("--say")) {
            String line = args.length > 1
                    ? String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))
                    : "Certainly, sir. All systems are online.";
            jarvis.core.Jarvis.speakOnce(settings, line);
            return;
        }

        if (!Files.isDirectory(Path.of(settings.modelPath))) {
            System.err.println("Speech model not found at: " + settings.modelPath);
            System.err.println("Run setup.ps1 first to download it (~40 MB, one time only).");
            System.exit(1);
        }

        // Mirror the console to logs/ so the session can be reviewed later.
        jarvis.util.SessionLog.start(Path.of("logs"));

        CommandRegistry registry = CommandRegistry.load(Path.of("config/commands.json"));
        new Jarvis(settings, registry).run();
    }

    /** Send one question to Gemini and print the answer. Used by setup-gemini.ps1. */
    private static void askOnce(Settings settings, String question) throws Exception {
        var secrets = jarvis.config.Secrets.load(Path.of("config/secrets.json"));
        String key = secrets.get("geminiApiKey", "GEMINI_API_KEY");
        if (key.isBlank()) {
            System.err.println("No Gemini API key found - run setup-gemini.ps1.");
            System.exit(1);
        }
        var gemini = new jarvis.ai.GeminiClient(key, settings.geminiModel);
        long start = System.currentTimeMillis();
        String answer = gemini.ask(question);
        System.out.println("Q: " + question);
        System.out.println("A: " + answer);
        System.out.println("(" + (System.currentTimeMillis() - start) + " ms)");
    }

    /** One-time Spotify consent, driven by setup-spotify.ps1. */
    private static void spotifyLogin(Settings settings) throws Exception {
        if (settings.spotifyClientId == null || settings.spotifyClientId.isBlank()) {
            System.err.println("No spotifyClientId in config/settings.json - run setup-spotify.ps1.");
            System.exit(1);
        }
        var auth = new jarvis.spotify.SpotifyAuth(settings.spotifyClientId,
                Path.of("config/spotify-tokens.json"));
        auth.authorizeInteractively();
        System.out.println("Spotify is connected. Try: \"Jarvis, play some music\".");
    }

    /** Sanity check: config, model, and audio devices - without opening the mic. */
    private static void check(Settings settings) throws Exception {
        System.out.println("== Jarvis environment check ==");
        System.out.println("Model path: " + settings.modelPath
                + (Files.isDirectory(Path.of(settings.modelPath)) ? " [OK]" : " [MISSING - run setup.ps1]"));

        CommandRegistry registry = CommandRegistry.load(Path.of("config/commands.json"));
        System.out.println("Commands loaded: " + registry.commands().size());

        System.out.println("Audio input devices:");
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getTargetLineInfo().length > 0) {
                System.out.println("  - " + info.getName());
            }
        }

        String voiceStatus = switch (settings.voice == null ? "" : settings.voice.toLowerCase()) {
            case "kokoro" -> Files.exists(Path.of(settings.kokoroModel))
                    ? " (" + settings.kokoroVoiceName + ") [OK]"
                    : " [MISSING - run setup-voice.ps1]";
            case "piper" -> Files.exists(Path.of(settings.piperExe))
                    ? " [OK]" : " [MISSING - run setup-voice.ps1]";
            default -> " [OK]";
        };
        System.out.println("Voice: " + settings.voice + voiceStatus);

        if (settings.aiEnabled) {
            var ai = new jarvis.ai.OllamaClient(settings.ollamaUrl, settings.ollamaModel, settings.aiMaxTokens);
            System.out.println("AI: " + settings.ollamaModel
                    + (ai.available() ? " [OK]" : " [UNREACHABLE at " + settings.ollamaUrl + "]"));
        } else {
            System.out.println("AI: disabled");
        }

        System.out.println("Spotify: " + (settings.spotifyClientId == null || settings.spotifyClientId.isBlank()
                ? "not configured - run setup-spotify.ps1"
                : (Files.exists(Path.of("config/spotify-tokens.json")) ? "connected [OK]" : "client ID set, not authorised yet")));

        if (Files.isDirectory(Path.of(settings.modelPath))) {
            System.out.println("Loading speech model (first load takes a few seconds)...");
            try (var stt = new jarvis.stt.SpeechRecognizer(settings.modelPath, settings.wakeWord)) {
                System.out.println("Speech model loaded [OK]");
            }
        }
        System.out.println("All checks passed.");
    }
}
