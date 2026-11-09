package dev.graphassembly.integration.bridge;

import com.mojang.serialization.JsonOps;
import net.minecraft.world.item.crafting.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.level.Level;
import java.util.*;

/** Observe original materials through their public codecs without cloning or changing their encoding. */
public final class IngredientDecoration {
    public interface OrdinaryDefinition {
        boolean graphAssembly$ordinaryDefinition();
        void graphAssembly$ordinaryDefinition(boolean ordinary);
    }
    private static final ThreadLocal<Set<Ingredient>> ENCODING = new ThreadLocal<>();
    private static final ThreadLocal<Integer> WIRE_DEFINITION_DEPTH = new ThreadLocal<>();
    private IngredientDecoration() {}
    public static boolean encodingWireDefinition() { return WIRE_DEFINITION_DEPTH.get() != null; }

    /** Keep the native/custom wire discriminator independent of the transient runtime matching guard. */
    public static StreamCodec<RegistryFriendlyByteBuf, Ingredient> wireDefinitionCodec(
            StreamCodec<RegistryFriendlyByteBuf, Ingredient> delegate) {
        return new StreamCodec<>() {
            @Override public void encode(RegistryFriendlyByteBuf buffer, Ingredient value) {
                Integer parent = WIRE_DEFINITION_DEPTH.get();
                WIRE_DEFINITION_DEPTH.set(parent == null ? 1 : parent + 1);
                try { delegate.encode(buffer, value); }
                finally {
                    if (parent == null) WIRE_DEFINITION_DEPTH.remove();
                    else WIRE_DEFINITION_DEPTH.set(parent);
                }
            }
            @Override public Ingredient decode(RegistryFriendlyByteBuf buffer) { return delegate.decode(buffer); }
        };
    }
    public static Ingredient encoded(Ingredient value) {
        var materials = ENCODING.get();
        if (materials != null && !value.isEmpty()) materials.add(value);
        return value;
    }
    public static List<Ingredient> encodedList(List<Ingredient> values) {
        values.forEach(IngredientDecoration::encoded);
        return values;
    }
    public static void markOrdinary(Level level, Recipe<?> source) {
        var parent = ENCODING.get();
        Set<Ingredient> materials = Collections.newSetFromMap(new IdentityHashMap<>());
        ENCODING.set(materials);
        try {
            var ops = level.registryAccess().createSerializationContext(JsonOps.INSTANCE);
            Recipe.CODEC.encodeStart(ops, source).getOrThrow();
            // A failed encode never leaves a partially marked definition.
            for (var material : materials)
                ((OrdinaryDefinition)(Object)material).graphAssembly$ordinaryDefinition(true);
        } finally {
            if (parent == null) ENCODING.remove(); else ENCODING.set(parent);
        }
    }
}
