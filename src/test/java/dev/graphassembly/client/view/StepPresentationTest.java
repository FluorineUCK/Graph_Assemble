package dev.graphassembly.client.view;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class StepPresentationTest {
    @Test void fiveExistingInlineStepsKeepThumbnailsButCompleteReferencesAlwaysUseNativeCategory() {
        for (var serializer : List.of("create:pressing", "create:cutting", "create:deploying", "create:filling", "create:milling")) {
            assertEquals(StepPresentation.PreviewRoute.LEGACY_THUMBNAIL, StepPresentation.previewRoute(serializer, false), serializer);
            assertEquals(StepPresentation.PreviewRoute.NATIVE_CATEGORY, StepPresentation.previewRoute(serializer, true), serializer);
        }
    }
    @Test void basinAndForeignDevicesUseFullNativeCategoriesRegardlessOfExecutionSupport() {
        for (var serializer : List.of("create:mixing", "create:compacting", "createcraftedbeginning:forging_press",
                "create_fantasizing:exposing", "create_fantasizing:shadow_plating", "unknown:machine", "")) {
            assertEquals(StepPresentation.PreviewRoute.NATIVE_CATEGORY, StepPresentation.previewRoute(serializer, false), serializer);
            assertEquals(StepPresentation.PreviewRoute.NATIVE_CATEGORY, StepPresentation.previewRoute(serializer, true), serializer);
        }
    }
    @Test void nonAssemblyDescriptionsChooseTheActualMachineWithoutOptionalAddonClasses() {
        assertEquals(new StepPresentation.Machine("create:mechanical_mixer", "graph_assembly.operation.mixing"),
            StepPresentation.nonAssemblyMachine("create:mixing").orElseThrow());
        assertEquals(new StepPresentation.Machine("create:mechanical_press", "graph_assembly.operation.compacting"),
            StepPresentation.nonAssemblyMachine("create:compacting").orElseThrow());
        assertEquals(new StepPresentation.Machine("create:millstone", "graph_assembly.operation.milling"),
            StepPresentation.nonAssemblyMachine("create:milling").orElseThrow());
        assertTrue(StepPresentation.nonAssemblyMachine("createcraftedbeginning:forging_press").isEmpty(),
            "Withdrawn adapters must use the generic native preview, not a dedicated machine mapping");
        assertTrue(StepPresentation.nonAssemblyMachine("unknown:machine").isEmpty(), "Unknown types must not be called milling");
    }
}
