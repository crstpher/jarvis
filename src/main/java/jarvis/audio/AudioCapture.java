package jarvis.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.TargetDataLine;
import java.util.Arrays;
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
    private volatile boolean running = true;

    public AudioCapture(BlockingQueue<byte[]> out) {
        this.out = out;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        AudioFormat format = new AudioFormat(SAMPLE_RATE, 16, 1, true, false);
        try (TargetDataLine line = AudioSystem.getTargetDataLine(format)) {
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
