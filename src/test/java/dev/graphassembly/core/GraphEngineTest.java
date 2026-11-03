package dev.graphassembly.core;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class GraphEngineTest {
    @Test
    void overflowingRewardStopsBeforeSecondLoopWithoutChangingStateOrDrawing() {
        var graph = graph("""
            "nodes":[{"id":"loop","start":true,"recipe":{"type":"create:pressing"},
                      "rewards":{"x":2147483647}}],
            "edges":[{"from":"loop","to":"loop"}]
            """);
        var first = GraphEngine.complete(graph, null, "loop", "test:raw", () -> fail("No random branch"));
        String saved = first.state().toJson().toString();
        assertEquals(java.util.Map.of("x", Integer.MAX_VALUE), first.state().marks());
        assertTrue(GraphEngine.allowed(graph, first.state()).isEmpty(), "Unsafe reward must not be offered to a consuming device");
        var blocked = assertThrows(IllegalArgumentException.class,
            () -> GraphEngine.complete(graph, first.state(), "loop", first.item(), () -> fail("Stopped operation must not draw")));
        assertTrue(blocked.getMessage().contains("loop") && blocked.getMessage().contains("x")
            && blocked.getMessage().contains("2147483647"), blocked.getMessage());
        assertEquals(saved, first.state().toJson().toString());
    }

    @Test
    void overflowingChoiceDoesNotBlockSafeBranchesAndRewardCapsCanRecoverIt() {
        var graph = graph("""
            "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"rewards":{"x":2147483647}},
                     {"id":"unsafe","recipe":{"type":"create:pressing"},"rewards":{"x":1}},
                     {"id":"safe","recipe":{"type":"create:cutting"}}],
            "edges":[{"from":"s","to":"unsafe"},{"from":"s","to":"safe"}]
            """);
        var state = GraphEngine.complete(graph, null, "s", "test:raw", () -> fail("No draws")).state();
        assertEquals(java.util.List.of("safe"), GraphEngine.allowed(graph, state));
        assertEquals(java.util.Set.of("unsafe"), GraphEngine.blockedSteps(graph, state).keySet());
        assertEquals(state.marks(), GraphEngine.complete(graph, state, "safe", "test:work", () -> fail("No draws")).state().marks());

        var capped = graph("""
            "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"rewards":{"x":2147483647},"reward_limit":1}],
            "edges":[{"from":"s","to":"s"}]
            """);
        assertTrue(GraphEngine.blockedSteps(capped, state).isEmpty());
        assertEquals(java.util.List.of("s"), GraphEngine.allowed(capped, state));
        assertEquals(state.marks(), GraphEngine.complete(capped, state, "s", "test:work", () -> fail("No draws")).state().marks());
    }

    @Test
    void counterOverflowStopsEvenWithZeroRewardAndDoesNotRerollALockedBranch() {
        var graph = graph("""
            "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                     {"id":"locked","recipe":{"type":"create:cutting"},"rewards":{"x":0}},
                     {"id":"other","recipe":{"type":"create:pressing"}}],
            "edges":[{"from":"s","to":"locked"},{"from":"s","to":"other"}]
            """);
        var state = new Progress(graph.id(), "s", "s", java.util.Map.of("x", 0), java.util.List.of("s"),
            java.util.Map.of("locked", Integer.MAX_VALUE), "locked", java.util.List.of("s"));
        assertTrue(GraphEngine.allowed(graph, state).isEmpty());
        assertTrue(GraphEngine.blockedSteps(graph, state).get("locked").contains("reward counter"));
        var restored = Progress.fromJson(state.toJson());
        assertEquals(state, restored);
        assertEquals("locked", restored.lockedNext());
        assertThrows(IllegalArgumentException.class,
            () -> GraphEngine.complete(graph, restored, "locked", "test:work", () -> fail("Blocked lock cannot reroll")));
    }

    @Test
    void disabledConditionsStillRecordAndOnlySettleAtFinish() {
        var base = graph("""
            "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"rewards":{"x":2}},
                     {"id":"f","finish":true,"recipe":{"type":"create:cutting"}}],
            "edges":[{"from":"s","to":"f"}],
            "outputs":[{"results":[{"item":"test:result"}]}]
            """);
        var rule = new OutputRule(null, null, new CompletionRequirements(null, null), base.outputs().getFirst().results());
        var graph = new GraphDefinition(base.id(), base.ingredient(), base.transitionalItem(), base.nodes(), base.edges(), java.util.List.of(rule));
        var started = GraphEngine.complete(graph, null, "s", "test:raw", () -> fail("Not finish"));
        assertFalse(started.settled());
        assertEquals(java.util.Map.of("x", 2), started.state().marks());
        assertEquals(java.util.List.of("s"), started.state().checkpoints());
        var restored = Progress.fromJson(started.state().toJson());
        assertEquals(started.state(), restored);
        assertEquals("test:result", GraphEngine.complete(graph, restored, "f", started.item(), () -> 0).output().item());
    }

    @Test
    void partialAndExactPoolsMergeWithoutPriorityOrExtraDraws() {
        var base = graph("""
            "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"},"rewards":{"x":3},
                      "outputs":[{"requirements":{"marks":{"x":3}},"results":[{"item":"test:local","weight":2}]}]}],
            "edges":[], "outputs":[{"requirements":{"marks":{"x":3}},"results":[{"item":"test:exact","weight":1}]}]
            """);
        var partial = new OutputRule(null, null, new CompletionRequirements(new MarkRequirement(MarkRequirement.Mode.PARTIAL, java.util.Map.of("x", 2)), null),
            java.util.List.of(new WeightedOutput("test:partial", 1, 1, new com.google.gson.JsonObject())));
        var graph = new GraphDefinition(base.id(), base.ingredient(), base.transitionalItem(), base.nodes(), base.edges(),
            java.util.List.of(base.outputs().getFirst(), partial));
        var draws = new java.util.concurrent.atomic.AtomicInteger();
        for (double draw : new double[] {0, .3, .8}) {
            var result = GraphEngine.complete(graph, null, "s", "test:raw", () -> { draws.incrementAndGet(); return draw; });
            assertEquals(draw == 0 ? "test:exact" : draw == .3 ? "test:partial" : "test:local", result.output().item());
        }
        assertEquals(3, draws.get(), "One draw from all matching graph and finish rules");
        var explicitEmpty = new OutputRule(null, null,
            new CompletionRequirements(new MarkRequirement(MarkRequirement.Mode.EXACT, Map.of()), null), partial.results());
        assertFalse(explicitEmpty.requirements().matches(java.util.Map.of("x", 1), java.util.List.of()));
    }

    private static GraphDefinition graph(String body) {
        return GraphCodec.parse("test:assembly", JsonParser.parseString("""
                {"ingredient":{"item":"test:raw"},"transitional_item":"test:work",
                %s}
                """.formatted(body)).getAsJsonObject());
    }

    @Test
    void singleStartFinishSettlesUsingItsOwnRewardAndCheckpoint() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"start","start":true,"finish":true,
                  "recipe":{"type":"create:pressing"},"rewards":{"pressed":1}}],
                "edges":[],"outputs":[{"start":"start","finish":"start",
                  "requirements":{"marks":{"pressed":1},"checkpoints":["start"]},
                  "results":[{"item":"test:finished","count":4}]}]
                """);

        assertEquals(java.util.List.of("start"), GraphEngine.allowed(graph, null));
        Completion completed = GraphEngine.complete(graph, null, "start", "test:raw", () -> 0);

        assertTrue(completed.settled());
        assertNull(completed.state());
        assertNull(completed.item());
        assertEquals("test:finished", completed.output().item());
        assertEquals(4, completed.output().count());
    }

    @Test
    void bidirectionalCycleKeepsOriginalEntryAndEveryStartVisit() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"a","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"b","start":true,"recipe":{"type":"create:cutting"}}],
                "edges":[{"from":"a","to":"b","bidirectional":true}]
                """);
        assertEquals(java.util.List.of("a", "b"), GraphEngine.allowed(graph, null));
        Completion first = GraphEngine.complete(graph, null, "a", "test:raw", () -> 0);
        assertEquals("test:work", first.item());
        Completion second = GraphEngine.complete(graph, first.state(), "b", first.item(), () -> 0);
        assertEquals(java.util.List.of("a"), GraphEngine.allowed(graph, second.state()));
        Completion third = GraphEngine.complete(graph, second.state(), "a", second.item(), () -> 0);
        assertEquals("a", third.state().entry());
        assertEquals(java.util.List.of("a", "b", "a"), third.state().checkpoints());
    }

    @Test
    void rewardCapIsLifetimeButDoesNotPreventFurtherVisits() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"a","start":true,"recipe":{"type":"create:pressing"},
                           "rewards":{"work":3},"reward_limit":2}],
                "edges":[{"from":"a","to":"a"}]
                """);
        Completion result = GraphEngine.complete(graph, null, "a", "test:raw", () -> 0);
        for (int i = 0; i < 4; i++) result = GraphEngine.complete(graph, result.state(), "a", result.item(), () -> 0);
        assertEquals(java.util.Map.of("work", 6), result.state().marks());
        assertEquals(java.util.Map.of("a", 2), result.state().rewardCounts());
        assertEquals(java.util.List.of("a", "a", "a", "a", "a"), result.state().checkpoints());
    }

    @Test
    void persistedStateKeepsAllCheckpointsButOnlyLastThreeHistoryEntries() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"a","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"b","checkpoint":true,"recipe":{"type":"create:cutting"},
                          "transitional_item":"test:changed","rewards":{"cut":1}}],
                "edges":[{"from":"a","to":"b","bidirectional":true}]
                """);
        Completion result = GraphEngine.complete(graph, null, "a", "test:raw", () -> 0);
        for (String next : java.util.List.of("b", "a", "b", "a")) {
            result = GraphEngine.complete(graph, result.state(), next, result.item(), () -> 0);
        }
        assertEquals("test:changed", result.item());
        Progress restored = Progress.fromJson(result.state().toJson());
        assertEquals(result.state(), restored);
        assertEquals(java.util.List.of("a", "b", "a", "b", "a"), restored.checkpoints());
        assertEquals(java.util.List.of("a", "b", "a"), restored.history());
        assertEquals(java.util.Map.of("cut", 2), restored.marks());
        assertEquals(java.util.Map.of("b", 2), restored.rewardCounts());
    }

    @Test
    void ordinaryStepReusesImmutableCheckpointSnapshotWithoutLosingDuplicates() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"n","recipe":{"type":"create:cutting"}}],
                "edges":[{"from":"s","to":"n"},{"from":"n","to":"n"}]
                """);
        Progress prior = new Progress(graph.id(), "s", "s", Map.of(),
                List.of("s", "s", "s"), Map.of(), null, List.of("s"));
        Progress next = GraphEngine.complete(graph, prior, "n", "test:work", () -> fail("No draw")).state();
        assertSame(prior.checkpoints(), next.checkpoints());
        assertEquals(List.of("s", "s", "s"), next.checkpoints());
        assertEquals(List.of("s", "n"), next.history());
    }

    @Test
    void randomSuccessorIsDrawnOncePersistedAndExclusivelyAllowed() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                         {"id":"a","recipe":{"type":"create:cutting"}},
                         {"id":"b","recipe":{"type":"create:filling"}}],
                "edges":[{"from":"s","to":"a","weight":1},{"from":"s","to":"b","weight":3},
                         {"from":"b","to":"a"}]
                """);
        java.util.concurrent.atomic.AtomicInteger draws = new java.util.concurrent.atomic.AtomicInteger();
        java.util.function.DoubleSupplier random = () -> { draws.incrementAndGet(); return 0.4; };
        Completion started = GraphEngine.complete(graph, null, "s", "test:raw", random);
        assertEquals(1, draws.get());
        Progress restored = Progress.fromJson(started.state().toJson());
        assertEquals("b", restored.lockedNext());
        assertEquals(java.util.List.of("b"), GraphEngine.allowed(graph, restored));
        assertNull(GraphEngine.incompatibility(graph, restored));
        assertThrows(IllegalArgumentException.class, () -> GraphEngine.complete(graph, restored, "a", started.item(), random));
        assertEquals(1, draws.get());
        Completion advanced = GraphEngine.complete(graph, restored, "b", started.item(), random);
        assertNull(advanced.state().lockedNext());
        assertEquals(java.util.List.of("s"), advanced.state().checkpoints());
        assertEquals(java.util.List.of("a"), GraphEngine.allowed(graph, advanced.state()));
        assertEquals(1, draws.get());
    }

    @Test
    void allMatchingGraphAndNodePoolsMergeIntoOneWeightedWholeStackBeforeBranching() {
        GraphDefinition graph = graph("""
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
        Completion result = GraphEngine.complete(graph, null, "s", "test:raw", () -> { draws.incrementAndGet(); return 0.5; });
        assertTrue(result.settled());
        assertEquals("test:second", result.output().item());
        assertEquals(7, result.output().count());
        assertEquals(2, result.output().components().getAsJsonObject("test:quality").get("value").getAsInt());
        assertEquals(1, draws.get());
        assertEquals("test:first", GraphEngine.complete(graph, null, "s", "test:raw", () -> 0).output().item());
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
        GraphDefinition graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"a","checkpoint":true,"recipe":{"type":"create:cutting"}},
                         {"id":"x","checkpoint":true,"recipe":{"type":"create:cutting"}},
                         {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                "edges":[{"from":"s","to":"a"},{"from":"a","to":"x"},
                         {"from":"x","to":"a"},{"from":"a","to":"f"}],
                "outputs":[{%s"results":[{"item":"test:finished"}]}]
                """.formatted(requirement.isEmpty() ? "" : "\"requirements\":{\"checkpoints\":" + requirement + "},"));
        Completion result = GraphEngine.complete(graph, null, "s", "test:raw", () -> 0);
        for (String next : java.util.List.of("a", "x", "a", "f")) {
            result = GraphEngine.complete(graph, result.state(), next, result.item(), () -> 0);
        }
        assertEquals(settles, result.settled());
    }

    @Test
    void strictMismatchConsumesWithOptionalWeightedScrapAndNeverDrawsAnOutgoingBranch() {
        for (boolean hasScrap : java.util.List.of(false, true)) {
            GraphDefinition graph = graph("""
                    "nodes":[{"id":"s","start":true,"finish":true,"finish_mode":"strict",
                      "branch_mode":"random","recipe":{"type":"create:pressing"},"rewards":{"pressed":2}%s}],
                    "edges":[{"from":"s","to":"s"}],
                    "outputs":[{"requirements":{"marks":{"pressed":1}},"results":[{"item":"test:wrong"}]}]
                    """.formatted(hasScrap ? ",\"scrap\":[{\"item\":\"test:scrap\",\"count\":3}]" : ""));
            java.util.concurrent.atomic.AtomicInteger draws = new java.util.concurrent.atomic.AtomicInteger();
            Completion result = GraphEngine.complete(graph, null, "s", "test:raw", () -> { draws.incrementAndGet(); return 0; });
            assertTrue(result.settled());
            assertNull(result.state());
            assertNull(result.item());
            assertEquals(hasScrap ? 1 : 0, draws.get());
            if (hasScrap) {
                assertEquals("test:scrap", result.output().item());
                assertEquals(3, result.output().count());
            } else assertNull(result.output());
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
        GraphDefinition graph = graph(original);
        Progress state = GraphEngine.complete(graph, null, "s", "test:raw", () -> 0.75).state();
        String before = state.toJson().toString();
        java.util.List<GraphDefinition> reloads = new java.util.ArrayList<>();
        reloads.add(null);
        reloads.add(new GraphDefinition("test:different", graph.ingredient(), graph.transitionalItem(), graph.nodes(), graph.edges(), graph.outputs()));
        reloads.add(graph(original.replace("\"branch_mode\":\"random\"", "\"branch_mode\":\"choice\"")));
        reloads.add(graph(original.replace(", {\"from\":\"s\",\"to\":\"b\"}", "").replace(",{\"from\":\"s\",\"to\":\"b\"}", "")));
        reloads.add(graph("""
                "nodes":[{"id":"other","start":true,"recipe":{"type":"create:pressing"}}],"edges":[]
                """));
        for (GraphDefinition reloaded : reloads) {
            assertNotNull(GraphEngine.incompatibility(reloaded, state));
            assertTrue(GraphEngine.allowed(reloaded, state).isEmpty());
            assertThrows(IllegalArgumentException.class, () -> GraphEngine.complete(reloaded, state, "b", "test:work", () -> fail("Reload must not reroll")));
            assertEquals(before, state.toJson().toString());
        }
        GraphDefinition oldChoice = graph(original.replace("\"branch_mode\":\"random\"", "\"branch_mode\":\"choice\""));
        Progress noLock = GraphEngine.complete(oldChoice, null, "s", "test:raw", () -> fail("Choice must not draw")).state();
        assertNotNull(GraphEngine.incompatibility(graph, noLock));
        assertTrue(GraphEngine.allowed(graph, noLock).isEmpty());
    }

    @Test
    void weightedSelectionHandlesFiniteWeightsWhoseSumOverflowsAndZeroDrawAtTinyWeight() {
        String template = """
                "nodes":[{"id":"s","start":true,"finish":true,"recipe":{"type":"create:pressing"}}],"edges":[],
                "outputs":[{"results":[{"item":"test:first","weight":%s},{"item":"test:second","weight":1e308}]}]
                """;
        GraphDefinition large = graph(template.formatted("1e308"));
        assertEquals("test:second", GraphEngine.complete(large, null, "s", "test:raw", () -> 0.75).output().item());
        GraphDefinition tiny = graph(template.formatted("5e-324"));
        assertEquals("test:first", GraphEngine.complete(tiny, null, "s", "test:raw", () -> 0).output().item());
    }

    @Test
    void lenientMismatchRetainsUpdatedItemMarksCheckpointAndThenDrawsBranch() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"},"rewards":{"base":1}},
                         {"id":"f","finish":true,"recipe":{"type":"create:cutting"},"rewards":{"extra":1},
                          "transitional_item":"test:changed","branch_mode":"random"}],
                "edges":[{"from":"s","to":"f"},{"from":"f","to":"s"}],
                "outputs":[{"requirements":{"marks":{"base":1}},"results":[{"item":"test:wrong"}]}]
                """);
        Completion start = GraphEngine.complete(graph, null, "s", "test:raw", () -> fail("Non-finish does not settle"));
        assertFalse(start.settled());
        java.util.concurrent.atomic.AtomicInteger draws = new java.util.concurrent.atomic.AtomicInteger();
        Completion finish = GraphEngine.complete(graph, start.state(), "f", start.item(), () -> { draws.incrementAndGet(); return 0; });
        assertFalse(finish.settled());
        assertNull(finish.output());
        assertEquals("test:changed", finish.item());
        assertEquals(java.util.Map.of("base", 1, "extra", 1), finish.state().marks());
        assertEquals(java.util.List.of("s", "f"), finish.state().checkpoints());
        assertEquals("s", finish.state().lockedNext());
        assertEquals(1, draws.get());
        assertEquals(java.util.Map.of("base", 1), start.state().marks());
    }

    @Test
    void passingThroughAnotherStartDoesNotSatisfyItsEntryConstraint() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"t","start":true,"recipe":{"type":"create:cutting"}},
                         {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                "edges":[{"from":"s","to":"t"},{"from":"t","to":"f"}],
                "outputs":[{"start":"t","results":[{"item":"test:result"}]}]
                """);
        Completion result = GraphEngine.complete(graph, null, "s", "test:raw", () -> 0);
        result = GraphEngine.complete(graph, result.state(), "t", result.item(), () -> 0);
        result = GraphEngine.complete(graph, result.state(), "f", result.item(), () -> 0);
        assertFalse(result.settled());
        assertEquals("s", result.state().entry());
        assertTrue(GraphEngine.allowed(graph, result.state()).isEmpty());
        Completion direct = GraphEngine.complete(graph, null, "t", "test:raw", () -> 0);
        assertTrue(GraphEngine.complete(graph, direct.state(), "f", direct.item(), () -> 0).settled());
    }

    @Test
    void illegalChoiceCannotGrantMarksConsumeRandomOrMutateTheSnapshot() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                         {"id":"a","recipe":{"type":"create:cutting"}},
                         {"id":"b","recipe":{"type":"create:cutting"}},
                         {"id":"unreachable","rewards":{"wrong":1},"recipe":{"type":"create:pressing"}}],
                "edges":[{"from":"s","to":"a"},{"from":"s","to":"b"}]
                """);
        assertThrows(IllegalArgumentException.class, () -> GraphEngine.complete(graph, null, "a", "test:raw", () -> fail("Illegal operation cannot draw")));
        Progress state = GraphEngine.complete(graph, null, "s", "test:raw", () -> 0).state();
        assertEquals(java.util.List.of("a", "b"), GraphEngine.allowed(graph, state));
        String before = state.toJson().toString();
        assertThrows(IllegalArgumentException.class, () -> GraphEngine.complete(graph, state, "unreachable", "test:work", () -> fail("Illegal operation cannot draw")));
        assertThrows(IllegalArgumentException.class, () -> GraphEngine.complete(graph, state, "missing", "test:work", () -> fail("Illegal operation cannot draw")));
        assertEquals(before, state.toJson().toString());
    }

    @Test
    void zeroRewardCapNeverGrantsButStillAdvancesAndRecordsVisits() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"s","start":true,"rewards":{"work":1},"reward_limit":0,
                          "transitional_item":"test:node_override","recipe":{"type":"create:pressing"}}],
                "edges":[{"from":"s","to":"s"}]
                """);
        Completion first = GraphEngine.complete(graph, null, "s", "test:raw", () -> 0);
        Completion second = GraphEngine.complete(graph, first.state(), "s", first.item(), () -> 0);
        assertEquals("test:node_override", second.item());
        assertTrue(second.state().marks().isEmpty());
        assertTrue(second.state().rewardCounts().isEmpty());
        assertEquals(java.util.List.of("s", "s"), second.state().checkpoints());
    }

    @Test
    void reloadReasonsIdentifyRemovedCurrentEntryAndLockedTargets() {
        GraphDefinition graph = graph("""
                "nodes":[{"id":"s","start":true,"branch_mode":"random","recipe":{"type":"create:pressing"}},
                         {"id":"f","finish":true,"recipe":{"type":"create:cutting"}}],
                "edges":[{"from":"s","to":"f"}]
                """);
        Progress missingCurrent = new Progress(graph.id(), "s", "deleted", java.util.Map.of(), java.util.List.of("s"), java.util.Map.of(), null, java.util.List.of());
        assertTrue(GraphEngine.incompatibility(graph, missingCurrent).contains("Current node is missing: deleted"));
        Progress missingEntry = new Progress(graph.id(), "deleted", "s", java.util.Map.of(), java.util.List.of("deleted"), java.util.Map.of(), "f", java.util.List.of());
        assertTrue(GraphEngine.incompatibility(graph, missingEntry).contains("Entry node is missing: deleted"));
        Progress missingLock = new Progress(graph.id(), "s", "s", java.util.Map.of(), java.util.List.of("s"), java.util.Map.of(), "deleted", java.util.List.of());
        assertTrue(GraphEngine.incompatibility(graph, missingLock).contains("Locked target is missing: deleted"));
    }
}
