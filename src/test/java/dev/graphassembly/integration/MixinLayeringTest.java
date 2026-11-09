package dev.graphassembly.integration;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Source/configuration contract: package ownership follows the injected target. */
class MixinLayeringTest {
    private static final Path JAVA = Path.of("src/main/java");
    private static final String ROOT = "dev.graphassembly.mixin.";
    private static final String MAIN = "graph_assembly.mixins.json";
    private static final String OPTIONAL = "graph_assembly.jei.mixins.json";

    private JsonObject config(String name) throws Exception {
        return JsonParser.parseString(Files.readString(Path.of("src/main/resources", name))).getAsJsonObject();
    }

    @Test void allActualMixinsAreRegisteredExactlyOnceAndOwnedByTheirTargets() throws Exception {
        var registered = new HashSet<String>();
        for (String name : new String[] {MAIN, OPTIONAL}) {
            var config = config(name);
            for (String side : new String[] {"mixins", "client", "server"}) {
                if (!config.has(side)) continue;
                for (var entry : config.getAsJsonArray(side)) {
                    String qualified = config.get("package").getAsString() + "." + entry.getAsString();
                    assertTrue(registered.add(qualified), "Duplicate registration: " + qualified);
                    assertTrue(Files.isRegularFile(JAVA.resolve(qualified.replace('.', '/') + ".java")), qualified);
                }
            }
        }
        var actual = new HashSet<String>();
        try (var sources = Files.walk(JAVA.resolve("dev/graphassembly/mixin"))) {
            for (Path file : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                if (!Pattern.compile("@Mixin\\s*\\(").matcher(source).find()) continue;
                String qualified = JAVA.relativize(file).toString().replace('\\', '.').replace('/', '.').replaceFirst("\\.java$", "");
                actual.add(qualified);
                assertTrue(source.contains("package " + qualified.substring(0, qualified.lastIndexOf('.')) + ";"), qualified);
                var target = Pattern.compile("@Mixin\\s*\\(\\s*(?:value\\s*=\\s*)?(?:targets\\s*=\\s*\"([^\"]+)\"|(\\w+)\\.class)").matcher(source);
                assertTrue(target.find(), "Unrecognized target: " + qualified);
                String targetName = target.group(1);
                if (targetName == null) {
                    targetName = resolveTargetImport(source, target.group(2));
                }
                String layer = targetName.startsWith("net.minecraft.") ? "vanilla."
                        : targetName.startsWith("com.simibubi.create.") ? "create."
                        : targetName.startsWith("mezz.jei.") ? "jei." : null;
                assertNotNull(layer, "Unknown target dependency: " + targetName);
                assertTrue(qualified.startsWith(ROOT + layer), qualified + " targets " + targetName);
            }
        }
        assertEquals(actual, registered);
    }

    private String resolveTargetImport(String source, String simpleName) {
        var explicit = Pattern.compile("import\\s+([\\w.]+\\." + Pattern.quote(simpleName) + ");").matcher(source);
        if (explicit.find()) return explicit.group(1);
        var candidates = new HashSet<String>();
        var wildcard = Pattern.compile("import\\s+([\\w.]+)\\.\\*;").matcher(source);
        while (wildcard.find()) {
            String candidate = wildcard.group(1) + "." + simpleName;
            // Resolve the class resource without loading/initializing Minecraft or Create classes.
            if (getClass().getClassLoader().getResource(candidate.replace('.', '/') + ".class") != null)
                candidates.add(candidate);
        }
        assertEquals(1, candidates.size(), "Target wildcard import missing or ambiguous: " + simpleName + " " + candidates);
        return candidates.iterator().next();
    }

    @Test void wildcardTargetResolutionUsesActualClassesRatherThanImportOrder() {
        assertEquals("java.util.List", resolveTargetImport("import java.nio.file.*; import java.util.*;", "List"));
        assertEquals("java.util.List", resolveTargetImport("import java.util.List; import java.awt.*;", "List"));
        assertThrows(AssertionError.class, () -> resolveTargetImport("import java.util.*; import java.awt.*;", "List"));
        assertThrows(AssertionError.class, () -> resolveTargetImport("import java.nio.file.*;", "List"));
    }

