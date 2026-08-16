package jarvis.core;

import jarvis.actions.ActionExecutor;
import jarvis.actions.CommandRegistry;
import jarvis.ai.Brain;
import jarvis.ai.GeminiClient;
import jarvis.ai.OllamaClient;
import jarvis.ai.ToolBox;
import jarvis.audio.AudioCapture;
import jarvis.config.Secrets;
import jarvis.config.Settings;
import jarvis.nlu.CommandMatcher;
import jarvis.spotify.SpotifyAuth;
import jarvis.spotify.SpotifyClient;
import jarvis.stt.SpeechRecognizer;
import jarvis.tts.KokoroVoice;
import jarvis.tts.PiperVoice;
import jarvis.tts.Speaker;
import jarvis.tts.Voice;
import jarvis.util.Chime;
import jarvis.wake.ClapDetector;
import jarvis.wake.ClapPatternFSM;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The orchestrator: consumer side of the audio pipeline (CS240).
 *
 * A two-state machine drives everything:
 *   IDLE      - every frame goes to the clap detector + the
 *               grammar-restricted wake-word recognizer.
 *   LISTENING - after a wake trigger (chime!), frames go to both command
 *               recognizers until the endpointer says the sentence is
 *               over, then the sentence is routed.
 *
 * Routing is two-tier, which is what keeps the assistant quick:
 *   fast path - the sentence fuzzy-matches a phrase in commands.json, so
 *               it executes immediately (tens of milliseconds, no AI).
 *   AI path   - anything else goes to the local model, which either
 *               answers conversationally or calls tools to get it done.
 */
public class Jarvis {

    private enum State { IDLE, LISTENING }

    /** Fast path needs a confident match; a weak one is better served by the AI. */
    private static final double FAST_PATH_FREE_THRESHOLD = 0.35;
    private static final double FAST_PATH_GRAMMAR_THRESHOLD = 0.20;

    private final Settings settings;
    private final SpeechRecognizer stt;
    private final CommandMatcher matcher;
    private final ActionExecutor executor = new ActionExecutor();
    private final Voice voice;
    private final ClapDetector clapDetector;
    private final ClapPatternFSM clapFSM;
    private final Brain brain; // null when the AI is off or unreachable

    private final BlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(256);
    private final AudioCapture capture;

    private State state = State.IDLE;
    private long listeningSince;
    private long wakeAt;
    /** Ignore wake triggers until this time - stops Jarvis waking itself with its own voice. */
    private long muteWakeUntil;
    /** Skip command frames until this time, so the wake chime isn't transcribed. */
    private long skipCommandUntil;

    public Jarvis(Settings settings, CommandRegistry registry) throws Exception {
        this.settings = settings;
        this.capture = new AudioCapture(frames, settings.inputDevice);

        var grammar = settings.strictGrammar
                ? registry.commands().stream()
                    .flatMap(c -> c.phrases().stream())
                    .map(CommandMatcher::normalise)
                    .distinct().toList()
                : java.util.List.<String>of();
        this.stt = new SpeechRecognizer(settings.modelPath, settings.wakeWord, grammar);
        this.matcher = new CommandMatcher(registry, settings.matchThreshold);
        this.voice = buildVoice(settings);
        this.clapDetector = new ClapDetector(settings.clapSensitivity);
        this.clapFSM = settings.clapEnabled ? ClapPatternFSM.fromString(settings.clapPattern) : null;
        this.brain = buildBrain(settings, registry);
    }

    /**
     * Pick the best available voice, degrading gracefully:
     * Kokoro (most natural) -> Piper (lighter) -> Windows SAPI (always works).
     */
    private static Voice buildVoice(Settings settings) throws Exception {
        String choice = settings.voice == null ? "kokoro" : settings.voice.toLowerCase();

        if (choice.equals("kokoro")) {
            try {
                KokoroVoice.verify(Path.of(settings.kokoroScript),
                        Path.of(settings.kokoroModel), Path.of(settings.kokoroVoices));
                String python = KokoroVoice.resolvePython(settings.pythonExe);
                if (!python.equals(settings.pythonExe)) {
                    System.out.println("[tts] using python at " + python);
                }
                Voice v = new KokoroVoice(python,
                        Path.of(settings.kokoroScript),
                        Path.of(settings.kokoroModel),
                        Path.of(settings.kokoroVoices),
                        settings.kokoroVoiceName,
                        settings.kokoroSpeed,
                        settings.outputDevice);
                System.out.println("[tts] kokoro neural voice (" + settings.kokoroVoiceName + ")");
                return v;
            } catch (Exception e) {
                System.err.println("[tts] kokoro unavailable (" + e.getMessage()
                        + ") - trying piper. Run setup-voice.ps1 to install it.");
                choice = "piper";
            }
        }

        if (choice.equals("piper")) {
            try {
                PiperVoice.verify(Path.of(settings.piperExe), Path.of(settings.piperModel));
                Voice v = new PiperVoice(Path.of(settings.piperExe),
                        Path.of(settings.piperModel), settings.piperSampleRate,
                        settings.outputDevice);
                System.out.println("[tts] piper neural voice");
                return v;
            } catch (Exception e) {
                System.err.println("[tts] piper unavailable (" + e.getMessage()
                        + ") - falling back to the Windows voice.");
            }
        }

        System.out.println("[tts] windows voice (sapi)");
        return new Speaker();
    }

