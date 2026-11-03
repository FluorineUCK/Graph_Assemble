package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import net.minecraft.gametest.framework.GameTestGenerator;
import net.minecraft.gametest.framework.TestFunction;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
public final class JeiGameTests {
    @GameTestGenerator
    public static List<TestFunction> optionalJeiTests() {
        if (!ModList.get().isLoaded("jei")) return List.of();
        return List.of(new TestFunction("graph_assembly_jei", "graph_assembly.jei_fluid_inputs", "graph_assembly:test_empty",
            100, 0, true, JeiFluidAssertions::verify),
            new TestFunction("graph_assembly_jei", "graph_assembly.native_preview_data", "graph_assembly:test_empty",
                100, 0, true, NativePreviewAssertions::verify));
    }
}
