package jarvis.actions;

import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Executes a matched command and returns the sentence Jarvis should
 * speak in reply. "launch" and "url" go through the Windows shell's
 * `start`, which understands exe names, full paths, steam:// URIs and
 * https:// links alike - so no per-app scripts are needed.
 */
public class ActionExecutor {

    /** Thrown for the special "exit" action so the orchestrator can shut down cleanly. */
    public static class ExitRequested extends RuntimeException {
        public ExitRequested(String reply) { super(reply); }
    }

    public String execute(CommandSpec cmd) throws IOException {
        CommandSpec.ActionSpec action = cmd.action();
        String reply = cmd.reply() != null ? cmd.reply() : "Done";

        switch (action.type()) {
            case "launch", "url" -> {
                new ProcessBuilder("cmd", "/c", "start", "", action.target()).start();
            }
            case "shell" -> {
                new ProcessBuilder("powershell", "-NoProfile", "-Command", action.target()).start();
            }
            case "close" -> close(cmd);
            case "time" -> {
                String time = LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"));
                reply = "It's " + time;
            }
            case "exit" -> throw new ExitRequested(reply);
            default -> {
                return "I don't know how to handle action type " + action.type();
            }
        }
        return reply;
    }

    /**
     * Kill the target process. Some games (e.g. Marvel Rivals) run
     * elevated, so a normal-privilege taskkill is denied. setup-admin.ps1
     * registers an elevated Task Scheduler task per close command
     * ("Jarvis kill <name>"); triggering our own pre-approved task needs
     * no UAC prompt. If the task doesn't exist we fall back to plain
     * taskkill, which is enough for normal apps.
     */
    private void close(CommandSpec cmd) throws IOException {
        try {
            Process task = new ProcessBuilder("schtasks", "/run", "/tn", "Jarvis kill " + cmd.name())
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (task.waitFor() == 0) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        new ProcessBuilder("taskkill", "/F", "/IM", cmd.action().target())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
    }
}
