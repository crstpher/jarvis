package jarvis;

import jarvis.actions.CommandRegistry;
import jarvis.config.Settings;
import jarvis.core.Jarvis;

import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Mixer;
import java.nio.file.Files;
import java.nio.file.Path;

public class Main {

    public static void main(String[] args) throws Exception {
        Settings settings = Settings.load(Path.of("config/settings.json"));

        if (args.length > 0 && args[0].equals("--check")) {
            check(settings);
            return;
        }

        if (!Files.isDirectory(Path.of(settings.modelPath))) {
            System.err.println("Speech model not found at: " + settings.modelPath);
            System.err.println("Run setup.ps1 first to download it (~40 MB, one time only).");
            System.exit(1);
        }

        CommandRegistry registry = CommandRegistry.load(Path.of("config/commands.json"));
        new Jarvis(settings, registry).run();
    }

    /** Sanity check: config, model, and audio devices - without opening the mic. */
    private static void check(Settings settings) throws Exception {
        System.out.println("== Jarvis environment check ==");
        System.out.println("Model path: " + settings.modelPath
                + (Files.isDirectory(Path.of(settings.modelPath)) ? " [OK]" : " [MISSING - run setup.ps1]"));

        CommandRegistry registry = CommandRegistry.load(Path.of("config/commands.json"));
        System.out.println("Commands loaded: " + registry.commands().size());

        System.out.println("Audio input devices:");
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            if (mixer.getTargetLineInfo().length > 0) {
                System.out.println("  - " + info.getName());
            }
        }

        if (Files.isDirectory(Path.of(settings.modelPath))) {
            System.out.println("Loading speech model (first load takes a few seconds)...");
            try (var stt = new jarvis.stt.SpeechRecognizer(settings.modelPath, settings.wakeWord)) {
                System.out.println("Speech model loaded [OK]");
            }
        }
        System.out.println("All checks passed.");
    }
}
