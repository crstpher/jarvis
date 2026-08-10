package jarvis.wake;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClapPatternFSMTest {

    // "short,short" = three quick claps
    private ClapPatternFSM tripleClap() {
        return ClapPatternFSM.fromString("short,short");
    }

    @Test
    void threeQuickClapsTrigger() {
        ClapPatternFSM fsm = tripleClap();
        assertFalse(fsm.onClap(0));
        assertFalse(fsm.onClap(300));
        assertTrue(fsm.onClap(600));
    }

    @Test
    void slowClapsDoNotTrigger() {
        ClapPatternFSM fsm = tripleClap();
        assertFalse(fsm.onClap(0));
        assertFalse(fsm.onClap(2000)); // timed out - treated as a new first clap
        assertFalse(fsm.onClap(4000));
    }

    @Test
    void failedAttemptCanRestartImmediately() {
        ClapPatternFSM fsm = tripleClap();
        assertFalse(fsm.onClap(0));
        assertFalse(fsm.onClap(300));
        // Long pause ruins the attempt, but this clap starts a fresh one...
        assertFalse(fsm.onClap(5000));
        assertFalse(fsm.onClap(5300));
        assertTrue(fsm.onClap(5600));
    }

    @Test
    void mixedPatternMatchesOnlyCorrectRhythm() {
        // clap [short] clap [long] clap
        ClapPatternFSM fsm = ClapPatternFSM.fromString("short,long");
        assertFalse(fsm.onClap(0));
        assertFalse(fsm.onClap(300));   // short gap - ok
        assertTrue(fsm.onClap(1100));   // long gap (800ms) - pattern complete

        // Wrong rhythm: three quick claps should NOT trigger short,long
        ClapPatternFSM fsm2 = ClapPatternFSM.fromString("short,long");
        assertFalse(fsm2.onClap(0));
        assertFalse(fsm2.onClap(300));
        assertFalse(fsm2.onClap(600)); // second gap is short, expected long
    }

    @Test
    void triggerResetsTheMachine() {
        ClapPatternFSM fsm = tripleClap();
        fsm.onClap(0);
        fsm.onClap(300);
        assertTrue(fsm.onClap(600));
        // Needs a full new pattern to trigger again.
        assertFalse(fsm.onClap(900));
        assertFalse(fsm.onClap(1200));
        assertTrue(fsm.onClap(1500));
    }

    @Test
    void emptyPatternRejected() {
        assertThrows(IllegalArgumentException.class, () -> ClapPatternFSM.fromString(""));
    }
}
