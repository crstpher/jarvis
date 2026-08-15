package jarvis.tts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Piper neural text-to-speech — fast and fully offline, but audibly
 * synthetic next to Kokoro. Kept as the lightweight option: it needs no
 * Python runtime, just a single executable and a voice model.
 */
public class PiperVoice extends StreamingVoice {

    /**
     * @param piperExe   path to piper.exe
     * @param modelOnnx  path to the .onnx voice model
     * @param sampleRate model's sample rate (22050 for the "medium" voices)
     */
    public PiperVoice(Path piperExe, Path modelOnnx, int sampleRate) throws IOException {
        super(List.of(piperExe.toString(),
                        "--model", modelOnnx.toString(),
                        "--output_raw"),
                Map.of(),
                sampleRate);
        // Constructor already launched it; validation below is for clear errors.
    }

    /** Everything Piper needs is present. */
    public static void verify(Path piperExe, Path modelOnnx) throws IOException {
        if (!Files.exists(piperExe)) throw new IOException("piper.exe not found at " + piperExe);
        if (!Files.exists(modelOnnx)) throw new IOException("voice model not found at " + modelOnnx);
    }
}
