package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeCrafterContractsGameTests {
    private static final class Session implements AutoCloseable {
        final GameTestHelper h; final BlockPos pos,target,power; final List<RecipeHolder<?>> saved;
        final CrafterBlockEntity machine; final ChestBlockEntity chest;
        Session(GameTestHelper h,GraphRecipe graph) {
            this.h=h;var level=h.getLevel();saved=new ArrayList<>(level.getRecipeManager().getRecipes());
            pos=h.absolutePos(new BlockPos(1,1,1));target=pos.east();power=pos.west();
            level.getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:crafter_contract"),graph)));GraphIndex.invalidate();
            level.setBlockAndUpdate(target,Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(pos,Blocks.CRAFTER.defaultBlockState().setValue(BlockStateProperties.ORIENTATION,FrontAndTop.EAST_UP));
            machine=(CrafterBlockEntity)level.getBlockEntity(pos);chest=(ChestBlockEntity)level.getBlockEntity(target);
        }
        void tick(){h.getLevel().getBlockState(pos).tick(h.getLevel(),pos,h.getLevel().random);}
        void pulse(){h.getLevel().removeBlock(power,false);h.getLevel().setBlockAndUpdate(power,Blocks.REDSTONE_BLOCK.defaultBlockState());}
        void later(int ticks,Runnable action){h.runAfterDelay(ticks,()->{try{action.run();}catch(RuntimeException|Error failure){close();throw failure;}});}
        int count(Item item){int n=0;for(int i=0;i<chest.getContainerSize();i++)if(chest.getItem(i).is(item))n+=chest.getItem(i).getCount();return n;}
        void done(){close();h.succeed();}
        public void close(){CraftingRemainderFixture.callback=stack->{};FurnaceCodecFixture.decode=()->{};var level=h.getLevel();level.removeBlock(power,false);level.removeBlock(pos,false);level.removeBlock(target,false);level.getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static GraphRecipe graph(String recipe,String outputs,boolean finish) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"finish":%s,"recipe":%s}%s],"edges":%s,
             "outputs":%s}
            """.formatted(finish,recipe,finish?"":",{\"id\":\"end\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}",
                finish?"[]":"[{\"from\":\"s\",\"to\":\"end\"}]",outputs.equals("[]")?"[]":"[{\"results\":"+outputs+"}]")).getAsJsonObject());
    }
    private static final String REMAINDER_RECIPE="""
        {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"graph_assembly:crafting_remainder_fixture"}],"result":{"id":"minecraft:diamond"}}
        """;
    private static void check(boolean ok,String reason){if(!ok)throw new GameTestAssertException(reason);}

    @GameTest(template="test_empty",timeoutTicks=80,batch="crafter_throw")
    public static void throwingRealRemainderPreservesInputsContextAndAllowsLaterPulse(GameTestHelper h) {
        var s=new Session(h,graph(REMAINDER_RECIPE,"[{\"item\":\"minecraft:diamond\"}]",true));
        s.machine.setItem(0,new ItemStack(Items.IRON_INGOT,2));s.machine.setItem(1,new ItemStack(CraftingRemainderFixture.ITEM,2));
        var original=CommonHooks.getCraftingPlayer();
        var parent=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"crafter-parent"));
        int[] calls={0};
        CraftingRemainderFixture.callback=stack->{calls[0]++;check(CommonHooks.getCraftingPlayer()==null,"Machine remainder must see no crafting player");throw new IllegalStateException("crafter actual remainder fixture");};
        try {
            CommonHooks.setCraftingPlayer(parent);
            boolean threw=false;
            try {h.getLevel().getBlockState(s.pos).tick(h.getLevel(),s.pos,h.getLevel().random);}
            catch(RuntimeException expected){threw=true;}
            check(!threw,"Scheduled native block execution must absorb a failed preparation callback rather than crash the server");
            check(calls[0]==1,"Actual registered remainder callback must run once");
            check(CommonHooks.getCraftingPlayer()==parent,"Throw must restore ambient crafting player");
            check(s.count(Items.DIAMOND)==0&&s.machine.getItem(0).getCount()==2&&s.machine.getItem(1).getCount()==2,"Throwing preparation must not consume or deliver");
            CraftingRemainderFixture.callback=stack->{};
            h.getLevel().getBlockState(s.pos).tick(h.getLevel(),s.pos,h.getLevel().random);
            check(s.machine.getItem(0).getCount()==1&&s.machine.getItem(1).getCount()==1&&s.count(Items.DIAMOND)==1&&s.count(Items.GLASS_BOTTLE)==1,"Later valid pulse proves lock released and native remainder expelled, never grid-refilled");
            h.succeed();
        } finally {CommonHooks.setCraftingPlayer(original);s.close();}
    }

    private static final String SINGLE="""
        {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"}],"result":{"id":"minecraft:iron_nugget"}}
        """;

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void geometryDisabledCellsAndNativeHopperDistributionRemainNative(GameTestHelper h) {
        String shaped="""
            {"type":"minecraft:crafting_shaped","pattern":["A "," B"],"key":{"A":{"item":"minecraft:iron_ingot"},"B":{"item":"minecraft:stick"}},"result":{"id":"minecraft:iron_nugget"}}
            """;
        for(var cells:List.of(new int[]{4,8},new int[]{5,7}))try(var s=new Session(h,graph(shaped,"[{\"item\":\"minecraft:diamond\"}]",false))) {
            s.machine.setSlotState(0,false);s.machine.setItem(cells[0],new ItemStack(Items.IRON_INGOT,2));s.machine.setItem(cells[1],new ItemStack(Items.STICK,2));s.tick();
            check(s.count(Items.IRON_NUGGET)==1&&s.machine.getItem(cells[0]).getCount()==1&&s.machine.getItem(cells[1]).getCount()==1,"Mirrored offset native3x3 consumes matched physical cells once");
            check(s.machine.isSlotDisabled(0)&&s.machine.getItem(0).isEmpty(),"Disabled empty slot remains untouched");
        }
        String repeated="""
            {"type":"minecraft:crafting_shaped","pattern":["AA"],"key":{"A":{"item":"minecraft:iron_ingot"}},"result":{"id":"minecraft:iron_nugget"}}
            """;
        try(var s=new Session(h,graph(repeated,"[{\"item\":\"minecraft:diamond\"}]",false))) {
            s.machine.setItem(3,new ItemStack(Items.IRON_INGOT,2));s.tick();check(s.count(Items.IRON_NUGGET)==0&&s.machine.getItem(3).getCount()==2,"Stack count cannot replace repeated physical cells");
            s.machine.setItem(4,new ItemStack(Items.IRON_INGOT));s.tick();check(s.count(Items.IRON_NUGGET)==1&&s.machine.getItem(3).getCount()==1&&s.machine.getItem(4).isEmpty(),"Repeated cells paid separately");
            s.machine.clearContent();s.machine.setSlotState(0,false);
            var rest=HopperBlockEntity.addItem(null,s.machine,new ItemStack(Items.STICK,8),Direction.UP);
            check(rest.isEmpty()&&s.machine.getItem(0).isEmpty()&&s.machine.getItem(1).getCount()==8,"Native bulk addItem inserts its supplied stack into first enabled cell");
            s.machine.clearContent();
            for(int n=0;n<8;n++)check(HopperBlockEntity.addItem(null,s.machine,new ItemStack(Items.STICK),Direction.UP).isEmpty(),"Single native hopper transfer accepted");
            for(int i=1;i<9;i++)check(s.machine.getItem(i).is(Items.STICK)&&s.machine.getItem(i).getCount()==1,"Native crafter insertion distributes one item per enabled cell");
            check(s.machine.getRedstoneSignal()==9,"Comparator includes disabled slot plus occupied cells");
        }
        h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void cachedOrdinaryCollisionCannotStealGraphOrInvalidProgress(GameTestHelper h) {
        try(var s=new Session(h,graph(SINGLE,"[{\"item\":\"minecraft:diamond\"}]",false))) {
            var ordinary=new RecipeHolder<>(ResourceLocation.parse("test:crafter_ordinary"),new ShapelessRecipe("",CraftingBookCategory.MISC,new ItemStack(Items.GOLD_INGOT),NonNullList.of(Ingredient.EMPTY,Ingredient.of(Items.IRON_INGOT))));
            var manager=h.getLevel().getRecipeManager();manager.replaceRecipes(List.of(ordinary));GraphIndex.invalidate();
            s.machine.setItem(0,new ItemStack(Items.IRON_INGOT,3));
            s.machine.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("unique-cache-contract-key"));
            check(net.minecraft.world.level.block.CrafterBlock.getPotentialResults(h.getLevel(),s.machine.asCraftInput()).isPresent(),"Warm actual static native cache with ordinary recipe");
            s.tick();check(s.count(Items.GOLD_INGOT)==1&&s.machine.getItem(0).getCount()==2,"No graph preserves ordinary machine execution");
            manager.replaceRecipes(List.of(ordinary,new RecipeHolder<>(ResourceLocation.parse("test:crafter_contract"),graph(SINGLE,"[{\"item\":\"minecraft:diamond\"}]",false))));GraphIndex.invalidate();
            s.tick();check(s.count(Items.IRON_NUGGET)==1&&s.count(Items.GOLD_INGOT)==1,"Graph entrance wins ordinary collision even after cache warmup");
            s.machine.setItem(0,Workpiece.write(new ItemStack(Items.IRON_INGOT),new dev.graphassembly.core.Progress("test:missing","s","s",Map.of(),List.of(),Map.of(),null,List.of("s"))));
            check(net.minecraft.world.level.block.CrafterBlock.getPotentialResults(h.getLevel(),s.machine.asCraftInput()).isEmpty(),"Static cache rejects progress-bearing ordinary match");
            s.tick();check(Workpiece.has(s.machine.getItem(0))&&s.count(Items.GOLD_INGOT)==1,"Invalid progress must not ordinary-fallback");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void multipleEmptyChanceAndBucketOutputsUseNativeChestAndWorldDelivery(GameTestHelper h) {
        String bucket="""
            {"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_ingot"},{"item":"minecraft:milk_bucket"}],"result":{"id":"minecraft:diamond"}}
            """;
        for(int capacity:List.of(0,1,27))try(var s=new Session(h,graph(bucket,"[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\",\"count\":3}]}]",true))) {
            if(capacity<27){for(int i=0;i<27;i++)s.chest.setItem(i,new ItemStack(Items.COBBLESTONE,64));if(capacity==1)s.chest.setItem(0,new ItemStack(Items.DIAMOND,63));}
            var area=new net.minecraft.world.phys.AABB(s.pos).inflate(3);
            var existing=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().map(net.minecraft.world.entity.Entity::getId).toList();
            s.machine.setItem(0,new ItemStack(Items.IRON_INGOT));s.machine.setItem(1,new ItemStack(Items.MILK_BUCKET));s.tick();
            check(s.machine.isEmpty(),"Machine consumes each ingredient and never returns bucket into input grid");
            var drops=h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,area).stream().filter(e->!existing.contains(e.getId())).toList();
            int diamond=drops.stream().filter(e->e.getItem().is(Items.DIAMOND)).mapToInt(e->e.getItem().getCount()).sum();
            int emerald=drops.stream().filter(e->e.getItem().is(Items.EMERALD)).mapToInt(e->e.getItem().getCount()).sum();
            int buckets=drops.stream().filter(e->e.getItem().is(Items.BUCKET)).mapToInt(e->e.getItem().getCount()).sum();
            check(s.count(Items.DIAMOND)+diamond==(capacity==1?65:2)&&s.count(Items.EMERALD)+emerald==3&&s.count(Items.BUCKET)+buckets==1,"Native partial/full chest overflow capacity="+capacity+" chest(diamond,emerald,bucket)="+s.count(Items.DIAMOND)+","+s.count(Items.EMERALD)+","+s.count(Items.BUCKET)+" newWorld="+diamond+","+emerald+","+buckets);
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        }
        for(String outputs:List.of("[]","[{\"item\":\"minecraft:diamond\",\"chance\":0}]")) {
            var data=graph(SINGLE,outputs,true).json();
            // GraphEngine's existing default is LENIENT: no pool keeps a carrier, not settlement.
            // An intentionally empty settlement therefore uses the existing STRICT finish policy.
            if(outputs.equals("[]"))data.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("finish_mode","strict");
            try(var s=new Session(h,new GraphRecipe(data))) {
                s.machine.setItem(0,new ItemStack(Items.IRON_INGOT,2));s.tick();
                check(s.machine.getItem(0).getCount()==1&&s.chest.isEmpty(),"Empty/chance-zero settlement outputs="+outputs+" input="+s.machine.getItem(0)+" chest0="+s.chest.getItem(0)+" disabled="+GraphIndex.get(h.getLevel()).disabledReason("test:crafter_contract"));
            }
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void realRemainderMutationsReloadAndReplacementReentryAbortBeforeRng(GameTestHelper h) {
        for(String action:List.of("component","count","mask","facing","remove","replace","reload","reenter"))try(var s=new Session(h,graph(REMAINDER_RECIPE,"[{\"item\":\"minecraft:diamond\",\"chance\":0.5}]",true))) {
            s.machine.setItem(0,new ItemStack(Items.IRON_INGOT,2));s.machine.setItem(1,new ItemStack(CraftingRemainderFixture.ITEM,2));int[] calls={0};
            CraftingRemainderFixture.callback=stack->{calls[0]++;switch(action) {
                case "component" -> s.machine.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("changed"));
                case "count" -> s.machine.getItem(0).setCount(1);
                case "mask" -> s.machine.setSlotState(8,false);
                case "facing" -> h.getLevel().setBlock(s.pos,h.getLevel().getBlockState(s.pos).setValue(BlockStateProperties.ORIENTATION,FrontAndTop.NORTH_UP),2);
                case "remove" -> h.getLevel().removeBlockEntity(s.pos);
                case "replace" -> {h.getLevel().removeBlockEntity(s.pos);var replacement=new CrafterBlockEntity(s.pos,h.getLevel().getBlockState(s.pos));h.getLevel().setBlockEntity(replacement);replacement.setItem(0,new ItemStack(Items.IRON_INGOT));replacement.setItem(1,new ItemStack(CraftingRemainderFixture.ITEM));s.tick();}
                case "reload" -> {h.getLevel().getRecipeManager().replaceRecipes(List.of());GraphIndex.invalidate();}
                case "reenter" -> s.tick();
            }};
            var random=h.getLevel().random;random.setSeed(517);long expected=random.nextLong();random.setSeed(517);s.tick();
            check(calls[0]==1,"Actual remainder callback/reentry runs only once for "+action);
            if(action.equals("reenter"))check(s.machine.getItem(0).getCount()==1&&s.machine.getItem(1).getCount()==1,"Reentry cannot commit second round");
            else check(random.nextLong()==expected&&s.count(Items.DIAMOND)==0&&s.machine.getItem(1).getCount()==2,"Callback "+action+" invalidates before RNG/consumption/delivery");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void realOutputCodecRunsInsideBaselineAndCannotAuthorizeMutatedGrid(GameTestHelper h) {
        try(var s=new Session(h,graph(SINGLE,"[{\"item\":\"minecraft:diamond\",\"chance\":0.5,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"callback\"}}]",true))) {
            s.machine.setItem(0,new ItemStack(Items.IRON_INGOT,2));int[] calls={0};
            FurnaceCodecFixture.decode=()->{calls[0]++;s.machine.setSlotState(8,false);s.tick();};
            var random=h.getLevel().random;random.setSeed(518);long expected=random.nextLong();random.setSeed(518);s.tick();
            check(calls[0]>0&&s.machine.isSlotDisabled(8),"Real registered output component codec executed");
            check(random.nextLong()==expected&&s.machine.getItem(0).getCount()==2&&s.chest.isEmpty(),"Baseline precedes index codec and blocks stale execution/RNG");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeCrafterMenuPreviewCannotBeExtractedAndNeverConsumesRng(GameTestHelper h) {
        try(var s=new Session(h,graph(SINGLE,"[{\"item\":\"minecraft:diamond\",\"chance\":0.5}]",false))) {
            s.machine.setItem(0,new ItemStack(Items.IRON_INGOT,2));
            var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"crafter-menu"));
            var menu=(net.minecraft.world.inventory.CrafterMenu)s.machine.createMenu(22,player.getInventory(),player);player.containerMenu=menu;
            var result=menu.getSlot(45);check(Workpiece.has(result.getItem()),"Real native crafter menu synchronizes shared graph carrier preview");
            var random=h.getLevel().random;random.setSeed(519);long expected=random.nextLong();random.setSeed(519);
            for(int i=0;i<5;i++){menu.slotChanged(menu,0,s.machine.getItem(0));menu.broadcastChanges();}
            check(result.remove(64).isEmpty()&&menu.quickMoveStack(player,45).isEmpty(),"Direct remove and quickMove cannot extract graph preview");
            menu.clicked(45,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
            check(player.getInventory().isEmpty()&&menu.getCarried().isEmpty()&&s.machine.getItem(0).getCount()==2&&random.nextLong()==expected,"Repeated native preview/click consumes neither ingredients nor RNG");
            menu.setSlotState(8,false);check(s.machine.isSlotDisabled(8),"Native menu disabled-slot UI remains linked to BE data");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void twoMachineWorkpiecesInheritOnlyPrimaryAndRejectInvalidAuxiliary(GameTestHelper h) {
        var g=new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"body","start":true,"rewards":{"quality":3},"recipe":{"type":"create:pressing"}},
              {"id":"insert","start":true,"ingredient":{"item":"minecraft:copper_ingot"},"rewards":{"quality":99},"recipe":{"type":"create:cutting"}},
              {"id":"join","checkpoint":true,"recipe":{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_nugget"},{"item":"minecraft:iron_nugget"}],"result":{"id":"minecraft:iron_nugget"}},
               "workpiece_inputs":[{"item_input":0,"current_nodes":["body"]},{"item_input":1,"current_nodes":["insert"]}]},
              {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
             "edges":[{"from":"body","to":"join"},{"from":"insert","to":"join"},{"from":"join","to":"finish"}],
             "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
            """).getAsJsonObject());
        try(var s=new Session(h,g)) {
            var definition=g.definition("test:crafter_contract");
            var main=Workpiece.write(new ItemStack(Items.IRON_NUGGET),dev.graphassembly.core.GraphEngine.complete(definition,null,"body","minecraft:iron_ingot",()->0).state());
            var auxiliary=Workpiece.write(new ItemStack(Items.IRON_NUGGET),dev.graphassembly.core.GraphEngine.complete(definition,null,"insert","minecraft:copper_ingot",()->0).state());
            s.machine.setItem(7,main.copy());s.machine.setItem(1,new ItemStack(Items.IRON_NUGGET));s.tick();
            check(s.machine.getItem(7).getCount()==1&&s.machine.getItem(1).getCount()==1&&s.chest.isEmpty(),"Declared secondary workpiece cannot be replaced by ordinary nugget");
            s.machine.setItem(1,auxiliary);s.tick();var output=s.chest.getItem(0);var progress=Workpiece.read(h.getLevel(),output);
            check(progress!=null&&progress.current().equals("join")&&progress.marks().get("quality")==3&&!progress.history().contains("insert"),"Machine output inherits only logical primary progress, not auxiliary reward/history");
            check(s.machine.isEmpty()&&output.getCount()==1,"Native machine consumes both distinct bound cells once");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=80,batch="crafter_pending_reload")
    public static void nativeSavedNewEntityResumesPendingPulseAndRebindsContinuation(GameTestHelper h) {
        var json=graph(SINGLE,"[{\"item\":\"minecraft:diamond\"}]",false).json();
        json.getAsJsonArray("nodes").add(JsonParser.parseString("""
            {"id":"next","recipe":{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_nugget"}],"result":{"id":"minecraft:iron_nugget"}}}
            """));
        json.add("edges",JsonParser.parseString("[{\"from\":\"s\",\"to\":\"next\"},{\"from\":\"next\",\"to\":\"end\"}]"));
        var s=new Session(h,new GraphRecipe(json));
        s.machine.setItem(0,new ItemStack(Items.IRON_INGOT));s.machine.setSlotState(8,false);s.pulse();
        var level=h.getLevel();var tag=s.machine.saveWithFullMetadata(level.registryAccess());
        level.removeBlockEntity(s.pos);var loaded=(CrafterBlockEntity)BlockEntity.loadStatic(s.pos,level.getBlockState(s.pos),tag,level.registryAccess());level.setBlockEntity(loaded);
        check(loaded!=s.machine&&loaded.isTriggered()&&loaded.isSlotDisabled(8),"Native inventory/disabled/trigger data restore into new block entity");
        s.later(12,()->{
            var carrier=s.chest.getItem(0).copy();var first=Workpiece.read(level,carrier);
            check(first!=null&&first.current().equals("s")&&loaded.isEmpty(),"Pending native scheduled pulse survives replacement BE and emits once");
            s.chest.clearContent();loaded.setItem(0,carrier);var workpieceTag=loaded.saveWithFullMetadata(level.registryAccess());
            level.removeBlockEntity(s.pos);var resumed=(CrafterBlockEntity)BlockEntity.loadStatic(s.pos,level.getBlockState(s.pos),workpieceTag,level.registryAccess());level.setBlockEntity(resumed);
            GraphIndex.invalidate();s.pulse();
            s.later(12,()->{var progress=Workpiece.read(level,s.chest.getItem(0));check(progress!=null&&progress.current().equals("next")&&resumed.isEmpty()&&s.chest.getItem(0).getCount()==1,"Persisted primary progress rebinds continuation on next distinct native pulse without retained plan");s.done();});
        });
    }
}
