package dev.graphassembly.gametest;

import com.mojang.serialization.MapCodec;
import dev.graphassembly.GraphAssembly;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.crafting.*;
import net.neoforged.neoforge.registries.*;
import java.util.stream.Stream;

/** Real native ingredient predicate with a codec-preserved test callback; GameTest-only. */
@EventBusSubscriber(modid=GraphAssembly.ID)
public record SmithingIngredientFixture() implements ICustomIngredient {
    public static Runnable query=()->{};
    public static final IngredientType<SmithingIngredientFixture> TYPE=new IngredientType<>(MapCodec.unit(new SmithingIngredientFixture()));
    public boolean test(ItemStack stack){query.run();return stack.is(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE);}
    public Stream<ItemStack> getItems(){return Stream.of(new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE));}
    public boolean isSimple(){return false;}
    public IngredientType<?> getType(){return TYPE;}
    @SubscribeEvent public static void register(RegisterEvent event){event.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES,GraphAssembly.id("smithing_ingredient_fixture"),()->TYPE);}
}
