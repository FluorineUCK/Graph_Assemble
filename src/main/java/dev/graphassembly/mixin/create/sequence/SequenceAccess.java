package dev.graphassembly.mixin.create.sequence;

import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Call upstream progression rather than duplicating its loops, weight drawing or components. */
@Mixin(value=SequencedAssemblyRecipe.class,remap=false)
public interface SequenceAccess {
    @Invoker("advance") ItemStack graphAssembly$advance(ResourceLocation id,ItemStack input,RandomSource random);
    @Invoker("appliesTo") boolean graphAssembly$appliesTo(ResourceLocation id,ItemStack input);
}
