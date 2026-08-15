package jarvis.tts;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
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
    /** When audio last reached the sound card, for awaitQuiet(). */
    private volatile long lastAudioAt = 0;
    private volatile SourceDataLine activeLine;

    protected StreamingVoice(List<String> command, Map<String, String> env,
                             int sampleRate, String outputDevice) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().putAll(env);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        process = pb.start();

        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));

        Thread pump = new Thread(() -> playStream(process.getInputStream(), sampleRate, outputDevice),
                "tts-audio");
        pump.setDaemon(true);
        pump.start();
    }

    /** Continuously copy the engine's PCM output to the speakers. */
    private void playStream(InputStream in, int sampleRate, String outputDevice) {
        AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
        try (SourceDataLine line = openLine(format, outputDevice)) {
            line.open(format, 16384);
            line.start();
            activeLine = line;
            byte[] buf = new byte[4096];
            int n;
            long total = 0;
            while (running && (n = in.read(buf)) > 0) {
                if (total == 0) System.out.println("[tts] audio flowing");
                total += n;
                line.write(buf, 0, n);
                lastAudioAt = System.currentTimeMillis();
            }
            if (running) System.out.println("[tts] engine closed the stream after " + total + " bytes");
        } catch (Exception e) {
            if (running) System.err.println("[tts] audio stream failed: " + e);
        } finally {
            activeLine = null;
        }
    }

    /**
     * Open the requested output device, or the Windows default. Same
     * problem as the microphone: the default device is often not the one
     * the user is actually listening to.
     */
    private static SourceDataLine openLine(AudioFormat format, String outputDevice) throws Exception {
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        if (outputDevice != null && !outputDevice.isBlank()) {
            String want = outputDevice.toLowerCase(Locale.ROOT);
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().toLowerCase(Locale.ROOT).contains(want)) {
                    Mixer mixer = AudioSystem.getMixer(mi);
                    if (mixer.isLineSupported(info)) {
                        System.out.println("[tts] output device: " + mi.getName());
                        return (SourceDataLine) mixer.getLine(info);
                    }
                }
            }
            System.err.println("[tts] no output device matching \"" + outputDevice
                    + "\" - using the Windows default");
        }
        return AudioSystem.getSourceDataLine(format);
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

    /**
     * Block until speech has finished: no new audio for {@code quietMs},
     * then drain whatever the sound card still has buffered.
     *
     * Synthesis latency varies (the first utterance includes model load),
     * so waiting on actual silence is the only reliable way to know a
     * line has finished playing.
     */
    public void awaitQuiet(long quietMs, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        try {
            // Wait for audio to start at all.
            while (lastAudioAt == 0 && System.currentTimeMillis() < deadline) {
                Thread.sleep(50);
            }
            // Then wait for it to stop arriving.
            while (System.currentTimeMillis() < deadline
                    && System.currentTimeMillis() - lastAudioAt < quietMs) {
                Thread.sleep(50);
            }
            SourceDataLine line = activeLine;
            if (line != null) line.drain();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
