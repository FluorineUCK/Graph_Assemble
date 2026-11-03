package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.content.kinetics.crafter.*;
import com.simibubi.create.infrastructure.config.AllConfigs;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.GraphEngine;
import dev.graphassembly.recipe.*;
import net.createmod.catnip.math.Pointing;
import net.minecraft.core.*;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class MechanicalCrafterBridgeContractsGameTests {
    private static final ResourceLocation ID=ResourceLocation.parse("test:mechanical_contract");
    private static final String SINGLE="{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:amethyst_shard\"}],\"result\":{\"id\":\"minecraft:stone\"}}";
    private static GraphRecipe graph(String recipe,boolean finish,String results) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"s","start":true,"finish":%s,"finish_mode":"strict","recipe":%s}%s],"edges":%s,"outputs":%s}
            """.formatted(finish,recipe,finish?"":",{\"id\":\"f\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}",
                finish?"[]":"[{\"from\":\"s\",\"to\":\"f\"}]",results.equals("[]")?"[]":"[{\"results\":"+results+"}]")).getAsJsonObject());
    }
    private static final class Session implements AutoCloseable {
        final GameTestHelper helper;final List<RecipeHolder<?>> saved;final List<MechanicalCrafterBlockEntity> machines=new ArrayList<>();
        final BlockPos anchor;final AABB bounds;final MechanicalCrafterBlockEntity first,root;
        com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity motor;
        com.simibubi.create.content.kinetics.base.KineticBlockEntity cog;
        final List<BlockPos> powerBlocks=new ArrayList<>();
        Session(GameTestHelper helper,boolean two) {
            this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());anchor=helper.absolutePos(new BlockPos(2,1,2));bounds=new AABB(anchor).inflate(3);
            var base=AllBlocks.MECHANICAL_CRAFTER.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING,Direction.SOUTH).setValue(MechanicalCrafterBlock.POINTING,Pointing.DOWN);
            if(two) {
                // Match the root's proven real-client supply: two vertical crafters mesh with
                // a side Z-axis cog, powered axially by one creative motor. Never fake both
                // consumer speeds: native attachment can otherwise destroy a conflicted cog.
                root=place(anchor,base);first=place(anchor.above(),base);
                var cogPos=anchor.above().east();var motorPos=cogPos.north();
                helper.getLevel().setBlockAndUpdate(cogPos,AllBlocks.COGWHEEL.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.AXIS,Direction.Axis.Z));
                cog=(com.simibubi.create.content.kinetics.base.KineticBlockEntity)helper.getLevel().getBlockEntity(cogPos);cog.initialize();powerBlocks.add(cogPos);
                helper.getLevel().setBlockAndUpdate(motorPos,AllBlocks.CREATIVE_MOTOR.getDefaultState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.FACING,Direction.SOUTH));
                motor=(com.simibubi.create.content.kinetics.motor.CreativeMotorBlockEntity)helper.getLevel().getBlockEntity(motorPos);motor.initialize();motor.generatedSpeed.setValue(128);motor.updateGeneratedRotation();powerBlocks.add(0,motorPos);
                drive(40);
                check(!first.isRemoved()&&!root.isRemoved()&&Math.abs(first.getSpeed())==128&&Math.abs(root.getSpeed())==128&&first.getSpeed()==-root.getSpeed(),"Native motor/cog supply must establish stable opposite consumer rotations; "+diagnostic());
                check(RecipeGridHandler.getTargetingCrafter(first)==root,"Two-cell fixture must use actual native routing into the output crafter");
            } else {root=place(anchor,base);first=root;root.setSpeed(128);}
        }
        MechanicalCrafterBlockEntity place(BlockPos pos,net.minecraft.world.level.block.state.BlockState state){helper.getLevel().setBlockAndUpdate(pos,state);var machine=(MechanicalCrafterBlockEntity)helper.getLevel().getBlockEntity(pos);machine.initialize();machines.add(machine);return machine;}
        void install(RecipeHolder<?>... recipes){helper.getLevel().getRecipeManager().replaceRecipes(List.of(recipes));GraphIndex.invalidate();}
        void graph(GraphRecipe graph){install(new RecipeHolder<>(ID,graph));}
        RecipeHolder<?> ordinary(String id,String recipe){return new RecipeHolder<>(ResourceLocation.parse(id),Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(recipe)).getOrThrow());}
        void insert(ItemStack main,ItemStack auxiliary){check(first.getInventory().insertItem(0,main,false).isEmpty(),"Native first-cell insertion must accept exactly its supplied unit");if(first!=root)check(root.getInventory().insertItem(0,auxiliary,false).isEmpty(),"Native second-cell insertion must accept exactly its supplied unit");}
        void drive(int ticks){for(int tick=0;tick<ticks;tick++){if(motor!=null){motor.tick();cog.tick();}else root.setSpeed(128);for(var machine:machines)machine.tick();}}
        List<ItemEntity> drops(){return helper.getLevel().getEntitiesOfClass(ItemEntity.class,bounds);}
        int count(Item item){return drops().stream().map(ItemEntity::getItem).filter(stack->stack.is(item)).mapToInt(ItemStack::getCount).sum();}
        List<ItemStack> pending(){var pending=new ArrayList<ItemStack>();for(var machine:machines){var tag=machine.saveWithoutMetadata(helper.getLevel().registryAccess());for(var entry:tag.getCompound("GroupedItems").getList("Grid",10)){var stack=ItemStack.parseOptional(helper.getLevel().registryAccess(),((net.minecraft.nbt.CompoundTag)entry).getCompound("item"));if(!stack.isEmpty())pending.add(stack);}if(!machine.getInventory().isEmpty())pending.add(machine.getInventory().getStackInSlot(0).copy());}return pending;}
        void clearDrops(){drops().forEach(ItemEntity::discard);}
        String diagnostic(){return "drops="+drops().stream().map(entity->entity.getItem().toString()).toList()+" pending="+pending()+" machines="+machines.stream().map(machine->"pos="+machine.getBlockPos()+" removed="+machine.isRemoved()+" speed="+machine.getSpeed()+" world="+helper.getLevel().getBlockState(machine.getBlockPos())+" nbt="+machine.saveWithoutMetadata(helper.getLevel().registryAccess())).toList();}
        @Override public void close(){FurnaceCodecFixture.decode=()->{};CraftingRemainderFixture.callback=stack->{};for(var pos:powerBlocks)helper.getLevel().removeBlock(pos,false);for(var machine:machines)helper.getLevel().removeBlock(machine.getBlockPos(),false);clearDrops();helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean condition,String message){if(!condition)throw new GameTestAssertException(message);}

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void actualTwoCellGeometryAndDualWorkpieceRolesBindWithoutHistoryMerge(GameTestHelper helper) {
        try(var s=new Session(helper,true)) {
            s.graph(graph("{\"type\":\"minecraft:crafting_shaped\",\"pattern\":[\"A\",\"B\"],\"key\":{\"A\":{\"item\":\"minecraft:amethyst_shard\"},\"B\":{\"item\":\"minecraft:copper_ingot\"}},\"result\":{\"id\":\"minecraft:stone\"}}",false,"[]"));
            s.insert(new ItemStack(Items.AMETHYST_SHARD),new ItemStack(Items.COPPER_INGOT));s.drive(120);
            check(s.drops().size()==1&&s.count(Items.IRON_NUGGET)==1&&Workpiece.read(helper.getLevel(),s.drops().getFirst().getItem()).current().equals("s")&&s.pending().isEmpty(),"Actual native two-crafter chain must preserve shaped grid geometry and pay each cell once; "+s.diagnostic());
        }
        try(var s=new Session(helper,true)) {
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"body","start":true,"rewards":{"quality":3},"recipe":{"type":"create:pressing"}},
                   {"id":"insert","start":true,"ingredient":{"item":"minecraft:copper_ingot"},"rewards":{"quality":99},"recipe":{"type":"create:cutting"}},
                   {"id":"join","checkpoint":true,"recipe":{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_nugget"},{"item":"minecraft:iron_nugget"}],"result":{"id":"minecraft:stone"}},
                    "workpiece_inputs":[{"item_input":0,"current_nodes":["body"]},{"item_input":1,"current_nodes":["insert"]}]},
                   {"id":"f","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"body","to":"join"},{"from":"insert","to":"join"},{"from":"join","to":"f"}]}
                """).getAsJsonObject());s.graph(graph);var definition=graph.definition(ID.toString());
            var main=Workpiece.write(new ItemStack(Items.IRON_NUGGET),GraphEngine.complete(definition,null,"body","minecraft:amethyst_shard",()->0).state());
            var auxiliary=Workpiece.write(new ItemStack(Items.IRON_NUGGET),GraphEngine.complete(definition,null,"insert","minecraft:copper_ingot",()->0).state());
            s.insert(auxiliary,main);s.drive(120);
            check(s.drops().size()==1,"Dual workpiece native chain must export exactly one primary carrier");var progress=Workpiece.read(helper.getLevel(),s.drops().getFirst().getItem());
            check(progress!=null&&progress.current().equals("join")&&progress.marks().get("quality")==3&&!progress.checkpoints().contains("insert")&&s.pending().isEmpty(),"Physical cell order must not swap logical primary or merge auxiliary history/marks");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void completeBundlesContainerReturnAndStrictEmptyArePaidOnce(GameTestHelper helper) {
        try(var s=new Session(helper,true)) {
            var recipe="{\"type\":\"minecraft:crafting_shapeless\",\"ingredients\":[{\"item\":\"minecraft:amethyst_shard\"},{\"item\":\"minecraft:milk_bucket\"}],\"result\":{\"id\":\"minecraft:stone\"}}";
            s.graph(graph(recipe,true,"[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:emerald\",\"count\":3}]}]"));
            s.insert(new ItemStack(Items.AMETHYST_SHARD),new ItemStack(Items.MILK_BUCKET));s.drive(120);
            check(s.count(Items.DIAMOND)==2&&s.count(Items.EMERALD)==3&&s.count(Items.BUCKET)==1&&s.count(Items.STONE)==0&&s.pending().isEmpty(),"All native GroupedItems results and one bucket remainder must export exactly once without template reward or duplicate containers; "+s.diagnostic());
        }
        try(var s=new Session(helper,false)) {
            s.graph(graph(SINGLE,true,"[]"));s.insert(new ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY);s.drive(80);
            check(s.drops().isEmpty()&&s.pending().isEmpty()&&s.root.saveWithoutMetadata(helper.getLevel().registryAccess()).getString("Phase").equals("IDLE"),"STRICT empty graph settlement pays once and reaches native IDLE, never null-failure/raw ejection");
        }helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void originalReferencePolicyPendingConfigReloadAndOrdinaryCollisionRemainNative(GameTestHelper helper) {
        var config=AllConfigs.server().recipes.allowRegularCraftingInCrafter;boolean savedConfig=config.get();config.set(true);
        try {
            for(boolean blocked:List.of(true,false))try(var s=new Session(helper,false)) {
                String source=blocked?"test:mechanical_manual_only":"test:mechanical_allowed";var graphId=ResourceLocation.parse(blocked?"test:normal_mechanical_graph":"test:graph_manual_only");
                var graph=graph("{\"type\":\"minecraft:crafting_shapeless\",\"recipe_id\":\""+source+"\"}",false,"[]");s.install(new RecipeHolder<>(graphId,graph),s.ordinary(source,SINGLE));
                s.insert(new ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY);s.drive(80);
                if(blocked)check(s.count(Items.IRON_NUGGET)==0&&s.count(Items.STONE)==0&&s.count(Items.AMETHYST_SHARD)+s.pending().stream().filter(stack->stack.is(Items.AMETHYST_SHARD)).mapToInt(ItemStack::getCount).sum()==1,"Manual-only original reference cannot automate or lose raw input under a normal graph ID");
                else check(s.count(Items.IRON_NUGGET)==1&&Workpiece.read(helper.getLevel(),s.drops().getFirst().getItem()).graphId().equals(graphId.toString()),"Allowed original reference cannot be excluded merely by containing graph ID suffix");
            }
            try(var s=new Session(helper,false)) {
                var graph=new GraphRecipe(JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
                     "nodes":[{"id":"s","start":true,"recipe":{"type":"create:pressing"}},
                       {"id":"f","finish":true,"finish_mode":"strict","recipe":{"type":"minecraft:crafting_shapeless","ingredients":[{"item":"minecraft:iron_nugget"}],"result":{"id":"minecraft:stone"}}}],
                     "edges":[{"from":"s","to":"f"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                    """).getAsJsonObject());s.graph(graph);
                var carrier=Workpiece.write(new ItemStack(Items.IRON_NUGGET),GraphEngine.complete(graph.definition(ID.toString()),null,"s","minecraft:amethyst_shard",()->0).state());
                s.insert(carrier,ItemStack.EMPTY);config.set(false);s.drive(3);
                check(s.drops().isEmpty()&&s.pending().size()==1&&Workpiece.has(s.pending().getFirst()),"Existing progressing native job cannot bypass disabled config or fall back/eject");
                var replacement=graph.json();replacement.getAsJsonArray("outputs").get(0).getAsJsonObject().getAsJsonArray("results").get(0).getAsJsonObject().addProperty("item","minecraft:emerald");s.graph(new GraphRecipe(replacement));config.set(true);s.drive(80);
                check(s.count(Items.EMERALD)==1&&s.count(Items.DIAMOND)==0&&s.pending().isEmpty(),"Restored native pending grid rebinds current definition, never cached old settlement");
            }
            try(var s=new Session(helper,false)) {
                var ordinary=s.ordinary("test:ordinary_mechanical",SINGLE.replace("minecraft:stone","minecraft:gold_ingot"));s.install(ordinary);s.insert(new ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY);s.drive(80);
                check(s.count(Items.GOLD_INGOT)==1&&!Workpiece.has(s.drops().getFirst().getItem()),"No graph leaves native ordinary mechanical crafting unchanged");
                s.install(ordinary,new RecipeHolder<>(ID,graph(SINGLE,false,"[]")));s.insert(new ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY);s.drive(80);
                check(s.count(Items.GOLD_INGOT)==1&&s.count(Items.IRON_NUGGET)==1,"Admitted graph entry wins a warmed ordinary crafting collision");
            }
        } finally {config.set(savedConfig);}
        helper.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100,batch="create_native_bridge")
    public static void finalMechanicalCodecInputConfigReloadReentryAndThrowKeepPendingAndReleaseScope(GameTestHelper helper) {
        var config=AllConfigs.server().recipes.allowRegularCraftingInCrafter;boolean savedConfig=config.get();
        try {
            for(String action:List.of("input","config","reload","reenter","throw"))try(var s=new Session(helper,false)) {
                config.set(true);var graph=graph(SINGLE,true,"[{\"item\":\"minecraft:diamond\",\"chance\":0.5,\"components\":{\"graph_assembly:furnace_codec_fixture\":\"mechanical\"}}]");s.graph(graph);GraphIndex.get(helper.getLevel());s.insert(new ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY);
                int[] calls={0};FurnaceCodecFixture.decode=()->{if(calls[0]++!=0)return;switch(action) {
                    case "input" -> {var saved=s.root.saveWithoutMetadata(helper.getLevel().registryAccess());saved.getCompound("GroupedItems").getList("Grid",10).getCompound(0).put("item",new ItemStack(Items.DIRT).save(helper.getLevel().registryAccess()));s.root.loadWithComponents(saved,helper.getLevel().registryAccess());}
                    case "config" -> config.set(false);
                    case "reload" -> s.graph(new GraphRecipe(graph.json()));
                    case "reenter" -> s.root.tick();
                    case "throw" -> throw new IllegalStateException("mechanical output codec fixture");
                }};
                var random=helper.getLevel().random;random.setSeed(42153);long expected=random.nextLong();random.setSeed(42153);
                // Native kinetic initialization may clear the first supplied speed. Wait for
                // the actual first preparation callback, then stop before any healthy retry.
                try {for(int tick=0;tick<40&&calls[0]==0;tick++)s.drive(1);}finally{FurnaceCodecFixture.decode=()->{};}
                check(calls[0]>0&&s.drops().isEmpty()&&s.pending().size()==1&&s.pending().getFirst().is(action.equals("input")?Items.DIRT:Items.AMETHYST_SHARD),"Real mechanical preparation "+action+" cannot consume/drop stale pending grid; calls="+calls[0]+" "+s.diagnostic());
                check(random.nextLong()==expected,"Real mechanical preparation "+action+" aborts before graph RNG");
                config.set(true);s.root.ejectWholeGrid();s.clearDrops();s.graph(graph(SINGLE,true,"[{\"item\":\"minecraft:diamond\"}]"));s.insert(new ItemStack(Items.AMETHYST_SHARD),ItemStack.EMPTY);s.drive(80);
                check(s.count(Items.DIAMOND)==1&&s.pending().isEmpty(),"Same native mechanical crafter recovers after failed preparation releases scope: "+action);
            }
        } finally {FurnaceCodecFixture.decode=()->{};config.set(savedConfig);}
        helper.succeed();
    }
}
