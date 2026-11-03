package dev.graphassembly.mixin.create;

import com.simibubi.create.content.processing.basin.BasinBlockEntity;
import com.simibubi.create.content.processing.basin.BasinOperatingBlockEntity;
import dev.graphassembly.integration.BasinGraphRuntime;
import net.minecraft.world.item.crafting.Recipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(BasinOperatingBlockEntity.class)
public abstract class BasinOperatingMixin {
    @Shadow protected abstract Optional<BasinBlockEntity> getBasin();
    @Inject(method="getMatchingRecipes",at=@At("HEAD"),cancellable=true)
    private void graphAssembly$recipes(CallbackInfoReturnable<List<Recipe<?>>> cir) {
        var basin=getBasin(); if(basin.isEmpty()) return;
        var lookup=BasinGraphRuntime.lookup((BasinOperatingBlockEntity)(Object)this,basin.get());
        if(lookup.handled()) cir.setReturnValue(new ArrayList<>(lookup.recipes()));
    }
}
