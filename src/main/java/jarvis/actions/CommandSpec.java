package jarvis.actions;

import java.util.List;

/**
 * One entry from config/commands.json.
 *
 * action types:
 *   launch - open an app or URI via the Windows shell ("start"), e.g.
 *            "notepad", "steam://open/main", or a full .exe path
 *   url    - open a web page in the default browser
 *   shell  - run an arbitrary PowerShell command
 *   close  - kill a process by image name (wildcards allowed, e.g. "Marvel*")
 *   time   - speak the current time (no target needed)
 *   exit   - shut the assistant down
 */
public record CommandSpec(String name, List<String> phrases, ActionSpec action, String reply) {

    public record ActionSpec(String type, String target) {}
}
