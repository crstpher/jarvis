package jarvis.nlu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Confirmation guards sensitive actions, so a misread yes/no matters:
 * a false yes runs something the user didn't approve.
 */
class ConfirmWordsTest {

    @Test
    void plainYesForms() {
        assertTrue(ConfirmWords.isYes("yes"));
        assertTrue(ConfirmWords.isYes("yeah go ahead"));
        assertTrue(ConfirmWords.isYes("yes please"));
        assertTrue(ConfirmWords.isYes("do it"));
    }

    @Test
    void plainNoForms() {
        assertTrue(ConfirmWords.isNo("no"));
        assertTrue(ConfirmWords.isNo("never mind"));
        assertTrue(ConfirmWords.isNo("cancel that"));
        assertTrue(ConfirmWords.isNo("no don't"));
    }

    @Test
    void negatedYesIsNotYes() {
        // "no yes I mean no" style confusion must never run the action.
        assertFalse(ConfirmWords.isYes("no wait"));
        assertFalse(ConfirmWords.isYes("yes actually no cancel"));
    }

    @Test
    void unrelatedSpeechIsNeither() {
        assertFalse(ConfirmWords.isYes("open steam"));
        assertFalse(ConfirmWords.isNo("open steam"));
        assertFalse(ConfirmWords.isYes("what time is it"));
    }

    @Test
    void wordBoundariesRespected() {
        // "notepad" contains "no"; "yesterday" contains "yes".
        assertFalse(ConfirmWords.isNo("open notepad"));
        assertFalse(ConfirmWords.isYes("what happened yesterday"));
    }
}
