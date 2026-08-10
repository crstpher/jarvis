package jarvis.nlu;

import jarvis.actions.CommandRegistry;
import jarvis.actions.CommandSpec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandMatcherTest {

    private CommandMatcher matcher() {
        var steam = new CommandSpec("open-steam",
                List.of("open steam", "launch steam"),
                new CommandSpec.ActionSpec("launch", "steam://open/main"), "Opening Steam");
        var time = new CommandSpec("what-time",
                List.of("what time is it", "tell me the time"),
                new CommandSpec.ActionSpec("time", ""), "");
        return new CommandMatcher(new CommandRegistry(List.of(steam, time)), 0.4);
    }

    @Test
    void exactPhraseMatches() {
        var m = matcher().match("open steam");
        assertTrue(m.isPresent());
        assertEquals("open-steam", m.get().command().name());
        assertEquals(0.0, m.get().score());
    }

    @Test
    void misheardPhraseStillMatches() {
        var m = matcher().match("opens team");
        assertTrue(m.isPresent());
        assertEquals("open-steam", m.get().command().name());
    }

    @Test
    void fillerWordsAreIgnored() {
        var m = matcher().match("jarvis please open steam");
        assertTrue(m.isPresent());
        assertEquals("open-steam", m.get().command().name());
    }

    @Test
    void gibberishIsRejected() {
        assertTrue(matcher().match("make me a sandwich right now").isEmpty());
    }

    @Test
    void blankTranscriptIsRejected() {
        assertTrue(matcher().match("").isEmpty());
        assertTrue(matcher().match("jarvis").isEmpty()); // wake word alone is not a command
    }

    @Test
    void normaliseStripsPunctuationAndFiller() {
        assertEquals("open steam", CommandMatcher.normalise("Jarvis, please... OPEN STEAM!"));
    }
}
