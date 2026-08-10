package jarvis.tts;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;

/**
 * Text-to-speech via Windows' built-in voice (System.Speech / SAPI).
 *
 * A single PowerShell process is started once and kept alive for the
 * whole session, reading lines from stdin and speaking them. That way
 * each reply costs ~0ms of process-startup latency instead of ~500ms
 * if we launched PowerShell per sentence.
 */
public class Speaker implements AutoCloseable {

    private static final String PS_SCRIPT =
            "[Console]::InputEncoding=[System.Text.Encoding]::UTF8;" +
            "Add-Type -AssemblyName System.Speech;" +
            "$s=New-Object System.Speech.Synthesis.SpeechSynthesizer;" +
            "$s.Rate=1;" +
            "while(($l=[Console]::In.ReadLine()) -ne $null){" +
            "  if($l.Trim() -ne ''){ $s.Speak($l) }" +
            "}";

    private final Process process;
    private final BufferedWriter stdin;

    public Speaker() throws IOException {
        process = new ProcessBuilder("powershell", "-NoProfile", "-NonInteractive", "-Command", PS_SCRIPT)
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
    }

    /** Queue a sentence to be spoken (returns immediately; speech happens in the helper process). */
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
        try {
            stdin.close();
        } catch (IOException ignored) {
        }
        process.destroy();
    }
}
