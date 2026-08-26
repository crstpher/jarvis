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
        var lewis = new CommandSpec("voice-lewis",
                List.of("change your voice to lewis", "voice to lewis"),
                new CommandSpec.ActionSpec("voice", "bm_lewis"), "");
        var george = new CommandSpec("voice-george",
                List.of("change your voice to george", "voice to george"),
                new CommandSpec.ActionSpec("voice", "bm_george"), "");
        var rivals = new CommandSpec("play-marvel-rivals",
                List.of("open marvel rivals", "play marvel rivals"),
                new CommandSpec.ActionSpec("launch", "steam://rungameid/2767030"), "Launching");
        return new CommandMatcher(
                new CommandRegistry(List.of(steam, time, george, lewis, rivals)), 0.4);
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
    void scrambledWordOrderStillMatches() {
        // Grammar-restricted Vosk can emit the right words in the wrong order.
        var m = matcher().match("steam open");
        assertTrue(m.isPresent());
        assertEquals("open-steam", m.get().command().name());
    }

    @Test
    void repeatedWordsDoNotCrash() {
        // Real speech repeats words constantly. Set.of() throws on
        // duplicates, which crashed the whole assistant mid-sentence.
        assertDoesNotThrow(() -> matcher().match("now listen to are we there are"));
        assertDoesNotThrow(() -> matcher().match("open open open steam steam"));
        assertDoesNotThrow(() -> matcher().match("the the the"));
    }

    @Test
    void repeatedWordsStillMatchCorrectly() {
        var m = matcher().match("open open steam");
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
    void misheardNameStillPicksTheRightVoice() {
        // The recognizer heard "louis"; one edit from "lewis". This
        // previously tied lewis with george and george won by list order.
        var m = matcher().match("change your voice to louis");
        assertTrue(m.isPresent());
        assertEquals("voice-lewis", m.get().command().name());
    }

    @Test
    void searchSentenceMustNotConfidentlyMatchLaunch() {
        // "search for marvel rivals patch notes" launched the game three
        // times in one session. It may still weakly match, but never below
        // the 0.30 fast-path bar - the AI must get it instead.
        var m = matcher().match("search for marvel rivals patch notes");
        m.ifPresent(match -> assertTrue(match.score() > 0.30,
                "scored " + match.score() + " - would hijack the fast path"));
    }

    @Test
    void normaliseStripsPunctuationAndFiller() {
        assertEquals("open steam", CommandMatcher.normalise("Jarvis, please... OPEN STEAM!"));
    }
}
