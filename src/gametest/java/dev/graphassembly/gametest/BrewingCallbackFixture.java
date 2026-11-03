package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.brewing.RegisterBrewingRecipesEvent;
import net.neoforged.neoforge.common.brewing.IBrewingRecipe;

/** Real registered native callbacks, inert outside a scoped test. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public final class BrewingCallbackFixture {
    public static Runnable ingredient=()->{}, output=()->{};
    @SubscribeEvent public static void register(RegisterBrewingRecipesEvent event){
        event.getBuilder().addRecipe(new IBrewingRecipe(){
            public boolean isInput(ItemStack input){return input.is(net.minecraft.world.item.Items.POTION);}
            public boolean isIngredient(ItemStack stack){ingredient.run();return false;}
            public ItemStack getOutput(ItemStack input,ItemStack stack){output.run();return ItemStack.EMPTY;}
        });
    }
}
