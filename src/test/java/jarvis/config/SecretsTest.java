package jarvis.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * API keys must never end up in a committed file, so the loader has to
 * cope with the secrets file simply not being there.
 */
class SecretsTest {

    @Test
    void readsKeyFromFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("secrets.json");
        Files.writeString(file, "{\"geminiApiKey\": \"abc123\"}");

        Secrets secrets = Secrets.load(file);
        assertEquals("abc123", secrets.get("geminiApiKey", "NO_SUCH_ENV_VAR"));
    }

    @Test
    void missingFileYieldsBlankRatherThanCrashing(@TempDir Path dir) {
        Secrets secrets = Secrets.load(dir.resolve("does-not-exist.json"));
        assertEquals("", secrets.get("geminiApiKey", "NO_SUCH_ENV_VAR"));
    }

    @Test
    void malformedFileYieldsBlankRatherThanCrashing(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("secrets.json");
        Files.writeString(file, "not json at all {{{");

        Secrets secrets = Secrets.load(file);
        assertEquals("", secrets.get("geminiApiKey", "NO_SUCH_ENV_VAR"));
    }

    @Test
    void absentKeyYieldsBlank(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("secrets.json");
        Files.writeString(file, "{\"somethingElse\": \"x\"}");

        Secrets secrets = Secrets.load(file);
        assertEquals("", secrets.get("geminiApiKey", "NO_SUCH_ENV_VAR"));
    }

    @Test
    void environmentVariableWinsOverFile(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("secrets.json");
        Files.writeString(file, "{\"geminiApiKey\": \"from-file\"}");

        // PATH is always set; it should override the file value.
        Secrets secrets = Secrets.load(file);
        assertNotEquals("from-file", secrets.get("geminiApiKey", "PATH"));
    }
}
