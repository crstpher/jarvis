package jarvis.stt;

import com.google.gson.JsonParser;
import org.vosk.LibVosk;
import org.vosk.LogLevel;
import org.vosk.Model;
import org.vosk.Recognizer;

import java.io.IOException;
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

    private final Model model;
    private final Recognizer wakeRecognizer;
    private final Recognizer commandRecognizer;
    private final String wakeWord;

    public SpeechRecognizer(String modelPath, String wakeWord) throws IOException {
        LibVosk.setLogLevel(LogLevel.WARNINGS);
        this.model = new Model(modelPath);
        this.wakeWord = wakeWord.toLowerCase(Locale.ROOT);
        this.wakeRecognizer = new Recognizer(model, SAMPLE_RATE,
                "[\"" + this.wakeWord + "\", \"[unk]\"]");
        this.commandRecognizer = new Recognizer(model, SAMPLE_RATE);
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
     * Feed a command-mode frame. Returns the final transcript once the
     * endpointer detects the user has stopped speaking, else null.
     */
    public String feedCommand(byte[] frame) {
        if (commandRecognizer.acceptWaveForm(frame, frame.length)) {
            return extractText(commandRecognizer.getResult());
        }
        return null;
    }

    /** Force whatever has been heard so far to be finalised (timeout path). */
    public String finishCommand() {
        return extractText(commandRecognizer.getFinalResult());
    }

    public void resetCommand() {
        commandRecognizer.reset();
    }

    public void resetWake() {
        wakeRecognizer.reset();
    }

    private static String extractText(String resultJson) {
        return JsonParser.parseString(resultJson).getAsJsonObject()
                .get("text").getAsString().trim();
    }

    @Override
    public void close() {
        wakeRecognizer.close();
        commandRecognizer.close();
        model.close();
    }
}
