package jarvis.wake;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A deterministic finite automaton over timed clap events (CS290).
 *
 * The alphabet is the *gap* between consecutive claps, classified as
 * SHORT (up to 450ms) or LONG (450ms to 1200ms). A gap above 1200ms is
 * a timeout and resets the machine. The pattern string in settings.json
 * lists the expected gaps, e.g.:
 *
 *   "short,short"  = three quick claps
 *   "short,long,short" = clap-clap ... clap-clap
 *
 * State = how many gaps of the pattern have been matched so far.
 * Accepting state = all gaps matched, which triggers the assistant.
 * On any mismatch or timeout the current clap is treated as a possible
 * *first* clap of a new attempt (so you never have to wait to retry).
 */
public class ClapPatternFSM {

    public enum Gap { SHORT, LONG }

    private final List<Gap> pattern;
    private final long shortMaxMs;
    private final long timeoutMs;

    private int matched = 0;     // current DFA state
    private long lastClapMs = -1;

    public ClapPatternFSM(List<Gap> pattern) {
        this(pattern, 450, 1200);
    }

    public ClapPatternFSM(List<Gap> pattern, long shortMaxMs, long timeoutMs) {
        if (pattern.isEmpty()) {
            throw new IllegalArgumentException("Clap pattern needs at least one gap (two claps)");
        }
        this.pattern = List.copyOf(pattern);
        this.shortMaxMs = shortMaxMs;
        this.timeoutMs = timeoutMs;
    }

    /** Parse a pattern string like "short,short" or "short,long,short". */
    public static ClapPatternFSM fromString(String spec) {
        List<Gap> gaps = new ArrayList<>();
        for (String part : spec.split(",")) {
            gaps.add(Gap.valueOf(part.trim().toUpperCase(Locale.ROOT)));
        }
        return new ClapPatternFSM(gaps);
    }

    /**
     * Advance the DFA with a clap at time t (ms).
     * Returns true when the full pattern has just been completed.
     */
    public boolean onClap(long tMs) {
        if (lastClapMs >= 0) {
            long gap = tMs - lastClapMs;
            Gap g = classify(gap);
            if (g != null && matched < pattern.size() && g == pattern.get(matched)) {
                matched++;
            } else {
                matched = 0; // this clap becomes the first of a new attempt
            }
        }
        lastClapMs = tMs;

        if (matched == pattern.size()) {
            reset();
            return true;
        }
        return false;
    }

    public void reset() {
        matched = 0;
        lastClapMs = -1;
    }

    private Gap classify(long gapMs) {
        if (gapMs <= shortMaxMs) return Gap.SHORT;
        if (gapMs <= timeoutMs) return Gap.LONG;
        return null; // timeout
    }
}
