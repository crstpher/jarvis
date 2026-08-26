package jarvis.ai;

import com.google.gson.JsonObject;
import jarvis.actions.ActionExecutor;
import jarvis.actions.CommandRegistry;
import jarvis.actions.CommandSpec;
import jarvis.nlu.CommandMatcher;
import jarvis.spotify.SpotifyClient;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * The capabilities the model is allowed to reach for.
 *
 * Each tool is a thin bridge onto machinery that already exists: app
 * launching goes through the same CommandRegistry and fuzzy matcher the
 * fast path uses, so anything you can say directly, the AI can also do.
 */
public class ToolBox {

    /** Tool name whose answer is spoken verbatim rather than re-summarised. */
    public static final String KNOWLEDGE_TOOL = "answer_question";

    private final CommandRegistry registry;
    private final CommandMatcher matcher;
    private final ActionExecutor executor;
    private final SpotifyClient spotify; // may be null if not configured
    private final GeminiClient gemini;   // may be null if not configured

    public ToolBox(CommandRegistry registry, CommandMatcher matcher,
                   ActionExecutor executor, SpotifyClient spotify, GeminiClient gemini) {
        this.registry = registry;
        this.matcher = matcher;
        this.executor = executor;
        this.spotify = spotify;
        this.gemini = gemini;
    }

    public List<ToolSpec> specs() {
        List<ToolSpec> tools = new ArrayList<>();

        tools.add(new ToolSpec("open_app",
                "Open an application, game or website on the user's PC. "
                        + "Known targets include: " + knownTargets("open", 25),
                this::openApp)
                .param("name", "string", "What to open, e.g. 'steam' or 'marvel rivals'", true));

        tools.add(new ToolSpec("close_app",
                "Close a running application or game on the user's PC.",
                this::closeApp)
                .param("name", "string", "What to close, e.g. 'marvel rivals'", true));

        tools.add(new ToolSpec("get_time",
                "Get the current time of day.",
                args -> "It's " + LocalTime.now().format(DateTimeFormatter.ofPattern("h:mm a"))));

        tools.add(new ToolSpec("search_web",
                "Open the web browser showing search results. Use when the user asks to "
                        + "search for, look up, google, or find something online - as opposed "
                        + "to asking a question they want answered aloud.",
                this::searchWeb)
                .param("query", "string", "What to search for", true));

        {
            tools.add(new ToolSpec(KNOWLEDGE_TOOL,
                    "Look up the answer to any question about the world - facts, people, "
                            + "places, history, science, definitions, explanations, current "
                            + "affairs, maths, or anything you are unsure about. Prefer this "
                            + "over answering from memory whenever the user asks a question "
                            + "that is not about their own PC, games or music.",
                    this::answerQuestion)
                    .param("question", "string",
                            "The user's question, rephrased as a clear standalone question", true));
        }

        // Spotify tools are registered even when Spotify is not connected.
        // Omitting them leaves the model with no way to act on a music
        // request and, in practice, it invents one instead - claiming to
        // play songs that never played. A tool that honestly reports being
        // unavailable gives it a true result to relay.
        {
            tools.add(new ToolSpec("spotify_play",
                    "Search Spotify and start playing music. Use this whenever the user "
                            + "asks to hear a song, artist, album or playlist.",
                    this::spotifyPlay)
                    .param("query", "string", "What to search for, e.g. 'bohemian rhapsody'", true)
                    .param("type", "string", "One of: track, album, artist, playlist. Default track.", false));

            tools.add(new ToolSpec("spotify_control",
                    "Control playback that is already running: pause, resume, skip forward or back.",
                    this::spotifyControl)
                    .param("action", "string", "One of: pause, resume, next, previous", true));

            tools.add(new ToolSpec("spotify_volume",
                    "Set the Spotify playback volume.",
                    this::spotifyVolume)
                    .param("percent", "integer", "Volume from 0 to 100", true));

            tools.add(new ToolSpec("spotify_now_playing",
                    "Find out which song is currently playing.",
                    args -> spotify == null ? spotifyUnavailable() : spotify.nowPlaying()));
        }

        return tools;
    }

    // --- handlers ------------------------------------------------------

    private String openApp(JsonObject args) throws Exception {
        return runMatching("open " + str(args, "name"), "open");
    }

    private String closeApp(JsonObject args) throws Exception {
        return runMatching("close " + str(args, "name"), "close");
    }

    /**
     * Resolve a spoken target to a configured command and run it. The
     * prefix ("open"/"close") steers the matcher toward the right variant
     * when a game has both.
     */
    private String runMatching(String phrase, String verb) throws Exception {
        var match = matcher.match(phrase);
        if (match.isEmpty()) {
            return "No command is configured for that. Known: " + knownTargets(verb, 15);
        }
        // Sensitive commands only run through the exact voice phrase, where
        // the assistant asks for spoken confirmation first.
        if (match.get().command().confirm()) {
            return "BLOCKED: this action is protected. Tell the user to say the exact "
                    + "command phrase, which will ask for their confirmation.";
        }
        return executor.execute(match.get().command());
    }

    private String answerQuestion(JsonObject args) throws Exception {
        if (gemini == null || !gemini.configured()) {
            return "UNAVAILABLE: no lookup service is configured, so this question cannot be "
                    + "answered. Tell the user you have no way to look that up.";
        }
        String question = str(args, "question");
        if (question.isBlank()) return "I need to know what the question is.";
        return gemini.ask(question);
    }

    private String searchWeb(JsonObject args) throws Exception {
        String query = str(args, "query");
        if (query.isBlank()) return "I need a search query.";
        String url = "https://www.google.com/search?q="
                + java.net.URLEncoder.encode(query, java.nio.charset.StandardCharsets.UTF_8);
        new ProcessBuilder("cmd", "/c", "start", "", url).start();
        return "Browser opened with search results for " + query;
    }

    /** The honest answer when Spotify was never connected. */
    private String spotifyUnavailable() {
        return "UNAVAILABLE: Spotify is not connected, so nothing was played or changed. "
                + "Tell the user Spotify isn't set up and that they need to run setup-spotify.";
    }

    private String spotifyPlay(JsonObject args) throws Exception {
        if (spotify == null) return spotifyUnavailable();
        return spotify.play(str(args, "query"), str(args, "type"));
    }

    private String spotifyControl(JsonObject args) throws Exception {
        if (spotify == null) return spotifyUnavailable();
        String action = str(args, "action").toLowerCase();
        return switch (action) {
            case "pause" -> spotify.pause();
            case "resume", "play", "unpause" -> spotify.resume();
            case "next", "skip" -> spotify.next();
            case "previous", "back", "prev" -> spotify.previous();
            default -> "I don't know the playback action " + action;
        };
    }

    private String spotifyVolume(JsonObject args) throws Exception {
        if (spotify == null) return spotifyUnavailable();
        int percent;
        try {
            percent = args.get("percent").getAsInt();
        } catch (Exception e) {
            return "I need a volume between 0 and 100";
        }
        return spotify.volume(percent);
    }

    // --- helpers -------------------------------------------------------

    /** A sample of configured command names, to ground the model in what exists. */
    private String knownTargets(String verb, int limit) {
        return registry.commands().stream()
                .filter(c -> c.name().startsWith(verb) || c.name().startsWith("play"))
                .map(CommandSpec::name)
                .map(n -> n.replaceFirst("^(open|close|play)-", ""))
                .distinct()
                .limit(limit)
                .collect(Collectors.joining(", "));
    }

    private static String str(JsonObject args, String key) {
        return args.has(key) && !args.get(key).isJsonNull() ? args.get(key).getAsString() : "";
    }
}
