package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeFurnaceContractsGameTests {
    static final ResourceLocation ID=ResourceLocation.parse("test:furnace_contract");
    static String nativeRecipe(String type,int time){return """
        {"type":"minecraft:%s","ingredient":{"item":"minecraft:amethyst_shard"},"result":{"id":"minecraft:stone"},"cookingtime":%d,"experience":1}
        """.formatted(type,time);}
    static GraphRecipe graph(String type,int time,String results){return new GraphRecipe(JsonParser.parseString("""
        {"ingredient":{"item":"minecraft:amethyst_shard"},"transitional_item":"minecraft:iron_nugget",
         "nodes":[{"id":"s","start":true,"finish":true,"recipe":%s}],"edges":[],"outputs":[{"results":%s}]}
        """.formatted(nativeRecipe(type,time),results)).getAsJsonObject());}
    static final class Session implements AutoCloseable {
        final GameTestHelper helper; final List<RecipeHolder<?>> saved; final BlockPos pos;
        AbstractFurnaceBlockEntity furnace;
        Session(GameTestHelper helper){this.helper=helper;saved=new ArrayList<>(helper.getLevel().getRecipeManager().getRecipes());pos=helper.absolutePos(new BlockPos(1,1,1));}
        void install(RecipeHolder<?>...recipes){helper.getLevel().getRecipeManager().replaceRecipes(List.of(recipes));GraphIndex.invalidate();}
        void graph(GraphRecipe graph){install(new RecipeHolder<>(ID,graph));}
        void machine(net.minecraft.world.level.block.Block block){helper.getLevel().setBlockAndUpdate(pos,block.defaultBlockState());furnace=(AbstractFurnaceBlockEntity)helper.getLevel().getBlockEntity(pos);}
        void input(int count){furnace.setItem(0,new ItemStack(Items.AMETHYST_SHARD,count));furnace.setItem(1,new ItemStack(Items.COAL,2));}
        void tick(int count){for(int i=0;i<count;i++)AbstractFurnaceBlockEntity.serverTick(helper.getLevel(),pos,helper.getLevel().getBlockState(pos),furnace);}
        net.minecraft.nbt.CompoundTag nbt(){return furnace.saveWithoutMetadata(helper.getLevel().registryAccess());}
        @Override public void close(){helper.getLevel().removeBlock(pos,false);helper.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    static void check(boolean condition,String message){if(!condition)throw new AssertionError(message);}

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeTopUpPreservesGraphAndOrdinaryProgressAcrossUnrelatedIndexRefresh(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",6,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.FURNACE);s.input(1);
            s.tick(3);check(s.nbt().getInt("CookTime")==3,"Graph fixture must reach half cooking progress");
            s.furnace.getItem(0).grow(1);s.tick(3);
            check(s.furnace.getItem(2).is(Items.DIAMOND)&&s.furnace.getItem(0).getCount()==1,"Native same-stack top-up must not reset graph cook progress");
            s.furnace.setItem(2,ItemStack.EMPTY);s.furnace.setItem(0,ItemStack.EMPTY);
            var ordinary=Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(nativeRecipe("smelting",6))).getOrThrow();
            s.install(new RecipeHolder<>(ResourceLocation.parse("test:ordinary_cooking"),ordinary));s.input(1);s.tick(3);
            check(s.nbt().getInt("CookTime")==3,"Ordinary fixture must reach half cooking progress");
            GraphIndex.invalidate();s.furnace.getItem(0).grow(1);s.tick(3);
            check(s.furnace.getItem(2).is(Items.STONE)&&s.furnace.getItem(0).getCount()==1,"Ordinary native progress must ignore unrelated graph index rebuild and same-stack top-up");
        }
        helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void allThreeCookingMachinesPreserveNativeFuelDurationAndTime(GameTestHelper helper) {
        var blocks=List.of(Blocks.FURNACE,Blocks.SMOKER,Blocks.BLAST_FURNACE);var types=List.of("smelting","smoking","blasting");
        for(int i=0;i<3;i++)try(var s=new Session(helper)) {
            s.graph(graph(types.get(i),4,"[{\"item\":\"minecraft:diamond\",\"count\":2}]"));s.machine(blocks.get(i));s.input(2);
            s.tick(3);check(s.furnace.getItem(2).isEmpty(),"Cooking must not finish before native cookingtime on "+types.get(i));s.tick(1);
            check(s.furnace.getItem(2).is(Items.DIAMOND)&&s.furnace.getItem(2).getCount()==2&&s.furnace.getItem(0).getCount()==1,"Native cooking completion exactly once on "+types.get(i));
            check(s.nbt().getInt("BurnTime")== (i==0?1597:797),"Native smoker/blast fuel duration must remain half furnace duration");
            check(helper.getLevel().getBlockState(s.pos).getValue(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT),"Native lit blockstate must be maintained");
            check(s.nbt().getCompound("RecipesUsed").isEmpty(),"Graph IDs must never enter native cooking recipe XP casts");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void outputBundlesCapacityAndComponentsBlockBeforeFuelAndRng(GameTestHelper helper) {
        var bundles=List.of("[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\"},{\"item\":\"minecraft:emerald\"}]}]",
            "[{\"item\":\"minecraft:diamond\",\"count\":2}]","[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"chance\":0.5},{\"item\":\"minecraft:emerald\"}]}]");
        for(int i=0;i<bundles.size();i++)try(var s=new Session(helper)) {
            s.graph(graph("smelting",2,bundles.get(i)));s.machine(Blocks.FURNACE);s.input(2);
            if(i==1)s.furnace.setItem(2,new ItemStack(Items.DIAMOND,63));
            var random=helper.getLevel().random;random.setSeed(2701);long expected=random.nextLong();random.setSeed(2701);s.tick(8);
            check(random.nextLong()==expected,"Blocked output preflight must consume no world RNG");
            check(s.furnace.getItem(0).getCount()==2&&s.furnace.getItem(1).getCount()==2&&s.nbt().getInt("BurnTime")==0,"Impossible simultaneous/overflow/random bundle blocks before input and fuel consumption");
        }
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",2,"[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\",\"count\":2},{\"item\":\"minecraft:diamond\",\"count\":3}]}]"));s.machine(Blocks.FURNACE);s.input(1);s.furnace.setItem(2,new ItemStack(Items.DIAMOND,59));s.tick(2);
            check(s.furnace.getItem(2).getCount()==64&&s.furnace.getItem(0).isEmpty(),"Same-kind simultaneous results merge to exact one-slot capacity");
        }
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",2,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.FURNACE);s.input(1);
            var named=new ItemStack(Items.DIAMOND);named.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("different"));s.furnace.setItem(2,named);s.tick(3);
            check(s.furnace.getItem(0).getCount()==1&&s.furnace.getItem(1).getCount()==2&&s.furnace.getItem(2).getHoverName().getString().equals("different"),"Same item with distinct components cannot merge or ignite");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void emptyAndRandomResultsAreSingleRoundsWithoutNativePlaceholder(GameTestHelper helper) {
        for(String outputs:List.of("[{\"item\":\"minecraft:diamond\",\"chance\":0}]","[{\"item\":\"minecraft:diamond\",\"chance\":0.5}]"))try(var s=new Session(helper)) {
            s.graph(graph("smelting",2,outputs));s.machine(Blocks.FURNACE);s.input(1);s.tick(2);
            check(s.furnace.getItem(0).isEmpty()&&s.furnace.getItem(1).getCount()==1,"Empty/random graph result consumes exactly one round");
            check(s.furnace.getItem(2).isEmpty()||s.furnace.getItem(2).is(Items.DIAMOND),"Native stone placeholder must never be delivered");
            if(outputs.contains("chance\":0}"))check(s.furnace.getItem(2).isEmpty(),"Zero-chance graph output stays empty");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void lavaRemainderWetSpongeAndCoolingRemainNative(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var json=graph("smelting",2,"[{\"item\":\"minecraft:diamond\"}]").json();json.add("ingredient",JsonParser.parseString("{\"item\":\"minecraft:wet_sponge\"}"));
            s.graph(new GraphRecipe(json));s.machine(Blocks.FURNACE);s.furnace.setItem(0,new ItemStack(Items.WET_SPONGE));s.furnace.setItem(1,new ItemStack(Items.LAVA_BUCKET));s.tick(2);
            check(s.furnace.getItem(2).is(Items.DIAMOND)&&s.furnace.getItem(1).is(Items.WATER_BUCKET),"Native lava remainder becomes bucket and wet sponge fills that bucket");
        }
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",120,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.FURNACE);s.input(1);s.furnace.setItem(1,new ItemStack(Items.STICK));s.tick(100);
            int before=s.nbt().getInt("CookTime");s.tick(1);
            check(s.furnace.getItem(0).getCount()==1&&s.furnace.getItem(2).isEmpty()&&s.nbt().getInt("CookTime")==before-2,"Burnout without new fuel cools by native two ticks and consumes no graph input");
            s.furnace.setItem(1,new ItemStack(Items.COAL));s.tick(22);
            check(s.furnace.getItem(0).isEmpty()&&s.furnace.getItem(2).is(Items.DIAMOND),"Remaining native progress resumes after refuel");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void silentComponentSwapAndSameIdGraphReloadRestartProgress(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var g=graph("smelting",6,"[{\"item\":\"minecraft:diamond\"}]");s.graph(g);s.machine(Blocks.FURNACE);s.input(1);s.tick(3);
            s.furnace.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("silent swap"));s.tick(3);
            check(s.furnace.getItem(2).isEmpty()&&s.nbt().getInt("CookTime")==3,"Silent input component replacement cannot reuse old elapsed cooking time");
            s.install(new RecipeHolder<>(ID,new GraphRecipe(g.json())));s.tick(3);
            check(s.furnace.getItem(2).isEmpty()&&s.nbt().getInt("CookTime")==3,"Same-ID identical-content holder reload still restarts live operation");s.tick(3);
            check(s.furnace.getItem(2).is(Items.DIAMOND),"Unchanged replacement operation may complete its own full time");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void savedNewBlockEntityResumesSameUnchangedGraphAndNativeExperience(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",6,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.FURNACE);s.input(1);s.tick(3);
            var old=s.furnace;var tag=old.saveWithFullMetadata(helper.getLevel().registryAccess());
            helper.getLevel().removeBlockEntity(s.pos);
            s.furnace=(AbstractFurnaceBlockEntity)BlockEntity.loadStatic(s.pos,helper.getLevel().getBlockState(s.pos),tag,helper.getLevel().registryAccess());helper.getLevel().setBlockEntity(s.furnace);
            check(s.furnace!=old,"Reload fixture must be a new block entity instance");s.tick(3);
            check(s.furnace.getItem(2).is(Items.DIAMOND)&&s.furnace.getItem(0).isEmpty(),"Unchanged saved graph resumes elapsed cooking in a fresh BE");
            check(s.nbt().getDouble("GraphAssemblyCookingExperience")==1,"Successful graph XP persists independently of graph recipe IDs");
            var paid=s.furnace.saveWithFullMetadata(helper.getLevel().registryAccess());helper.getLevel().removeBlockEntity(s.pos);
            s.furnace=(AbstractFurnaceBlockEntity)BlockEntity.loadStatic(s.pos,helper.getLevel().getBlockState(s.pos),paid,helper.getLevel().registryAccess());helper.getLevel().setBlockEntity(s.furnace);
            s.furnace.getRecipesToAwardAndPopExperience(helper.getLevel(),s.pos.getCenter());check(s.nbt().getDouble("GraphAssemblyCookingExperience")==0,"Reloaded XP is paid and ledger cleared once");
            s.furnace.getRecipesToAwardAndPopExperience(helper.getLevel(),s.pos.getCenter());check(s.nbt().getDouble("GraphAssemblyCookingExperience")==0,"Second public XP payout cannot recreate ledger");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void oldOrdinarySaveWithoutGraphJobKeepsExistingCookTime(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var ordinary=Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(nativeRecipe("smelting",6))).getOrThrow();
            s.install(new RecipeHolder<>(ResourceLocation.parse("test:ordinary_saved"),ordinary));s.machine(Blocks.FURNACE);s.input(1);s.tick(3);
            var tag=s.furnace.saveWithFullMetadata(helper.getLevel().registryAccess());tag.remove("GraphAssemblyCookingJob");tag.remove("GraphAssemblyCookingExperience");helper.getLevel().removeBlockEntity(s.pos);
            s.furnace=(AbstractFurnaceBlockEntity)BlockEntity.loadStatic(s.pos,helper.getLevel().getBlockState(s.pos),tag,helper.getLevel().registryAccess());helper.getLevel().setBlockEntity(s.furnace);s.tick(3);
            check(s.furnace.getItem(0).isEmpty()&&s.furnace.getItem(2).is(Items.STONE),"Installing mod must not erase elapsed ordinary cooking from an old save lacking graph job metadata");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void actualHopperAndMenuMoveUnstackableCarriersAndPayGraphExperience(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var data=graph("smelting",2,"[{\"item\":\"minecraft:diamond\"}]").json();data.getAsJsonArray("nodes").get(0).getAsJsonObject().remove("finish");
            data.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"f\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}"));data.add("edges",JsonParser.parseString("[{\"from\":\"s\",\"to\":\"f\"}]"));
            s.graph(new GraphRecipe(data));s.machine(Blocks.FURNACE);s.input(2);s.tick(2);var carrier=s.furnace.getItem(2).copy();
            check(Workpiece.has(carrier)&&carrier.getMaxStackSize()==1,"Hopper fixture must contain an actual unstackable graph carrier");
            var below=s.pos.below();helper.getLevel().setBlockAndUpdate(below,Blocks.HOPPER.defaultBlockState());var hopper=(HopperBlockEntity)helper.getLevel().getBlockEntity(below);
            try {
                check(HopperBlockEntity.suckInItems(helper.getLevel(),hopper),"Actual hopper must extract furnace graph carrier");
                check(s.furnace.getItem(2).isEmpty()&&Workpiece.has(hopper.getItem(0)),"Actual hopper transfer preserves progress components");
                check(s.nbt().getDouble("GraphAssemblyCookingExperience")==1,"Hopper extraction accumulates XP rather than paying it");
                s.tick(2);
                var player=new net.neoforged.neoforge.common.util.FakePlayer(helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"furnace-menu"));
                var menu=s.furnace.createMenu(1,player.getInventory(),player);player.containerMenu=menu;menu.clicked(2,0,net.minecraft.world.inventory.ClickType.PICKUP,player);
                check(Workpiece.has(menu.getCarried())&&s.furnace.getItem(2).isEmpty(),"Real furnace menu extraction delivers one progress-bearing output");
                check(s.nbt().getDouble("GraphAssemblyCookingExperience")==0,"Manual output slot awards accumulated graph XP and clears once");
                menu.setCarried(ItemStack.EMPTY);player.getInventory().clearContent();
                s.furnace.setItem(0,ItemStack.EMPTY);var leftover=HopperBlockEntity.addItem(hopper,s.furnace,hopper.removeItem(0,1),net.minecraft.core.Direction.UP);
                check(leftover.isEmpty()&&Workpiece.has(s.furnace.getItem(0)),"Actual sided hopper insertion accepts unstackable carrier in input");
                check(!s.furnace.canPlaceItem(1,s.furnace.getItem(0)),"Progress carrier cannot be placed in fuel slot");
            }finally{helper.getLevel().removeBlock(below,false);}
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void fuelCallbacksCannotCommitStaleInventoryDefinitionOrDetachedMachine(GameTestHelper helper) {
        for(String phase:List.of("duration","remainder"))for(String action:List.of("input","output","fuel","reload","remove","replace","state","reenter"))try(var s=new Session(helper)) {
            var g=graph("smelting",1,"[{\"item\":\"minecraft:diamond\",\"chance\":0.5}]");s.graph(g);s.machine(Blocks.FURNACE);s.input(2);s.furnace.setItem(1,new ItemStack(FurnaceFuelFixture.ITEM,2));
            Runnable callback=()->{switch(action) {
                case "input" -> s.furnace.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("callback changed"));
                case "output" -> s.furnace.setItem(2,new ItemStack(Items.EMERALD));
                case "fuel" -> s.furnace.getItem(1).setCount(1);
                case "reload" -> s.install(new RecipeHolder<>(ID,new GraphRecipe(g.json())));
                case "remove" -> helper.getLevel().removeBlockEntity(s.pos);
                case "replace" -> {helper.getLevel().removeBlockEntity(s.pos);helper.getLevel().setBlockEntity(new FurnaceBlockEntity(s.pos,helper.getLevel().getBlockState(s.pos)));}
                case "state" -> helper.getLevel().setBlockAndUpdate(s.pos,helper.getLevel().getBlockState(s.pos).cycle(net.minecraft.world.level.block.AbstractFurnaceBlock.LIT));
                case "reenter" -> s.tick(1);
            }};
            if(phase.equals("duration"))FurnaceFuelFixture.duration=callback;else FurnaceFuelFixture.remainder=callback;
            var random=helper.getLevel().random;random.setSeed(3001);long expected=random.nextLong();random.setSeed(3001);
            try{s.tick(1);}finally{FurnaceFuelFixture.duration=()->{};FurnaceFuelFixture.remainder=()->{};}
            check(s.furnace.getItem(0).getCount()==2,"Fuel callback "+phase+"/"+action+" must not consume primary input");
            check(!s.furnace.getItem(2).is(Items.DIAMOND),"Fuel callback "+phase+"/"+action+" must not deliver stale graph output");
            // Changing a real blockstate can legitimately consume world RNG in level callbacks; other fixture actions cannot.
            if(!action.equals("state"))check(random.nextLong()==expected,"Fuel callback "+phase+"/"+action+" aborts before graph RNG");
            check(s.nbt().getDouble("GraphAssemblyCookingExperience")==0,"Aborted callback records no successful graph XP");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void fuelCallbackExceptionReleasesTickScopeForNextOperation(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",1,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.FURNACE);s.input(1);s.furnace.setItem(1,new ItemStack(FurnaceFuelFixture.ITEM));
            FurnaceFuelFixture.duration=()->{throw new IllegalStateException("intentional fuel fixture");};
            try{s.tick(1);throw new AssertionError("Fixture exception must reach caller");}catch(IllegalStateException expected){check(expected.getMessage().equals("intentional fuel fixture"),"Only the fixture exception is expected");}finally{FurnaceFuelFixture.duration=()->{};}
            s.furnace.setItem(1,new ItemStack(Items.COAL));s.tick(1);
            check(s.furnace.getItem(0).isEmpty()&&s.furnace.getItem(2).is(Items.DIAMOND),"Exceptional fuel callback cannot leave a permanent busy scope");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void ordinaryCollisionMismatchAndUnknownMachineStaySeparated(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var ordinary=Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(nativeRecipe("smelting",2))).getOrThrow();
            s.install(new RecipeHolder<>(ID,graph("smelting",2,"[{\"item\":\"minecraft:diamond\"}]")),new RecipeHolder<>(ResourceLocation.parse("test:ordinary_collision"),ordinary));s.machine(Blocks.FURNACE);s.input(1);s.tick(2);
            check(s.furnace.getItem(2).is(Items.DIAMOND),"Raw graph entrance takes priority over a matching ordinary recipe");
            s.machine(Blocks.SMOKER);s.input(1);s.tick(3);check(s.furnace.getItem(2).isEmpty()&&s.furnace.getItem(0).getCount()==1,"Wrong native cooking RecipeType cannot run graph step");
            s.machine(Blocks.FURNACE);helper.getLevel().removeBlockEntity(s.pos);s.furnace=new FurnaceBlockEntity(s.pos,helper.getLevel().getBlockState(s.pos)){};helper.getLevel().setBlockEntity(s.furnace);s.input(1);s.tick(2);
            check(s.furnace.getItem(2).is(Items.STONE),"Unknown furnace subclass retains ordinary native operation and cannot execute graph");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void cookingNativeReferencesReloadAndPreviewAdmissionStayExact(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var base=new SmeltingRecipe("fixture",CookingBookCategory.MISC,Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE),1,6);
            var referenceId=ResourceLocation.parse("test:furnace_reference");
            var data=graph("smelting",6,"[{\"item\":\"minecraft:diamond\"}]").json();data.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"recipe_id\":\"test:furnace_reference\"}"));
            var g=new GraphRecipe(data);var holder=new RecipeHolder<>(referenceId,base);s.install(new RecipeHolder<>(ID,g),holder);s.machine(Blocks.FURNACE);s.input(1);s.tick(3);
            s.install(new RecipeHolder<>(ID,g),new RecipeHolder<>(referenceId,new SmeltingRecipe("fixture",CookingBookCategory.MISC,Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE),1,6)));s.tick(3);
            check(s.furnace.getItem(2).isEmpty()&&s.nbt().getInt("CookTime")==3,"Same-ID native reference replacement cannot reuse elapsed time");s.tick(3);check(s.furnace.getItem(2).is(Items.DIAMOND),"Rebound standard reference completes normally");
            var unknown=new SmeltingRecipe("fixture",CookingBookCategory.MISC,Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE),1,6){};var unknownHolder=new RecipeHolder<>(referenceId,unknown);s.install(new RecipeHolder<>(ID,g),unknownHolder);
            var definition=g.definition(ID.toString());var node=definition.nodes().get("s");
            check(dev.graphassembly.integration.GraphPreviewRecipes.resolve(helper.getLevel(),definition,node)==unknownHolder,"Native preview keeps exact unknown reference holder without authorizing execution");
            check(!GraphIndex.get(helper.getLevel()).graphs().containsKey(ID.toString()),"Unknown cooking subclass is rejected before codec can erase subclass identity");
            var inline=graph("smelting",6,"[{\"item\":\"minecraft:diamond\"}]").json();inline.add("ingredient",JsonParser.parseString("[{\"item\":\"minecraft:amethyst_shard\"},{\"item\":\"minecraft:copper_ingot\"}]"));var inlineGraph=new GraphRecipe(inline).definition(ID.toString());
            var preview=dev.graphassembly.integration.GraphPreviewRecipes.resolve(helper.getLevel(),inlineGraph,inlineGraph.nodes().get("s")).value();
            check(preview.getIngredients().getFirst().test(new ItemStack(Items.AMETHYST_SHARD))&&preview.getIngredients().getFirst().test(new ItemStack(Items.COPPER_INGOT)),"Inline cooking preview preserves full alternative entrance ingredient");
            check(dev.graphassembly.integration.AssemblyDiscovery.scan(List.of(holder)).stream().anyMatch(o->o.capability().deviceAdapter()),"Discovery includes supported exact native cooking");
            s.install(new RecipeHolder<>(ID,g),holder);
            check(dev.graphassembly.integration.bridge.NativeRecipeCatalog.find(new Object(),helper.getLevel(),r->r.value().getType()==RecipeType.SMELTING).stream().noneMatch(r->r.id().getNamespace().equals(GraphAssembly.ID)&&r.id().getPath().startsWith("projection/")),"Cooking graph never becomes a Create ProcessingRecipe projection");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void realCarrierKeepsPrimaryComponentsAndCannotFallbackOrBurnAsFuel(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var data=graph("smelting",2,"[{\"item\":\"minecraft:diamond\"}]").json();data.addProperty("transitional_item","minecraft:amethyst_shard");data.getAsJsonArray("nodes").get(0).getAsJsonObject().remove("finish");data.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"f\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}"));data.add("edges",JsonParser.parseString("[{\"from\":\"s\",\"to\":\"f\"}]"));
            var ordinary=Recipe.CODEC.parse(helper.getLevel().registryAccess().createSerializationContext(JsonOps.INSTANCE),JsonParser.parseString(nativeRecipe("smelting",2))).getOrThrow();
            s.install(new RecipeHolder<>(ID,new GraphRecipe(data)),new RecipeHolder<>(ResourceLocation.parse("test:fallback"),ordinary));s.machine(Blocks.FURNACE);s.input(1);s.furnace.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("primary kept"));s.tick(2);
            var carrier=s.furnace.getItem(2).copy();check(Workpiece.has(carrier)&&carrier.getHoverName().getString().equals("primary kept"),"Same-item graph carrier preserves full primary components");
            s.furnace.setItem(2,ItemStack.EMPTY);s.furnace.setItem(0,carrier);s.tick(5);
            check(Workpiece.has(s.furnace.getItem(0))&&s.furnace.getItem(2).isEmpty(),"Wrong-next-machine workpiece must not fall back to matching ordinary smelting");
            var coalData=data.deepCopy();coalData.addProperty("transitional_item","minecraft:coal");s.graph(new GraphRecipe(coalData));s.furnace.setItem(0,new ItemStack(Items.AMETHYST_SHARD));s.tick(2);var fuel=s.furnace.getItem(2).copy();
            check(fuel.is(Items.COAL)&&Workpiece.has(fuel),"Fuel fixture must come from a real graph operation");s.furnace.setItem(2,ItemStack.EMPTY);s.furnace.setItem(0,new ItemStack(Items.AMETHYST_SHARD));s.furnace.setItem(1,fuel);s.tick(3);
            check(ItemStack.matches(fuel,s.furnace.getItem(1))&&s.furnace.getItem(0).getCount()==1&&s.furnace.getItem(2).isEmpty(),"Progress-bearing fuel is never consumed, even with remaining native burn time");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void graphExperienceDropsOnRealFurnaceBlockBreak(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",2,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.FURNACE);s.input(1);s.tick(2);
            var box=new net.minecraft.world.phys.AABB(s.pos).inflate(1);
            int before=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,box).stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum();
            helper.getLevel().setBlockAndUpdate(s.pos,Blocks.AIR.defaultBlockState());
            int after=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.ExperienceOrb.class,box).stream().mapToInt(net.minecraft.world.entity.ExperienceOrb::getValue).sum();
            check(after==before+1,"Actual block onRemove awards one successful graph XP round");
            check(s.nbt().getDouble("GraphAssemblyCookingExperience")==0,"Broken furnace graph XP ledger is cleared");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void outputComponentCodecsRunBeforeSettlementRandomness(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",1,"""
                [{"item":"minecraft:diamond","components":{"graph_assembly:furnace_codec_fixture":"first"}},
                 {"item":"minecraft:diamond","components":{"graph_assembly:furnace_codec_fixture":"second"}}]
                """));s.machine(Blocks.FURNACE);s.input(1);
            var random=helper.getLevel().random;long seed=4409;random.setSeed(seed);long expected=random.nextLong();random.setSeed(seed);int[] decoded={0};
            FurnaceCodecFixture.decode=()->{
                decoded[0]++;long observed=random.nextLong();random.setSeed(seed);
                check(observed==expected,"External result component codec must execute before settlement RNG, never after random outcome selection");
            };
            try{s.tick(1);}finally{FurnaceCodecFixture.decode=()->{};}
            check(decoded[0]>0,"Real registered output component codec must be exercised");
            check(s.furnace.getItem(0).isEmpty()&&s.furnace.getItem(2).is(Items.DIAMOND),"Prepared native output codecs still allow one real furnace graph operation");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void outputComponentCallbacksInvalidateBeforeAnyConsumptionOrRng(GameTestHelper helper) {
        for(String action:List.of("input","fuel","output","reload","reenter","remove"))try(var s=new Session(helper)) {
            var g=graph("smelting",1,"[{\"item\":\"minecraft:diamond\",\"components\":{\"graph_assembly:furnace_codec_fixture\":\"callback\"}}]");
            s.graph(g);s.machine(Blocks.FURNACE);s.input(2);int[] calls={0};
            FurnaceCodecFixture.decode=()->{calls[0]++;switch(action){
                case "input" -> s.furnace.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("output codec changed"));
                case "fuel" -> s.furnace.getItem(1).setCount(1);
                case "output" -> s.furnace.setItem(2,new ItemStack(Items.EMERALD));
                case "reload" -> s.install(new RecipeHolder<>(ID,new GraphRecipe(g.json())));
                case "reenter" -> s.tick(1);
                case "remove" -> helper.getLevel().removeBlockEntity(s.pos);
            }};
            var random=helper.getLevel().random;random.setSeed(4903);long expected=random.nextLong();random.setSeed(4903);
            try{s.tick(1);}finally{FurnaceCodecFixture.decode=()->{};}
            check(calls[0]>0,"Real result component codec callback must execute");
            check(random.nextLong()==expected&&s.furnace.getItem(0).getCount()==2,"Result codec "+action+" invalidation aborts before RNG or primary consumption");
            check(!s.furnace.getItem(2).is(Items.DIAMOND)&&s.nbt().getDouble("GraphAssemblyCookingExperience")==0,"Result codec callback cannot deliver old result or award graph XP");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void changedNativeReferencePersistsItsNewOperationFingerprint(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var ref=ResourceLocation.parse("test:changed_saved_reference");var json=graph("smelting",6,"[{\"item\":\"minecraft:diamond\"}]").json();
            json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("recipe",JsonParser.parseString("{\"type\":\"minecraft:smelting\",\"recipe_id\":\"test:changed_saved_reference\"}"));var g=new GraphRecipe(json);
            s.install(new RecipeHolder<>(ID,g),new RecipeHolder<>(ref,new SmeltingRecipe("old",CookingBookCategory.MISC,Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE),1,6)));s.machine(Blocks.FURNACE);s.input(1);s.tick(2);
            s.install(new RecipeHolder<>(ID,g),new RecipeHolder<>(ref,new SmeltingRecipe("new",CookingBookCategory.MISC,Ingredient.of(Items.AMETHYST_SHARD),new ItemStack(Items.STONE),2,8)));s.tick(3);
            check(s.nbt().getInt("CookTime")==3&&s.nbt().getInt("CookTimeTotal")==8,"Changed native definition binds new time without inherited progress");
            var tag=s.furnace.saveWithFullMetadata(helper.getLevel().registryAccess());helper.getLevel().removeBlockEntity(s.pos);s.furnace=(AbstractFurnaceBlockEntity)BlockEntity.loadStatic(s.pos,helper.getLevel().getBlockState(s.pos),tag,helper.getLevel().registryAccess());helper.getLevel().setBlockEntity(s.furnace);s.tick(5);
            check(s.furnace.getItem(2).is(Items.DIAMOND)&&s.furnace.getItem(0).isEmpty()&&s.nbt().getDouble("GraphAssemblyCookingExperience")==2,"Saved replacement reference resumes exactly its new identity and XP");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void simultaneousSameItemDifferentComponentsCannotShareOutputSlot(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",1,"""
                [{"mode":"all","results":[{"item":"minecraft:diamond","components":{"graph_assembly:furnace_codec_fixture":"first"}},
                  {"item":"minecraft:diamond","components":{"graph_assembly:furnace_codec_fixture":"second"}}]}]
                """));s.machine(Blocks.FURNACE);s.input(1);s.tick(3);
            check(s.furnace.getItem(0).getCount()==1&&s.furnace.getItem(1).getCount()==2&&s.furnace.getItem(2).isEmpty(),"Same item with different output components must block complete bundle before ignition");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeTemplateCodecCannotReplaceTickInventoryBaseline(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            var data=graph("smelting",1,"[{\"item\":\"minecraft:diamond\"}]").json();
            var result=data.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").getAsJsonObject("result");
            result.add("components",JsonParser.parseString("{\"graph_assembly:furnace_codec_fixture\":\"native-placeholder\"}"));
            s.graph(new GraphRecipe(data));s.machine(Blocks.FURNACE);s.input(2);
            // The index and native setItem time query are deliberately warmed before enabling the actual codec callback.
            check(GraphIndex.get(helper.getLevel()).graphs().containsKey(ID.toString()),"Native placeholder codec fixture must be execution-admitted");int[] calls={0};
            FurnaceCodecFixture.decode=()->{calls[0]++;s.furnace.getItem(0).shrink(1);FurnaceCodecFixture.decode=()->{};};
            var random=helper.getLevel().random;random.setSeed(5107);long expected=random.nextLong();random.setSeed(5107);
            try{s.tick(1);}finally{FurnaceCodecFixture.decode=()->{};}
            check(calls[0]==1,"Actual native placeholder result codec must perform its one-shot mutation");
            check(s.furnace.getItem(0).getCount()==1&&s.furnace.getItem(2).isEmpty(),"Native template codec mutation must invalidate original tick snapshot, not become a new baseline and consume again");
            check(s.furnace.getItem(1).getCount()==2&&random.nextLong()==expected,"Native query callback invalidation must precede ignition and settlement RNG");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void invalidFuelRemainderCannotLeaveFreeIgnitionForNextTick(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",1,"[{\"item\":\"minecraft:diamond\"}]"));s.machine(Blocks.FURNACE);s.input(2);s.furnace.setItem(1,new ItemStack(FurnaceFuelFixture.ITEM,2));int[] calls={0};
            FurnaceFuelFixture.remainder=()->{calls[0]++;s.furnace.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("invalid first ignition"));};
            try{s.tick(1);}finally{FurnaceFuelFixture.remainder=()->{};}
            check(s.furnace.getItem(0).getCount()==2&&s.furnace.getItem(2).isEmpty()&&s.furnace.getItem(1).getCount()==2,"Invalid fuel remainder preserves material and unconsumed fuel");
            FurnaceFuelFixture.remainder=()->{calls[0]++;};try{s.tick(1);}finally{FurnaceFuelFixture.remainder=()->{};}
            check(calls[0]==2&&s.furnace.getItem(1).is(Items.BUCKET),"Aborted remainder ignition must be retried with actual fuel consumption, never power next tick for free");
            check(s.furnace.getItem(0).getCount()==1&&s.furnace.getItem(2).is(Items.DIAMOND),"Subsequent valid ignition may complete exactly one graph round");
        }helper.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void invalidCallbackDoesNotRollBackPreviouslyPaidBurnTime(GameTestHelper helper) {
        try(var s=new Session(helper)) {
            s.graph(graph("smelting",2,"[{\"item\":\"minecraft:diamond\",\"components\":{\"graph_assembly:furnace_codec_fixture\":\"existing-burn\"}}]"));s.machine(Blocks.FURNACE);s.input(2);s.tick(1);
            check(s.nbt().getInt("BurnTime")==1600&&s.furnace.getItem(1).getCount()==1,"Fixture must have already paid for ordinary coal ignition");
            FurnaceCodecFixture.decode=()->{s.furnace.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("aborted existing burn"));FurnaceCodecFixture.decode=()->{};};
            try{s.tick(1);}finally{FurnaceCodecFixture.decode=()->{};}
            check(s.nbt().getInt("BurnTime")==1599&&s.furnace.getItem(1).getCount()==1,"Invalid callback cannot refund or erase fuel which was already burning before this tick");
            check(s.furnace.getItem(0).getCount()==2&&s.furnace.getItem(2).isEmpty(),"Aborted callback during existing burn consumes no graph material");
        }helper.succeed();
    }
}
