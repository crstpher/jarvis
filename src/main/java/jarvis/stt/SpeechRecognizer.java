package jarvis.stt;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wraps the Vosk offline speech engine with two recognizers:
 *
 *  - wake recognizer: grammar-restricted to just the wake word plus
 *    [unk] (everything else). Restricting the grammar makes idle
 *    listening cheap and keeps false positives low.
 *  - command recognizer: full vocabulary, used only during the short
 *    window after a wake trigger. Vosk's endpointer tells us when the
 *    user has finished speaking (acceptWaveForm returns true).
 */
public class SpeechRecognizer implements AutoCloseable {

    public static final float SAMPLE_RATE = 16000f;

    /** What was heard during a command window, from both recognizers. */
    public record Heard(String grammar, String free) {
        /** Prefer the free-vocabulary text; fall back to the grammar one. */
        public String best() {
            return free != null && !free.isBlank() ? free : grammar;
        }
    }

    private final Model model;
    private final Recognizer wakeRecognizer;
    private final Recognizer commandRecognizer;
    private final Recognizer freeRecognizer;
    private final String wakeWord;

    public SpeechRecognizer(String modelPath, String wakeWord) throws IOException {
        this(modelPath, wakeWord, List.of());
    }

    /**
     * @param commandPhrases if non-empty, the command recognizer's grammar
     *   is restricted to these phrases (plus [unk] for everything else).
     *   Mishearings then snap to the closest known phrase instead of
     *   drifting to arbitrary words - more accurate AND faster.
     */
    public SpeechRecognizer(String modelPath, String wakeWord, List<String> commandPhrases)
            throws IOException {
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        this.model = new Model(modelPath);
        this.wakeWord = wakeWord.toLowerCase(Locale.ROOT);
        this.wakeRecognizer = new Recognizer(model, SAMPLE_RATE,
                "[\"" + this.wakeWord + "\", \"[unk]\"]");
        if (commandPhrases == null || commandPhrases.isEmpty()) {
            this.commandRecognizer = new Recognizer(model, SAMPLE_RATE);
        } else {
            List<String> grammar = new ArrayList<>(commandPhrases);
            grammar.add("[unk]");
            this.commandRecognizer = new Recognizer(model, SAMPLE_RATE, new Gson().toJson(grammar));
        }
        // Unrestricted vocabulary, for anything that isn't a known command.
        this.freeRecognizer = new Recognizer(model, SAMPLE_RATE);
    }

    /** Feed an idle-mode frame; true if the wake word was just heard. */
    public boolean feedWake(byte[] frame) {
        if (feedWakeHearing(frame).contains(wakeWord)) {
            wakeRecognizer.reset();
            return true;
        }
        return false;
    }

    /** Feed an idle-mode frame and return whatever the wake recognizer currently hears. */
    public String feedWakeHearing(byte[] frame) {
        boolean endOfUtterance = wakeRecognizer.acceptWaveForm(frame, frame.length);
        String json = endOfUtterance ? wakeRecognizer.getResult() : wakeRecognizer.getPartialResult();
        String field = endOfUtterance ? "text" : "partial";
        return JsonParser.parseString(json).getAsJsonObject()
                .get(field).getAsString();
    }

    /**
     * Feed a command-mode frame to both recognizers. Returns what was
     * heard once the endpointer decides the user has stopped speaking,
     * else null.
     *
     * Running both in parallel is what lets the assistant stay fast for
     * known commands while still understanding free-form speech: the
     * grammar recognizer gives a clean, snap-to-known-phrase transcript,
     * and the full recognizer catches everything else.
     */
    public Heard feedCommand(byte[] frame) {
        boolean grammarDone = commandRecognizer.acceptWaveForm(frame, frame.length);
        boolean freeDone = freeRecognizer.acceptWaveForm(frame, frame.length);
        if (grammarDone || freeDone) {
            return new Heard(
                    extractText(commandRecognizer.getResult()),
                    extractText(freeRecognizer.getResult()));
        }
        return null;
    }

    /** Force whatever has been heard so far to be finalised (timeout path). */
    public Heard finishCommand() {
        return new Heard(
                extractText(commandRecognizer.getFinalResult()),
                extractText(freeRecognizer.getFinalResult()));
    }

    public void resetCommand() {
        commandRecognizer.reset();
        freeRecognizer.reset();
    }

    public void resetWake() {
        wakeRecognizer.reset();
    }

    private static String extractText(String resultJson) {
        return JsonParser.parseString(resultJson).getAsJsonObject()
                .get("text").getAsString()
                .replace("[unk]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    @Override
    public void close() {
        wakeRecognizer.close();
        commandRecognizer.close();
        freeRecognizer.close();
        model.close();
    }
}
