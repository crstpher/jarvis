package jarvis.tts;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Shared machinery for any speech engine that runs as a long-lived
 * child process: text goes in on stdin, raw 16-bit PCM comes back on
 * stdout, and a daemon thread pumps that audio to the speakers.
 *
 * Both supported engines fit this shape, so the difference between them
 * is reduced to a command line and a sample rate.
 */
public abstract class StreamingVoice implements Voice {

    private final Process process;
    private final BufferedWriter stdin;
    private volatile boolean running = true;

    protected StreamingVoice(List<String> command, Map<String, String> env, int sampleRate)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = pb.start();

        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        Thread pump = new Thread(() -> playStream(process.getInputStream(), sampleRate), "tts-audio");
        pump.setDaemon(true);
        pump.start();
    }

    /** Continuously copy the engine's PCM output to the speakers. */
    private void playStream(InputStream in, int sampleRate) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, 16384);
            line.start();
            byte[] buf = new byte[4096];
            int n;
            while (running && (n = in.read(buf)) > 0) {
                line.write(buf, 0, n);
            }
        } catch (Exception e) {
            if (running) System.err.println("[tts] audio stream stopped: " + e.getMessage());
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

    /** True while the engine process is alive. */
    public boolean healthy() {
        return process.isAlive();
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
