package jarvis.tts;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Neural text-to-speech via Piper — offline, free, and far more natural
 * than the built-in Windows voice.
 *
 * Piper is launched once in raw-output mode and kept alive for the whole
 * session: sentences go in on stdin, 16-bit PCM comes back on stdout. A
 * pump thread streams that audio straight to the speakers, so there is no
 * per-sentence process startup and no temporary WAV files.
 */
public class PiperVoice implements Voice {

    private final Process process;
    private final BufferedWriter stdin;
    private final Thread pump;
    private volatile boolean running = true;

    /**
     * @param piperExe   path to piper.exe
     * @param modelOnnx  path to the .onnx voice model
     * @param sampleRate model's sample rate (22050 for the "medium" voices)
     */
    public PiperVoice(Path piperExe, Path modelOnnx, int sampleRate) throws IOException {
        if (!Files.exists(piperExe)) throw new IOException("piper.exe not found at " + piperExe);
        if (!Files.exists(modelOnnx)) throw new IOException("voice model not found at " + modelOnnx);

        process = new ProcessBuilder(
                piperExe.toString(),
                "--model", modelOnnx.toString(),
                "--output_raw")
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .start();

        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        pump = new Thread(() -> playStream(process.getInputStream(), sampleRate), "piper-audio");
        pump.setDaemon(true);
        pump.start();
    }

    /** Continuously copy Piper's raw PCM output to the speakers. */
    private void playStream(InputStream in, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, 8192);
            line.start();
            byte[] buf = new byte[2048];
            int n;
            while (running && (n = in.read(buf)) > 0) {
                line.write(buf, 0, n);
            }
        } catch (Exception e) {
            if (running) System.err.println("[tts] piper audio stopped: " + e.getMessage());
        }
    }

    @Override
    public synchronized void say(String text) {
        try {
            stdin.write(text.replace("\r", " ").replace("\n", " "));
            stdin.newLine();
            stdin.flush();
        } catch (IOException e) {
            System.err.println("[tts] " + e.getMessage());
        }
    }

    @Override
    public void close() {
        running = false;
        try {
            stdin.close();
        } catch (IOException ignored) {
        }
        process.destroy();
    }
}
