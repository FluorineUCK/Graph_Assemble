package dev.graphassembly.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import org.apache.maven.artifact.versioning.DefaultArtifactVersion;
import org.apache.maven.artifact.versioning.VersionRange;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JeiVersionRangeTest {
    @Test void acceptsTheAuditedJeiRangeWithoutMakingJeiRequired() throws Exception {
        var metadata = Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"));
        var jei = List.of(metadata.split("\\[\\[")).stream()
            .filter(block -> block.startsWith("dependencies.graph_assembly]]") && "jei".equals(value(block, "modId")))
            .findFirst().orElseThrow();
        assertEquals("optional", value(jei, "type"));
        assertEquals("CLIENT", value(jei, "side"));
        var range = VersionRange.createFromVersionSpec(value(jei, "versionRange"));
        for (var version : List.of("19.39.0.368", "19.50.0.414", "19.70.0.469")) {
            assertTrue(range.containsVersion(new DefaultArtifactVersion(version)), "Rejects audited JEI " + version);
        }
        for (var version : List.of("19.38.0.367", "19.70.0.470", "20.0.0")) {
            assertFalse(range.containsVersion(new DefaultArtifactVersion(version)), "Accepts unaudited JEI " + version);
        }
    }

    private String value(String block, String key) {
        var match = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*=\\s*\"([^\"]+)\"").matcher(block);
        return match.find() ? match.group(1) : null;
    }
}