    /** Wire up the local model, if it's enabled and actually running. */
    private Brain buildBrain(Settings settings, CommandRegistry registry) {
        if (!settings.aiEnabled) {
            System.out.println("[ai] disabled in settings");
            return null;
        }
        OllamaClient client = new OllamaClient(settings.ollamaUrl, settings.ollamaModel, settings.aiMaxTokens);
        if (!client.available()) {
            System.err.println("[ai] Ollama not reachable at " + settings.ollamaUrl
                    + " (or model " + settings.ollamaModel + " missing) - commands still work, conversation won't.");
            return null;
        }

        Secrets secrets = Secrets.load(Path.of("config/secrets.json"));

        GeminiClient gemini = null;
        if (settings.geminiEnabled) {
            String key = secrets.get("geminiApiKey", "GEMINI_API_KEY");
            if (key.isBlank()) {
                System.out.println("[gemini] no API key - questions answered locally only "
                        + "(run setup-gemini.ps1 for better answers)");
            } else {
                gemini = new GeminiClient(key, settings.geminiModel);
                System.out.println("[gemini] " + settings.geminiModel + " ready for questions");
            }
        }

        SpotifyClient spotify = null;
        if (settings.spotifyClientId != null && !settings.spotifyClientId.isBlank()) {
            SpotifyAuth auth = new SpotifyAuth(settings.spotifyClientId,
                    Path.of("config/spotify-tokens.json"));
            if (auth.hasRefreshToken()) {
                spotify = new SpotifyClient(auth);
                System.out.println("[spotify] connected");
            } else {
                System.out.println("[spotify] not authorised yet - run setup-spotify.ps1");
            }
        }

        System.out.println("[ai] " + settings.ollamaModel + " ready");
        // Load the model into VRAM now so the first question isn't slow.
        Thread warm = new Thread(client::warmUp, "ai-warmup");
        warm.setDaemon(true);
        warm.start();

        return new Brain(client,
                new ToolBox(registry, matcher, executor, spotify, gemini),
                settings.persona,
                settings.speakAnswersVerbatim);
    }

    /**
     * Speak one line using the configured voice, then stop. Used by
     * --say to audition voices without involving the microphone.
     */
    public static void speakOnce(Settings settings, String line) throws Exception {
        Voice voice = buildVoice(settings);
        System.out.println("[say] " + line);
        voice.say(line);
        if (voice instanceof jarvis.tts.StreamingVoice sv) {
            // Wait for real silence rather than guessing: the first
            // utterance also pays the model's load time.
            sv.awaitQuiet(1200, 60_000);
        } else {
            Thread.sleep(voice.estimateMillis(line) + 1500);
        }
        voice.close();
    }

