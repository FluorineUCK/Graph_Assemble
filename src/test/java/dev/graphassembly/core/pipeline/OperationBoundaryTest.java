package dev.graphassembly.core.pipeline;

import com.google.gson.JsonObject;
import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.condition.ProgressRequirements;
import dev.graphassembly.core.definition.GraphDefaults;
import dev.graphassembly.core.definition.GraphLayout;
import dev.graphassembly.core.definition.GraphTopology;
import dev.graphassembly.core.definition.basic.Edge;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.basic.NodeDefinition;
import dev.graphassembly.core.io.WeightedOutput;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.BranchMode;
import dev.graphassembly.core.settlement.FinishMode;
import dev.graphassembly.core.settlement.OutputRule;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class OperationBoundaryTest {
    private static NodeDefinition<String> node(String id, boolean start, boolean finish, String operation) {
        return new NodeDefinition<>(id, new NodeDefinition.NodeRoles(start, finish), operation,
            new NodeDefinition.InputPolicy(null, List.of()),
            new NodeDefinition.NodeEffects(null, Map.of("worked", 1), null, false, List.of()),
            new NodeDefinition.RoutingPolicy(BranchMode.CHOICE, null),
            new NodeDefinition.SettlementPolicy(FinishMode.STRICT, List.of(), List.of()));
    }

    private static GraphDefinition<String> graph(String operation) {
        return new GraphDefinition<>("test:opaque", new GraphDefaults(null, "test:work"),
            Map.of("start", node("start", true, false, operation),
                "finish", node("finish", false, true, "another opaque operation")),
            new GraphTopology(List.of(new Edge("start", "finish", 1))),
            List.of(new OutputRule(null, null, ProgressRequirements.NONE,
                List.of(new WeightedOutput("test:result", 2, 1, new JsonObject(), 1)))),
            new GraphLayout(Map.of(), Map.of()));
    }

    @Test void pipelineRoutesAndSettlesWithoutUnderstandingOperationPayload() {
        var graph = graph("not recipe JSON or a device type");
        assertEquals(List.of("start"), GraphPipeline.allowed(graph, null));
        var start = assertInstanceOf(TransitionResult.Continued.class, GraphPipeline.resolve(
            GraphPipeline.plan(graph, null, "start", "test:raw"), () -> fail("No random route")));
        assertEquals("test:work", start.carrierItem());
        assertEquals(Map.of("worked", 1), start.progress().marks().values());
        assertEquals(List.of("start"), start.progress().checkpoints());
        assertEquals(List.of("finish"), GraphPipeline.allowed(graph, start.progress()));
        var finish = assertInstanceOf(TransitionResult.Settled.class, GraphPipeline.resolve(
            GraphPipeline.plan(graph, start.progress(), "finish", start.carrierItem()), () -> 0));
        assertEquals(1, finish.outputs().size());
        assertEquals("test:result", finish.outputs().getFirst().item());
        assertEquals(2, finish.outputs().getFirst().count());
    }

    @Test void opaqueConfigurationStillParticipatesInDefinitionEquality() {
        assertEquals(graph("one"), graph("one"));
        assertNotEquals(graph("one"), graph("two"));
        assertEquals(graph("one").hashCode(), graph("one").hashCode());
    }

    @Test void coreHasNoCodecRecipeDeviceOrPresentationDependencies() throws Exception {
        var forbidden = Pattern.compile("dev\\.graphassembly\\.(?!core\\b)[A-Za-z]"
            + "|net\\.(?:minecraft|neoforged)\\.|com\\.simibubi\\.|mezz\\.jei\\.");
        assertTrue(forbidden.matcher("import dev.graphassembly.codec.JsonValidation;").find());
        assertTrue(forbidden.matcher("import dev.graphassembly.recipe.OperationSpec;").find());
        assertTrue(forbidden.matcher("net.minecraft.world.item.ItemStack").find());
        try (var files = Files.walk(Path.of("src/main/java/dev/graphassembly/core"))) {
            var sources = files.filter(file -> file.toString().endsWith(".java")).toList();
            assertFalse(sources.isEmpty());
            for (var file : sources)
                assertFalse(forbidden.matcher(Files.readString(file)).find(), file.toString());
        }
    }
}
