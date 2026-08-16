package jarvis.ai;

import com.google.gson.JsonObject;
import jarvis.actions.ActionExecutor;
import jarvis.actions.CommandRegistry;
import jarvis.actions.CommandSpec;
import jarvis.nlu.CommandMatcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The model was observed claiming to play music while Spotify was not
 * even connected. Leaving a capability unregistered gives it a gap to
 * improvise into, so every capability is always present and reports
 * honestly when it cannot act.
 */
class ToolBoxTest {

    private ToolBox unconfigured() {
        var steam = new CommandSpec("open-steam", List.of("open steam"),
                new CommandSpec.ActionSpec("launch", "steam://open/main"), "Opening Steam");
        var registry = new CommandRegistry(List.of(steam));
        return new ToolBox(registry, new CommandMatcher(registry, 0.4),
                new ActionExecutor(), null, null);
    }

    @Test
    void spotifyToolsExistEvenWhenSpotifyIsNotConnected() {
        var names = unconfigured().specs().stream().map(ToolSpec::name).toList();
        assertTrue(names.contains("spotify_play"),
                "the model needs something to call, or it invents an outcome");
        assertTrue(names.contains("spotify_control"));
    }

    @Test
    void knowledgeToolExistsEvenWithoutAnApiKey() {
        var names = unconfigured().specs().stream().map(ToolSpec::name).toList();
        assertTrue(names.contains(ToolBox.KNOWLEDGE_TOOL));
    }

    @Test
    void playingWithoutSpotifyReportsUnavailableRatherThanSuccess() throws Exception {
        ToolSpec play = unconfigured().specs().stream()
                .filter(t -> t.name().equals("spotify_play")).findFirst().orElseThrow();

        JsonObject args = new JsonObject();
        args.addProperty("query", "love on top");
        String result = play.handler().run(args);

        assertTrue(result.startsWith("UNAVAILABLE"), "got: " + result);
        assertFalse(result.toLowerCase().contains("playing love on top"),
                "must not read as though the song played");
    }

    @Test
    void askingWithoutAnApiKeyReportsUnavailable() throws Exception {
        ToolSpec ask = unconfigured().specs().stream()
                .filter(t -> t.name().equals(ToolBox.KNOWLEDGE_TOOL)).findFirst().orElseThrow();

        JsonObject args = new JsonObject();
        args.addProperty("question", "how far away is the moon");

        assertTrue(ask.handler().run(args).startsWith("UNAVAILABLE"));
    }
}
