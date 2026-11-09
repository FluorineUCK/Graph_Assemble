package dev.graphassembly.integration;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Package ownership is an explicit source-layout contract, not an execution capability check. */
class IntegrationLayoutTest {
    private static final Path JAVA = Path.of("src/main/java");
    private static final Path INTEGRATION = JAVA.resolve("dev/graphassembly/integration");

    @Test void nativeBridgePackagesReplaceProjectionWithoutAliases() throws Exception {
        for (Path parent : java.util.List.of(INTEGRATION,
                JAVA.resolve("dev/graphassembly/mixin/create"), JAVA.resolve("dev/graphassembly/mixin/vanilla"))) {
            assertTrue(Files.isDirectory(parent.resolve("bridge")), parent.toString());
            assertFalse(Files.exists(parent.resolve("projection")), "Do not retain legacy bridge packages");
        }
        String mixins = Files.readString(Path.of("src/main/resources/graph_assembly.mixins.json"));
        assertFalse(mixins.contains(".projection."));
        assertTrue(mixins.contains("create.bridge.RecipeFinderViewMixin"));
        assertTrue(mixins.contains("vanilla.bridge.ProofStackMixin"));
    }

    @Test void vanillaDeviceFamiliesAreGroupedAndPackagesMatchTheirLocations() throws Exception {
        for (String family : Set.of("anvil", "brewing", "campfire", "crafter", "crafting",
                "furnace", "menu", "smithing", "stonecutting")) {
            assertTrue(Files.isDirectory(INTEGRATION.resolve("vanilla").resolve(family)), family);
            assertFalse(Files.exists(INTEGRATION.resolve(family)), "Legacy device package: " + family);
        }
        try (var sources = Files.walk(INTEGRATION)) {
            for (Path file : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String declaredPackage = JAVA.relativize(file.getParent()).toString()
                    .replace('\\', '.').replace('/', '.');
                assertTrue(Files.readString(file).contains("package " + declaredPackage + ";"), file.toString());
            }
        }
    }

    @Test void sharedGraphCompletionDoesNotBelongToAFurnaceAdapter() throws Exception {
        Path completion = INTEGRATION.resolve("GraphCompletion.java");
        assertTrue(Files.isRegularFile(completion), "Create and vanilla share the prepared graph completion");
        try (var sources = Files.walk(INTEGRATION)) {
            assertTrue(sources.noneMatch(p -> p.getFileName().toString().equals("CookingCompletion.java")),
                "Do not retain a furnace-owned alias for shared graph completion");
        }
    }
}
