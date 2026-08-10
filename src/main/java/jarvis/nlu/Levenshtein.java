package jarvis.nlu;

/**
 * Classic dynamic-programming edit distance (CS210/CS211).
 * Used to fuzzy-match what Vosk heard against known command phrases,
 * so "opens team" still matches "open steam".
 */
public final class Levenshtein {

    private Levenshtein() {}

    public static int distance(String a, String b) {
        int n = a.length(), m = b.length();
        if (n == 0) return m;
        if (m == 0) return n;

        // Two-row DP: O(n*m) time, O(m) space.
        int[] prev = new int[m + 1];
        int[] curr = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;

        for (int i = 1; i <= n; i++) {
            curr[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                curr[j] = Math.min(
                        curr[j - 1] + 1,        // insertion
                        Math.min(prev[j] + 1,   // deletion
                        prev[j - 1] + cost));   // substitution
            }
            int[] tmp = prev; prev = curr; curr = tmp;
        }
        return prev[m];
    }

    /** Distance normalised by the longer string's length: 0 = identical, 1 = nothing shared. */
    public static double normalised(String a, String b) {
        int max = Math.max(a.length(), b.length());
        if (max == 0) return 0;
        return distance(a, b) / (double) max;
    }
}
