package dev.graphassembly.mixin.create.projection;

import com.simibubi.create.foundation.recipe.RecipeFinder;
import dev.graphassembly.integration.projection.NativeRecipeCatalog;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;
import java.util.function.Predicate;

@Mixin(value=RecipeFinder.class,remap=false)
public abstract class RecipeFinderViewMixin {
    @Inject(method="get",at=@At("HEAD"),cancellable=true)
    private static void graphAssembly$views(Object key, Level level, Predicate<RecipeHolder<?>> filter, CallbackInfoReturnable<List<RecipeHolder<?>>> cir) {
        var result=NativeRecipeCatalog.find(key,level,filter);
        if(result!=null)cir.setReturnValue(result);
    }
}