    public void run() {
        Thread captureThread = new Thread(capture, "audio-capture");
        captureThread.setDaemon(true);
        captureThread.start();

        System.out.println("Jarvis is listening. Say \"" + settings.wakeWord + "\""
                + (clapFSM != null ? " or clap the pattern [" + settings.clapPattern + "]" : "")
                + ", then speak. Ctrl+C to quit.");

        try {
            while (true) {
                byte[] frame = frames.take();
                long now = System.currentTimeMillis();
                switch (state) {
                    case IDLE -> idleFrame(frame, now);
                    case LISTENING -> listeningFrame(frame, now);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            shutdown();
        }
    }

    private void idleFrame(byte[] frame, long now) {
        if (now < muteWakeUntil) {
            return; // still speaking a reply; don't listen to ourselves
        }
        if (clapFSM != null && clapDetector.process(frame, now) && clapFSM.onClap(now)) {
            System.out.println("[wake] clap pattern recognised");
            wake();
            return;
        }
        if (stt.feedWake(frame)) {
            System.out.println("[wake] wake word heard");
            wake();
        }
    }

    private void wake() {
        Chime.play();
        frames.clear();            // drop stale audio; listen to what comes next
        stt.resetCommand();
        state = State.LISTENING;
        wakeAt = System.currentTimeMillis();
        listeningSince = wakeAt;
        skipCommandUntil = wakeAt + 350; // let the chime finish before transcribing
        System.out.println("[state] LISTENING...");
    }

    private void listeningFrame(byte[] frame, long now) {
        if (now < skipCommandUntil) return;

        SpeechRecognizer.Heard heard = stt.feedCommand(frame);
        if (heard == null && now - listeningSince > settings.commandTimeoutMs) {
            heard = stt.finishCommand();
        }
        if (heard == null) return;

        long sttDoneAt = System.currentTimeMillis();
        state = State.IDLE;
        stt.resetWake();
        if (clapFSM != null) clapFSM.reset();

        route(heard, sttDoneAt);
        // Anything captured while thinking or speaking is stale.
        frames.clear();
    }

    /** Decide between the fast path and the AI, then act. */
    private void route(SpeechRecognizer.Heard heard, long sttDoneAt) {
        String spoken = heard.best();
        System.out.printf("[stt] free=\"%s\" grammar=\"%s\"%n", heard.free(), heard.grammar());

        if (spoken.isBlank()) {
            reply("I didn't catch that");
            return;
        }

        Optional<CommandMatcher.Match> fast = fastPathMatch(heard);
        if (fast.isPresent()) {
            runCommand(fast.get(), sttDoneAt);
            return;
        }

        if (brain == null) {
            reply("Sorry, I don't know that one");
            return;
        }

        System.out.println("[ai] thinking...");
        Optional<String> answer = brain.handle(spoken);
        long done = System.currentTimeMillis();
        System.out.printf("[latency] listen %dms | think %dms | wake-to-done %dms%n",
                sttDoneAt - wakeAt, done - sttDoneAt, done - wakeAt);
        answer.ifPresent(this::reply);
    }

    /**
     * A confident match against a configured command. The free-vocabulary
     * transcript is tried first because it is what the user actually said;
     * the grammar transcript is a stricter fallback for when free-form
     * recognition garbles a known phrase.
     */
    private Optional<CommandMatcher.Match> fastPathMatch(SpeechRecognizer.Heard heard) {
        Optional<CommandMatcher.Match> free = matcher.match(heard.free());
        if (free.isPresent() && free.get().score() <= FAST_PATH_FREE_THRESHOLD) {
            return free;
        }
        Optional<CommandMatcher.Match> grammar = matcher.match(heard.grammar());
        if (grammar.isPresent() && grammar.get().score() <= FAST_PATH_GRAMMAR_THRESHOLD) {
            return grammar;
        }
        return Optional.empty();
    }

    private void runCommand(CommandMatcher.Match m, long sttDoneAt) {
        System.out.printf("[fast] matched \"%s\" (score %.2f)%n", m.command().name(), m.score());
        try {
            String spoken = executor.execute(m.command());
            long done = System.currentTimeMillis();
            System.out.printf("[latency] listen %dms | act %dms | wake-to-done %dms%n",
                    sttDoneAt - wakeAt, done - sttDoneAt, done - wakeAt);
            reply(spoken);
        } catch (ActionExecutor.ExitRequested e) {
            voice.say(e.getMessage());
            try { Thread.sleep(1800); } catch (InterruptedException ignored) {}
            shutdown();
            System.exit(0);
        } catch (Exception e) {
            System.err.println("[action] failed: " + e.getMessage());
            reply("That didn't work");
        }
    }

    /**
     * Speak a reply and mute wake detection while it plays, so the
     * assistant doesn't hear its own voice through the speakers and
     * trigger itself.
     */
    private void reply(String text) {
        System.out.println("[say] " + text);
        muteWakeUntil = System.currentTimeMillis() + voice.estimateMillis(text);
        voice.say(text);
        // Drop anything the wake recognizer buffered while we were talking.
        stt.resetWake();
        if (clapFSM != null) clapFSM.reset();
    }

    private void shutdown() {
        capture.stop();
        voice.close();
        stt.close();
    }
}
