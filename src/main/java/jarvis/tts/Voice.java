package jarvis.tts;

/**
 * Anything that can speak. Two implementations exist — the built-in
 * Windows SAPI voice and the neural Piper voice — and the orchestrator
 * neither knows nor cares which one it holds.
 */
public interface Voice extends AutoCloseable {

    /** Queue a sentence to be spoken. Returns immediately. */
    void say(String text);

    /** Rough estimate of how long the sentence will take, for self-mute timing. */
    default long estimateMillis(String text) {
        return 700 + text.split("\\s+").length * 320L;
    }

    @Override
    void close();
}
