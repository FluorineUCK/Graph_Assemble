package dev.graphassembly.core.pipeline;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.condition.checkpoint.CheckpointRequirement;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.codec.GraphCodec;
import dev.graphassembly.codec.ProgressCodec;
import dev.graphassembly.core.condition.mark.MarkRequirement;
import dev.graphassembly.core.condition.ProgressFacts;
import dev.graphassembly.core.condition.ProgressMatcher;
import dev.graphassembly.core.condition.ProgressRequirements;
import dev.graphassembly.core.definition.GraphDefaults;
import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.core.definition.GraphTopology;
import dev.graphassembly.core.condition.mark.MarkAccounting;
import dev.graphassembly.core.io.WeightedOutput;
import dev.graphassembly.core.progress.Progress;
import dev.graphassembly.core.progress.TransitionResult;
import dev.graphassembly.core.route.RouteResolver;
import dev.graphassembly.core.route.RouteState;
import dev.graphassembly.core.settlement.OutputRule;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphPipelineTest {
    @Test
    void overflowingRewardStopsBeforeSecondLoopWithoutChangingStateOrDrawing() {
        var graph = graph("""
            "nodes":[{"id":"loop","start":true,"recipe":{"type":"create:pressing"},
                      "mark_amounts":{"x":2147483647}}],
            "edges":[{"from":"loop","to":"loop"}]
            """);
        var first = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "loop", "test:raw"), () -> fail("No random branch"));
        String saved = ProgressCodec.toJson(((TransitionResult.Continued) first).progress()).toString();
        assertEquals(java.util.Map.of("x", Integer.MAX_VALUE), ((TransitionResult.Continued) first).progress().marks().values());
        assertTrue(GraphPipeline.allowed(graph, ((TransitionResult.Continued) first).progress()).isEmpty(), "Unsafe reward must not be offered to a consuming device");
        var blocked = assertThrows(IllegalArgumentException.class,
            () -> GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) first).progress(), "loop", ((TransitionResult.Continued) first).carrierItem()), () -> fail("Stopped operation must not draw")));
        assertTrue(blocked.getMessage().contains("loop") && blocked.getMessage().contains("x")
            && blocked.getMessage().contains("2147483647"), blocked.getMessage());
        assertEquals(saved, ProgressCodec.toJson(((TransitionResult.Continued) first).progress()).toString());
    }

    @Test
    void overflowingChoiceDoesNotBlockSafeBranchesAndRewardCapsCanRecoverIt() {
        var graph = graph("""
            "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"mark_amounts":{"x":2147483647}},
                     {"id":"unsafe","recipe":{"type":"create:pressing"},"mark_amounts":{"x":1}},
                     {"id":"safe","recipe":{"type":"create:cutting"}}],
            "edges":[{"from":"s","to":"unsafe"},{"from":"s","to":"safe"}]
            """);
        var state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> fail("No draws"))).progress();
        assertEquals(java.util.List.of("safe"), GraphPipeline.allowed(graph, state));
        assertEquals(java.util.Set.of("unsafe"), GraphPipeline.preflight(graph, state).blocked().keySet());
        assertEquals(state.marks().values(), ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, state, "safe", "test:work"), () -> fail("No draws"))).progress().marks().values());

        var capped = graph("""
            "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"mark_amounts":{"x":2147483647},"max_mark_applications":1}],
            "edges":[{"from":"s","to":"s"}]
            """);
        assertTrue(GraphPipeline.preflight(capped, state).blocked().isEmpty());
        assertEquals(java.util.List.of("s"), GraphPipeline.allowed(capped, state));
        assertEquals(state.marks().values(), ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(capped, state, "s", "test:work"), () -> fail("No draws"))).progress().marks().values());
    }

    @Test
    void counterOverflowStopsEvenWithZeroRewardAndDoesNotRerollALockedBranch() {
        var graph = graph("""
            "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                     {"id":"locked","recipe":{"type":"create:cutting"},"mark_amounts":{"x":0}},
                     {"id":"other","recipe":{"type":"create:pressing"}}],
            "edges":[{"from":"s","to":"locked"},{"from":"s","to":"other"}]
            """);
        var state = new Progress(graph.id(), "s", "s", new MarkAccounting.State(java.util.Map.of("x", 0), java.util.Map.of("locked", Integer.MAX_VALUE)), java.util.List.of("s"), new RouteState("locked", null), java.util.List.of("s"));
        assertTrue(GraphPipeline.allowed(graph, state).isEmpty());
        assertTrue(GraphPipeline.preflight(graph, state).blocked().get("locked").message().contains("mark grant counter"));
        var restored = ProgressCodec.fromJson(ProgressCodec.toJson(state));
        assertEquals(state, restored);
        assertEquals("locked", restored.routing().lockedNext());
        assertThrows(IllegalArgumentException.class,
            () -> GraphPipeline.resolve(GraphPipeline.plan(graph, restored, "locked", "test:work"), () -> fail("Blocked lock cannot reroll")));
    }

    @Test
    void disabledConditionsStillRecordAndOnlySettleAtFinish() {
        var base = graph("""
            "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"mark_amounts":{"x":2}},
                     {"id":"f","finish":true,"recipe":{"type":"create:cutting"}}],
            "edges":[{"from":"s","to":"f"}],
            "outputs":[{"results":[{"item":"test:result"}]}]
            """);
        var rule = new OutputRule(null, null, ProgressRequirements.NONE, base.outputRules().getFirst().entries());
        var graph = new GraphDefinition<>(base.id(), new GraphDefaults(base.defaults().ingredient(), base.defaults().transitionalItem()), base.nodes(), new GraphTopology(base.topology().edges()), java.util.List.of(rule), base.layout());
        var started = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> fail("Not finish"));
        assertFalse((started instanceof TransitionResult.Settled));
        assertEquals(java.util.Map.of("x", 2), ((TransitionResult.Continued) started).progress().marks().values());
        assertEquals(java.util.List.of("s"), ((TransitionResult.Continued) started).progress().checkpoints());
        var restored = ProgressCodec.fromJson(ProgressCodec.toJson(((TransitionResult.Continued) started).progress()));
        assertEquals(((TransitionResult.Continued) started).progress(), restored);
        assertEquals("test:result", ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, restored, "f", ((TransitionResult.Continued) started).carrierItem()), () -> 0)).outputs().getFirst().item());
    }

    @Test
    void partialAndExactPoolsMergeWithoutPriorityOrExtraDraws() {
        var base = graph("""
            "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"},"mark_amounts":{"x":3},
                      "outputs":[{"requirements":{"marks":{"x":3}},"results":[{"item":"test:local","weight":2}]}]}],
            "edges":[], "outputs":[{"requirements":{"marks":{"x":3}},"results":[{"item":"test:exact","weight":1}]}]
            """);
        var partial = new OutputRule(null, null, new ProgressRequirements(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.PARTIAL, java.util.Map.of("x", 2)), Map.of()), CheckpointRequirement.NONE),
            java.util.List.of(new WeightedOutput("test:partial", 1, 1, new com.google.gson.JsonObject(), 1)));
        var graph = new GraphDefinition<>(base.id(), new GraphDefaults(base.defaults().ingredient(), base.defaults().transitionalItem()), base.nodes(), new GraphTopology(base.topology().edges()), java.util.List.of(base.outputRules().getFirst(), partial), base.layout());
        var draws = new java.util.concurrent.atomic.AtomicInteger();
        for (double draw : new double[] {0, .3, .8}) {
            var result = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> { draws.incrementAndGet(); return draw; });
            assertEquals(draw == 0 ? "test:exact" : draw == .3 ? "test:partial" : "test:local", ((TransitionResult.Settled) result).outputs().getFirst().item());
        }
        assertEquals(3, draws.get(), "One draw from all matching graph and finish rules");
        var explicitEmpty = new OutputRule(null, null,
            new ProgressRequirements(new MarkRequirement(new MarkRequirement.Match(MarkRequirement.Mode.EXACT, Map.of()), Map.of()), CheckpointRequirement.NONE), partial.entries());
        assertFalse(ProgressMatcher.matches(explicitEmpty.requirements(), new ProgressFacts(java.util.Map.of("x", 1), java.util.List.of())));
    }

    private static GraphDefinition<OperationSpec> graph(String body) {
        return GraphCodec.parse("test:assembly", JsonParser.parseString("""
                {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
                %s}
                """.formatted(body)).getAsJsonObject());
    }

    @Test
    void singleStartFinishSettlesUsingItsOwnRewardAndCheckpoint() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"start","start":true,"finish":true,
                  "recipe":{"type":"create:pressing"},"mark_amounts":{"pressed":1}}],
                "edges":[],"outputs":[{"start":"start","finish":"start",
                  "requirements":{"marks":{"pressed":1},"checkpoints":["start"]},
                  "results":[{"item":"test:finished","count":4}]}]
                """);

        assertEquals(java.util.List.of("start"), GraphPipeline.allowed(graph, null));
        TransitionResult completed = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "start", "test:raw"), () -> 0);

        assertTrue((completed instanceof TransitionResult.Settled));
        assertInstanceOf(TransitionResult.Settled.class, completed);
        assertInstanceOf(TransitionResult.Settled.class, completed);
        assertEquals("test:finished", ((TransitionResult.Settled) completed).outputs().getFirst().item());
        assertEquals(4, ((TransitionResult.Settled) completed).outputs().getFirst().count());
    }

    @Test
    void bidirectionalCycleKeepsOriginalEntryAndEveryStartVisit() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"a","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"b","start":true,"recipe":{"type":"create:cutting"}}],
                "edges":[{"from":"a","to":"b","bidirectional":true}]
                """);
        assertEquals(java.util.List.of("a", "b"), GraphPipeline.allowed(graph, null));
        TransitionResult first = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "a", "test:raw"), () -> 0);
        assertEquals("test:work", ((TransitionResult.Continued) first).carrierItem());
        TransitionResult second = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) first).progress(), "b", ((TransitionResult.Continued) first).carrierItem()), () -> 0);
        assertEquals(java.util.List.of("a"), GraphPipeline.allowed(graph, ((TransitionResult.Continued) second).progress()));
        TransitionResult third = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) second).progress(), "a", ((TransitionResult.Continued) second).carrierItem()), () -> 0);
        assertEquals("a", ((TransitionResult.Continued) third).progress().entry());
        assertEquals(java.util.List.of("a", "b", "a"), ((TransitionResult.Continued) third).progress().checkpoints());
    }

    @Test
    void rewardCapIsLifetimeButDoesNotPreventFurtherVisits() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"a","start":true,"recipe":{"type":"create:pressing"},
                           "mark_amounts":{"work":3},"max_mark_applications":2}],
                "edges":[{"from":"a","to":"a"}]
                """);
        TransitionResult result = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "a", "test:raw"), () -> 0);
        for (int i = 0; i < 4; i++) result = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) result).progress(), "a", ((TransitionResult.Continued) result).carrierItem()), () -> 0);
        assertEquals(java.util.Map.of("work", 6), ((TransitionResult.Continued) result).progress().marks().values());
        assertEquals(java.util.Map.of("a", 2), ((TransitionResult.Continued) result).progress().marks().grantCounts());
        assertEquals(java.util.List.of("a", "a", "a", "a", "a"), ((TransitionResult.Continued) result).progress().checkpoints());
    }

    @Test
    void persistedStateKeepsAllCheckpointsButOnlyLastThreeHistoryEntries() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"a","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"b","checkpoint":true,"recipe":{"type":"create:cutting"},
                          "transitional_item":"test:changed","mark_amounts":{"cut":1}}],
                "edges":[{"from":"a","to":"b","bidirectional":true}]
                """);
        TransitionResult result = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "a", "test:raw"), () -> 0);
        for (String next : java.util.List.of("b", "a", "b", "a")) {
            result = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) result).progress(), next, ((TransitionResult.Continued) result).carrierItem()), () -> 0);
        }
        assertEquals("test:changed", ((TransitionResult.Continued) result).carrierItem());
        Progress restored = ProgressCodec.fromJson(ProgressCodec.toJson(((TransitionResult.Continued) result).progress()));
        assertEquals(((TransitionResult.Continued) result).progress(), restored);
        assertEquals(java.util.List.of("a", "b", "a", "b", "a"), restored.checkpoints());
        assertEquals(java.util.List.of("a", "b", "a"), restored.recentHistory());
        assertEquals(java.util.Map.of("cut", 2), restored.marks().values());
        assertEquals(java.util.Map.of("b", 2), restored.marks().grantCounts());
    }

    @Test
    void ordinaryStepReusesImmutableCheckpointSnapshotWithoutLosingDuplicates() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"n","recipe":{"type":"create:cutting"}}],
                "edges":[{"from":"s","to":"n"},{"from":"n","to":"n"}]
                """);
        Progress prior = new Progress(graph.id(), "s", "s", new MarkAccounting.State(Map.of(), Map.of()), List.of("s", "s", "s"), new RouteState(null, null), List.of("s"));
        Progress next = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, prior, "n", "test:work"), () -> fail("No draw"))).progress();
        assertSame(prior.checkpoints(), next.checkpoints());
        assertEquals(List.of("s", "s", "s"), next.checkpoints());
        assertEquals(List.of("s", "n"), next.recentHistory());
    }

    @Test
    void randomSuccessorIsDrawnOncePersistedAndExclusivelyAllowed() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                         {"id":"a","recipe":{"type":"create:cutting"}},
                         {"id":"b","recipe":{"type":"create:filling"}}],
                "edges":[{"from":"s","to":"a","weight":1},{"from":"s","to":"b","weight":3},
                         {"from":"b","to":"a"}]
                """);
        java.util.concurrent.atomic.AtomicInteger draws = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.DoubleSupplier random = () -> { draws.incrementAndGet(); return 0.4; };
        TransitionResult started = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), random);
        assertEquals(1, draws.get());
        Progress restored = ProgressCodec.fromJson(ProgressCodec.toJson(((TransitionResult.Continued) started).progress()));
        assertEquals("b", restored.routing().lockedNext());
        assertEquals(java.util.List.of("b"), GraphPipeline.allowed(graph, restored));
        assertNull(RouteResolver.incompatibility(graph, restored));
        assertThrows(IllegalArgumentException.class, () -> GraphPipeline.resolve(GraphPipeline.plan(graph, restored, "a", ((TransitionResult.Continued) started).carrierItem()), random));
        assertEquals(1, draws.get());
        TransitionResult advanced = GraphPipeline.resolve(GraphPipeline.plan(graph, restored, "b", ((TransitionResult.Continued) started).carrierItem()), random);
        assertNull(((TransitionResult.Continued) advanced).progress().routing().lockedNext());
        assertEquals(java.util.List.of("s"), ((TransitionResult.Continued) advanced).progress().checkpoints());
        assertEquals(java.util.List.of("a"), GraphPipeline.allowed(graph, ((TransitionResult.Continued) advanced).progress()));
        assertEquals(1, draws.get());
    }

    @Test
    void allMatchingGraphAndNodePoolsMergeIntoOneWeightedWholeStackBeforeBranching() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"finish":true,"branch_mode":"random",
                          "recipe":{"type":"create:pressing"},"outputs":[
                            {"results":[{"item":"test:second","count":7,"weight":3,
                              "components":{"test:quality":{"value":2}}}]},
                            {"finish":"other","results":[{"item":"test:wrong","weight":1000}]}]},
                         {"id":"other","finish":true,"recipe":{"type":"create:cutting"}}],
                "edges":[{"from":"s","to":"other"}],
                "outputs":[{"results":[{"item":"test:first","count":2,"weight":1}]}]
                """);
        java.util.concurrent.atomic.AtomicInteger draws = new java.util.concurrent.atomic.AtomicInteger();
        TransitionResult result = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> { draws.incrementAndGet(); return 0.5; });
        assertTrue((result instanceof TransitionResult.Settled));
        assertEquals("test:second", ((TransitionResult.Settled) result).outputs().getFirst().item());
        assertEquals(7, ((TransitionResult.Settled) result).outputs().getFirst().count());
        assertEquals(2, ((TransitionResult.Settled) result).outputs().getFirst().components().getAsJsonObject("test:quality").get("value").getAsInt());
        assertEquals(1, draws.get());
        assertEquals("test:first", ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> 0)).outputs().getFirst().item());
    }

    static java.util.stream.Stream<Arguments> checkpointCases() {
        return java.util.stream.Stream.of(
                Arguments.of("", true),
                Arguments.of("[]", false),
                Arguments.of("[\"s\",\"a\",\"f\"]", false),
                Arguments.of("[\"s\",\"a\",\"x\",\"a\",\"f\"]", true),
                Arguments.of("{\"mode\":\"exact\",\"nodes\":[\"s\",\"a\",\"x\",\"a\",\"f\"]}", true),
                Arguments.of("{\"mode\":\"partial\",\"nodes\":[\"s\",\"a\",\"a\",\"f\"]}", true),
                Arguments.of("{\"mode\":\"partial\",\"nodes\":[\"s\",\"x\",\"x\",\"f\"]}", false),
                Arguments.of("{\"mode\":\"unordered\",\"nodes\":[\"f\",\"a\",\"s\"]}", true));
    }

    @ParameterizedTest
    @MethodSource("checkpointCases")
    void checkpointsMatchFullHistoryOrTheExplicitRelaxedMode(String requirement, boolean settles) {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"a","checkpoint":true,"recipe":{"type":"create:cutting"}},
                         {"id":"x","checkpoint":true,"recipe":{"type":"create:cutting"}},
                         {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                "edges":[{"from":"s","to":"a"},{"from":"a","to":"x"},
                         {"from":"x","to":"a"},{"from":"a","to":"f"}],
                "outputs":[{%s"results":[{"item":"test:finished"}]}]
                """.formatted(requirement.isEmpty() ? "" : "\"requirements\":{\"checkpoints\":" + requirement + "},"));
        TransitionResult result = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> 0);
        for (String next : java.util.List.of("a", "x", "a", "f")) {
            result = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) result).progress(), next, ((TransitionResult.Continued) result).carrierItem()), () -> 0);
        }
        assertEquals(settles, (result instanceof TransitionResult.Settled));
    }

    @Test
    void strictMismatchConsumesWithOptionalWeightedScrapAndNeverDrawsAnOutgoingBranch() {
        for (boolean hasScrap : java.util.List.of(false, true)) {
            GraphDefinition<OperationSpec> graph = graph("""
                    "nodes":[{"id":"s","start":true,"finish":true,"finish_mode":"strict",
                      "branch_mode":"random","recipe":{"type":"create:pressing"},"mark_amounts":{"pressed":2}%s}],
                    "edges":[{"from":"s","to":"s"}],
                    "outputs":[{"requirements":{"marks":{"pressed":1}},"results":[{"item":"test:wrong"}]}]
                    """.formatted(hasScrap ? ",\"scrap\":[{\"item\":\"test:scrap\",\"count\":3}]" : ""));
            java.util.concurrent.atomic.AtomicInteger draws = new java.util.concurrent.atomic.AtomicInteger();
            TransitionResult result = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> { draws.incrementAndGet(); return 0; });
            assertTrue((result instanceof TransitionResult.Settled));
            assertInstanceOf(TransitionResult.Settled.class, result);
            assertInstanceOf(TransitionResult.Settled.class, result);
            assertEquals(hasScrap ? 1 : 0, draws.get());
            if (hasScrap) {
                assertEquals("test:scrap", ((TransitionResult.Settled) result).outputs().getFirst().item());
                assertEquals(3, ((TransitionResult.Settled) result).outputs().getFirst().count());
            } else assertTrue(((TransitionResult.Settled) result).outputs().isEmpty());
        }
    }

    @Test
    void incompatibleReloadStopsWithoutMutatingOrRerollingPersistedLock() {
        String original = """
                "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                         {"id":"a","recipe":{"type":"create:cutting"}},
                         {"id":"b","recipe":{"type":"create:filling"}}],
                "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]
                """;
        GraphDefinition<OperationSpec> graph = graph(original);
        Progress state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> 0.75)).progress();
        String before = ProgressCodec.toJson(state).toString();
        java.util.List<GraphDefinition<OperationSpec>> reloads = new java.util.ArrayList<>();
        reloads.add(null);
        reloads.add(new GraphDefinition<>("test:different", new GraphDefaults(graph.defaults().ingredient(), graph.defaults().transitionalItem()), graph.nodes(), new GraphTopology(graph.topology().edges()), graph.outputRules(), graph.layout()));
        reloads.add(graph(original.replace("\"branch_mode\":\"random\"", "\"branch_mode\":\"choice\"")));
        reloads.add(graph(original.replace(", {\"from\":\"s\",\"to\":\"b\"}", "").replace(",{\"from\":\"s\",\"to\":\"b\"}", "")));
        reloads.add(graph("""
                "nodes":[{"id":"other","start":true,"recipe":{"type":"create:pressing"}}],"edges":[]
                """));
        for (GraphDefinition<OperationSpec> reloaded : reloads) {
            assertNotNull(RouteResolver.incompatibility(reloaded, state));
            assertTrue(GraphPipeline.allowed(reloaded, state).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> GraphPipeline.resolve(GraphPipeline.plan(reloaded, state, "b", "test:work"), () -> fail("Reload must not reroll")));
            assertEquals(before, ProgressCodec.toJson(state).toString());
        }
        GraphDefinition<OperationSpec> oldChoice = graph(original.replace("\"branch_mode\":\"random\"", "\"branch_mode\":\"choice\""));
        Progress noLock = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(oldChoice, null, "s", "test:raw"), () -> fail("Choice must not draw"))).progress();
        assertNotNull(RouteResolver.incompatibility(graph, noLock));
        assertTrue(GraphPipeline.allowed(graph, noLock).isEmpty());
    }

    @Test
    void weightedSelectionHandlesFiniteWeightsWhoseSumOverflowsAndZeroDrawAtTinyWeight() {
        String template = """
                "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
                "outputs":[{"results":[{"item":"test:first","weight":%s},{"item":"test:second","weight":1e308}]}]
                """;
        GraphDefinition<OperationSpec> large = graph(template.formatted("1e308"));
        assertEquals("test:second", ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(large, null, "s", "test:raw"), () -> 0.75)).outputs().getFirst().item());
        GraphDefinition<OperationSpec> tiny = graph(template.formatted("5e-324"));
        assertEquals("test:first", ((TransitionResult.Settled) GraphPipeline.resolve(GraphPipeline.plan(tiny, null, "s", "test:raw"), () -> 0)).outputs().getFirst().item());
    }

    @Test
    void lenientMismatchRetainsUpdatedItemMarksCheckpointAndThenDrawsBranch() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"mark_amounts":{"base":1}},
                         {"id":"f","finish":true,"recipe":{"type":"create:cutting"},"mark_amounts":{"extra":1},
                          "transitional_item":"test:changed","branch_mode":"random"}],
                "edges":[{"from":"s","to":"f"},{"from":"f","to":"s"}],
                "outputs":[{"requirements":{"marks":{"base":1}},"results":[{"item":"test:wrong"}]}]
                """);
        TransitionResult start = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> fail("Non-finish does not settle"));
        assertFalse((start instanceof TransitionResult.Settled));
        java.util.concurrent.atomic.AtomicInteger draws = new java.util.concurrent.atomic.AtomicInteger();
        TransitionResult finish = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) start).progress(), "f", ((TransitionResult.Continued) start).carrierItem()), () -> { draws.incrementAndGet(); return 0; });
        assertFalse((finish instanceof TransitionResult.Settled));
        assertInstanceOf(TransitionResult.Continued.class, finish);
        assertEquals("test:changed", ((TransitionResult.Continued) finish).carrierItem());
        assertEquals(java.util.Map.of("base", 1, "extra", 1), ((TransitionResult.Continued) finish).progress().marks().values());
        assertEquals(java.util.List.of("s", "f"), ((TransitionResult.Continued) finish).progress().checkpoints());
        assertEquals("s", ((TransitionResult.Continued) finish).progress().routing().lockedNext());
        assertEquals(1, draws.get());
        assertEquals(java.util.Map.of("base", 1), ((TransitionResult.Continued) start).progress().marks().values());
    }

    @Test
    void passingThroughAnotherStartDoesNotSatisfyItsEntryConstraint() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"t","start":true,"recipe":{"type":"create:cutting"}},
                         {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                "edges":[{"from":"s","to":"t"},{"from":"t","to":"f"}],
                "outputs":[{"start":"t","results":[{"item":"test:result"}]}]
                """);
        TransitionResult result = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> 0);
        result = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) result).progress(), "t", ((TransitionResult.Continued) result).carrierItem()), () -> 0);
        result = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) result).progress(), "f", ((TransitionResult.Continued) result).carrierItem()), () -> 0);
        assertFalse((result instanceof TransitionResult.Settled));
        assertEquals("s", ((TransitionResult.Continued) result).progress().entry());
        assertTrue(GraphPipeline.allowed(graph, ((TransitionResult.Continued) result).progress()).isEmpty());
        TransitionResult direct = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "t", "test:raw"), () -> 0);
        assertTrue((GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) direct).progress(), "f", ((TransitionResult.Continued) direct).carrierItem()), () -> 0) instanceof TransitionResult.Settled));
    }

    @Test
    void illegalChoiceCannotGrantMarksConsumeRandomOrMutateTheSnapshot() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"a","recipe":{"type":"create:cutting"}},
                         {"id":"b","recipe":{"type":"create:cutting"}},
                         {"id":"unreachable","mark_amounts":{"wrong":1},"recipe":{"type":"create:pressing"}}],
                "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]
                """);
        assertThrows(IllegalArgumentException.class, () -> GraphPipeline.resolve(GraphPipeline.plan(graph, null, "a", "test:raw"), () -> fail("Illegal operation cannot draw")));
        Progress state = ((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> 0)).progress();
        assertEquals(java.util.List.of("a", "b"), GraphPipeline.allowed(graph, state));
        String before = ProgressCodec.toJson(state).toString();
        assertThrows(IllegalArgumentException.class, () -> GraphPipeline.resolve(GraphPipeline.plan(graph, state, "unreachable", "test:work"), () -> fail("Illegal operation cannot draw")));
        assertThrows(IllegalArgumentException.class, () -> GraphPipeline.resolve(GraphPipeline.plan(graph, state, "missing", "test:work"), () -> fail("Illegal operation cannot draw")));
        assertEquals(before, ProgressCodec.toJson(state).toString());
    }

    @Test
    void zeroRewardCapNeverGrantsButStillAdvancesAndRecordsVisits() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"mark_amounts":{"work":1},"max_mark_applications":0,
                          "transitional_item":"test:node_override","recipe":{"type":"create:pressing"}}],
                "edges":[{"from":"s","to":"s"}]
                """);
        TransitionResult first = GraphPipeline.resolve(GraphPipeline.plan(graph, null, "s", "test:raw"), () -> 0);
        TransitionResult second = GraphPipeline.resolve(GraphPipeline.plan(graph, ((TransitionResult.Continued) first).progress(), "s", ((TransitionResult.Continued) first).carrierItem()), () -> 0);
        assertEquals("test:node_override", ((TransitionResult.Continued) second).carrierItem());
        assertTrue(((TransitionResult.Continued) second).progress().marks().values().isEmpty());
        assertTrue(((TransitionResult.Continued) second).progress().marks().grantCounts().isEmpty());
        assertEquals(java.util.List.of("s", "s"), ((TransitionResult.Continued) second).progress().checkpoints());
    }

    @Test
    void reloadReasonsIdentifyRemovedCurrentEntryAndLockedTargets() {
        GraphDefinition<OperationSpec> graph = graph("""
                "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                         {"id":"f","finish":true,"recipe":{"type":"create:cutting"}}],
                "edges":[{"from":"s","to":"f"}]
                """);
        Progress missingCurrent = new Progress(graph.id(), "s", "deleted", new MarkAccounting.State(java.util.Map.of(), java.util.Map.of()), java.util.List.of("s"), new RouteState(null, null), java.util.List.of());
        assertTrue(RouteResolver.incompatibility(graph, missingCurrent).contains("Current node is missing: deleted"));
        Progress missingEntry = new Progress(graph.id(), "deleted", "s", new MarkAccounting.State(java.util.Map.of(), java.util.Map.of()), java.util.List.of("deleted"), new RouteState("f", null), java.util.List.of());
        assertTrue(RouteResolver.incompatibility(graph, missingEntry).contains("Entry node is missing: deleted"));
        Progress missingLock = new Progress(graph.id(), "s", "s", new MarkAccounting.State(java.util.Map.of(), java.util.Map.of()), java.util.List.of("s"), new RouteState("deleted", null), java.util.List.of());
        assertTrue(RouteResolver.incompatibility(graph, missingLock).contains("Locked target is missing: deleted"));
    }
}
