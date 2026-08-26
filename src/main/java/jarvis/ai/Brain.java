package jarvis.ai;

import java.util.Optional;

/**
 * Something that can hold a conversation and act through tools.
 *
 * Two implementations exist - a local model via Ollama and Google's
 * Gemini - and the orchestrator neither knows nor cares which it holds,
 * so the provider can be swapped in settings (or fall back automatically
 * when one is unavailable).
 */
public interface Brain {

    /**
     * Handle one thing the user said.
     *
     * @return what Jarvis should say back, or empty if there is nothing to say
     */
    Optional<String> handle(String userText);

    /** Drop the conversation history. */
    void forget();

    /** Replace the personality (system prompt) from now on. */
    void setPersona(String persona);

    /** Short human-readable description, e.g. for the dashboard. */
    String name();
}
