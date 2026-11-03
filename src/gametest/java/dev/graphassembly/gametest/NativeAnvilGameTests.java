package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Actual native repair/pickup control followed by the identical graph-authorized operation. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeAnvilGameTests {
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeAnvilRepairPreservesSwordPaymentAndAdvancesGraph(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,
            new com.mojang.authlib.GameProfile(UUID.randomUUID(),"anvil-native-tracer"));
        AnvilMenu menu=null;
        try {
            manager.replaceRecipes(List.of());GraphIndex.invalidate();
            level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());
            player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);
            player.getInventory().clearContent();player.getAbilities().instabuild=false;player.experienceLevel=30;
            menu=new AnvilMenu(1,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
            var sword=new ItemStack(Items.IRON_SWORD);sword.setDamageValue(100);
            menu.getSlot(0).set(sword.copy());menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,3));
            menu.slotsChanged(menu.getSlot(0).container);
            var nativePreview=menu.getSlot(2).getItem().copy();int nativeCost=menu.getCost();
            check(nativePreview.is(Items.IRON_SWORD)&&nativePreview.getDamageValue()<100&&nativeCost>0,
                "Native control must expose a real affordable iron-sword material repair");
            check(menu.stillValid(player)&&menu.getSlot(2).mayPickup(player),"Native control is at a real usable anvil with sufficient XP");
            menu.clicked(2,0,ClickType.PICKUP,player);
            var nativeResult=menu.getCarried().copy();int nativeXp=player.experienceLevel;
            var nativeRemainder=menu.getSlot(1).getItem().copy();
            check(ItemStack.matches(nativeResult,nativePreview)&&!Workpiece.has(nativeResult)
                &&nativeXp==30-nativeCost&&menu.getSlot(0).getItem().isEmpty()
                &&nativeRemainder.is(Items.IRON_INGOT)&&nativeRemainder.getCount()>0&&nativeRemainder.getCount()<3,
                "Native pickup must deliver its preview, pay its observed XP cost once and consume only needed ingots");

            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"repair","start":true,"recipe":{"type":"graph_assembly:anvil","action":"repair","addition":{"item":"minecraft:iron_ingot"}}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"repair","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            var id=ResourceLocation.parse("test:native_anvil_repair_tracer");
            manager.replaceRecipes(List.of(new RecipeHolder<>(id,graph)));GraphIndex.invalidate();
            // Restore exactly the same native input/player/block conditions, not a direct graph commit.
            menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);
            level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.experienceLevel=30;
            menu.getSlot(0).set(sword.copy());menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,3));
            menu.slotsChanged(menu.getSlot(0).container);
            check(menu.getSlot(2).getItem().is(Items.IRON_SWORD),"Graph repair preview keeps native sword despite iron-nugget graph default");
            menu.clicked(2,0,ClickType.PICKUP,player);
            var result=menu.getCarried();var withoutProgress=result.copy();withoutProgress.remove(GraphAssembly.PROGRESS.get());
            check(ItemStack.matches(withoutProgress,nativeResult)&&player.experienceLevel==nativeXp
                &&menu.getSlot(0).getItem().isEmpty()&&ItemStack.matches(menu.getSlot(1).getItem(),nativeRemainder),
                "Graph pickup preserves actual native result components, damage, XP payment and material consumption");
            var progress=Workpiece.read(level,result);
            check(progress!=null&&progress.graphId().equals(id.toString())&&progress.current().equals("repair")
                &&progress.history().equals(List.of("repair")),
                "Successful native anvil repair must advance graph progress to repair exactly once");
            helper.succeed();
        } finally {
            if(menu!=null){for(int slot=0;slot<3;slot++)menu.getSlot(slot).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);}
            player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;
            level.removeBlock(pos,false);manager.replaceRecipes(saved);GraphIndex.invalidate();
        }
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void enchantedBookNativeControlAdvancesExplicitEnchantAndDefaultWorkButNotRenameOnly(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();
        var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());var pos=helper.absolutePos(new BlockPos(1,1,1));
        var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"anvil-enchant"));
        AnvilMenu menu=null;
        try {
            manager.replaceRecipes(List.of());GraphIndex.invalidate();level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());
            player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);player.getInventory().clearContent();player.getAbilities().instabuild=false;player.experienceLevel=30;
            menu=new AnvilMenu(5,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
            var sharpness=level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                .getOrThrow(net.minecraft.world.item.enchantment.Enchantments.SHARPNESS);
            var sword=new ItemStack(Items.IRON_SWORD);var book=new ItemStack(Items.ENCHANTED_BOOK);book.enchant(sharpness,2);
            check(book.get(net.minecraft.core.component.DataComponents.STORED_ENCHANTMENTS).getLevel(sharpness)==2,"Fixture book has actual stored native Sharpness II");
            menu.getSlot(0).set(sword.copy());menu.getSlot(1).set(book.copy());menu.slotsChanged(menu.getSlot(0).container);
            var nativePreview=menu.getSlot(2).getItem().copy();int nativeCost=menu.getCost();
            check(nativePreview.is(Items.IRON_SWORD)&&nativePreview.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS).getLevel(sharpness)==2
                &&nativeCost>0&&menu.getSlot(2).mayPickup(player),"Actual native enchanted-book control adds compatible enchantment at an affordable observed fee");
            menu.clicked(2,0,ClickType.PICKUP,player);var nativeResult=menu.getCarried().copy();int nativeXp=player.experienceLevel;
            var nativeRemainder=menu.getSlot(1).getItem().copy();
            check(ItemStack.matches(nativeResult,nativePreview)&&nativeXp==30-nativeCost&&menu.getSlot(0).getItem().isEmpty()&&nativeRemainder.isEmpty(),
                "Native enchanted-book pickup delivers original effect and pays/consumes exactly its native inputs");
            GraphRecipe workGraph=null;
            for(boolean explicitEnchant:List.of(true,false)) {
                var graph=new GraphRecipe(JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
                     "nodes":[{"id":"apply","start":true,"recipe":{"type":"graph_assembly:anvil"%s}},
                       {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                     "edges":[{"from":"apply","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                    """.formatted(explicitEnchant?",\"action\":\"enchant\",\"addition\":{\"item\":\"minecraft:enchanted_book\"}":"")).getAsJsonObject());
                if(!explicitEnchant)workGraph=graph;
                var id=ResourceLocation.parse(explicitEnchant?"test:anvil_explicit_enchant":"test:anvil_default_work");
                menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.experienceLevel=30;
                manager.replaceRecipes(List.of(new RecipeHolder<>(id,graph)));GraphIndex.invalidate();
                menu.getSlot(0).set(sword.copy());menu.getSlot(1).set(book.copy());menu.slotsChanged(menu.getSlot(0).container);menu.clicked(2,0,ClickType.PICKUP,player);
                var result=menu.getCarried();var stripped=result.copy();stripped.remove(GraphAssembly.PROGRESS.get());
                check(ItemStack.matches(stripped,nativeResult)&&player.experienceLevel==nativeXp&&menu.getSlot(0).getItem().isEmpty()
                    &&ItemStack.matches(menu.getSlot(1).getItem(),nativeRemainder),"Graph enchant/work retains actual native result components, fee and book consumption");
                var progress=Workpiece.read(level,result);
                check(progress!=null&&progress.graphId().equals(id.toString())&&progress.current().equals("apply")&&progress.history().equals(List.of("apply")),
                    "Actual enchanted-book operation must advance "+(explicitEnchant?"explicit enchant":"default work")+" graph once");
            }
            // A real valid rename is not an enchantment/repair operation for default work.
            menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);player.experienceLevel=30;
            manager.replaceRecipes(List.of());GraphIndex.invalidate();menu.getSlot(0).set(sword.copy());menu.setItemName("Rename-only work probe");
            check(menu.getSlot(2).getItem().is(Items.IRON_SWORD)&&menu.getSlot(2).getItem().getHoverName().getString().equals("Rename-only work probe")
                &&menu.getCost()>0,"Rename-only rejection control is an actually valid native rename, not impossible input");
            manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:anvil_default_work"),workGraph)));GraphIndex.invalidate();
            menu.createResult();menu.clicked(2,0,ClickType.PICKUP,player);
            check(menu.getCarried().isEmpty()&&player.experienceLevel==30&&menu.getSlot(0).getItem().is(Items.IRON_SWORD),"Rename alone cannot qualify default work or fall back to native pickup");
            helper.succeed();
        } finally {
            if(menu!=null){for(int i=0;i<3;i++)menu.getSlot(i).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);}
            player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;level.removeBlock(pos,false);manager.replaceRecipes(saved);GraphIndex.invalidate();
        }
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void explicitRenameUsesNativeNameRemovalAndRejectsRepeatOrWrongAction(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"anvil-rename"));
        AnvilMenu menu=null;
        try {
            level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);
            player.getInventory().clearContent();player.getAbilities().instabuild=false;player.experienceLevel=30;
            menu=new AnvilMenu(6,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
            for(boolean remove:List.of(false,true)) {
                String target=remove?"":"Verified native name";
                var original=new ItemStack(Items.IRON_SWORD);
                if(remove)original.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Remove this name"));
                manager.replaceRecipes(List.of());GraphIndex.invalidate();menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);
                level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.experienceLevel=30;
                menu.getSlot(0).set(original.copy());menu.setItemName(target);menu.slotsChanged(menu.getSlot(0).container);
                var nativePreview=menu.getSlot(2).getItem().copy();int nativeCost=menu.getCost();
                check(nativePreview.is(Items.IRON_SWORD)&&nativeCost>0&&menu.getSlot(2).mayPickup(player)
                    &&(remove?!nativePreview.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME):nativePreview.getHoverName().getString().equals(target)),
                    "Explicit rename fixture has valid native "+(remove?"name removal":"exact new name")+" with empty right slot");
                menu.clicked(2,0,ClickType.PICKUP,player);var nativeResult=menu.getCarried().copy();int nativeXp=player.experienceLevel;
                check(ItemStack.matches(nativeResult,nativePreview)&&nativeXp==30-nativeCost&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty(),
                    "Ordinary rename pickup applies exact native component result and observed fee");
                var graphJson=JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
                     "nodes":[{"id":"rename","start":true,"recipe":{"type":"graph_assembly:anvil","action":"rename"}},
                       {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                     "edges":[{"from":"rename","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                    """).getAsJsonObject();
                graphJson.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").addProperty("name",target);
                var id=ResourceLocation.parse(remove?"test:anvil_remove_name":"test:anvil_exact_name");
                menu.setCarried(ItemStack.EMPTY);player.experienceLevel=30;level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());
                manager.replaceRecipes(List.of(new RecipeHolder<>(id,new GraphRecipe(graphJson))));GraphIndex.invalidate();menu.getSlot(0).set(original.copy());
                menu.setItemName(target);menu.slotsChanged(menu.getSlot(0).container);menu.clicked(2,0,ClickType.PICKUP,player);
                var result=menu.getCarried();var stripped=result.copy();stripped.remove(GraphAssembly.PROGRESS.get());
                check(ItemStack.matches(stripped,nativeResult)&&player.experienceLevel==nativeXp&&menu.getSlot(0).getItem().isEmpty()&&menu.getSlot(1).getItem().isEmpty(),
                    "Explicit rename preserves actual native name effect, sword carrier and one-time fee");
                var progress=Workpiece.read(level,result);
                check(progress!=null&&progress.graphId().equals(id.toString())&&progress.current().equals("rename")&&progress.history().equals(List.of("rename")),
                    "Successful explicit native "+(remove?"name removal":"rename")+" advances graph once");
                menu.setCarried(ItemStack.EMPTY);menu.getSlot(0).set(nativeResult.copy());menu.setItemName(target);menu.slotsChanged(menu.getSlot(0).container);
                int unchangedXp=player.experienceLevel;menu.clicked(2,0,ClickType.PICKUP,player);
                check(menu.getCarried().isEmpty()&&!menu.getSlot(2).hasItem()&&player.experienceLevel==unchangedXp&&ItemStack.matches(menu.getSlot(0).getItem(),nativeResult),
                    "Repeated unchanged name or already removed name cannot create progress/pay/ordinary-fallback");
            }
            // Native rename is valid, but neither specialized effect nor default work may count it.
            for(String action:List.of("repair","enchant","work")) {
                var graph=new GraphRecipe(JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
                     "nodes":[{"id":"wrong","start":true,"recipe":{"type":"graph_assembly:anvil","action":"%s"}},
                       {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                     "edges":[{"from":"wrong","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                    """.formatted(action)).getAsJsonObject());
                manager.replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:rename_wrong_action"),graph)));GraphIndex.invalidate();
                menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));menu.setItemName("Wrong-action valid name");
                int beforeXp=player.experienceLevel;menu.clicked(2,0,ClickType.PICKUP,player);
                check(menu.getCarried().isEmpty()&&player.experienceLevel==beforeXp&&menu.getSlot(0).getItem().is(Items.IRON_SWORD),"Rename alone cannot satisfy "+action+" or ordinary-fallback");
            }
            helper.succeed();
        } finally {
            if(menu!=null){for(int i=0;i<3;i++)menu.getSlot(i).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);}
            player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;level.removeBlock(pos,false);manager.replaceRecipes(saved);GraphIndex.invalidate();
        }
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void explicitCombineUsesHostEventOutputAndRejectsOnlyNameCostOrProgressChanges(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();var saved=new ArrayList<RecipeHolder<?>>(manager.getRecipes());
        var pos=helper.absolutePos(new BlockPos(1,1,1));var player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"anvil-host-combine"));
        AnvilMenu menu=null;
        try {
            level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);
            player.getInventory().clearContent();player.getAbilities().instabuild=false;player.experienceLevel=30;
            menu=new AnvilMenu(7,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
            final AnvilMenu hostMenu=menu;
            var id=ResourceLocation.parse("test:anvil_host_combine");
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"combine","start":true,"recipe":{"type":"graph_assembly:anvil","action":"combine","addition":{"item":"minecraft:gold_ingot"}}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"combine","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            var ignoredProgress=dev.graphassembly.core.GraphEngine.complete(graph.definition(id.toString()),null,"combine","minecraft:iron_sword",()->0).state().toJsonString();
            for(String effect:List.of("changed-item","custom-component","name-only","repair-cost-only","progress-only")) {
                int[] calls={0};
                java.util.function.Consumer<net.neoforged.neoforge.event.AnvilUpdateEvent> listener=event->{
                    if(event.getPlayer()!=player||player.containerMenu!=hostMenu||!event.getLeft().is(Items.IRON_SWORD)||!event.getRight().is(Items.GOLD_INGOT))return;
                    calls[0]++;var output=effect.equals("changed-item")?new ItemStack(Items.DIAMOND_SWORD):event.getLeft().copy();
                    switch(effect) {
                        case "custom-component" -> {var tag=new net.minecraft.nbt.CompoundTag();tag.putString("host_effect","verified-combination");output.set(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.of(tag));}
                        case "name-only" -> output.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("Host name only"));
                        case "repair-cost-only" -> output.set(net.minecraft.core.component.DataComponents.REPAIR_COST,12);
                        case "progress-only" -> output.set(GraphAssembly.PROGRESS.get(),ignoredProgress);
                    }
                    // This is a genuine scoped host API fixture, not a graph-authored result/fee.
                    event.setOutput(output);event.setCost(3);event.setMaterialCost(1);
                };
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(listener);
                try {
                    manager.replaceRecipes(List.of());GraphIndex.invalidate();menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);
                    level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.experienceLevel=30;
                    menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));menu.getSlot(1).set(new ItemStack(Items.GOLD_INGOT,3));menu.slotsChanged(menu.getSlot(0).container);
                    var nativePreview=menu.getSlot(2).getItem().copy();int nativeCost=menu.getCost();
                    check(calls[0]>0&&!nativePreview.isEmpty()&&nativeCost>0&&menu.getSlot(2).mayPickup(player),"Scoped real AnvilUpdateEvent creates an actual payable host combination for "+effect);
                    menu.clicked(2,0,ClickType.PICKUP,player);var nativeResult=menu.getCarried().copy();int nativeXp=player.experienceLevel;var nativeRemainder=menu.getSlot(1).getItem().copy();
                    check(ItemStack.matches(nativeResult,nativePreview)&&nativeXp==30-nativeCost&&nativeRemainder.is(Items.GOLD_INGOT)&&nativeRemainder.getCount()==2,
                        "Original host onTake supplies exact result, XP and one-right-item consumption for "+effect);
                    menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.experienceLevel=30;
                    manager.replaceRecipes(List.of(new RecipeHolder<>(id,graph)));GraphIndex.invalidate();menu.getSlot(0).set(new ItemStack(Items.IRON_SWORD));menu.getSlot(1).set(new ItemStack(Items.GOLD_INGOT,3));menu.slotsChanged(menu.getSlot(0).container);
                    menu.clicked(2,0,ClickType.PICKUP,player);
                    if(effect.equals("changed-item")||effect.equals("custom-component")) {
                        var result=menu.getCarried();var stripped=result.copy();stripped.remove(GraphAssembly.PROGRESS.get());
                        check(ItemStack.matches(stripped,nativeResult)&&player.experienceLevel==nativeXp&&menu.getSlot(0).getItem().isEmpty()&&ItemStack.matches(menu.getSlot(1).getItem(),nativeRemainder),
                            "Combine keeps complete original host result and actual fee/material use, never graph default");
                        var progress=Workpiece.read(level,result);
                        check(progress!=null&&progress.graphId().equals(id.toString())&&progress.current().equals("combine")&&progress.history().equals(List.of("combine")),
                            "Actual host "+effect+" combination advances explicit combine graph once");
                    } else {
                        check(menu.getCarried().isEmpty()&&!menu.getSlot(2).hasItem()&&player.experienceLevel==30&&menu.getSlot(0).getItem().is(Items.IRON_SWORD)&&menu.getSlot(1).getItem().getCount()==3,
                            "Only "+effect+" is not a meaningful combine and cannot progress/pay/ordinary-fallback");
                    }
                } finally {net.neoforged.neoforge.common.NeoForge.EVENT_BUS.unregister(listener);}
            }
            helper.succeed();
        } finally {
            if(menu!=null){for(int i=0;i<3;i++)menu.getSlot(i).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);}
            player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;level.removeBlock(pos,false);manager.replaceRecipes(saved);GraphIndex.invalidate();
        }
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}
}
