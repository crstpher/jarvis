package jarvis.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Optional;

/**
 * The conversational layer (CS245): turns a free-form sentence into
 * either an answer or a sequence of tool calls, and keeps enough history
 * that follow-ups like "play the next one" make sense.
 *
 * The loop is the standard agentic one — ask the model, run whatever
 * tools it asks for, feed the results back, repeat until it answers in
 * plain words. It is bounded so a confused model can't spin forever.
 */
public class Brain {

    private static final int MAX_TOOL_ROUNDS = 4;
    private static final int MAX_HISTORY_TURNS = 12;

    private final OllamaClient client;
    private final ToolBox toolBox;
    private final String systemPrompt;
    private final Deque<JsonObject> history = new ArrayDeque<>();

    public Brain(OllamaClient client, ToolBox toolBox, String persona) {
        this.client = client;
        this.toolBox = toolBox;
        this.systemPrompt = persona;
    }

    /**
     * Handle one thing the user said.
     *
     * @return what Jarvis should say back, or empty if the model produced nothing
     */
    public Optional<String> handle(String userText) {
        List<ToolSpec> tools = toolBox.specs();
        remember("user", userText);

        try {
            for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
                OllamaClient.Reply reply = client.chat(buildMessages(), tools);

                if (!reply.wantsTools()) {
                    String text = reply.text();
                    if (!text.isBlank()) remember("assistant", text);
                    return text.isBlank() ? Optional.empty() : Optional.of(text);
                }

                // Record the assistant's tool request verbatim, then run each tool.
                history.addLast(reply.raw());
                trimHistory();

                for (OllamaClient.ToolCall call : reply.toolCalls()) {
                    String result = runTool(tools, call);
                    System.out.printf("[tool] %s -> %s%n", call.name(), result);
                    JsonObject toolMsg = new JsonObject();
                    toolMsg.addProperty("role", "tool");
                    toolMsg.addProperty("content", result);
                    history.addLast(toolMsg);
                    trimHistory();
                }
            }
            return Optional.of("That took more steps than I expected. Try asking a simpler way.");
        } catch (java.net.ConnectException e) {
            return Optional.of("My local AI isn't running. Start Ollama and try again.");
        } catch (Exception e) {
            System.err.println("[brain] " + e);
            return Optional.of("Something went wrong thinking about that.");
        }
    }

    /** Execute one tool call, converting failures into text the model can react to. */
    private String runTool(List<ToolSpec> tools, OllamaClient.ToolCall call) {
        for (ToolSpec spec : tools) {
            if (spec.name().equals(call.name())) {
                try {
                    return spec.handler().run(call.arguments());
                } catch (Exception e) {
                    // Deliberately fed back rather than thrown: the model can
                    // explain the problem or pick a different approach.
                    return "Failed: " + e.getMessage();
                }
            }
        }
        return "No such tool: " + call.name();
    }

    private JsonArray buildMessages() {
        JsonArray messages = new JsonArray();
        JsonObject system = new JsonObject();
        system.addProperty("role", "system");
        system.addProperty("content", systemPrompt);
        messages.add(system);
        for (JsonObject m : history) messages.add(m);
        return messages;
    }

    private void remember(String role, String content) {
        JsonObject msg = new JsonObject();
        msg.addProperty("role", role);
        msg.addProperty("content", content);
        history.addLast(msg);
        trimHistory();
    }

    private void trimHistory() {
        while (history.size() > MAX_HISTORY_TURNS * 2) {
            history.removeFirst();
        }
    }

    public void forget() {
        history.clear();
    }
}
