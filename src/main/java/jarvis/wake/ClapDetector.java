package jarvis.wake;

import jarvis.audio.AudioCapture;

/**
 * Detects individual claps from raw audio frames (ST221: adaptive
 * statistics — a clap is an energy spike far above the rolling
 * background noise level).
 *
 * The noise floor is tracked with an exponential moving average (EMA).
 * A frame counts as a clap onset when its RMS energy exceeds
 * max(absoluteFloor, ema * sensitivity) and we are not inside the
 * debounce window of the previous clap.
 */
public class ClapDetector {

    private final double sensitivity;   // multiplier over the noise floor
    private final double absoluteFloor; // never trigger below this RMS
    private final long debounceMs;      // minimum gap between claps

    private double ema = 0.01;          // rolling noise-floor estimate
    private static final double ALPHA = 0.05;
    private long lastClapMs = -10_000;

    public ClapDetector(double sensitivity) {
        this(sensitivity, 0.12, 150);
    }

    public ClapDetector(double sensitivity, double absoluteFloor, long debounceMs) {
        this.sensitivity = sensitivity;
        this.absoluteFloor = absoluteFloor;
        this.debounceMs = debounceMs;
    }

    /**
     * Feed one audio frame. Returns true if this frame is a clap onset.
     */
    public boolean process(byte[] frame, long nowMs) {
        double energy = AudioCapture.rms(frame);
        double threshold = Math.max(absoluteFloor, ema * sensitivity);

        boolean clap = energy > threshold && (nowMs - lastClapMs) >= debounceMs;
        if (clap) {
            lastClapMs = nowMs;
        } else {
            // Only let quiet frames influence the noise floor, so a burst
            // of claps doesn't raise the threshold against itself.
            ema = (1 - ALPHA) * ema + ALPHA * energy;
        }
        return clap;
    }
}
