package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.*;
import dev.graphassembly.integration.vanilla.crafting.SpecialCraftingDefinition;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.*;
import net.neoforged.neoforge.gametest.*;
import java.util.*;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeSpecialCraftingContractsGameTests {
    private static final ResourceLocation ID=ResourceLocation.parse("test:special_contract");
    private static GraphRecipe graph(String type,boolean finish) {
        return new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:leather_chestplate"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"special","start":true,"finish":%s,"recipe":{"type":"minecraft:crafting_special_%s"}}%s],
             "edges":%s,"outputs":[{"results":[{"mode":"all","results":[{"item":"minecraft:diamond","count":2},{"item":"minecraft:emerald","count":3}]}]}]}
            """.formatted(finish,type,finish?"":",{\"id\":\"end\",\"finish\":true,\"recipe\":{\"type\":\"create:pressing\"}}",
                finish?"[]":"[{\"from\":\"special\",\"to\":\"end\"}]")).getAsJsonObject());
    }
    private static final class Session implements AutoCloseable {
        final GameTestHelper h;final List<RecipeHolder<?>> saved;
        final net.neoforged.neoforge.common.util.FakePlayer player;AbstractContainerMenu menu;
        Session(GameTestHelper h,GraphRecipe graph,RecipeHolder<?>... extras){
            this.h=h;saved=new ArrayList<>(h.getLevel().getRecipeManager().getRecipes());
            player=new net.neoforged.neoforge.common.util.FakePlayer(h.getLevel(),new com.mojang.authlib.GameProfile(UUID.randomUUID(),"special-contract"));
            var recipes=new ArrayList<RecipeHolder<?>>();recipes.add(new RecipeHolder<>(ID,graph));recipes.addAll(List.of(extras));
            h.getLevel().getRecipeManager().replaceRecipes(recipes);GraphIndex.invalidate();menu=player.inventoryMenu;player.containerMenu=menu;
        }
        void workbench(){menu=new CraftingMenu(2,player.getInventory(),ContainerLevelAccess.create(h.getLevel(),h.absolutePos(BlockPos.ZERO)));player.containerMenu=menu;}
        void put(int slot,ItemStack item){menu.getSlot(slot).set(item);}
        void fill(Item dye){put(1,new ItemStack(Items.LEATHER_CHESTPLATE));put(2,new ItemStack(dye,2));refresh();}
        void refresh(){menu.slotsChanged(menu.getSlot(1).container);}
        void click(ClickType type){menu.clicked(0,0,type,player);}
        public void close(){SpecialDyeRemainderFixture.callback=()->{};menu.setCarried(ItemStack.EMPTY);for(int i=1;i<=(menu instanceof CraftingMenu?9:4);i++)menu.getSlot(i).set(ItemStack.EMPTY);player.getInventory().clearContent();h.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean ok,String reason){if(!ok)throw new GameTestAssertException(reason);}
    private static long count(Session s,Item item){long n=0;for(int i=0;i<36;i++)if(s.player.getInventory().getItem(i).is(item))n+=s.player.getInventory().getItem(i).getCount();return n;}

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void rawSpecialAuxiliaryRejectionNeverFallsBackToOrdinaryNativeRecipe(GameTestHelper h) {
        for(String type:List.of("armordye","repairitem")) {
            var json=graph(type,false).json();json.getAsJsonArray("nodes").get(0).getAsJsonObject().add("workpiece_inputs",JsonParser.parseString("[{\"item_input\":1,\"current_nodes\":[\"aux\"]}]"));
            json.getAsJsonArray("nodes").add(JsonParser.parseString("{\"id\":\"aux\",\"start\":true,\"ingredient\":{\"item\":\"minecraft:quartz\"},\"recipe\":{\"type\":\"create:cutting\"}}"));
            json.getAsJsonArray("edges").add(JsonParser.parseString("{\"from\":\"aux\",\"to\":\"special\"}"));
            CraftingRecipe ordinary=type.equals("armordye")?new ArmorDyeRecipe(CraftingBookCategory.MISC):new RepairItemRecipe(CraftingBookCategory.MISC);
            try(var s=new Session(h,new GraphRecipe(json),new RecipeHolder<>(ResourceLocation.parse("test:ordinary_special_collision"),ordinary))) {
                var base=new ItemStack(Items.LEATHER_CHESTPLATE);base.setDamageValue(60);s.put(1,base);
                var auxiliary=type.equals("armordye")?new ItemStack(Items.RED_DYE):new ItemStack(Items.LEATHER_CHESTPLATE);if(type.equals("repairitem"))auxiliary.setDamageValue(60);
                s.put(2,auxiliary);s.refresh();
                var random=h.getLevel().random;random.setSeed(6211);long next=random.nextLong();random.setSeed(6211);s.click(ClickType.PICKUP);
                check(s.menu.getCarried().isEmpty()&&!s.menu.getSlot(1).getItem().isEmpty()&&!s.menu.getSlot(2).getItem().isEmpty()&&random.nextLong()==next,
                    "Raw "+type+" rejected declared auxiliary cannot ordinary-fallback, pay or roll");
            }
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void offsetDyeRepeatedPreviewFinishAndInvalidInputsStayNative(GameTestHelper h) {
        try(var s=new Session(h,graph("armordye",false))) {
            s.workbench();s.put(6,new ItemStack(Items.RED_DYE,2));s.put(9,new ItemStack(Items.LEATHER_CHESTPLATE));
            var random=h.getLevel().random;random.setSeed(6413);long next=random.nextLong();random.setSeed(6413);
            for(int i=0;i<5;i++)s.refresh();check(random.nextLong()==next&&!Workpiece.has(s.menu.getSlot(9).getItem()),"Repeated preview neither draws RNG nor changes primary");
            s.click(ClickType.PICKUP);check(Workpiece.has(s.menu.getCarried())&&s.menu.getSlot(9).getItem().isEmpty()&&s.menu.getSlot(6).getItem().getCount()==1,"Dye primary after dye in rightmost column pays offset physical slots: carried="+s.menu.getCarried()+", dye="+s.menu.getSlot(6).getItem()+", base="+s.menu.getSlot(9).getItem());
        }
        try(var s=new Session(h,graph("armordye",true))) {
            s.fill(Items.RED_DYE);s.click(ClickType.PICKUP);check(s.menu.getCarried().is(Items.DIAMOND)&&s.menu.getCarried().getCount()==2&&count(s,Items.EMERALD)==3,"Special finish validates native operation but delivers complete graph output bundle");
        }
        for(String invalid:List.of("no-dye","wrong-primary","extra","two-bases"))try(var s=new Session(h,graph("armordye",true))) {
            s.put(1,new ItemStack(invalid.equals("wrong-primary")?Items.IRON_CHESTPLATE:Items.LEATHER_CHESTPLATE));
            if(!invalid.equals("no-dye"))s.put(2,new ItemStack(Items.RED_DYE));
            if(invalid.equals("extra"))s.put(3,new ItemStack(Items.STICK));if(invalid.equals("two-bases"))s.put(3,new ItemStack(Items.LEATHER_CHESTPLATE));
            s.refresh();s.click(ClickType.PICKUP);check(s.menu.getCarried().isEmpty()&&!s.menu.getSlot(1).getItem().isEmpty(),"Invalid native "+invalid+" cannot finish");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void exactReferencesNodeCarriersAndCreateBoundariesRemainClosed(GameTestHelper h) {
        var nativeId=ResourceLocation.parse("test:exact_special_reference");
        for(String type:List.of("armordye","repairitem")) {
            CraftingRecipe exact=type.equals("armordye")?new ArmorDyeRecipe(CraftingBookCategory.MISC):new RepairItemRecipe(CraftingBookCategory.MISC);
            CraftingRecipe subclass=type.equals("armordye")?new ArmorDyeRecipe(CraftingBookCategory.MISC){}:new RepairItemRecipe(CraftingBookCategory.MISC){};
            check(SpecialCraftingDefinition.standard(exact)&&!SpecialCraftingDefinition.standard(subclass),"Exact class and serializer admission, never unknown subclass");
            var json=graph(type,false).json();json.getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("recipe").addProperty("recipe_id",nativeId.toString());
            try(var s=new Session(h,new GraphRecipe(json),new RecipeHolder<>(nativeId,exact))) {
                var definition=GraphIndex.get(h.getLevel()).graphs().get(ID.toString());check(definition!=null,"Exact native reference admitted");
                var node=definition.nodes().get("special");var input=new ItemStack(Items.LEATHER_CHESTPLATE);
                boolean rejected=false;try{CreateNativeRecipes.crafterAdmitted(h.getLevel(),new GraphOperation(h.getLevel(),definition,node,input,List.of()),CraftingInput.of(1,1,List.of(input)));}catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"Create mechanical crafter must not widen special graph admission");
                rejected=false;try{CreateNativeRecipes.processing(h.getLevel(),definition,node,input,com.simibubi.create.AllRecipeTypes.MIXING.getType());}catch(IllegalArgumentException expected){rejected=true;}
                check(rejected,"Create basin must not widen special graph admission");
                boolean unknown=false;try{PreviewRecipeData.carriedInputs(definition,definition.nodes().get("end"));}catch(PreviewRecipeData.UnknownDynamicCarrier expected){unknown=true;}
                check(unknown,"Special successor cannot invent graph-default static carrier");
            }
            try(var s=new Session(h,new GraphRecipe(json),new RecipeHolder<>(nativeId,subclass))) {check(!GraphIndex.get(h.getLevel()).graphs().containsKey(ID.toString()),"Referenced subclass rejected before codec erases subtype");}
            var override=graph(type,false).json();override.getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("transitional_item","minecraft:gold_ingot");
            try(var s=new Session(h,new GraphRecipe(override))) {check(!GraphIndex.get(h.getLevel()).graphs().containsKey(ID.toString()),"NodeDefinition carrier override forbidden for special crafting");}
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void specialNativeRemainderMutationAndReentryAbortBeforePaymentAndRng(GameTestHelper h) {
        for(String action:List.of("grid","result","reload","reentry"))try(var s=new Session(h,graph("armordye",false))) {
            s.fill(SpecialDyeRemainderFixture.ITEM);int[] calls={0};
            SpecialDyeRemainderFixture.callback=()->{calls[0]++;switch(action){
                case "grid" -> s.menu.getSlot(1).getItem().set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("callback mutation"));
                case "result" -> s.menu.getSlot(0).set(new ItemStack(Items.STONE));
                case "reload" -> {h.getLevel().getRecipeManager().replaceRecipes(List.of());GraphIndex.invalidate();}
                case "reentry" -> {s.menu.clicked(0,0,ClickType.PICKUP,s.player);s.menu.getSlot(2).set(new ItemStack(Items.RED_DYE,2));}
            }};
            var random=h.getLevel().random;random.setSeed(7711);long next=random.nextLong();random.setSeed(7711);s.click(ClickType.PICKUP);
            check(calls[0]==1&&s.menu.getCarried().isEmpty()&&s.menu.getSlot(1).getItem().getCount()==1&&s.menu.getSlot(2).getItem().getCount()==2&&random.nextLong()==next,"Special remainder "+action+" invalidation/reentry aborts before payment/RNG");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void specialRemainderAndShiftOneUnitRoundsRemainNative(GameTestHelper h) {
        try(var s=new Session(h,graph("armordye",false))) {
            s.fill(SpecialDyeRemainderFixture.ITEM);s.click(ClickType.PICKUP);
            check(Workpiece.has(s.menu.getCarried())&&count(s,Items.GLASS_BOTTLE)==1&&s.menu.getSlot(2).getItem().getCount()==1,"Real native dye remainder kept, occupied dye cell not overwritten");
        }
        try(var s=new Session(h,graph("armordye",true))) {
            s.put(1,new ItemStack(Items.LEATHER_CHESTPLATE,2));s.put(2,new ItemStack(Items.RED_DYE,2));s.refresh();
            // Native DyedItemColor.applyDyes already produces exactly one unit from stacked armor.
            s.click(ClickType.QUICK_MOVE);check(count(s,Items.DIAMOND)==4&&count(s,Items.EMERALD)==6&&s.menu.getSlot(1).getItem().isEmpty(),"Shift executes native-valid count-two armor in two one-unit rounds");
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void crafterRepairFinishMultioutputUsesNativeConditionsAndNoDefaultCarrier(GameTestHelper h) {
        var pos=h.absolutePos(new BlockPos(1,1,1));var target=pos.east();
        try(var s=new Session(h,graph("repairitem",true))) {
            var level=h.getLevel();level.setBlockAndUpdate(target,Blocks.CHEST.defaultBlockState());
            level.setBlockAndUpdate(pos,Blocks.CRAFTER.defaultBlockState().setValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.ORIENTATION,net.minecraft.core.FrontAndTop.EAST_UP));
            var machine=(CrafterBlockEntity)level.getBlockEntity(pos);machine.setSlotState(0,false);
            var base=new ItemStack(Items.LEATHER_CHESTPLATE);base.setDamageValue(60);machine.setItem(5,base.copy());machine.setItem(8,base.copy());
            level.getBlockState(pos).tick(level,pos,level.random);
            var chest=(ChestBlockEntity)level.getBlockEntity(target);int diamonds=0,emeralds=0;
            for(int i=0;i<chest.getContainerSize();i++){var item=chest.getItem(i);if(item.is(Items.DIAMOND))diamonds+=item.getCount();else if(item.is(Items.EMERALD))emeralds+=item.getCount();else check(item.isEmpty(),"No native/default carrier granted on finish");}
            check(diamonds==2&&emeralds==3&&machine.getItem(5).isEmpty()&&machine.getItem(8).isEmpty()&&machine.isSlotDisabled(0),"Vanilla crafter repair finish consumes two offset cells and delivers all graph outputs");
        } finally {h.getLevel().removeBlock(pos,false);h.getLevel().removeBlock(target,false);}h.succeed();
    }
    @GameTest(template="test_empty",timeoutTicks=100)
    public static void featureDisabledNativeDyeCarrierCannotAdvanceEvenAtFinish(GameTestHelper h) {
        var disabled=new ItemStack(CampfireFeatureFixture.ITEM);
        var nativeRecipe=new ArmorDyeRecipe(CraftingBookCategory.MISC);
        var input=CraftingInput.of(2,1,List.of(disabled.copy(),new ItemStack(Items.RED_DYE)));
        var expected=nativeRecipe.assemble(input,h.getLevel().registryAccess());
        check(nativeRecipe.matches(input,h.getLevel())&&expected.is(CampfireFeatureFixture.ITEM)
            &&expected.has(DataComponents.DYED_COLOR)&&!expected.isItemEnabled(h.getLevel().enabledFeatures()),
            "Feature fixture genuinely assembles native dyed disabled carrier, not invalid dye input");
        for(boolean finish:List.of(false,true)) {
            var json=graph("armordye",finish).json();json.add("ingredient",JsonParser.parseString("{\"item\":\"graph_assembly:campfire_disabled_fixture\"}"));
            try(var s=new Session(h,new GraphRecipe(json),new RecipeHolder<>(ResourceLocation.parse("test:ordinary_disabled_dye"),nativeRecipe))) {
                s.put(1,disabled.copy());s.put(2,new ItemStack(Items.RED_DYE));s.refresh();
                var random=h.getLevel().random;random.setSeed(8851);long next=random.nextLong();random.setSeed(8851);s.click(ClickType.PICKUP);
                check(s.menu.getCarried().isEmpty()&&!s.menu.getSlot(0).hasItem()&&s.menu.getSlot(1).getItem().getCount()==1
                    &&s.menu.getSlot(2).getItem().getCount()==1&&random.nextLong()==next,
                    "Disabled native carrier cannot progress/pay/roll or settle enabled graph finish outputs; finish="+finish);
            }
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void shiftStopsAfterInvalidatedLaterSpecialRoundWithoutOrdinaryFallback(GameTestHelper h) {
        try(var s=new Session(h,graph("armordye",true),new RecipeHolder<>(ResourceLocation.parse("test:ordinary_shift_dye"),new ArmorDyeRecipe(CraftingBookCategory.MISC)))) {
            s.put(1,new ItemStack(Items.LEATHER_CHESTPLATE,2));s.put(2,new ItemStack(SpecialDyeRemainderFixture.ITEM,2));s.refresh();
            int[] calls={0};SpecialDyeRemainderFixture.callback=()->{if(++calls[0]==2)s.menu.getSlot(1).getItem().set(DataComponents.CUSTOM_NAME,net.minecraft.network.chat.Component.literal("later callback"));};
            s.click(ClickType.QUICK_MOVE);
            check(calls[0]==2&&count(s,Items.DIAMOND)==2&&count(s,Items.EMERALD)==3&&count(s,Items.GLASS_BOTTLE)==1
                &&count(s,Items.LEATHER_CHESTPLATE)==0&&s.menu.getSlot(1).getItem().getCount()==1&&s.menu.getSlot(2).getItem().getCount()==1,
                "Shift preserves first completed bundle but aborts invalidated second round without native fallback or second remainder payment");
        }h.succeed();
    }

}
