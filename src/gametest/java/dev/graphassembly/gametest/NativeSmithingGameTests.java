package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

/** Actual native three-slot acceptance, player clicks and output pickup, graph recipe only. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeSmithingGameTests {
    @GameTest(template="test_empty",timeoutTicks=100,batch="native_smithing")
    public static void graphOnlySmithingSlotsAcceptClicksAndDeliverBaseOwnedCarrier(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"smithing-native"));
        SmithingMenu menu=null;
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"minecraft:smithing_transform",
                   "template":{"item":"minecraft:netherite_upgrade_smithing_template"},"base":{"item":"minecraft:diamond_sword"},
                   "addition":{"item":"minecraft:copper_ingot"},"result":{"id":"minecraft:netherite_sword"}}},
                   {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:smithing_tracer"),graph)));GraphIndex.invalidate();
            level.setBlockAndUpdate(pos,Blocks.SMITHING_TABLE.defaultBlockState());player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);
            player.getInventory().clearContent();menu=new SmithingMenu(1,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
            var template=new ItemStack(Items.NETHERITE_UPGRADE_SMITHING_TEMPLATE,2);var base=new ItemStack(Items.AMETHYST_SHARD,2);var addition=new ItemStack(Items.COPPER_INGOT,2);
            check(menu.getSlot(0).mayPlace(template)&&menu.getSlot(1).mayPlace(base)&&menu.getSlot(2).mayPlace(addition),
                "Graph-only native smithing slots must admit template/base/addition without ordinary smithing recipe cache");
            var inputs=List.of(template,base,addition);
            for(int physical=0;physical<3;physical++) {
                menu.setCarried(inputs.get(physical));menu.clicked(physical,1,ClickType.PICKUP,player);
                check(menu.getSlot(physical).getItem().getCount()==1&&menu.getCarried().getCount()==1,"Native right-click deposits exactly one input into physical slot "+physical);
            }
            menu.setCarried(ItemStack.EMPTY);
            check(menu.getSlot(3).hasItem()&&menu.getSlot(0).getItem().getCount()==1&&menu.getSlot(1).getItem().getCount()==1&&menu.getSlot(2).getItem().getCount()==1,"Smithing preview must appear without consuming any physical input");
            menu.clicked(3,0,ClickType.PICKUP,player);var result=menu.getCarried();var progress=Workpiece.read(level,result);
            check(result.is(Items.IRON_NUGGET)&&result.getCount()==1&&progress!=null&&progress.graphId().equals("test:smithing_tracer")&&progress.current().equals("s"),
                "Native smithing pickup must advance logical base input0 once, not grant native netherite-sword template result");
            check(menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(2).getItem().isEmpty(),"One smithing graph round consumes each of its three physical inputs exactly once");
            helper.succeed();
        } finally {
            if(menu!=null){for(int slot=0;slot<4;slot++)menu.getSlot(slot).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);}
            player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;level.removeBlock(pos,false);manager.replaceRecipes(saved);GraphIndex.invalidate();
        }
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
