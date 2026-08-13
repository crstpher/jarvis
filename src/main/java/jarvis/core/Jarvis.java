package jarvis.core;

import jarvis.actions.ActionExecutor;
import jarvis.actions.CommandRegistry;
import jarvis.audio.AudioCapture;
import jarvis.config.Settings;
import jarvis.nlu.CommandMatcher;
import jarvis.stt.SpeechRecognizer;
import jarvis.tts.Speaker;
import jarvis.util.Chime;
import jarvis.wake.ClapDetector;
import jarvis.wake.ClapPatternFSM;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The orchestrator: consumer side of the audio pipeline (CS240).
 *
 * A two-state machine drives everything:
 *   IDLE      - every frame goes to the clap detector + the
 *               grammar-restricted wake-word recognizer.
 *   LISTENING - after a wake trigger (chime!), frames go to the
 *               full-vocabulary recognizer until Vosk's endpointer
 *               says the sentence is over (or we time out), then the
 *               transcript is fuzzy-matched and the action executed.
 */
public class Jarvis {

    private enum State { IDLE, LISTENING }

    private final Settings settings;
    private final SpeechRecognizer stt;
    private final CommandMatcher matcher;
    private final ActionExecutor executor = new ActionExecutor();
    private final Speaker speaker;
    private final ClapDetector clapDetector;
    private final ClapPatternFSM clapFSM;

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
        this.stt = new SpeechRecognizer(settings.modelPath, settings.wakeWord);
        this.matcher = new CommandMatcher(registry, settings.matchThreshold);
        this.speaker = new Speaker();
        this.clapDetector = new ClapDetector(settings.clapSensitivity);
        this.clapFSM = settings.clapEnabled ? ClapPatternFSM.fromString(settings.clapPattern) : null;
    }

    public void run() {
        Thread captureThread = new Thread(capture, "audio-capture");
        captureThread.setDaemon(true);
        captureThread.start();

        System.out.println("Jarvis is listening. Say \"" + settings.wakeWord + "\""
                + (clapFSM != null ? " or clap the pattern [" + settings.clapPattern + "]" : "")
                + ", then give a command. Ctrl+C to quit.");

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
        String transcript = stt.feedCommand(frame);
        if (transcript == null && now - listeningSince > settings.commandTimeoutMs) {
            transcript = stt.finishCommand();
        }
        if (transcript == null) return;

        long sttDoneAt = System.currentTimeMillis();
        state = State.IDLE;
        stt.resetWake();
        if (clapFSM != null) clapFSM.reset();

        handleTranscript(transcript, sttDoneAt);
    }

    private void handleTranscript(String transcript, long sttDoneAt) {
        System.out.println("[stt] heard: \"" + transcript + "\"");
        if (transcript.isBlank()) {
            reply("I didn't catch that");
            return;
        }

        var match = matcher.match(transcript);
        if (match.isEmpty()) {
            reply("Sorry, I don't know that one");
            return;
        }

        var m = match.get();
        System.out.printf("[nlu] matched \"%s\" (score %.2f)%n", m.command().name(), m.score());
        try {
            String reply = executor.execute(m.command());
            long done = System.currentTimeMillis();
            System.out.printf("[latency] listen %dms | act %dms | wake-to-done %dms%n",
                    sttDoneAt - wakeAt, done - sttDoneAt, done - wakeAt);
            reply(reply);
        } catch (ActionExecutor.ExitRequested e) {
            speaker.say(e.getMessage());
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
     * trigger itself. Duration is a rough words-per-minute estimate.
     */
    private void reply(String text) {
        long speakMs = 800 + text.split("\\s+").length * 350L;
        muteWakeUntil = System.currentTimeMillis() + speakMs;
        speaker.say(text);
        // Drop anything the wake recognizer buffered while we were talking.
        stt.resetWake();
        if (clapFSM != null) clapFSM.reset();
    }

    private void shutdown() {
        capture.stop();
        speaker.close();
        stt.close();
    }
}
