package dev.graphassembly.mixin.create;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TransportedResult;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessingType;
import dev.graphassembly.integration.FanGraphRuntime;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.List;

/** Preserve native exposure/countdown/delivery while substituting graph eligibility/results only. */
@Mixin(FanProcessing.class)
public abstract class FanProcessingMixin {
    @WrapMethod(method="canProcess")
    private static boolean graphAssembly$canProcess(ItemEntity entity,FanProcessingType type,Operation<Boolean> original) {
        var level=entity.level();
        try(var scope=FanGraphRuntime.open(entity,level,entity::getItem,()->entity.level()==level&&!entity.isRemoved(),type)) {
            return scope!=null&&scope.allowed()&&(scope.restarting()?scope.freshEligibility():original.call(entity,type));
        }
    }
    @WrapMethod(method="applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z")
    private static boolean graphAssembly$entity(ItemEntity entity,FanProcessingType type,Operation<Boolean> original) {
        var level=entity.level();
        try(var scope=FanGraphRuntime.open(entity,level,entity::getItem,()->entity.level()==level&&!entity.isRemoved(),type)) {
            if(scope==null||!scope.allowed())return false;
            scope.beginEntity(entity);boolean result=original.call(entity,type);scope.finishEntity(entity);return result;
        }
    }
    @WrapMethod(method="applyProcessing(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Lcom/simibubi/create/content/kinetics/belt/behaviour/TransportedItemStackHandlerBehaviour$TransportedResult;")
    private static TransportedResult graphAssembly$belt(TransportedItemStack transported,Level level,FanProcessingType type,Operation<TransportedResult> original) {
        try(var scope=FanGraphRuntime.open(transported,level,()->transported.stack,()->true,type)) {
            if(scope==null||!scope.allowed())return TransportedResult.doNothing();
            scope.beginTransported(transported);var result=original.call(transported,level,type);scope.finishTransported(transported,result);return result;
        }
    }
    @WrapOperation(method={"canProcess","applyProcessing(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Lcom/simibubi/create/content/kinetics/belt/behaviour/TransportedItemStackHandlerBehaviour$TransportedResult;"},
        at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;canProcess(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Z"))
    private static boolean graphAssembly$eligible(FanProcessingType type,ItemStack stack,Level level,Operation<Boolean> original) {
        var graph=FanGraphRuntime.eligible();return graph==null?original.call(type,stack,level):graph;
    }
    @WrapOperation(method={"applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z",
        "applyProcessing(Lcom/simibubi/create/content/kinetics/belt/transport/TransportedItemStack;Lnet/minecraft/world/level/Level;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Lcom/simibubi/create/content/kinetics/belt/behaviour/TransportedItemStackHandlerBehaviour$TransportedResult;"},
        at=@At(value="INVOKE",target="Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;process(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;)Ljava/util/List;"))
    private static List<ItemStack> graphAssembly$process(FanProcessingType type,ItemStack stack,Level level,Operation<List<ItemStack>> original) {
        return FanGraphRuntime.handled()?FanGraphRuntime.process():original.call(type,stack,level);
    }
    @WrapOperation(method="applyProcessing(Lnet/minecraft/world/entity/item/ItemEntity;Lcom/simibubi/create/content/kinetics/fan/processing/FanProcessingType;)Z",
        at=@At(value="INVOKE",target="Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean graphAssembly$inheritExposure(Level level,net.minecraft.world.entity.Entity entity,Operation<Boolean> original) {
        if(entity instanceof ItemEntity item)FanGraphRuntime.inheritExposure(item);
        return original.call(level,entity);
    }
}
