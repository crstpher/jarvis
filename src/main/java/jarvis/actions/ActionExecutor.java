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
}
