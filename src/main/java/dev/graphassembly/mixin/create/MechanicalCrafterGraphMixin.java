package dev.graphassembly.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.kinetics.crafter.MechanicalCrafterBlockEntity;
import com.simibubi.create.content.kinetics.crafter.RecipeGridHandler;
import dev.graphassembly.integration.MechanicalCrafterTransaction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Collection;
import java.util.function.Consumer;

/** Replace only graph assembly settlement; native chain motion, timers and export remain intact. */
@Mixin(MechanicalCrafterBlockEntity.class)
public abstract class MechanicalCrafterGraphMixin implements MechanicalCrafterTransaction.View {
    @Shadow protected RecipeGridHandler.GroupedItems groupedItems;
    @Shadow protected int countDown;
    public RecipeGridHandler.GroupedItems graphAssembly$groupedItems(){return groupedItems;}
    public int graphAssembly$countDown(){return countDown;}
    @WrapMethod(method="tick")
    private void graphAssembly$tick(Operation<Void> original) {
        try(var scope=MechanicalCrafterTransaction.open((MechanicalCrafterBlockEntity)(Object)this)){if(scope!=null)original.call();}
    }
    @Inject(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler;tryToApplyRecipe(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler$GroupedItems;)Lnet/minecraft/world/item/ItemStack;"),cancellable=true)
    private void graphAssembly$prepare(CallbackInfo ci) {
        if(!MechanicalCrafterTransaction.current().prepare())ci.cancel();
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler;tryToApplyRecipe(Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler$GroupedItems;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack graphAssembly$recipe(Level level,RecipeGridHandler.GroupedItems items,Operation<ItemStack> original) {
        return MechanicalCrafterTransaction.substituting()?MechanicalCrafterTransaction.representative():original.call(level,items);
    }
    @WrapOperation(method="tick",at=@At(value="INVOKE",target="Ljava/util/Collection;forEach(Ljava/util/function/Consumer;)V"))
    private void graphAssembly$preparedContainers(Collection<ItemStack> items,Consumer<ItemStack> callback,Operation<Void> original) {
        if(!MechanicalCrafterTransaction.substituting())original.call(items,callback);
    }
    @WrapOperation(method="tick",at=@At(value="FIELD",target="Lcom/simibubi/create/content/kinetics/crafter/MechanicalCrafterBlockEntity;groupedItems:Lcom/simibubi/create/content/kinetics/crafter/RecipeGridHandler$GroupedItems;",opcode=Opcodes.PUTFIELD))
    private void graphAssembly$bundle(MechanicalCrafterBlockEntity machine,RecipeGridHandler.GroupedItems items,Operation<Void> original) {
        original.call(machine,MechanicalCrafterTransaction.commit(items));
    }
}
