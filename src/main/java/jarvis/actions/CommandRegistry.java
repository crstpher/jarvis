package jarvis.actions;

import com.google.gson.Gson;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Loads the phrase -> action mappings from config/commands.json.
 * Adding a new voice command is a config edit, not a code change.
 */
public class CommandRegistry {

    private record FileFormat(List<CommandSpec> commands) {}

    private final List<CommandSpec> commands;

    public CommandRegistry(List<CommandSpec> commands) {
        this.commands = List.copyOf(commands);
    }

    public static CommandRegistry load(Path path) throws IOException {
        try (Reader r = Files.newBufferedReader(path)) {
            FileFormat f = new Gson().fromJson(r, FileFormat.class);
            if (f == null || f.commands() == null || f.commands().isEmpty()) {
                throw new IOException("No commands found in " + path);
            }
            return new CommandRegistry(f.commands());
        }
    }

    public List<CommandSpec> commands() {
        return commands;
    }
}
