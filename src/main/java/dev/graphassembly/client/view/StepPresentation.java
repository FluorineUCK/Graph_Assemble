package dev.graphassembly.client.view;

import java.util.Map;
import java.util.Set;
import java.util.Optional;

/** Pure presentation capabilities, intentionally independent of the device execution allow-list. */
public final class StepPresentation {
    private static final Set<String> LEGACY_THUMBNAILS = Set.of(
        "create:pressing", "create:cutting", "create:deploying", "create:filling", "create:milling");
    public enum PreviewRoute { LEGACY_THUMBNAIL, NATIVE_CATEGORY }
    public record Machine(String item, String descriptionKey) {}
    private static final Map<String, Machine> NON_ASSEMBLY = Map.of(
        "create:milling", new Machine("create:millstone", "graph_assembly.operation.milling"),
        "create:mixing", new Machine("create:mechanical_mixer", "graph_assembly.operation.mixing"),
        "create:compacting", new Machine("create:mechanical_press", "graph_assembly.operation.compacting"));
    private StepPresentation() {}
    public static PreviewRoute previewRoute(String serializer, boolean completeRecipe) {
        return !completeRecipe && LEGACY_THUMBNAILS.contains(serializer)
            ? PreviewRoute.LEGACY_THUMBNAIL : PreviewRoute.NATIVE_CATEGORY;
    }
    public static Optional<Machine> nonAssemblyMachine(String serializer) { return Optional.ofNullable(NON_ASSEMBLY.get(serializer)); }
}
