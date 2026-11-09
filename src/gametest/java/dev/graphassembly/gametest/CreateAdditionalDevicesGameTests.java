package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllDataComponents;
import com.simibubi.create.AllItems;
import com.simibubi.create.content.equipment.sandPaper.SandPaperPolishingRecipe;
import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.*;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class CreateAdditionalDevicesGameTests {
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;
        final List<RecipeHolder<?>> saved;
        Session(GameTestHelper h) {helper=h;saved=List.copyOf(h.getLevel().getRecipeManager().getRecipes());}
        void install(String type,boolean sequence) {
            String json=sequence ? """
                {"type":"create:sequenced_assembly","ingredient":{"item":"minecraft:iron_ingot"},
                 "transitional_item":{"id":"minecraft:iron_ingot"},"loops":2,
                 "sequence":[{"type":"graph_assembly:sequence_step","step":{"recipe":{"type":"%s"}}}],
                 "results":[{"id":"minecraft:diamond"}]}
                """.formatted(type) : """
                {"type":"graph_assembly:assembly","ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_ingot",
                 "nodes":[{"id":"s","start":true,"recipe":{"type":"%s"}},
                          {"id":"f","finish":true,"recipe":{"type":"%s"}}],"edges":[{"from":"s","to":"f"}],
                 "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """.formatted(type,type);
            var recipe=Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(json)).getOrThrow();
            helper.getLevel().getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:extra_devices"),recipe)));
            GraphIndex.invalidate();
        }
        @Override public void close(){helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
    private static ItemStack fan(GameTestHelper h,ItemStack input,FanProcessingType type){
        var transported=new TransportedItemStack(input.copy());
        for(int i=0;i<1000;i++){
            var result=FanProcessing.applyProcessing(transported,h.getLevel(),type);
            if(!result.doesNothing()&&!result.getOutputs().isEmpty())return result.getOutputs().getFirst().stack;
        }
        throw new AssertionError("Fan did not finish "+type+" for "+input);
    }
    private static void fanCase(GameTestHelper h,String serializer,FanProcessingType type,boolean sequence){
        try(var session=new Session(h)){
            session.install(serializer,sequence);
            var raw=new ItemStack(Items.IRON_INGOT);
            raw.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("kept"));
            var middle=fan(h,raw,type);
            check(middle.is(Items.IRON_INGOT),"Expected intermediate workpiece");
            check(middle.has(AllDataComponents.SEQUENCED_ASSEMBLY)==sequence && Workpiece.has(middle)!=sequence,"Exactly one progress owner");
            if(!sequence)check(middle.getHoverName().getString().equals("kept"),"Graph same-item carrier retains components");
            var output=fan(h,middle,type);
            check(output.is(Items.DIAMOND)&&output.getCount()==1&&!Workpiece.has(output)&&!output.has(AllDataComponents.SEQUENCED_ASSEMBLY),"Exactly one final output without progress");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="extra_create_devices") public static void washingGraph(GameTestHelper h){fanCase(h,"create:splashing",AllFanProcessingTypes.SPLASHING,false);}
    @GameTest(template="test_empty",batch="extra_create_devices") public static void washingNativeSequence(GameTestHelper h){fanCase(h,"create:splashing",AllFanProcessingTypes.SPLASHING,true);}
    @GameTest(template="test_empty",batch="extra_create_devices") public static void hauntingGraph(GameTestHelper h){fanCase(h,"create:haunting",AllFanProcessingTypes.HAUNTING,false);}
    @GameTest(template="test_empty",batch="extra_create_devices") public static void hauntingNativeSequence(GameTestHelper h){fanCase(h,"create:haunting",AllFanProcessingTypes.HAUNTING,true);}
    private static void sandCase(GameTestHelper h,boolean sequence){
        try(var session=new Session(h)){
            session.install("create:sandpaper_polishing",sequence);
            var raw=new ItemStack(Items.IRON_INGOT);
            check(SandPaperPolishingRecipe.canPolish(h.getLevel(),raw),"Real sandpaper query must discover graph/native operation");
            var middle=SandPaperPolishingRecipe.applyPolish(h.getLevel(),Vec3.ZERO,raw,AllItems.SAND_PAPER.asStack());
            check(middle.has(AllDataComponents.SEQUENCED_ASSEMBLY)==sequence&&Workpiece.has(middle)!=sequence,"Sandpaper advances correct progress owner");
            var output=SandPaperPolishingRecipe.applyPolish(h.getLevel(),Vec3.ZERO,middle,AllItems.SAND_PAPER.asStack());
            check(output.is(Items.DIAMOND)&&output.getCount()==1,"Sandpaper returns parent output rather than preview placeholder");
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="extra_create_devices") public static void sandpaperGraph(GameTestHelper h){sandCase(h,false);}
    @GameTest(template="test_empty",batch="extra_create_devices") public static void sandpaperNativeSequence(GameTestHelper h){sandCase(h,true);}

    @GameTest(template="test_empty",batch="extra_create_devices")
    public static void playerSandpaperPaysDurabilityAndReturnsProgress(GameTestHelper h) {
        try(var session=new Session(h)) {
            session.install("create:sandpaper_polishing",false);
            var player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(),"sandpaper-test"));
            var paper=AllItems.SAND_PAPER.asStack();
            player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,paper);
            player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND,new ItemStack(Items.IRON_INGOT,2));
            paper.getItem().use(h.getLevel(),player,net.minecraft.world.InteractionHand.MAIN_HAND);
            check(player.getOffhandItem().getCount()==1,"Player starts by reserving exactly one input");
            paper.finishUsingItem(h.getLevel(),player);
            check(paper.getDamageValue()==1,"Native sanding pays one durability");
            int pieces=0;
            for(int slot=0;slot<player.getInventory().getContainerSize();slot++)if(Workpiece.has(player.getInventory().getItem(slot)))pieces++;
            check(pieces==1,"Actual sandpaper item delivers exactly one advanced workpiece");
            player.getInventory().clearContent();
        }
        h.succeed();
    }
    @GameTest(template="test_empty",batch="extra_create_devices")
    public static void sandpaperDeployerExecutesAndRejectsChangedHeldTool(GameTestHelper h) {
        var deployerPos=new net.minecraft.core.BlockPos(1,3,1);
        var depotPos=new net.minecraft.core.BlockPos(1,1,1);
        try(var session=new Session(h)) {
            session.install("create:sandpaper_polishing",true);
            h.setBlock(deployerPos,com.simibubi.create.AllBlocks.DEPLOYER.getDefaultState());
            h.setBlock(depotPos,com.simibubi.create.AllBlocks.DEPOT.getDefaultState());
            var deployer=(com.simibubi.create.content.kinetics.deployer.DeployerBlockEntity)h.getBlockEntity(deployerPos);deployer.initialize();
            var depot=(com.simibubi.create.content.logistics.depot.DepotBlockEntity)h.getBlockEntity(depotPos);depot.initialize();
            var paper=AllItems.SAND_PAPER.asStack();
            deployer.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,paper);
            var input=new ItemStack(Items.IRON_INGOT);
            var selected=deployer.getRecipe(input);
            check(selected!=null,"Deployer with sandpaper finds native sequence wrapper");
            var transported=new TransportedItemStack(input);
            depot.getBehaviour(com.simibubi.create.content.logistics.depot.DepotBehaviour.TYPE).setHeldItem(transported);
            deployer.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,new ItemStack(Items.STICK));
            com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks.activate(transported,depot.getBehaviour(com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE),deployer,selected.value());
            check(ItemStack.matches(input,depot.getHeldItem()),"Changing tool after selection cannot pay/advance");
            deployer.getPlayer().setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,paper);
            selected=deployer.getRecipe(input);
            com.simibubi.create.content.kinetics.deployer.BeltDeployerCallbacks.activate(transported,depot.getBehaviour(com.simibubi.create.content.kinetics.belt.behaviour.TransportedItemStackHandlerBehaviour.TYPE),deployer,selected.value());
            check(depot.getHeldItem().has(AllDataComponents.SEQUENCED_ASSEMBLY)&&paper.getDamageValue()==1,"Actual deployer advances native sequence and damages sandpaper once");
        } finally { h.getLevel().removeBlock(h.absolutePos(deployerPos),false);h.getLevel().removeBlock(h.absolutePos(depotPos),false); }
        h.succeed();
    }

    @GameTest(template="test_empty",batch="extra_create_devices")
    public static void zeroDurationCrushingUsesNativeTimingInsteadOfDisablingGraph(GameTestHelper h) throws Exception {
        var pos=h.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
        try(var session=new Session(h)) {
            session.install("create:crushing",false);
            check(GraphIndex.get(h.getLevel()).graphs().containsKey("test:extra_devices"),"Native default zero duration remains admitted");
            h.getLevel().setBlockAndUpdate(pos,com.simibubi.create.AllBlocks.CRUSHING_WHEEL_CONTROLLER.getDefaultState());
            var crusher=(com.simibubi.create.content.kinetics.crusher.CrushingWheelControllerBlockEntity)h.getLevel().getBlockEntity(pos);crusher.initialize();
            crusher.inventory.setStackInSlot(0,new ItemStack(Items.IRON_INGOT));crusher.crushingspeed=1;
            crusher.tick();
            check(crusher.inventory.appliedRecipe&&Workpiece.has(crusher.inventory.getStackInSlot(1)),"Native zero-duration tick pays and advances once");
        } finally {h.getLevel().removeBlock(pos,false);}
        h.succeed();
    }
}
