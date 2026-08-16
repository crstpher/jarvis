package jarvis.util;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

/**
 * Mirrors everything printed to the console into logs/, so a session can
 * be reviewed after the fact.
 *
 * Reading back what the recognizer actually heard, against what the
 * assistant then did, is how most of the interesting bugs here have been
 * found - and terminal scrollback is a poor place to keep that.
 */
public final class SessionLog {

    /** How many past sessions to keep. */
    private static final int KEEP = 20;

    private SessionLog() {}

    /** Start mirroring stdout and stderr to a timestamped file. */
    public static Path start(Path dir) {
        try {
            Files.createDirectories(dir);
            prune(dir);

            String stamp = LocalDateTime.now()
                    .format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
            Path file = dir.resolve("jarvis-" + stamp + ".log");

            OutputStream fileOut = new FileOutputStream(file.toFile(), true);
            System.setOut(new PrintStream(new Tee(System.out, fileOut), true, StandardCharsets.UTF_8));
            System.setErr(new PrintStream(new Tee(System.err, fileOut), true, StandardCharsets.UTF_8));

            System.out.println("[log] session transcript: " + file.toAbsolutePath());
            return file;
        } catch (IOException e) {
            System.err.println("[log] could not open a session log: " + e.getMessage());
            return null;
        }
    }

    /** Keep only the most recent few logs. */
    private static void prune(Path dir) {
        try (var files = Files.list(dir)) {
            List<Path> old = files
                    .filter(p -> p.getFileName().toString().startsWith("jarvis-"))
                    .sorted(Comparator.comparing((Path p) -> p.getFileName().toString()).reversed())
                    .skip(KEEP)
                    .toList();
            for (Path p : old) Files.deleteIfExists(p);
        } catch (IOException ignored) {
        }
    }

    /** Writes to the console and the log file at once. */
    private static final class Tee extends OutputStream {
        private final OutputStream console;
        private final OutputStream file;

        Tee(OutputStream console, OutputStream file) {
            this.console = console;
            this.file = file;
        }

        @Override
        public void write(int b) throws IOException {
            console.write(b);
            file.write(b);
        }

        @Override
        public void write(byte[] buf, int off, int len) throws IOException {
            console.write(buf, off, len);
            file.write(buf, off, len);
        }

        @Override
        public void flush() throws IOException {
            console.flush();
            file.flush();
        }
    }
}
