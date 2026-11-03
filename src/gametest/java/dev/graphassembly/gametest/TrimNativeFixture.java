package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.*;
import net.neoforged.neoforge.registries.*;
import java.util.Arrays;
import java.util.function.*;
import java.util.stream.Stream;

/** GameTest-only native ingredient and item extension callbacks; no production hooks. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public final class TrimNativeFixture extends Item {
    public static Item ITEM;
    public static Consumer<ItemStack> capacity=stack->{};
    public static ToIntFunction<ItemStack> limit=stack->64;
    public static Consumer<ItemStack> predicate=stack->{};
    private TrimNativeFixture(){super(new Item.Properties());}
    @Override public int getMaxStackSize(ItemStack stack){capacity.accept(stack);return limit.applyAsInt(stack);}
    public record Material(Ingredient ingredient) implements ICustomIngredient {
        public boolean test(ItemStack stack){predicate.accept(stack);return ingredient.test(stack);}
        public Stream<ItemStack> getItems(){return Arrays.stream(ingredient.getItems());}
        public boolean isSimple(){return false;}
        public IngredientType<?> getType(){return TYPE;}
    }
    public static final IngredientType<Material> TYPE=new IngredientType<>(Ingredient.CODEC.fieldOf("ingredient").xmap(Material::new,Material::ingredient));
    public static void reset(){capacity=stack->{};limit=stack->64;predicate=stack->{};}
    @SubscribeEvent public static void register(RegisterEvent event) {
        event.register(Registries.ITEM,GraphAssembly.id("trim_native_fixture"),()->{ITEM=new TrimNativeFixture();return ITEM;});
        event.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES,GraphAssembly.id("trim_native_fixture"),()->TYPE);
    }
}
