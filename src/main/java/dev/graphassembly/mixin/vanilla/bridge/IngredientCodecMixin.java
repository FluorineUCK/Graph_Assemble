package dev.graphassembly.mixin.vanilla.bridge;

import com.mojang.serialization.*;
import dev.graphassembly.integration.bridge.IngredientDecoration;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Only materials observed while encoding selected native definitions receive the transient guard. */
@Mixin(Ingredient.class)
public abstract class IngredientCodecMixin implements IngredientDecoration.OrdinaryDefinition {
    @Unique private transient boolean graphAssembly$ordinaryDefinition;
    public boolean graphAssembly$ordinaryDefinition() { return graphAssembly$ordinaryDefinition; }
    public void graphAssembly$ordinaryDefinition(boolean ordinary) { graphAssembly$ordinaryDefinition = ordinary; }
    @Shadow @Final @Mutable public static Codec<Ingredient> CODEC;
    @Shadow @Final @Mutable public static Codec<Ingredient> CODEC_NONEMPTY;
    @Shadow @Final @Mutable public static MapCodec<Ingredient> MAP_CODEC_NONEMPTY;
    @Shadow @Final @Mutable public static Codec<List<Ingredient>> LIST_CODEC;
    @Shadow @Final @Mutable public static Codec<List<Ingredient>> LIST_CODEC_NONEMPTY;
    @Shadow @Final @Mutable public static StreamCodec<RegistryFriendlyByteBuf, Ingredient> CONTENTS_STREAM_CODEC;
    @Inject(method="<clinit>", at=@At("RETURN")) private static void graphAssembly$nativeCodecs(CallbackInfo ci) {
        CODEC = CODEC.xmap(v -> v, IngredientDecoration::encoded);
        CODEC_NONEMPTY = CODEC_NONEMPTY.xmap(v -> v, IngredientDecoration::encoded);
        MAP_CODEC_NONEMPTY = MAP_CODEC_NONEMPTY.xmap(v -> v, IngredientDecoration::encoded);
        LIST_CODEC = LIST_CODEC.xmap(v -> v, IngredientDecoration::encodedList);
        LIST_CODEC_NONEMPTY = LIST_CODEC_NONEMPTY.xmap(v -> v, IngredientDecoration::encodedList);
        CONTENTS_STREAM_CODEC = IngredientDecoration.wireDefinitionCodec(CONTENTS_STREAM_CODEC);
    }
    @Inject(method="test(Lnet/minecraft/world/item/ItemStack;)Z", at=@At("HEAD"), cancellable=true)
    private void graphAssembly$protectOrdinary(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (graphAssembly$ordinaryDefinition && stack != null && Workpiece.has(stack)) cir.setReturnValue(false);
    }
    @Inject(method="isSimple", at=@At("HEAD"), cancellable=true)
    private void graphAssembly$componentSensitive(CallbackInfoReturnable<Boolean> cir) {
        if (graphAssembly$ordinaryDefinition && !IngredientDecoration.encodingWireDefinition()) cir.setReturnValue(false);
    }
}
