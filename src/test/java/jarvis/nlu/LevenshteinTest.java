package jarvis.nlu;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LevenshteinTest {

    @Test
    void identicalStringsHaveZeroDistance() {
        assertEquals(0, Levenshtein.distance("open steam", "open steam"));
    }

    @Test
    void emptyStringDistanceIsOtherLength() {
        assertEquals(5, Levenshtein.distance("", "steam"));
        assertEquals(5, Levenshtein.distance("steam", ""));
    }

    @Test
    void classicExamples() {
        assertEquals(3, Levenshtein.distance("kitten", "sitting"));
        assertEquals(1, Levenshtein.distance("steam", "team"));
    }

    @Test
    void misheardCommandIsClose() {
        // What Vosk might hear vs. what the user meant.
        assertEquals(2, Levenshtein.distance("opens team", "open steam"));
    }

    @Test
    void normalisedIsBetweenZeroAndOne() {
        assertEquals(0.0, Levenshtein.normalised("abc", "abc"));
        assertEquals(1.0, Levenshtein.normalised("abc", "xyz"));
    }
}
