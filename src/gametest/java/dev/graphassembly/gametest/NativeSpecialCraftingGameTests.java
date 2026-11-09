package dev.graphassembly.gametest;

import dev.graphassembly.core.GraphPipeline;
import dev.graphassembly.core.progress.TransitionResult;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.ArmorDyeRecipe;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.enchantment.Enchantments;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Special crafting acceptance goes through the actual native result-slot pickup. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeSpecialCraftingGameTests {
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void inventoryMenuDyeUsesNativeCarrierAndColor(GameTestHelper helper) {
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"special-crafting-test"));
        String id="test:inventory_native_dye";
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:leather_chestplate"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"dye","start":true,"recipe":{"type":"minecraft:crafting_special_armordye"}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"dye","to":"finish"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            var base=new ItemStack(Items.LEATHER_CHESTPLATE); base.setDamageValue(37);
            base.set(DataComponents.CUSTOM_NAME,Component.literal("Native dye carrier"));
            base.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION),3);
            var red=new ItemStack(Items.RED_DYE,2); var blue=new ItemStack(Items.BLUE_DYE,2);
            var nativeInput=CraftingInput.of(2,2,List.of(base.copy(),red.copy(),ItemStack.EMPTY,blue.copy()));
            var nativeRecipe=new ArmorDyeRecipe(CraftingBookCategory.MISC);
            var nativeResult=nativeRecipe.assemble(nativeInput,level.registryAccess());
            check(nativeRecipe.matches(nativeInput,level)&&nativeResult.is(Items.LEATHER_CHESTPLATE)
                &&nativeResult.has(DataComponents.DYED_COLOR),"Tracer fixture is a valid original native dye operation");
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(id),graph))); GraphIndex.invalidate();
            InventoryMenu menu=player.inventoryMenu; player.containerMenu=menu;
            player.getInventory().clearContent(); menu.setCarried(ItemStack.EMPTY);
            menu.getSlot(1).set(base.copy()); menu.getSlot(2).set(red.copy()); menu.getSlot(4).set(blue.copy());
            menu.slotsChanged(menu.getSlot(1).container);
            check(!menu.getSlot(0).getItem().isEmpty(),"Native dye graph has a menu preview");
            check(!Workpiece.has(menu.getSlot(1).getItem())&&menu.getSlot(2).getItem().getCount()==2
                &&menu.getSlot(4).getItem().getCount()==2,"Preview neither changes progress nor consumes dyes");
            menu.clicked(0,0,ClickType.PICKUP,player);
            ItemStack result=menu.getCarried();
            check(result.is(Items.LEATHER_CHESTPLATE)&&result.getCount()==1,"Native carrier survives graph default");
            check(Objects.equals(result.get(DataComponents.DYED_COLOR),nativeResult.get(DataComponents.DYED_COLOR)),"Native color is retained");
            check(result.getDamageValue()==37&&Objects.equals(result.get(DataComponents.CUSTOM_NAME),base.get(DataComponents.CUSTOM_NAME))
                &&Objects.equals(result.get(DataComponents.ENCHANTMENTS),base.get(DataComponents.ENCHANTMENTS)),"Native dye preserves name, damage and enchantments");
            var state=Workpiece.read(level,result);
            check(state!=null&&state.graphId().equals(id)&&state.current().equals("dye")&&state.recentHistory().equals(List.of("dye")),"One graph step advanced");
            check(menu.getSlot(1).getItem().isEmpty()&&menu.getSlot(2).getItem().getCount()==1
                &&menu.getSlot(4).getItem().getCount()==1,"Pickup pays exactly one unit per occupied cell");
            helper.succeed();
        } finally {
            player.getInventory().clearContent(); player.inventoryMenu.clearCraftingContent();
            player.inventoryMenu.setCarried(ItemStack.EMPTY); manager.replaceRecipes(saved); GraphIndex.invalidate();
        }
    }
    @GameTest(template="test_empty", timeoutTicks=100)
    public static void inventoryMenuRepairUsesFirstPhysicalWorkpieceAndNativeComponents(GameTestHelper helper) {
        var level=helper.getLevel(); var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"repair-crafting-test"));
        String id="test:inventory_native_repair";
        try {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:diamond_chestplate"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"left","start":true,"transitional_item":"minecraft:diamond_chestplate","mark_amounts":{"quality":3},"recipe":{"type":"create:pressing"}},
                   {"id":"right","start":true,"ingredient":{"item":"minecraft:quartz"},"transitional_item":"minecraft:diamond_chestplate","checkpoint":true,"mark_amounts":{"quality":99},"recipe":{"type":"create:cutting"}},
                   {"id":"repair","workpiece_inputs":[{"item_input":0,"current_nodes":["left","right"]},{"item_input":1,"current_nodes":["left","right"]}],"recipe":{"type":"minecraft:crafting_special_repairitem"}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"left","to":"repair"},{"from":"right","to":"repair"},{"from":"repair","to":"finish"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            var definition=graph.definition(id);
            var left=Workpiece.write(new ItemStack(Items.DIAMOND_CHESTPLATE),((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(definition, null, "left", "minecraft:diamond_chestplate"), ()->0)).progress());
            var right=Workpiece.write(new ItemStack(Items.DIAMOND_CHESTPLATE),((TransitionResult.Continued) GraphPipeline.resolve(GraphPipeline.plan(definition, null, "right", "minecraft:quartz"), ()->0)).progress());
            left.setDamageValue(420);right.setDamageValue(320);
            left.set(DataComponents.CUSTOM_NAME,Component.literal("Left named armor"));
            right.set(DataComponents.CUSTOM_NAME,Component.literal("Right named armor"));
            var enchants=level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT);
            left.enchant(enchants.getOrThrow(Enchantments.PROTECTION),3);
            left.enchant(enchants.getOrThrow(Enchantments.BINDING_CURSE),1);
            right.enchant(enchants.getOrThrow(Enchantments.VANISHING_CURSE),1);
            InventoryMenu menu=player.inventoryMenu;player.containerMenu=menu;
            player.getInventory().clearContent();
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse(id),graph)));GraphIndex.invalidate();
            for(boolean reversed:List.of(false,true)) {
                menu.clearCraftingContent();menu.setCarried(ItemStack.EMPTY);
                var first=reversed?right:left;var second=reversed?left:right;
                var nativeInput=CraftingInput.of(2,2,List.of(ItemStack.EMPTY,first.copy(),ItemStack.EMPTY,second.copy()));
                var nativeRecipe=new net.minecraft.world.item.crafting.RepairItemRecipe(CraftingBookCategory.MISC);
                var expected=nativeRecipe.assemble(nativeInput,level.registryAccess());
                check(nativeRecipe.matches(nativeInput,level)&&expected.is(Items.DIAMOND_CHESTPLATE)
                    &&!expected.has(DataComponents.CUSTOM_NAME),"Repair tracer is a valid native name-removing repair");
                menu.getSlot(2).set(first.copy());menu.getSlot(4).set(second.copy());
                menu.slotsChanged(menu.getSlot(2).container);
                check(!menu.getSlot(0).getItem().isEmpty(),"Native repair graph has a menu preview");
                menu.clicked(0,0,ClickType.PICKUP,player);
                var result=menu.getCarried();var state=Workpiece.read(level,result);
                check(result.is(Items.DIAMOND_CHESTPLATE)&&result.getDamageValue()==expected.getDamageValue()
                    &&Objects.equals(result.get(DataComponents.ENCHANTMENTS),expected.get(DataComponents.ENCHANTMENTS))
                    &&!result.has(DataComponents.CUSTOM_NAME),"Repair keeps original native durability/curses without restoring ordinary enchantments or names");
                var inherited=Workpiece.read(level,first);
                check(state!=null&&state.current().equals("repair")&&state.recentHistory().equals(List.of(reversed?"right":"left","repair"))
                    &&state.marks().values().equals(inherited.marks().values())&&state.checkpoints().equals(inherited.checkpoints()),
                    "Only the fixed row-major first cell owns repair history, marks and checkpoints, even after reversing damage/name identities");
                check(menu.getSlot(2).getItem().isEmpty()&&menu.getSlot(4).getItem().isEmpty(),"Repair pays exactly its two occupied cells");
            }
            helper.succeed();
        } finally {
            player.getInventory().clearContent();player.inventoryMenu.clearCraftingContent();player.inventoryMenu.setCarried(ItemStack.EMPTY);
            manager.replaceRecipes(saved);GraphIndex.invalidate();
        }
    }
    @GameTest(template="test_empty",timeoutTicks=80,batch="native_special_crafter")
    public static void crafterScheduledDyePulseUsesNativeCarrierAndPhysicalOffset(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1));var target=pos.east();var power=pos.west();
        Runnable cleanup=()->{level.removeBlock(power,false);level.removeBlock(pos,false);level.removeBlock(target,false);manager.replaceRecipes(saved);GraphIndex.invalidate();};
        try {
            var nativeRecipe=new ArmorDyeRecipe(CraftingBookCategory.MISC);
            var base=new ItemStack(Items.LEATHER_CHESTPLATE);base.setDamageValue(17);
            base.set(DataComponents.CUSTOM_NAME,Component.literal("Offset crafter dye"));
            var nativeInput=CraftingInput.of(3,3,List.of(ItemStack.EMPTY,ItemStack.EMPTY,ItemStack.EMPTY,
                ItemStack.EMPTY,base.copy(),new ItemStack(Items.RED_DYE),ItemStack.EMPTY,new ItemStack(Items.BLUE_DYE),ItemStack.EMPTY));
            var expected=nativeRecipe.assemble(nativeInput,level.registryAccess());
            check(nativeRecipe.matches(nativeInput,level)&&expected.has(DataComponents.DYED_COLOR),"Crafter fixture is native dyeable");
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:ordinary_crafter_dye"),nativeRecipe)));GraphIndex.invalidate();
            level.setBlockAndUpdate(target,net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(pos,net.minecraft.world.level.block.Blocks.CRAFTER.defaultBlockState().setValue(
                net.minecraft.world.level.block.state.properties.BlockStateProperties.ORIENTATION,net.minecraft.core.FrontAndTop.EAST_UP));
            var machine=(net.minecraft.world.level.block.entity.CrafterBlockEntity)level.getBlockEntity(pos);
            for(int i:new int[]{0,1,2,3,6,8})machine.setSlotState(i,false);
            machine.setItem(4,base.copy());machine.setItem(5,new ItemStack(Items.RED_DYE,2));machine.setItem(7,new ItemStack(Items.BLUE_DYE,2));
            level.setBlockAndUpdate(power,net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
            helper.runAfterDelay(12,()->{
                try {
                    var chest=(net.minecraft.world.level.block.entity.ChestBlockEntity)level.getBlockEntity(target);
                    check(ItemStack.matches(chest.getItem(0),expected)&&!Workpiece.has(chest.getItem(0)),"Ordinary native scheduled dye control retains native components without graph progress: actual="+chest.getItem(0)
                        +", expected="+expected+", recipes="+manager.getRecipes().stream().map(RecipeHolder::id).toList()
                        +", inputs="+machine.getItems()+", block="+level.getBlockState(pos));
                    level.removeBlock(power,false);chest.clearContent();machine.clearContent();
                    var graph=new GraphRecipe(JsonParser.parseString("""
                        {"ingredient":{"item":"minecraft:leather_chestplate"},"transitional_item":"minecraft:iron_nugget",
                         "nodes":[{"id":"dye","start":true,"recipe":{"type":"minecraft:crafting_special_armordye"}},
                           {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                         "edges":[{"from":"dye","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                        """).getAsJsonObject());
                    manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:crafter_native_dye"),graph)));GraphIndex.invalidate();
                    machine.setItem(4,base.copy());machine.setItem(5,new ItemStack(Items.RED_DYE,2));machine.setItem(7,new ItemStack(Items.BLUE_DYE,2));
                    level.setBlockAndUpdate(power,net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState());
                    helper.runAfterDelay(12,()->{
                        try {
                            var output=chest.getItem(0);var state=Workpiece.read(level,output);
                            check(output.is(Items.LEATHER_CHESTPLATE)&&state!=null&&state.current().equals("dye")&&state.recentHistory().equals(List.of("dye")),
                                "Scheduled crafter special graph pulse delivers the native carrier with exactly one graph step");
                            check(output.getDamageValue()==17&&Objects.equals(output.get(DataComponents.CUSTOM_NAME),base.get(DataComponents.CUSTOM_NAME))
                                &&Objects.equals(output.get(DataComponents.DYED_COLOR),expected.get(DataComponents.DYED_COLOR)),"Crafter native assembled components survive graph default");
                            check(machine.getItem(4).isEmpty()&&machine.getItem(5).getCount()==1&&machine.getItem(7).getCount()==1,
                                "Offset physical cells pay exactly one round under sustained power");
                            check(!Workpiece.has(machine.getItem(5))&&!Workpiece.has(machine.getItem(7)),"Unconsumed ordinary dyes gain no progress");
                            for(int i=1;i<chest.getContainerSize();i++)check(chest.getItem(i).isEmpty(),"No extra default carrier or native remainder is invented");
                            helper.succeed();
                        } finally {cleanup.run();}
                    });
                } catch(RuntimeException|Error failure) {cleanup.run();throw failure;}
            });
        } catch(RuntimeException|Error failure) {cleanup.run();throw failure;}
    }
    private static void check(boolean condition,String message) { if(!condition)throw new net.minecraft.gametest.framework.GameTestAssertException(message); }
}
