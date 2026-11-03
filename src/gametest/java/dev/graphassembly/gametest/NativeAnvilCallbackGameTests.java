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
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.entity.player.AnvilRepairEvent;
import net.neoforged.neoforge.gametest.*;
import java.util.*;
import java.util.function.Consumer;

/** Bounded real-event regressions for preview extraction and one-shot native payment. */
@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class NativeAnvilCallbackGameTests {
    private static final class Session implements AutoCloseable {
        final GameTestHelper h;final List<RecipeHolder<?>> saved;final BlockPos pos;
        final net.neoforged.neoforge.common.util.FakePlayer player;final AnvilMenu menu;
        final ItemStack nativeResult,nativeRemainder;final int nativeXp;
        Session(GameTestHelper h) {
            this.h=h;var level=h.getLevel();saved=new ArrayList<>(level.getRecipeManager().getRecipes());pos=h.absolutePos(new BlockPos(1,1,1));
            player=new net.neoforged.neoforge.common.util.FakePlayer(level,new com.mojang.authlib.GameProfile(UUID.randomUUID(),"anvil-callback"));
            level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.setPos(pos.getX()+0.5,pos.getY()+0.5,pos.getZ()+2);
            player.getInventory().clearContent();player.getAbilities().instabuild=false;player.experienceLevel=30;
            menu=new AnvilMenu(4,player.getInventory(),ContainerLevelAccess.create(level,pos));player.containerMenu=menu;
            level.getRecipeManager().replaceRecipes(List.of());GraphIndex.invalidate();fill();
            check(menu.getSlot(2).hasItem()&&menu.getCost()>0,"Callback fixture has a real ordinary native repair control");
            menu.clicked(2,0,ClickType.PICKUP,player);nativeResult=menu.getCarried().copy();nativeRemainder=menu.getSlot(1).getItem().copy();nativeXp=player.experienceLevel;
            check(nativeResult.is(Items.IRON_SWORD)&&nativeResult.getDamageValue()<100&&nativeXp<30&&nativeRemainder.getCount()>0,"Ordinary control really repairs, pays and leaves native material remainder");
            var graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_sword"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"repair","start":true,"recipe":{"type":"graph_assembly:anvil","action":"repair","addition":{"item":"minecraft:iron_ingot"}}},
                   {"id":"finish","finish":true,"recipe":{"type":"create:pressing"}}],
                 "edges":[{"from":"repair","to":"finish"}],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
                """).getAsJsonObject());
            level.getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:anvil_callback"),graph)));GraphIndex.invalidate();
            menu.setCarried(ItemStack.EMPTY);menu.getSlot(1).set(ItemStack.EMPTY);level.setBlockAndUpdate(pos,Blocks.ANVIL.defaultBlockState());player.experienceLevel=30;fill();
            check(Workpiece.has(menu.getSlot(2).getItem()),"Callback fixture begins with an actual graph repair preview");
        }
        void fill(){var sword=new ItemStack(Items.IRON_SWORD);sword.setDamageValue(100);menu.getSlot(0).set(sword);menu.getSlot(1).set(new ItemStack(Items.IRON_INGOT,3));menu.slotsChanged(menu.getSlot(0).container);}
        ItemStack remove(String path) {
            return switch(path){case "slot"->menu.getSlot(2).remove(1);case "container"->menu.getSlot(2).container.removeItem(0,1);case "no-update"->menu.getSlot(2).container.removeItemNoUpdate(0);default->throw new IllegalArgumentException(path);};
        }
        void paidOnce(){
            var result=menu.getCarried();var stripped=result.copy();stripped.remove(GraphAssembly.PROGRESS.get());var progress=Workpiece.read(h.getLevel(),result);
            check(ItemStack.matches(stripped,nativeResult)&&player.experienceLevel==nativeXp&&menu.getSlot(0).getItem().isEmpty()
                &&ItemStack.matches(menu.getSlot(1).getItem(),nativeRemainder)&&progress!=null&&progress.current().equals("repair")&&progress.history().equals(List.of("repair"))
                &&player.getInventory().isEmpty(),"Exactly one graph carrier and native-control XP/material payment");
        }
        public void close(){for(int i=0;i<3;i++)menu.getSlot(i).set(ItemStack.EMPTY);menu.setCarried(ItemStack.EMPTY);menu.removed(player);player.getInventory().clearContent();player.containerMenu=player.inventoryMenu;h.getLevel().removeBlock(pos,false);h.getLevel().getRecipeManager().replaceRecipes(saved);GraphIndex.invalidate();}
    }
    private static void check(boolean ok,String message){if(!ok)throw new GameTestAssertException(message);}

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeUpdateCallbackCannotExtractPreviousGraphPreview(GameTestHelper h) {
        for(String path:List.of("slot","container","no-update"))try(var s=new Session(h)) {
            var escaped=new ArrayList<ItemStack>();
            Consumer<AnvilUpdateEvent> listener=event->{if(event.getPlayer()==s.player&&s.player.containerMenu==s.menu&&escaped.isEmpty())escaped.add(s.remove(path).copy());};
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                var random=h.getLevel().random;random.setSeed(48111);long next=random.nextLong();random.setSeed(48111);s.menu.createResult();
                check(escaped.size()==1&&escaped.getFirst().isEmpty(),"Native AnvilUpdateEvent "+path+" cannot extract old graph preview");
                check(s.player.experienceLevel==30&&s.menu.getCarried().isEmpty()&&s.menu.getSlot(0).getItem().getDamageValue()==100&&s.menu.getSlot(1).getItem().getCount()==3
                    &&random.nextLong()==next,"Update callback extraction probe does not pay, deliver or roll");
            } finally {NeoForge.EVENT_BUS.unregister(listener);}
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeRepairCallbackCannotExtractAdditionalNativeResult(GameTestHelper h) {
        for(String path:List.of("slot","container","no-update"))try(var s=new Session(h)) {
            var escaped=new ArrayList<ItemStack>();
            Consumer<AnvilRepairEvent> listener=event->{if(event.getEntity()==s.player&&s.player.containerMenu==s.menu){escaped.add(s.remove(path).copy());event.setBreakChance(0);}};
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                s.menu.clicked(2,0,ClickType.PICKUP,s.player);
                check(escaped.size()==1&&escaped.getFirst().isEmpty(),"Native AnvilRepairEvent "+path+" cannot extract another native result during graph payment");
                s.paidOnce();
            } finally {NeoForge.EVENT_BUS.unregister(listener);}
        }h.succeed();
    }

    @GameTest(template="test_empty",timeoutTicks=100)
    public static void nativeRepairCallbackBareOnTakeCannotReusePaymentAuthorization(GameTestHelper h) {
        try(var s=new Session(h)) {
            int[] events={0};boolean[] attempted={false};
            Consumer<AnvilRepairEvent> listener=event->{
                if(event.getEntity()!=s.player||s.player.containerMenu!=s.menu)return;
                events[0]++;event.setBreakChance(1);
                if(!attempted[0]){attempted[0]=true;s.menu.getSlot(2).onTake(s.player,event.getOutput().copy());}
            };
            NeoForge.EVENT_BUS.addListener(listener);
            try {
                s.menu.clicked(2,0,ClickType.PICKUP,s.player);
                check(attempted[0]&&events[0]==1,"Native repair callback direct Slot.onTake must not reuse outer one-shot authorization");
                s.paidOnce();check(h.getLevel().getBlockState(s.pos).is(Blocks.CHIPPED_ANVIL),"Forced native damage executes once, not again through callback reentry");
                // A later valid graph preview must still reject bare onTake after the narrow scope closes.
                s.menu.setCarried(ItemStack.EMPTY);s.fill();int xp=s.player.experienceLevel;
                s.menu.getSlot(2).onTake(s.player,s.menu.getSlot(2).getItem().copy());
                check(events[0]==1&&s.player.experienceLevel==xp&&s.menu.getSlot(0).getItem().getDamageValue()==100&&s.menu.getSlot(1).getItem().getCount()==3,
                    "Authorization cleanup leaves later graph preview bare-onTake guarded");
            } finally {NeoForge.EVENT_BUS.unregister(listener);}
        }h.succeed();
    }
}
