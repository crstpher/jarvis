package jarvis.util;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;

/**
 * Short rising two-tone chime played the instant Jarvis wakes up,
 * so you know it's listening before you say the command (CS242:
 * immediate feedback beats a spinner you can't hear).
 */
public final class Chime {

    private Chime() {}

    public static void play() {
        Thread t = new Thread(Chime::playBlocking, "chime");
        t.setDaemon(true);
        t.start();
    }

    /**
     * A quieter, single note for re-opening the mic mid-conversation.
     * The full two-tone chime every turn would be wearing.
     */
    public static void soft() {
        Thread t = new Thread(() -> {
            AudioFormat format = new AudioFormat(16000f, 16, 1, true, false);
            try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                line.open(format);
                line.start();
                byte[] note = tone(880, 0.06, 16000f, 0.14);
                line.write(note, 0, note.length);
                line.drain();
            } catch (Exception ignored) {
            }
        }, "chime-soft");
        t.setDaemon(true);
        t.start();
    }

    private static void playBlocking() {
        float sr = 16000f;
        AudioFormat format = new AudioFormat(sr, 16, 1, true, false);
        try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format);
            line.start();
            byte[] a = tone(660, 0.09, sr);
            byte[] b = tone(880, 0.11, sr);
            line.write(a, 0, a.length);
            line.write(b, 0, b.length);
            line.drain();
        } catch (Exception ignored) {
        }
    }

    private static byte[] tone(double freq, double seconds, float sampleRate) {
        return tone(freq, seconds, sampleRate, 0.35);
    }

    private static byte[] tone(double freq, double seconds, float sampleRate, double gain) {
        int n = (int) (seconds * sampleRate);
        byte[] out = new byte[n * 2];
        for (int i = 0; i < n; i++) {
            // Small fade in/out to avoid clicks.
            double env = Math.min(1.0, Math.min(i / (0.01 * sampleRate), (n - i) / (0.02 * sampleRate)));
            short s = (short) (Math.sin(2 * Math.PI * freq * i / sampleRate) * gain * env * Short.MAX_VALUE);
            out[i * 2] = (byte) (s & 0xff);
            out[i * 2 + 1] = (byte) (s >> 8);
        }
        return out;
    }
}
