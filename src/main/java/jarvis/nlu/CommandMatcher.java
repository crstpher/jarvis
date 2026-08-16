package jarvis.nlu;

import jarvis.actions.CommandRegistry;
import jarvis.actions.CommandSpec;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Maps a (possibly slightly wrong) transcript to the best known command.
 *
 * Strategy:
 *   1. Normalise the transcript (lowercase, strip punctuation and
 *      polite filler like "jarvis", "please", "can you").
 *   2. Score every phrase of every command with normalised edit
 *      distance (see Levenshtein). An exact substring match scores 0.
 *   3. Accept the best command if its score is under the threshold.
 */
public class CommandMatcher {

    /** Result of a match attempt: the command plus how confident we are. */
    public record Match(CommandSpec command, double score, String phrase) {}

    private static final String[] FILLER_PREFIXES = {
            "jarvis", "hey", "ok", "okay", "please", "can you", "could you", "would you"
    };

    private final CommandRegistry registry;
    private final double threshold;

    public CommandMatcher(CommandRegistry registry, double threshold) {
        this.registry = registry;
        this.threshold = threshold;
    }

    public Optional<Match> match(String transcript) {
        String text = normalise(transcript);
        if (text.isBlank()) return Optional.empty();

        Match best = null;
        for (CommandSpec cmd : registry.commands()) {
            for (String phrase : cmd.phrases()) {
                String p = normalise(phrase);
                double score = text.contains(p) ? 0.0
                        : Math.min(Levenshtein.normalised(text, p), tokenScore(text, p));
                if (best == null || score < best.score()) {
                    best = new Match(cmd, score, phrase);
                }
            }
        }
        if (best != null && best.score() <= threshold) {
            return Optional.of(best);
        }
        return Optional.empty();
    }

    /**
     * Order-insensitive score: the fraction of the phrase's words missing
     * from the transcript. Catches word-order scrambles like
     * "marvel rivals start open" vs "open marvel rivals".
     * Single-word phrases don't qualify (too easy to hit by accident).
     */
    static double tokenScore(String text, String phrase) {
        String[] phraseWords = phrase.split(" ");
        if (phraseWords.length < 2) return 1.0;
        // A HashSet, not Set.of: ordinary speech repeats words ("are we
        // there are"), and Set.of throws on duplicates.
        Set<String> textWords = new HashSet<>(Arrays.asList(text.split(" ")));
        int missing = 0;
        for (String w : phraseWords) {
            if (!textWords.contains(w)) missing++;
        }
        return missing / (double) phraseWords.length;
    }

    public static String normalise(String s) {
        String text = s.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        boolean stripped = true;
        while (stripped) {
            stripped = false;
            for (String filler : FILLER_PREFIXES) {
                if (text.equals(filler)) {
                    return "";
                }
                if (text.startsWith(filler + " ")) {
                    text = text.substring(filler.length() + 1);
                    stripped = true;
                }
            }
        }
        return text;
    }
}
