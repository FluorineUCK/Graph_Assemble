package dev.graphassembly.integration;

import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import java.util.*;

/** Capability observations, never an automatic execution-adapter registration mechanism. */
public final class AssemblyDiscovery {
    private AssemblyDiscovery() {}

    public record Capability(String serializer, String recipeType, String recipeClass, boolean registered,
                             boolean assemblyRecipe, boolean supportsAssembly, boolean deviceAdapter, String error) {
        public String diagnostic() {
            return serializer + " [registered=" + registered + ", recipeType=" + recipeType + ", class=" + recipeClass
                + ", IAssemblyRecipe=" + assemblyRecipe + ", supportsAssembly=" + supportsAssembly + "] "
                + (deviceAdapter ? "verified device adapter" : "no verified device adapter (consumption, progress hand-off and output capacity unverified)")
                + (error == null ? "" : "; capability check failed: " + error);
        }
    }
    public record Observation(Capability capability, String source) {}

    /** Inspect the registered-codec result; do not call client-only descriptions or JEI factories. */
    public static Capability inspect(ResourceLocation serializer, Recipe<?> recipe) {
        boolean assembly = recipe instanceof IAssemblyRecipe;
        boolean supports = false;
        String error = null;
        if (assembly) {
            try { supports = ((IAssemblyRecipe) recipe).supportsAssembly(); }
            catch (RuntimeException e) { error = e.getClass().getSimpleName() + ": " + e.getMessage(); }
        }
        boolean registered = serializer != null && BuiltInRegistries.RECIPE_SERIALIZER.containsKey(serializer);
        return new Capability(serializer == null ? "<unregistered>" : serializer.toString(), String.valueOf(BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType())),
            recipe.getClass().getName(), registered, assembly, supports,
            registered && NativeSteps.supportsExecution(serializer,recipe), error);
    }

    public static List<Observation> scan(Iterable<RecipeHolder<?>> recipes) {
        List<RecipeHolder<?>> ordered = new ArrayList<>();
        recipes.forEach(ordered::add);
        ordered.sort(Comparator.comparing(holder -> holder.id().toString()));
        Map<Capability, Observation> observations = new LinkedHashMap<>();
        for (var holder : ordered) {
            observe(observations, holder.value(), holder.id().toString());
            if (holder.value() instanceof SequencedAssemblyRecipe sequence) {
                var steps = sequence.getSequence();
                for (int i = 0; i < steps.size(); i++)
                    observe(observations, steps.get(i).getRecipe(), holder.id() + " sequence[" + i + "]");
            }
        }
        return List.copyOf(observations.values());
    }

    private static void observe(Map<Capability, Observation> observations, Recipe<?> recipe, String source) {
        var serializer = BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer());
        if (!(recipe instanceof IAssemblyRecipe) && !(recipe instanceof com.simibubi.create.content.processing.recipe.ProcessingRecipe<?, ?>) && !NativeSteps.supportsExecution(serializer,recipe)) return;
        var capability = inspect(BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()), recipe);
        observations.putIfAbsent(capability, new Observation(capability, source));
    }
}
