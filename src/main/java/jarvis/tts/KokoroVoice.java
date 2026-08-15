package jarvis.tts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Kokoro neural text-to-speech — the most natural voice Jarvis can
 * produce without leaving the machine.
 *
 * Runs as a resident Python process (see tools/kokoro/kokoro_server.py)
 * that holds the 82M-parameter model in memory and streams PCM back.
 */
public class KokoroVoice extends StreamingVoice {

    public static final int SAMPLE_RATE = 24000;

    public KokoroVoice(String python, Path script, Path model, Path voices,
                       String voiceName, double speed, String outputDevice) throws IOException {
        super(List.of(python, "-u", script.toString()),
                env(model, voices, voiceName, speed),
                SAMPLE_RATE, outputDevice);
    }

    private static Map<String, String> env(Path model, Path voices, String voiceName, double speed) {
        Map<String, String> env = new LinkedHashMap<>();
        env.put("KOKORO_MODEL", model.toString());
        env.put("KOKORO_VOICES", voices.toString());
        env.put("KOKORO_VOICE", voiceName);
        env.put("KOKORO_SPEED", String.valueOf(speed));
        env.put("KOKORO_LANG", voiceName.startsWith("b") ? "en-gb" : "en-us");
        env.put("PYTHONIOENCODING", "utf-8");
        return env;
    }

    /** Everything Kokoro needs is present. */
    public static void verify(Path script, Path model, Path voices) throws IOException {
        if (!Files.exists(script)) throw new IOException("kokoro_server.py missing at " + script);
        if (!Files.exists(model)) throw new IOException("kokoro model missing at " + model);
        if (!Files.exists(voices)) throw new IOException("kokoro voices missing at " + voices);
    }

    /**
     * Kokoro speaks a touch slower than the Windows voice, so the
     * self-mute window needs to be a little more generous.
     */
    @Override
    public long estimateMillis(String text) {
        return 900 + text.split("\\s+").length * 400L;
    }
}
