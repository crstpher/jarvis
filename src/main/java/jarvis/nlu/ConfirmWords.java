package jarvis.nlu;

import java.util.List;
import java.util.Locale;

/**
 * Recognises a spoken yes or no, for confirming sensitive commands.
 * Anything that is clearly neither is treated as "changed the subject",
 * which cancels the pending action.
 */
public final class ConfirmWords {

    private static final List<String> YES = List.of(
            "yes", "yeah", "yep", "yup", "sure", "go ahead", "do it",
            "confirm", "confirmed", "please do", "affirmative", "absolutely");

    private static final List<String> NO = List.of(
            "no", "nope", "don't", "do not", "cancel", "never mind",
            "nevermind", "stop", "negative", "forget it");

    private ConfirmWords() {}

    public static boolean isYes(String spoken) {
        return matches(spoken, YES) && !matches(spoken, NO);
    }

    public static boolean isNo(String spoken) {
        return matches(spoken, NO);
    }

    private static boolean matches(String spoken, List<String> words) {
        String text = " " + spoken.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim() + " ";
        for (String w : words) {
            if (text.contains(" " + w + " ")) return true;
        }
        return false;
    }
}
