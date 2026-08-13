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
        String image = cmd.action().target();
        boolean wasRunning = isRunning(image);
        System.out.println("[close] target " + image + " running=" + wasRunning);

        try {
            Process task = new ProcessBuilder("schtasks", "/run", "/tn", "Jarvis kill " + cmd.name())
                    .redirectErrorStream(true).start();
            int rc = task.waitFor();
            System.out.println("[close] elevated task rc=" + rc);
            if (rc != 0) {
                Process kill = new ProcessBuilder("taskkill", "/F", "/IM", image)
                        .redirectErrorStream(true).start();
                System.out.println("[close] fallback taskkill rc=" + kill.waitFor());
            }
            // The scheduled task runs asynchronously; give it a moment.
            Thread.sleep(1200);
            System.out.println("[close] still running after kill=" + isRunning(image));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** True if any running process matches the image name (wildcards allowed). */
    private boolean isRunning(String image) {
        try {
            Process p = new ProcessBuilder("tasklist", "/FI", "IMAGENAME eq " + image)
                    .redirectErrorStream(true).start();
            String out = new String(p.getInputStream().readAllBytes());
            p.waitFor();
            String stem = image.replace("*", "").replace(".exe", "");
            return out.toLowerCase().contains(stem.toLowerCase());
        } catch (Exception e) {
            return false;
        }
    }
}
