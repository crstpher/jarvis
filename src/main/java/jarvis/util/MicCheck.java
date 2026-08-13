package jarvis.util;

import jarvis.audio.AudioCapture;
import jarvis.config.Settings;
import jarvis.stt.SpeechRecognizer;
import jarvis.wake.ClapDetector;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * Diagnostic mode (run.ps1 --mic): shows a live input level meter,
 * flags detected claps, and prints everything the wake-word recognizer
 * hears. Lets you verify the right microphone is being captured and
 * that "jarvis" is actually being recognised.
 */
public final class MicCheck {

    private MicCheck() {}

    public static void run(Settings settings, long seconds) throws Exception {
        System.out.println("== Mic check: speak, say \"" + settings.wakeWord
                + "\", and clap. Running for " + seconds + "s ==");

        BlockingQueue<byte[]> frames = new ArrayBlockingQueue<>(256);
        AudioCapture capture = new AudioCapture(frames, settings.inputDevice);
        Thread t = new Thread(capture, "audio-capture");
        t.setDaemon(true);
        t.start();

        ClapDetector claps = new ClapDetector(settings.clapSensitivity);
        try (SpeechRecognizer stt = new SpeechRecognizer(settings.modelPath, settings.wakeWord)) {
            long end = System.currentTimeMillis() + seconds * 1000;
            double windowPeak = 0;
            long lastPrint = 0;
            String lastHeard = "";

            while (System.currentTimeMillis() < end) {
                byte[] frame = frames.poll(500, java.util.concurrent.TimeUnit.MILLISECONDS);
                long now = System.currentTimeMillis();
                if (frame == null) {
                    if (now - lastPrint > 2000) {
                        System.out.println("!! no audio arriving from the microphone at all");
                        lastPrint = now;
                    }
                    continue;
                }

                windowPeak = Math.max(windowPeak, AudioCapture.rms(frame));

                if (claps.process(frame, now)) {
                    System.out.printf("%n[clap] detected (level %.2f)%n", AudioCapture.rms(frame));
                }

                String heard = stt.feedWakeHearing(frame);
                if (!heard.isBlank() && !heard.equals(lastHeard)) {
                    System.out.printf("%n[hearing] \"%s\"%s%n", heard,
                            heard.contains(settings.wakeWord) ? "  <-- WAKE WORD!" : "");
                    lastHeard = heard;
                }

                if (now - lastPrint >= 500) {
                    int bars = (int) Math.min(40, windowPeak * 120);
                    System.out.printf("\rlevel %-40s %.3f ", "#".repeat(bars), windowPeak);
                    windowPeak = 0;
                    lastPrint = now;
                }
            }
        }
        capture.stop();
        System.out.println("\n== Mic check finished ==");
        System.out.println("If the level bar stayed near 0.000 while you spoke, the wrong device is");
        System.out.println("being captured: set \"inputDevice\" in config/settings.json to part of your");
        System.out.println("mic's name, e.g. \"Quadcast\", and try again.");
    }
}