    @Test void clientAndOptionalJeiLoadingBoundariesRemainUnchanged() throws Exception {
        var main = config(MAIN);
        assertFalse(main.has("plugin"));
        assertEquals(Set.of("jei.JeiRecipeLayoutResizeMixin"), entries(main, "client"));
        assertTrue(entries(main, "server").isEmpty());
        assertTrue(entries(main, "mixins").containsAll(Set.of("create.SandpaperMixin",
            "create.CrushingWheelControllerMixin", "create.ItemDrainMixin", "create.GenericItemEmptyingMixin")));
        assertTrue(entries(main, "mixins").contains("create.sequence.SequenceAccess"));
        assertTrue(entries(main, "mixins").contains("vanilla.BrewingStandMixin"));
        assertTrue(entries(main, "mixins").contains("create.MechanicalCrafterGraphMixin"));
        assertTrue(entries(main, "mixins").contains("create.GroupedItemsAccess"));
        assertTrue(entries(main, "mixins").contains("create.TransportedItemStackFanStateMixin"));
        assertTrue(entries(main, "mixins").contains("vanilla.ItemEntityFanStateMixin"));
        assertTrue(entries(main, "mixins").contains("vanilla.CampfireGraphMixin"));
        assertTrue(entries(main, "mixins").contains("vanilla.CrafterBlockMixin"));
        assertTrue(entries(main, "mixins").contains("vanilla.CrafterMenuMixin"));
        assertTrue(Files.readString(JAVA.resolve("dev/graphassembly/mixin/vanilla/CrafterBlockMixin.java")).contains("@Mixin(CrafterBlock.class)"));
        assertTrue(Files.readString(JAVA.resolve("dev/graphassembly/mixin/vanilla/CrafterMenuMixin.java")).contains("@Mixin(CrafterMenu.class)"));
        assertTrue(entries(main, "mixins").contains("vanilla.AbstractFurnaceGraphMixin"));
        assertTrue(entries(main, "mixins").contains("vanilla.StonecutterMenuMixin"));
        assertTrue(entries(main, "mixins").contains("vanilla.StonecutterResultSlotMixin"));
        assertTrue(Files.readString(JAVA.resolve("dev/graphassembly/mixin/vanilla/StonecutterMenuMixin.java")).contains("@Mixin(StonecutterMenu.class)"));
        assertTrue(Files.readString(JAVA.resolve("dev/graphassembly/mixin/vanilla/StonecutterResultSlotMixin.java")).contains("@Mixin(targets=\"net.minecraft.world.inventory.StonecutterMenu$2\")"));
        assertTrue(entries(main, "mixins").stream().noneMatch(s -> s.startsWith("jei.") || s.startsWith("vanilla.client.")));
        assertTrue(Files.readString(JAVA.resolve("dev/graphassembly/mixin/jei/JeiRecipeLayoutResizeMixin.java")).contains("@Pseudo"));
        var optional = config(OPTIONAL);
        assertEquals("dev.graphassembly.mixin.jei.JeiObservationMixinPlugin", optional.get("plugin").getAsString());
        assertEquals(Set.of("jei.RecipeSlotObservationMixin", "jei.NativeTooltipObservationMixin", "vanilla.client.GuiScissorObservationMixin"), entries(optional, "client"));
        assertTrue(entries(optional, "mixins").isEmpty());
        assertTrue(entries(optional, "server").isEmpty());
        String plugin = Files.readString(JAVA.resolve(optional.get("plugin").getAsString().replace('.', '/') + ".java"));
        assertTrue(plugin.contains("return FMLLoader.getDist().isClient() && FMLLoader.getLoadingModList().getModFileById(\"jei\") != null;"));
        String toml = Files.readString(Path.of("src/main/resources/META-INF/neoforge.mods.toml"));
        assertTrue(toml.contains(MAIN));
        assertTrue(toml.contains(OPTIONAL));
    }

    private Set<String> entries(JsonObject config, String side) {
        var entries = new HashSet<String>();
        if (config.has(side)) config.getAsJsonArray(side).forEach(entry -> entries.add(entry.getAsString()));
        return entries;
    }
}
