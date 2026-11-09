package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class CrusherDrainGameTests {
    private static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void actualCrusherAdvancesGraphWithoutDestroyingBatch(GameTestHelper h) throws Exception {
        var saved=List.copyOf(h.getLevel().getRecipeManager().getRecipes());
        var pos=h.absolutePos(new BlockPos(1,1,1));
        try {
            String json="""
                {"type":"graph_assembly:assembly","ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_ingot",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"create:crushing","processing_time":40}},
                          {"id":"f","finish":true,"recipe":{"type":"create:crushing","processing_time":40}}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """;
            var recipe=Recipe.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(json)).getOrThrow();
            h.getLevel().getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:crusher_drain"),recipe)));
            GraphIndex.invalidate();
            h.getLevel().setBlockAndUpdate(pos,AllBlocks.CRUSHING_WHEEL_CONTROLLER.getDefaultState());
            var crusher=(CrushingWheelControllerBlockEntity)h.getLevel().getBlockEntity(pos);
            crusher.initialize();
            crusher.inventory.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,3));
            var apply=CrushingWheelControllerBlockEntity.class.getDeclaredMethod("applyRecipe");apply.setAccessible(true);apply.invoke(crusher);
            int count=0;
            for(int i=0;i<crusher.inventory.getSlots();i++){
                var out=crusher.inventory.getStackInSlot(i);
                if(Workpiece.has(out))count+=out.getCount();
            }
            check(count==3,"Actual crusher must preserve and advance each batch item, not destroy raw graph inputs");
        } finally {
            h.getLevel().removeBlock(pos,false);
            h.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();
        }
        h.succeed();
    }
    private static final class Session implements AutoCloseable {
        final GameTestHelper h; final List<RecipeHolder<?>> saved; final BlockPos pos;
        Session(GameTestHelper h){this.h=h;saved=List.copyOf(h.getLevel().getRecipeManager().getRecipes());pos=h.absolutePos(new BlockPos(1,1,1));}
        void install(String type,boolean sequence){
            String step=type.equals("create:emptying")?"{\"type\":\"create:emptying\",\"results\":[{\"id\":\"minecraft:water\",\"amount\":250}]}":"{\"type\":\""+type+"\",\"processing_time\":40}";
            String json=sequence?"""
                {"type":"create:sequenced_assembly","ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":{"id":"minecraft:iron_ingot"},"loops":2,
                 "sequence":[{"type":"graph_assembly:sequence_step","step":{"recipe":%s}}],"results":[{"id":"minecraft:diamond"}]}
                """.formatted(step):"""
                {"type":"graph_assembly:assembly","ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_ingot",
                 "nodes":[{"id":"s","start":true,"recipe":%s},{"id":"f","finish":true,"recipe":%s}],
                 "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """.formatted(step,step);
            installJson(json);
        }
        void installJson(String json){
            var recipe=Recipe.CODEC.parse(h.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(json)).getOrThrow();
            h.getLevel().getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:crusher_drain"),recipe)));GraphIndex.invalidate();
        }
        CrushingWheelControllerBlockEntity crusher(){
            h.getLevel().setBlockAndUpdate(pos,AllBlocks.CRUSHING_WHEEL_CONTROLLER.getDefaultState());
            var be=(CrushingWheelControllerBlockEntity)h.getLevel().getBlockEntity(pos);be.initialize();return be;
        }
        com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity drain(){
            h.getLevel().setBlockAndUpdate(pos,AllBlocks.ITEM_DRAIN.getDefaultState());
            var be=(com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity)h.getLevel().getBlockEntity(pos);be.initialize();return be;
        }
        @Override public void close(){h.getLevel().removeBlock(pos,false);h.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void crush(CrushingWheelControllerBlockEntity be) throws Exception {
        var method=CrushingWheelControllerBlockEntity.class.getDeclaredMethod("applyRecipe");method.setAccessible(true);method.invoke(be);
    }
    private static void drainStep(com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity be,int ticks) throws Exception {
        var field=be.getClass().getDeclaredField("processingTicks");field.setAccessible(true);field.setInt(be,ticks);
        var method=be.getClass().getDeclaredMethod("continueProcessing");method.setAccessible(true);method.invoke(be);
    }
    private static com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour tank(com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity be) throws Exception {
        var field=be.getClass().getDeclaredField("internalTank");field.setAccessible(true);
        return (com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour)field.get(be);
    }
    private static void held(com.simibubi.create.content.fluids.drain.ItemDrainBlockEntity be,ItemStack stack){
        be.setHeldItem(new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(stack.copy()),net.minecraft.core.Direction.EAST);
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void actualDrainAdvancesGraphAndNativeLoops(GameTestHelper h) throws Exception {
        for(boolean sequence:List.of(false,true))try(var s=new Session(h)){
            s.install("create:emptying",sequence);var be=s.drain();
            held(be,new ItemStack(Items.IRON_INGOT));drainStep(be,19);drainStep(be,5);
            var middle=be.getHeldItemStack().copy();
            check(Workpiece.has(middle)!=sequence&&middle.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY)==sequence,"Actual drain advances exactly the selected progress owner");
            check(tank(be).getPrimaryHandler().getFluidInTank(0).getAmount()==250,"First drain pays fluid exactly once");
            held(be,middle);drainStep(be,19);drainStep(be,5);
            check(be.getHeldItemStack().is(Items.DIAMOND)&&be.getHeldItemStack().getCount()==1,"Second drain returns parent final, not template carrier");
            check(!Workpiece.has(be.getHeldItemStack())&&!be.getHeldItemStack().has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY),"Final drain result clears progress");
            check(tank(be).getPrimaryHandler().getFluidInTank(0).getAmount()==500,"Two operations produce two fluid outputs");
        }
        h.succeed();
    }
    private static void noRng(GameTestHelper h,String reason){check(h.getLevel().random.nextLong()==net.minecraft.util.RandomSource.create(8421).nextLong(),reason);}
    private static void fill(com.simibubi.create.foundation.blockEntity.behaviour.fluid.SmartFluidTankBehaviour tank,int amount,net.minecraft.world.level.material.Fluid fluid){
        tank.allowInsertion();try{tank.getPrimaryHandler().fill(new net.neoforged.neoforge.fluids.FluidStack(fluid,amount),net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);}finally{tank.forbidInsertion();}
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void drainRechecksFullAndIncompatibleTankAtFinalPayment(GameTestHelper h) throws Exception {
        try(var s=new Session(h)){
            s.install("create:emptying",false);var be=s.drain();var raw=new ItemStack(Items.IRON_INGOT);held(be,raw);
            drainStep(be,19);fill(tank(be),1500,net.minecraft.world.level.material.Fluids.WATER);
            h.getLevel().random.setSeed(8421);drainStep(be,5);
            check(ItemStack.matches(raw,be.getHeldItemStack()),"Full tank at tick 5 must preserve unpaid workpiece");noRng(h,"Full final tank must not draw RNG");
            tank(be).getPrimaryHandler().drain(1500,net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction.EXECUTE);
            fill(tank(be),1,net.minecraft.world.level.material.Fluids.LAVA);
            h.getLevel().random.setSeed(8421);drainStep(be,5);
            check(ItemStack.matches(raw,be.getHeldItemStack()),"Incompatible tank must preserve workpiece");noRng(h,"Incompatible final tank must not draw RNG");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void drainReloadAndInjectedStackCannotLoseOrConsumeItems(GameTestHelper h) throws Exception {
        try(var s=new Session(h)){
            s.install("create:emptying",false);var be=s.drain();held(be,new ItemStack(Items.IRON_INGOT));drainStep(be,19);
            s.install("create:emptying",false);h.getLevel().random.setSeed(8421);drainStep(be,5);
            check(!Workpiece.has(be.getHeldItemStack())&&tank(be).getPrimaryHandler().getFluidInTank(0).isEmpty(),"Recipe reload resets timer before payment");noRng(h,"Reload reset must not draw RNG");
            held(be,new ItemStack(Items.IRON_INGOT));drainStep(be,19);
            be.getHeldItemStack().set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("changed at payment"));
            h.getLevel().random.setSeed(8421);drainStep(be,5);
            check(!Workpiece.has(be.getHeldItemStack())&&tank(be).getPrimaryHandler().getFluidInTank(0).isEmpty(),"Held-item snapshot mutation resets payment timer");noRng(h,"Changed held snapshot must not roll");
            held(be,new ItemStack(Items.IRON_INGOT,3));h.getLevel().random.setSeed(8421);drainStep(be,5);
            check(be.getHeldItemStack().getCount()==3&&tank(be).getPrimaryHandler().getFluidInTank(0).isEmpty(),"Public direct injection must not drop excess raw inputs");noRng(h,"Injected oversized stack must not draw RNG");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void crusherNativeLoopsAndMillingFallbackUseParentCompletion(GameTestHelper h) throws Exception {
        for(String type:List.of("create:crushing","create:milling"))try(var s=new Session(h)){
            s.install(type,true);var be=s.crusher();be.inventory.setStackInSlot(0,new ItemStack(Items.IRON_INGOT));crush(be);
            var middle=be.inventory.getStackInSlot(1).copy();
            check(middle.has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY)&&!Workpiece.has(middle),"Crusher must advance native sequence for admitted family "+type);
            be.inventory.clear();be.inventory.setStackInSlot(0,middle);crush(be);
            check(be.inventory.getStackInSlot(1).is(Items.DIAMOND)&&!be.inventory.getStackInSlot(1).has(com.simibubi.create.AllDataComponents.SEQUENCED_ASSEMBLY),"Crusher final belongs only to native parent");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void crusherReservesOutputBeforeRngAndBoundsLargeUnstackableBatch(GameTestHelper h) throws Exception {
        try(var s=new Session(h)){
            s.install("create:crushing",false);var be=s.crusher();be.inventory.setStackInSlot(0,new ItemStack(Items.IRON_INGOT,64));
            for(int i=1;i<32;i++)be.inventory.setStackInSlot(i,new ItemStack(Items.STICK));
            h.getLevel().random.setSeed(8421);crush(be);
            check(be.inventory.getStackInSlot(0).getCount()==64,"Full crusher output preserves complete raw batch");noRng(h,"Full crusher output must not roll");
            be.crushingspeed=1;be.tick();be.inventory.remainingTime=1;be.tick();
            check(!be.inventory.appliedRecipe&&be.inventory.getStackInSlot(0).getCount()==64,"Host tick must not mark blocked payment applied");
            for(int i=1;i<32;i++)be.inventory.setStackInSlot(i,ItemStack.EMPTY);
            crush(be);int raw=be.inventory.getStackInSlot(0).getCount();int processed=0;
            for(int i=1;i<32;i++){var out=be.inventory.getStackInSlot(i);if(Workpiece.has(out))processed+=out.getCount();}
            check(processed==31&&raw==33,"Large unstackable batch produces bounded progress and retains raw remainder without loss");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void wrongTrackedStepCannotUseCrusherOrFluidCapabilityFallback(GameTestHelper h) throws Exception {
        try(var s=new Session(h)){
            s.install("create:crushing",false);var be=s.crusher();be.inventory.setStackInSlot(0,new ItemStack(Items.IRON_INGOT));crush(be);
            var tracked=be.inventory.getStackInSlot(1).copy();
            s.install("create:pressing",false);be.inventory.clear();be.inventory.setStackInSlot(0,tracked.copy());h.getLevel().random.setSeed(8421);crush(be);
            check(ItemStack.matches(tracked,be.inventory.getStackInSlot(0)),"Wrong tracked crusher step must never be destroyed");noRng(h,"Wrong tracked crusher must not draw RNG");
            tracked=Workpiece.write(new ItemStack(Items.WATER_BUCKET),Workpiece.read(h.getLevel(),tracked));
            check(!com.simibubi.create.content.fluids.transfer.GenericItemEmptying.canItemBeEmptied(h.getLevel(),tracked),"Wrong tracked step must not use generic capability fallback");
            var before=tracked.copy();com.simibubi.create.content.fluids.transfer.GenericItemEmptying.emptyItem(h.getLevel(),tracked,false);
            check(ItemStack.matches(before,tracked),"Generic emptying must not strip or consume tracked progress");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void drainAdmitsSingleStackSettlementButRejectsMultiKindBeforeRng(GameTestHelper h) throws Exception {
        for(boolean multi:List.of(false,true))try(var s=new Session(h)){
            String results=multi?"{\"item\":\"minecraft:diamond\"},{\"item\":\"minecraft:gold_ingot\"}":"{\"item\":\"minecraft:diamond\",\"count\":2}";
            s.installJson("""
                {"type":"graph_assembly:assembly","ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_ingot",
                 "nodes":[{"id":"f","start":true,"finish":true,"recipe":{"type":"create:emptying","results":[{"id":"minecraft:water","amount":250}]}}],
                 "edges":[],"outputs":[{"results":[{"mode":"all","results":[%s]}]}]}
                """.formatted(results));
            var be=s.drain();held(be,new ItemStack(Items.IRON_INGOT));drainStep(be,19);h.getLevel().random.setSeed(8421);drainStep(be,5);
            if(multi){check(be.getHeldItemStack().is(Items.IRON_INGOT)&&tank(be).getPrimaryHandler().getFluidInTank(0).isEmpty(),"Unsupported multi-kind output must not enter ordinary emptying: held="+be.getHeldItemStack()+", tank="+tank(be).getPrimaryHandler().getFluidInTank(0));noRng(h,"Unsupported settlement must not roll");}
            else check(be.getHeldItemStack().is(Items.DIAMOND)&&be.getHeldItemStack().getCount()==2,"Native drain can carry one valid stack containing two final items");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void drainNativeAdmissionSplitsOneAndTicksThroughRealFlow(GameTestHelper h) throws Exception {
        try(var s=new Session(h)){
            s.install("create:emptying",false);var be=s.drain();
            var transported=new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(new ItemStack(Items.IRON_INGOT,3));
            var insert=be.getClass().getDeclaredMethod("tryInsertingFromSide",transported.getClass(),net.minecraft.core.Direction.class,boolean.class);insert.setAccessible(true);
            var returned=(ItemStack)insert.invoke(be,transported,net.minecraft.core.Direction.EAST,false);
            check(returned.getCount()==2&&be.getHeldItemStack().getCount()==1,"Native admission accepts one unit and returns complete raw remainder");
            for(int i=0;i<19;i++)be.tick();
            check(Workpiece.has(be.getHeldItemStack()),"Real native movement and twenty-tick processing path must advance graph");
            check(tank(be).getPrimaryHandler().getFluidInTank(0).getAmount()==250,"Real native drain tick deposits fluid once");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void crusherRecipeReloadRestartsNativeDurationButReplacementDoesNotInheritProtection(GameTestHelper h) throws Exception {
        try(var s=new Session(h)){
            s.install("create:crushing",false);var be=s.crusher();be.inventory.setStackInSlot(0,new ItemStack(Items.IRON_INGOT));be.crushingspeed=0;
            be.tick();check(be.inventory.remainingTime==40,"Crusher retains configured native processing duration");
            be.inventory.remainingTime=2;s.install("create:crushing",false);be.tick();
            check(be.inventory.remainingTime==40&&!be.inventory.appliedRecipe,"Reload invalidates in-flight recipe/timer identity");
            be.inventory.setStackInSlot(0,new ItemStack(Items.STICK));be.tick();crush(be);
            check(be.inventory.isEmpty(),"Unrelated replacement must not inherit stale graph protection and block native behavior");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void admissionRecipeRemovalCannotDestroyCrusherInputBeforeFirstTick(GameTestHelper h) throws Exception {
        try(var s=new Session(h)){
            s.install("create:crushing",false);var be=s.crusher();
            check(be.inventory.insertItem(0,new ItemStack(Items.IRON_INGOT),false).isEmpty(),"Native inventory admits one graph input");
            h.getLevel().getRecipeManager().replaceRecipes(List.of());GraphIndex.invalidate();
            h.getLevel().random.setSeed(8421);crush(be);
            check(be.inventory.getStackInSlot(0).is(Items.IRON_INGOT),"Removing recipe between native insertion and first tick must not destroy raw admitted graph input");
            noRng(h,"Removed admitted recipe cannot roll");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="crusher_drain")
    public static void drainAdmissionRecipeRemovalCannotUseBucketFallbackBeforeFirstProcessingTick(GameTestHelper h) throws Exception {
        try(var s=new Session(h)){
            s.installJson("""
                {"type":"graph_assembly:assembly","ingredient":{"item":"minecraft:water_bucket"},"transitional_item":"minecraft:water_bucket",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"create:emptying","results":[{"id":"minecraft:water","amount":250}]}}],"edges":[]}
                """);
            var be=s.drain();var item=new com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack(new ItemStack(Items.WATER_BUCKET));
            item.beltPosition=.375F;be.setHeldItem(item,net.minecraft.core.Direction.EAST);be.tick();
            h.getLevel().getRecipeManager().replaceRecipes(List.of());GraphIndex.invalidate();
            h.getLevel().random.setSeed(8421);drainStep(be,5);
            check(be.getHeldItemStack().is(Items.WATER_BUCKET)&&tank(be).getPrimaryHandler().getFluidInTank(0).isEmpty(),"Removing admitted recipe cannot reopen ordinary bucket fallback before first processing tick");
            noRng(h,"Removed admitted drain recipe cannot roll");
        }
        h.succeed();
    }
}
