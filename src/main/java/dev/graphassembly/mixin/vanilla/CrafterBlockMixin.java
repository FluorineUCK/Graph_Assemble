package dev.graphassembly.mixin.vanilla;

import dev.graphassembly.integration.OrdinaryRecipeQueryGuard;
import dev.graphassembly.integration.vanilla.crafter.CrafterTransaction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CrafterBlock;
import net.minecraft.world.level.block.entity.CrafterBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.Optional;

@Mixin(CrafterBlock.class)
public abstract class CrafterBlockMixin {
    @Invoker("dispenseItem")
    public abstract void graphAssembly$dispenseItem(ServerLevel level,BlockPos pos,CrafterBlockEntity machine,ItemStack stack,BlockState state,RecipeHolder<CraftingRecipe> recipe);
    @Inject(method="dispenseFrom",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$pulse(BlockState state,ServerLevel level,BlockPos pos,CallbackInfo ci) {
        if(CrafterTransaction.pulse((CrafterBlock)(Object)this,state,level,pos,this::graphAssembly$dispenseItem))ci.cancel();
    }
    @Inject(method="getPotentialResults",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$guardCache(Level level,CraftingInput input,CallbackInfoReturnable<Optional<RecipeHolder<CraftingRecipe>>> cir) {
        if(OrdinaryRecipeQueryGuard.hasProgress(input))cir.setReturnValue(Optional.empty());
    }
}
