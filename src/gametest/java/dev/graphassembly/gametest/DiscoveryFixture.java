package dev.graphassembly.gametest;

import com.simibubi.create.content.kinetics.press.PressingRecipe;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import dev.graphassembly.GraphAssembly;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.registries.RegisterEvent;

/** Synthetic non-Create serializer; this entire package is excluded from the release jar. */
@EventBusSubscriber(modid = GraphAssembly.ID)
public class DiscoveryFixture extends PressingRecipe {
    public static final RecipeSerializer<DiscoveryFixture> SERIALIZER = new StandardProcessingRecipe.Serializer<>(DiscoveryFixture::new);
    public DiscoveryFixture(ProcessingRecipeParams params) { super(params); }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(Registries.RECIPE_SERIALIZER, GraphAssembly.id("discovery_fixture"), () -> SERIALIZER);
    }
}
