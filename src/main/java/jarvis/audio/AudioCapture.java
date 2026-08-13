package jarvis.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.BlockingQueue;

/**
 * Producer side of the audio pipeline (CS240: producer-consumer pattern).
 *
 * Runs on its own thread, continuously reading 20ms frames from the
 * microphone and pushing them onto a BlockingQueue. The consumer (the
 * Jarvis orchestrator) takes frames off the queue at its own pace.
 * If the consumer falls behind and the queue fills, the oldest frames
 * are dropped so the assistant always works with *live* audio.
 */
public class AudioCapture implements Runnable {

    public static final float SAMPLE_RATE = 16000f;
    /** 20ms of 16kHz mono 16-bit audio. */
    public static final int FRAME_BYTES = 320 * 2;

    private final BlockingQueue<byte[]> out;
    private final String deviceName;
    private volatile boolean running = true;

    /** @param deviceName substring of the input device to use, or "" for the Windows default. */
    public AudioCapture(BlockingQueue<byte[]> out, String deviceName) {
        this.out = out;
        this.deviceName = deviceName == null ? "" : deviceName.trim();
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (TargetDataLine line = openLine(format)) {
            line.open(format, FRAME_BYTES * 16);
            line.start();
            byte[] buf = new byte[FRAME_BYTES];
            while (running) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) continue;
                byte[] frame = Arrays.copyOf(buf, n);
                if (!out.offer(frame)) {
                    out.poll();       // drop the oldest frame
                    out.offer(frame); // keep the newest
                }
            }
        } catch (Exception e) {
            System.err.println("[audio] Microphone error: " + e.getMessage());
            System.err.println("[audio] Check that a microphone is connected and not in exclusive use.");
        }
    }

    /**
     * Open the requested capture device, or the Windows default one if
     * no (matching) device name was configured.
     */
    private TargetDataLine openLine(AudioFormat format) throws Exception {
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!deviceName.isEmpty()) {
            String want = deviceName.toLowerCase(Locale.ROOT);
            for (Mixer.Info mi : AudioSystem.getMixerInfo()) {
                if (mi.getName().toLowerCase(Locale.ROOT).contains(want)) {
                    Mixer mixer = AudioSystem.getMixer(mi);
                    if (mixer.isLineSupported(info)) {
                        System.out.println("[audio] capturing from: " + mi.getName());
                        return (TargetDataLine) mixer.getLine(info);
                    }
                }
            }
            System.err.println("[audio] no input device matching \"" + deviceName
                    + "\" found - falling back to the Windows default device");
        }
        System.out.println("[audio] capturing from the Windows default input device");
        return AudioSystem.getTargetDataLine(format);
    }

    /** RMS energy of a 16-bit little-endian frame, normalised to 0..1. */
    public static double rms(byte[] frame) {
        long sum = 0;
        int n = 0;
        for (int i = 0; i + 1 < frame.length; i += 2) {
            int s = (short) ((frame[i] & 0xff) | (frame[i + 1] << 8));
            sum += (long) s * s;
            n++;
        }
        if (n == 0) return 0;
        return Math.sqrt((double) sum / n) / 32768.0;
    }
}
