package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import java.util.UUID;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class SandpaperPaymentGameTests {
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}

    private static void removedRecipeRefundsReservation(GameTestHelper h,boolean sequence) {
        var level=h.getLevel();
        var saved=List.copyOf(level.getRecipeManager().getRecipes());
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"sandpaper-refund"));
        try {
            String json=sequence ? """
                {"type":"create:sequenced_assembly","ingredient":{"item":"minecraft:water_bucket"},
                 "transitional_item":{"id":"minecraft:water_bucket"},"loops":2,
                 "sequence":[{"type":"graph_assembly:sequence_step","step":{"recipe":{"type":"create:sandpaper_polishing"}}}],
                 "results":[{"id":"minecraft:diamond"}]}
                """ : """
                {"type":"graph_assembly:assembly","ingredient":{"item":"minecraft:water_bucket"},"transitional_item":"minecraft:water_bucket",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"create:sandpaper_polishing"}},
                          {"id":"f","finish":true,"recipe":{"type":"create:sandpaper_polishing"}}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """;
            var recipe=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(json)).getOrThrow();
            level.getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:sandpaper_payment"),recipe)));
            GraphIndex.invalidate();
            var tracked=SandPaperPolishingRecipe.applyPolish(level,Vec3.ZERO,new ItemStack(Items.WATER_BUCKET),AllItems.SAND_PAPER.asStack());
            check(tracked.is(Items.WATER_BUCKET)&&(sequence?tracked.has(AllDataComponents.SEQUENCED_ASSEMBLY):Workpiece.has(tracked)),"Fixture must reach a real tracked bucket sanding step");
            tracked.set(DataComponents.CUSTOM_NAME,Component.literal("reserved bucket components"));
            var expected=tracked.copy();
            var paper=AllItems.SAND_PAPER.asStack();
            paper.setDamageValue(2);
            player.setItemInHand(InteractionHand.MAIN_HAND,paper);
            player.setItemInHand(InteractionHand.OFF_HAND,tracked);
            paper.getItem().use(level,player,InteractionHand.MAIN_HAND);
            check(player.getOffhandItem().isEmpty()&&paper.has(AllDataComponents.SAND_PAPER_POLISHING),"Actual use must reserve exactly one tracked bucket");
            level.getRecipeManager().replaceRecipes(List.of());
            GraphIndex.invalidate();
            level.random.setSeed(8421);
            paper.finishUsingItem(level,player);
            check(player.getInventory().countItem(Items.BUCKET)==0,"Blocked sanding must not create an empty crafting-remainder bucket");
            check(paper.getDamageValue()==2,"Blocked sanding must not charge durability");
            check(!paper.has(AllDataComponents.SAND_PAPER_POLISHING),"Refund must clear the reservation");
            int matching=0;
            for(int slot=0;slot<player.getInventory().getContainerSize();slot++) {
                var item=player.getInventory().getItem(slot);
                if(ItemStack.matches(item,expected))matching++;
            }
            check(matching==1&&player.getInventory().countItem(Items.WATER_BUCKET)==1,"Refund must return the exact input and progress/components once");
            check(level.random.nextLong()==RandomSource.create(8421).nextLong(),"Blocked sanding must not roll RNG");
            paper.finishUsingItem(level,player);
            check(player.getInventory().countItem(Items.WATER_BUCKET)==1&&player.getInventory().countItem(Items.BUCKET)==0&&paper.getDamageValue()==2,"Repeated completion must not refund or charge twice");
        } finally {
            player.getInventory().clearContent();
            level.getRecipeManager().replaceRecipes(saved);
            GraphIndex.invalidate();
        }
        h.succeed();
    }

    @GameTest(template="test_empty",batch="sandpaper_payment")
    public static void removedGraphRefundsReservedBucketWithoutPayment(GameTestHelper h){removedRecipeRefundsReservation(h,false);}

    @GameTest(template="test_empty",batch="sandpaper_payment")
    public static void removedNativeSequenceRefundsReservedBucketWithoutPayment(GameTestHelper h){removedRecipeRefundsReservation(h,true);}

    @GameTest(template="test_empty",batch="sandpaper_payment")
    public static void ordinarySandpaperStillPaysNativeRemainderAndDurability(GameTestHelper h) {
        var level=h.getLevel();
        var saved=List.copyOf(level.getRecipeManager().getRecipes());
        var player=new FakePlayer(level,new GameProfile(UUID.randomUUID(),"sandpaper-ordinary"));
        try {
            var json=JsonParser.parseString("""
                {"type":"create:sandpaper_polishing","ingredients":[{"item":"minecraft:water_bucket"}],
                 "results":[{"id":"minecraft:diamond"}]}
                """);
            var recipe=Recipe.CODEC.parse(level.registryAccess().createSerializationContext(JsonOps.INSTANCE),json).getOrThrow();
            level.getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:ordinary_sanding"),recipe)));
            GraphIndex.invalidate();
            var paper=AllItems.SAND_PAPER.asStack();
            player.setItemInHand(InteractionHand.MAIN_HAND,paper);
            player.setItemInHand(InteractionHand.OFF_HAND,new ItemStack(Items.WATER_BUCKET));
            paper.getItem().use(level,player,InteractionHand.MAIN_HAND);
            check(player.getOffhandItem().isEmpty()&&paper.has(AllDataComponents.SAND_PAPER_POLISHING),"Ordinary use reserves input through Create");
            paper.finishUsingItem(level,player);
            check(player.getInventory().countItem(Items.DIAMOND)==1&&player.getInventory().countItem(Items.BUCKET)==1
                &&player.getInventory().countItem(Items.WATER_BUCKET)==0,"Ordinary success preserves Create output and remainder payment");
            check(paper.getDamageValue()==1&&!paper.has(AllDataComponents.SAND_PAPER_POLISHING),"Ordinary success preserves native durability and clears reservation");
        } finally {
            player.getInventory().clearContent();
            level.getRecipeManager().replaceRecipes(saved);
            GraphIndex.invalidate();
        }
        h.succeed();
    }
}
