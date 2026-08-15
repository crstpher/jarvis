package jarvis.stt;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Two recognizers run in parallel during a command window. Which
 * transcript wins decides whether a sentence reaches the AI at all, so
 * the preference rule is worth pinning down.
 */
class HeardTest {

    @Test
    void freeTranscriptWinsWhenPresent() {
        var heard = new SpeechRecognizer.Heard("open steam", "what's the weather like");
        assertEquals("what's the weather like", heard.best());
    }

    @Test
    void fallsBackToGrammarWhenFreeIsEmpty() {
        var heard = new SpeechRecognizer.Heard("open steam", "");
        assertEquals("open steam", heard.best());
    }

    @Test
    void fallsBackToGrammarWhenFreeIsNull() {
        var heard = new SpeechRecognizer.Heard("open steam", null);
        assertEquals("open steam", heard.best());
    }

    @Test
    void blankWhenNeitherHeardAnything() {
        var heard = new SpeechRecognizer.Heard("", "");
        assertTrue(heard.best().isBlank());
    }
}
