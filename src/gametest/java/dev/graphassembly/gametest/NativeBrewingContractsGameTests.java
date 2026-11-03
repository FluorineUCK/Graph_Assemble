package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.brewing.BrewingAccess;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.minecraft.world.item.alchemy.*;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.brewing.PotionBrewEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeBrewingContractsGameTests {
    static final ResourceLocation ID=ResourceLocation.parse("test:brewing_contract");
    static GraphRecipe graph(boolean ingredient,boolean finish,String results){return new GraphRecipe(JsonParser.parseString("""
        {"ingredient":{"item":"minecraft:%s"},"transitional_item":"minecraft:iron_nugget","nodes":[
        {"id":"brew","start":true,%s"recipe":{"type":"graph_assembly:brewing","primary":"%s"}},
        {"id":"end","finish":true,"recipe":{"type":"graph_assembly:brewing"}}],
        "edges":[{"from":"brew","to":"end"}],"outputs":%s}
        """.formatted(ingredient?"nether_wart":"potion",finish?"\"finish\":true,\"finish_mode\":\"strict\",":"",ingredient?"ingredient":"bottle",results.equals("[]")?"[]":"[{\"results\":"+results+"}]")).getAsJsonObject());}
    static final class Session implements AutoCloseable {
        final GameTestHelper h;final List<RecipeHolder<?>> saved;final BlockPos pos;BrewingStandBlockEntity stand;
        Session(GameTestHelper h){this.h=h;saved=List.copyOf(h.getLevel().getRecipeManager().getRecipes());pos=h.absolutePos(new BlockPos(1,1,1));h.getLevel().setBlock(pos,Blocks.BREWING_STAND.defaultBlockState(),3);stand=(BrewingStandBlockEntity)h.getLevel().getBlockEntity(pos);}
        void install(GraphRecipe graph){h.getLevel().getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ID,graph)));GraphIndex.invalidate();}
        void fill(int bottles){stand.clearContent();for(int i=0;i<bottles;i++)stand.setItem(i,PotionContents.createItemStack(Items.POTION,Potions.WATER));stand.setItem(3,new ItemStack(Items.NETHER_WART,4));stand.setItem(4,new ItemStack(Items.BLAZE_POWDER));((BrewingAccess)stand).graphAssembly$time(0);}
        void tick(int n){for(int i=0;i<n;i++)BrewingStandBlockEntity.serverTick(h.getLevel(),pos,h.getLevel().getBlockState(pos),stand);}
        BrewingAccess access(){return (BrewingAccess)stand;}
        public void close(){h.getLevel().removeBlock(pos,false);h.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    static void check(boolean pass,String message){if(!pass)throw new GameTestAssertException(message);}
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void postGraphReloadInvalidatesRoundBeforeRng(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,true,"[{\"item\":\"minecraft:diamond\"},{\"item\":\"minecraft:emerald\"}]"));s.fill(3);s.tick(400);
            Consumer<PotionBrewEvent.Post> post=e->GraphIndex.invalidate();NeoForge.EVENT_BUS.addListener(post);
            var random=helper.getLevel().random;random.setSeed(9876);double expected=random.nextDouble();random.setSeed(9876);
            try{s.tick(1);}finally{NeoForge.EVENT_BUS.unregister(post);}
            check(s.stand.getItem(3).getCount()==4,"Reload in Post must invalidate shared payment");
            check(random.nextDouble()==expected,"Aborted round consumes zero graph draws");
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void sharedMaterialFansOutAndAllOutcomesFit(GameTestHelper helper){
        try(var s=new Session(helper)){
            for(int bottles=1;bottles<=3;bottles++){
                s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(bottles);s.tick(401);
                check(s.stand.getItem(3).getCount()==3,"One material for "+bottles+" bottles");
                for(int i=0;i<bottles;i++)check(Workpiece.has(s.stand.getItem(i)),"Every bottle advances independently");
            }
            for(String outputs:List.of("[{\"item\":\"minecraft:diamond\",\"count\":2}]","[{\"mode\":\"all\",\"results\":[{\"item\":\"minecraft:diamond\"},{\"item\":\"minecraft:emerald\"}]}]")){
                s.install(graph(true,true,outputs));s.fill(3);s.tick(401);
                check(s.stand.getItem(3).getCount()==4&&s.access().graphAssembly$time()==0,"Any overflowing outcome rejects whole round");
            }
            s.install(graph(true,true,"[]"));s.fill(3);s.tick(401);
            check(s.stand.getItem(3).getCount()==3,"Empty outcomes still pay only one material");
            for(int i=0;i<3;i++)check(s.stand.getItem(i).isEmpty(),"Empty finish fits original slot");
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void nativeEventsCancellationMutationAndReentry(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));
            for(int mode=0;mode<4;mode++){
                s.fill(3);s.tick(400);final int action=mode;int[] calls={0,0};
                Consumer<PotionBrewEvent.Pre> pre=e->{calls[0]++;if(action==0)e.setCanceled(true);if(action==1)s.tick(1);if(action==2)s.stand.setItem(1,new ItemStack(Items.GLASS_BOTTLE));};
                Consumer<PotionBrewEvent.Post> post=e->{calls[1]++;if(action==3)e.getItem(0).setCount(2);};
                NeoForge.EVENT_BUS.addListener(pre);NeoForge.EVENT_BUS.addListener(post);
                var random=helper.getLevel().random;random.setSeed(221);double expected=random.nextDouble();random.setSeed(221);
                try{s.tick(1);}finally{NeoForge.EVENT_BUS.unregister(pre);NeoForge.EVENT_BUS.unregister(post);}
                check(s.stand.getItem(3).getCount()==4,"Cancellation/mutation/reentry cannot pay material");
                check(random.nextDouble()==expected,"Aborted events must not draw graph randomness");
                check(calls[0]==1&&calls[1]<=1,"At most one native event pair");
            }
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void independentFinishDrawsAndPostCarrier(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,true,"[{\"item\":\"minecraft:diamond\"},{\"item\":\"minecraft:emerald\"}]"));s.fill(3);s.tick(400);
            var random=helper.getLevel().random;random.setSeed(412);var expected=new ArrayList<Item>();
            // Pool selection consumes one draw per bottle, not one draw cloned to three slots.
            for(int i=0;i<3;i++)expected.add(random.nextDouble()<0.5?Items.DIAMOND:Items.EMERALD);double next=random.nextDouble();random.setSeed(412);
            s.tick(1);for(int i=0;i<3;i++)check(s.stand.getItem(i).is(expected.get(i)),"Independent bottle finish draw "+i);
            check(random.nextDouble()==next,"Exactly three independent finish pool draws");
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);s.tick(400);
            Consumer<PotionBrewEvent.Post> post=e->e.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("post carrier"));
            NeoForge.EVENT_BUS.addListener(post);try{s.tick(1);}finally{NeoForge.EVENT_BUS.unregister(post);}
            check(Workpiece.has(s.stand.getItem(0))&&s.stand.getItem(0).getHoverName().getString().equals("post carrier"),"Post final components are graph carrier");
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void saveLoadAndChangedInputsResetCountdown(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(2);s.tick(201);
            check(s.access().graphAssembly$time()==200,"Native countdown");
            var tag=s.stand.saveWithFullMetadata(helper.getLevel().registryAccess());helper.getLevel().removeBlockEntity(s.pos);
            s.stand=(BrewingStandBlockEntity)BlockEntity.loadStatic(s.pos,helper.getLevel().getBlockState(s.pos),tag,helper.getLevel().registryAccess());helper.getLevel().setBlockEntity(s.stand);
            s.tick(200);check(Workpiece.has(s.stand.getItem(0)),"Matching saved graph job resumes");
            s.fill(1);s.tick(400);s.stand.getItem(0).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("changed"));s.tick(1);
            check(!Workpiece.has(s.stand.getItem(0))&&s.access().graphAssembly$time()==400,"Changed components cannot borrow old time");
        }helper.succeed();
    }    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void savedJobCannotHideSameJvmReload(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);s.tick(400);
            var tag=s.stand.saveWithFullMetadata(helper.getLevel().registryAccess());GraphIndex.invalidate();
            helper.getLevel().removeBlockEntity(s.pos);s.stand=(BrewingStandBlockEntity)BlockEntity.loadStatic(s.pos,helper.getLevel().getBlockState(s.pos),tag,helper.getLevel().registryAccess());helper.getLevel().setBlockEntity(s.stand);
            s.tick(1);check(!Workpiece.has(s.stand.getItem(0))&&s.access().graphAssembly$time()==400,"Same JVM graph identity reload invalidates saved old job");
        }helper.succeed();
    }    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void ordinaryFuelUnclaimedBottlesAndDirectCompletion(GameTestHelper helper) throws Exception {
        try(var s=new Session(helper)){
            helper.getLevel().getRecipeManager().replaceRecipes(List.of());GraphIndex.invalidate();s.fill(1);s.tick(401);
            check(!Workpiece.has(s.stand.getItem(0))&&s.stand.getItem(3).getCount()==3,"Ordinary brew uses native chain");
            check(s.access().graphAssembly$fuel()==19,"Native powder loads 20 then spends one");
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);
            var method=BrewingStandBlockEntity.class.getDeclaredMethod("doBrew",net.minecraft.world.level.Level.class,BlockPos.class,net.minecraft.core.NonNullList.class);method.setAccessible(true);
            method.invoke(null,helper.getLevel(),s.pos,s.access().graphAssembly$items());
            check(!Workpiece.has(s.stand.getItem(0))&&s.stand.getItem(3).getCount()==4,"Direct completion cannot bypass timer/fuel");
            s.tick(1);method.invoke(null,helper.getLevel(),s.pos,s.access().graphAssembly$items());
            check(!Workpiece.has(s.stand.getItem(0))&&s.access().graphAssembly$time()==400,"Active job is not a direct completion permit");
            var data=graph(true,false,"[{\"item\":\"minecraft:diamond\"}]").json();
            data.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").add("addition",JsonParser.parseString("{\"item\":\"minecraft:potion\"}"));
            s.install(new GraphRecipe(data));s.fill(1);var splash=PotionContents.createItemStack(Items.SPLASH_POTION,Potions.WATER);s.stand.setItem(1,splash.copy());s.tick(401);
            check(Workpiece.has(s.stand.getItem(0))&&ItemStack.matches(splash,s.stand.getItem(1)),"Ingredient primary leaves unclaimed native-valid ordinary bottle unchanged");
            s.install(graph(false,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);s.stand.setItem(1,splash.copy());s.tick(401);
            check(Workpiece.has(s.stand.getItem(0))&&!Workpiece.has(s.stand.getItem(1))&&s.stand.getItem(1).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(Potions.AWKWARD),"Bottle primary coexists with ordinary brew");
            s.fill(1);var progress=Workpiece.read(s.stand.getItem(0));
            var tracked=new dev.graphassembly.core.Progress(ID.toString(),"brew","brew",Map.of(),List.of(),Map.of(),null,List.of("brew"));
            s.stand.setItem(4,Workpiece.write(new ItemStack(Items.BLAZE_POWDER),tracked));s.tick(401);
            check(Workpiece.has(s.stand.getItem(4))&&s.stand.getItem(3).getCount()==4,"Tracked fuel never burns, even with existing reserve");
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void dragonBreathRemainderUsesNativeMaterialSlotAndDrop(GameTestHelper helper){
        try(var s=new Session(helper)){
            var data=graph(true,false,"[{\"item\":\"minecraft:diamond\"}]").json();data.add("ingredient",JsonParser.parseString("{\"item\":\"minecraft:dragon_breath\"}"));s.install(new GraphRecipe(data));
            for(int count=1;count<=2;count++){
                s.fill(3);for(int i=0;i<3;i++)s.stand.setItem(i,PotionContents.createItemStack(Items.SPLASH_POTION,Potions.WATER));s.stand.setItem(3,new ItemStack(Items.DRAGON_BREATH,count));s.tick(401);
                for(int i=0;i<3;i++)check(Workpiece.has(s.stand.getItem(i))&&s.stand.getItem(i).is(Items.LINGERING_POTION),"Each native lingering bottle carries graph");
                check(s.stand.getItem(3).is(count==1?Items.GLASS_BOTTLE:Items.DRAGON_BREATH),"Native remainder slot rule");
                var drops=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(s.pos).inflate(1));
                check(drops.stream().filter(e->e.getItem().is(Items.GLASS_BOTTLE)).mapToInt(e->e.getItem().getCount()).sum()==count-1,"Exactly one leftover container when ingredient remains");drops.forEach(net.minecraft.world.entity.Entity::discard);
            }
        }helper.succeed();
    }    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void postCannotChangeUnclaimedBottle(GameTestHelper helper){
        try(var s=new Session(helper)){
            var data=graph(true,false,"[{\"item\":\"minecraft:diamond\"}]").json();data.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").add("addition",JsonParser.parseString("{\"item\":\"minecraft:potion\"}"));s.install(new GraphRecipe(data));
            s.fill(1);var splash=PotionContents.createItemStack(Items.SPLASH_POTION,Potions.WATER);s.stand.setItem(1,splash.copy());s.tick(400);
            Consumer<PotionBrewEvent.Post> post=e->e.getItem(1).set(net.minecraft.core.component.DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("unclaimed"));
            NeoForge.EVENT_BUS.addListener(post);try{s.tick(1);}finally{NeoForge.EVENT_BUS.unregister(post);}
            check(ItemStack.matches(splash,s.stand.getItem(1))&&s.stand.getItem(3).getCount()==4,"Unclaimed bottle cannot become an event output in a graph round");
        }helper.succeed();
    }    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void trackedSourceCopiesIndependentNextAndBottleHistories(GameTestHelper helper){
        try(var s=new Session(helper)){
            for(boolean ingredient:List.of(true,false)){
                var data=JsonParser.parseString("""
                    {"ingredient":{"item":"minecraft:%s"},"transitional_item":"minecraft:iron_nugget","nodes":[
                    {"id":"seed","start":true,"recipe":{"type":"graph_assembly:brewing","primary":"%s"}},
                    {"id":"brew","branch_mode":"random","recipe":{"type":"graph_assembly:brewing","primary":"%s"}},
                    {"id":"a","finish":true,"recipe":{"type":"graph_assembly:brewing"}},
                    {"id":"b","finish":true,"recipe":{"type":"graph_assembly:brewing"}}],
                    "edges":[{"from":"seed","to":"seed"},{"from":"seed","to":"brew"},{"from":"brew","to":"a"},{"from":"brew","to":"b"}],
                    "outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                    """.formatted(ingredient?"nether_wart":"potion",ingredient?"ingredient":"bottle",ingredient?"ingredient":"bottle")).getAsJsonObject();
                // Route makes seed->brew unambiguous even though seed has a self-edge for different histories.
                var progress=new dev.graphassembly.core.Progress(ID.toString(),"seed","seed",Map.of(),List.of(),Map.of(),null,List.of("seed"));
                data.getAsJsonArray("edges").remove(0);
                s.install(new GraphRecipe(data));s.fill(3);ItemStack source=ItemStack.EMPTY;
                if(ingredient){source=Workpiece.write(new ItemStack(Items.NETHER_WART),progress);s.stand.setItem(3,source);}
                else for(int i=0;i<3;i++){
                    var state=new dev.graphassembly.core.Progress(ID.toString(),"seed","seed",Map.of("history",i),List.of(),Map.of(),null,List.of("seed"));
                    s.stand.setItem(i,Workpiece.write(s.stand.getItem(i),state));
                }
                var before=source.copy();s.tick(400);var random=helper.getLevel().random;random.setSeed(311);
                var expected=new ArrayList<String>();for(int i=0;i<3;i++)expected.add(random.nextDouble()<0.5?"a":"b");double next=random.nextDouble();random.setSeed(311);s.tick(1);
                for(int i=0;i<3;i++){
                    var state=Workpiece.read(s.stand.getItem(i));check(state!=null&&state.lockedNext().equals(expected.get(i)),"Per-bottle independent next draw");
                    if(!ingredient)check(state.marks().get("history")==i,"Bottle history is not merged");
                }
                check(random.nextDouble()==next,"One next choice per bottle");if(ingredient)check(ItemStack.matches(source,before),"Original material state object is never rewritten");
            }
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void nativeHopperAndMenuSlotsRemainUsable(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.stand.clearContent();
            check(HopperBlockEntity.addItem(null,s.stand,new ItemStack(Items.NETHER_WART),net.minecraft.core.Direction.UP).isEmpty(),"Native top hopper ingredient insertion");
            check(HopperBlockEntity.addItem(null,s.stand,new ItemStack(Items.BLAZE_POWDER),net.minecraft.core.Direction.NORTH).isEmpty(),"Native side hopper fuel insertion");
            check(HopperBlockEntity.addItem(null,s.stand,PotionContents.createItemStack(Items.POTION,Potions.WATER),net.minecraft.core.Direction.NORTH).isEmpty(),"Native side hopper bottle insertion");
            var player=new net.minecraft.server.level.ServerPlayer(helper.getLevel().getServer(),helper.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"BrewingTest"),net.minecraft.server.level.ClientInformation.createDefault());
            var menu=s.stand.createMenu(1,player.getInventory(),player);check(menu!=null&&menu.getSlot(0).getItem().is(Items.POTION),"Native menu exposes physical bottle slot");
            s.tick(401);check(Workpiece.has(menu.getSlot(0).getItem()),"Menu sees completed graph carrier");
            check(s.stand.canTakeItemThroughFace(0,s.stand.getItem(0),net.minecraft.core.Direction.DOWN),"Native extraction permits graph bottle");
            var out=menu.getSlot(0).remove(1);check(Workpiece.has(out)&&s.stand.getItem(0).isEmpty(),"Menu extraction retains progress");
        }helper.succeed();
    }    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void outputCodecReloadCannotAuthorizeStart(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,true,"[{\"item\":\"minecraft:diamond\",\"components\":{\"graph_assembly:furnace_codec_fixture\":\"reload\"}}]"));s.fill(1);
            FurnaceCodecFixture.decode=GraphIndex::invalidate;
            try{s.tick(1);}finally{FurnaceCodecFixture.decode=()->{};}
            check(s.access().graphAssembly$time()==0&&s.access().graphAssembly$fuel()==20,"Callback reload must reject before starting a paid countdown");
        }helper.succeed();
    }    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void canceledEventSameObjectLoadInvalidatesInventoryPublication(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);s.tick(400);
            Consumer<PotionBrewEvent.Pre> pre=e->{e.setItem(0,new ItemStack(Items.GLASS_BOTTLE));e.setCanceled(true);
                s.stand.loadWithComponents(s.stand.saveWithoutMetadata(helper.getLevel().registryAccess()),helper.getLevel().registryAccess());};
            NeoForge.EVENT_BUS.addListener(pre);try{s.tick(1);}finally{NeoForge.EVENT_BUS.unregister(pre);}
            check(s.stand.getItem(0).is(Items.POTION),"Reload invalidates staged cancellation inventory even on same entity object");
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void removalReplacementAndCanceledInventory(GameTestHelper helper){
        for(int action=0;action<3;action++)try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);s.tick(400);final int mode=action;int[] calls={0};
            Consumer<PotionBrewEvent.Pre> pre=e->{if(mode==0)helper.getLevel().removeBlockEntity(s.pos);else if(mode==1)helper.getLevel().setBlock(s.pos,Blocks.STONE.defaultBlockState(),3);else {e.setItem(0,new ItemStack(Items.GLASS_BOTTLE));e.setCanceled(true);}};
            Consumer<PotionBrewEvent.Post> post=e->calls[0]++;NeoForge.EVENT_BUS.addListener(pre);NeoForge.EVENT_BUS.addListener(post);
            try{s.tick(1);}finally{NeoForge.EVENT_BUS.unregister(pre);NeoForge.EVENT_BUS.unregister(post);}
            if(mode==1){
                var drops=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(s.pos).inflate(1));
                check(drops.stream().filter(e->e.getItem().is(Items.NETHER_WART)).mapToInt(e->e.getItem().getCount()).sum()==4,"Native block replacement drops all unpaid material");
                check(drops.stream().noneMatch(e->Workpiece.has(e.getItem())),"No graph result published on replacement");
                check(helper.getLevel().getBlockState(s.pos).is(Blocks.STONE),"Invalidated stand cannot overwrite callback replacement during bottle appearance sync");
                drops.forEach(net.minecraft.world.entity.Entity::discard);
            }else check(s.stand.getItem(3).getCount()==4,"Removal/cancellation does not pay graph material");
            if(mode==2)check(s.stand.getItem(0).is(Items.GLASS_BOTTLE)&&calls[0]==1,"Valid cancelled native inventory replacement publishes; EventHooks owns the only Post");
            else check(!Workpiece.has(s.stand.getItem(0)),"Removed/replaced machine cannot publish graph result");
        }helper.succeed();
    }    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void ordinaryCallbackCannotConsumeNewTrackedMaterial(GameTestHelper helper){
        for(int mode=0;mode<3;mode++)try(var s=new Session(helper)){
            helper.getLevel().getRecipeManager().replaceRecipes(List.of());GraphIndex.invalidate();s.fill(1);s.tick(400);
            var tracked=Workpiece.write(new ItemStack(Items.NETHER_WART),new dev.graphassembly.core.Progress(ID.toString(),"brew","brew",Map.of(),List.of(),Map.of(),null,List.of("brew")));
            Runnable replace=()->s.stand.setItem(3,tracked.copy());final int action=mode;
            Consumer<PotionBrewEvent.Pre> pre=e->{if(action==1)replace.run();if(action==2)BrewingCallbackFixture.output=replace;};
            if(mode==0)BrewingCallbackFixture.ingredient=replace;NeoForge.EVENT_BUS.addListener(pre);
            try{s.tick(1);}finally{BrewingCallbackFixture.ingredient=()->{};BrewingCallbackFixture.output=()->{};NeoForge.EVENT_BUS.unregister(pre);}
            check(ItemStack.matches(tracked,s.stand.getItem(3)),"Ordinary callback cannot consume newly tracked material, window="+mode);
            check(s.stand.getItem(0).get(net.minecraft.core.component.DataComponents.POTION_CONTENTS).is(Potions.WATER),"No stale ordinary result published, window="+mode);
            check(s.access().graphAssembly$time()==0,"Changed input cannot inherit ordinary countdown");
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void canceledGraphReloadCannotPublishInventory(GameTestHelper helper){
        for(boolean postReload:List.of(false,true))try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);s.tick(400);
            Consumer<PotionBrewEvent.Pre> pre=e->{e.setItem(0,new ItemStack(Items.GLASS_BOTTLE));e.setCanceled(true);if(!postReload)GraphIndex.invalidate();};
            Consumer<PotionBrewEvent.Post> post=e->{if(postReload)GraphIndex.invalidate();};
            NeoForge.EVENT_BUS.addListener(pre);NeoForge.EVENT_BUS.addListener(post);
            try{s.tick(1);}finally{NeoForge.EVENT_BUS.unregister(pre);NeoForge.EVENT_BUS.unregister(post);}
            check(s.stand.getItem(0).is(Items.POTION),"Canceled reload must not publish old staged inventory");
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void automaticFuelRefillDoesNotRestartGraphJob(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);
            var tag=s.stand.saveWithoutMetadata(helper.getLevel().registryAccess());tag.putByte("Fuel",(byte)1);s.stand.loadWithComponents(tag,helper.getLevel().registryAccess());
            s.tick(1);check(s.access().graphAssembly$time()==400&&s.access().graphAssembly$fuel()==0,"Last reserve starts round");
            s.tick(1);check(s.access().graphAssembly$time()==399&&s.access().graphAssembly$fuel()==20,"Automatic refill cannot reset or double charge round");
            s.tick(399);check(Workpiece.has(s.stand.getItem(0))&&s.stand.getItem(3).getCount()==3,"Original 400 tick deadline retained");
        }helper.succeed();
    }    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void savedJobFromDifferentServerSessionUsesStableContent(GameTestHelper helper){
        try(var s=new Session(helper)){
            s.install(graph(true,false,"[{\"item\":\"minecraft:diamond\"}]"));s.fill(1);s.tick(201);
            var tag=s.stand.saveWithoutMetadata(helper.getLevel().registryAccess());
            // NBT fixture from another server session in the same process: preserve process ID,
            // change only server identity. No claim that this starts a second integrated server.
            var runtime=tag.getString("GraphAssemblyBrewingRuntime").split(":");runtime[1]=UUID.randomUUID().toString();tag.putString("GraphAssemblyBrewingRuntime",String.join(":",runtime));
            s.stand.loadWithComponents(tag,helper.getLevel().registryAccess());s.tick(1);
            check(s.access().graphAssembly$time()==199,"A different server session must compare stable job content, not old runtime references");
            s.tick(199);check(Workpiece.has(s.stand.getItem(0)),"Cross-session content-matching job resumes original remaining duration");
        }helper.succeed();
    }
    @GameTest(template="test_empty",batch="native_brewing_runtime")
    public static void ordinaryNativeRemainderEffectsAreDeferredNotDuplicated(GameTestHelper helper){
        for(boolean mutate:List.of(false,true))try(var s=new Session(helper)){
            helper.getLevel().getRecipeManager().replaceRecipes(List.of());GraphIndex.invalidate();s.fill(1);
            s.stand.setItem(0,PotionContents.createItemStack(Items.SPLASH_POTION,Potions.WATER));s.stand.setItem(3,new ItemStack(Items.DRAGON_BREATH,2));s.tick(400);
            int[] calls={0,0};var tracked=Workpiece.write(new ItemStack(Items.DRAGON_BREATH),new dev.graphassembly.core.Progress(ID.toString(),"brew","brew",Map.of(),List.of(),Map.of(),null,List.of("brew")));
            Consumer<PotionBrewEvent.Pre> pre=e->{calls[0]++;if(mutate)s.stand.setItem(3,tracked.copy());};
            Consumer<PotionBrewEvent.Post> post=e->calls[1]++;NeoForge.EVENT_BUS.addListener(pre);NeoForge.EVENT_BUS.addListener(post);
            try{s.tick(1);}finally{NeoForge.EVENT_BUS.unregister(pre);NeoForge.EVENT_BUS.unregister(post);}
            check(calls[0]==1&&calls[1]==1,"Original ordinary chain emits exactly one native pair");
            var drops=helper.getLevel().getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class,new net.minecraft.world.phys.AABB(s.pos).inflate(1));
            check(drops.stream().filter(e->e.getItem().is(Items.GLASS_BOTTLE)).mapToInt(e->e.getItem().getCount()).sum()==(mutate?0:1),"No speculative remainder drop on abort and no duplicate ordinary drop");
            check(s.stand.getItem(0).is(mutate?Items.SPLASH_POTION:Items.LINGERING_POTION),"Only validated ordinary carrier is published");
            if(mutate)check(ItemStack.matches(tracked,s.stand.getItem(3)),"Ordinary callbacks never pay live tracked material");
            drops.forEach(net.minecraft.world.entity.Entity::discard);
        }helper.succeed();
    }}
