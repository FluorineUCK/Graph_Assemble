package dev.graphassembly.integration.vanilla.furnace;

import dev.graphassembly.recipe.OperationSpec;

import dev.graphassembly.core.definition.basic.GraphDefinition;
import dev.graphassembly.recipe.*;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.*;

/** Local transaction and persisted operation identity; recipes are rebound on every tick. */
public final class CookingState {
    public final AbstractFurnaceBlockEntity furnace;
    public final RecipeType<?> type;
    public boolean busy,invalid;
    public CookingLookup.Selection selection;
    public ItemStack input=ItemStack.EMPTY,output=ItemStack.EMPTY;
    public String job="";
    public GraphIndex index;
    public double experience;
    private GraphDefinition<OperationSpec> identityGraph;
    private String identityNode;
    private ItemStack identityInput=ItemStack.EMPTY;
    private String cachedIdentity;
    private Level level;
    private BlockPos position;
    private BlockState block;
    public CookingState(AbstractFurnaceBlockEntity furnace,RecipeType<?> type){this.furnace=furnace;this.type=type;}
    public void begin(Level level) {
        busy=true;
        invalid=false;
        this.level=level;
        position=furnace.getBlockPos();
        block=level.getBlockState(position);
        // Recipe/ingredient/component codecs are external code too. Establish the tick transaction
        // before indexing, native template decoding, or operation-identity serialization.
        var baseline=snapshot();
        input=baseline.get(0);
        output=baseline.get(2);
        selection=CookingLookup.select(level,furnace,type);
        checkCallback(baseline);
        if(invalid)return;
        // Ordinary jobs belong entirely to vanilla, including old saves with no graph metadata.
        // Only leaving an actual graph operation needs an explicit cross-family progress reset.
        if(selection.operation()==null) {
            if(!job.isEmpty()) {
                ((CookingStateAccess)furnace).graphAssembly$cookProgress(0);
                ((CookingStateAccess)furnace).graphAssembly$cookTime(CookingLookup.ordinaryTime(level,type,furnace.getItem(0)));
            }
            job="";
            index=null;
            cachedIdentity=null;
            identityGraph=null;
            identityNode=null;
            return;
        }
        var currentIndex=selection.operation()==null?null:GraphIndex.get(level);
        var graph=selection.operation()==null?null:selection.operation().graph();
        var node=selection.operation()==null?null:selection.operation().node().id();
        var primary=furnace.getItem(0);
        String key;
        if(cachedIdentity!=null&&index==currentIndex&&identityGraph==graph&&Objects.equals(identityNode,node)&&ItemStack.isSameItemSameComponents(identityInput,primary))key=cachedIdentity;
        else {
            key=CookingLookup.identity(level,selection,primary.copyWithCount(1));
            identityGraph=graph;
            identityNode=node;
            identityInput=primary.copyWithCount(1);
            cachedIdentity=key;
        }
        checkCallback(baseline);
        if(invalid) {
            cachedIdentity=null;
            return;
        }
        if(!job.equals(key)||currentIndex!=null&&index!=null&&index!=currentIndex) {
            ((CookingStateAccess)furnace).graphAssembly$cookProgress(0);
            ((CookingStateAccess)furnace).graphAssembly$cookTime(selection.recipe()!=null?selection.recipe().value().getCookingTime():CookingLookup.ordinaryTime(level,type,primary));
        }
        job=key;
        index=currentIndex;
    }
    public void end() {
        if(invalid)((CookingStateAccess)furnace).graphAssembly$cookProgress(0);
        selection=null;
        busy=false;
    }
    public boolean currentMachine(){return furnace.getLevel()==level&&!furnace.isRemoved()&&level.getBlockEntity(position)==furnace&&level.getBlockState(position)==block;}
    public boolean valid() {
        return !invalid&&currentMachine()&&selection!=null&&selection.operation()!=null
            && ItemStack.matches(input,furnace.getItem(0))&&ItemStack.matches(output,furnace.getItem(2))
            && selection.operation().matchesPrimary(furnace.getLevel(),furnace.getItem(0))&&!dev.graphassembly.integration.sequence.NativeSequences.tracked(furnace.getItem(1));
    }
    public boolean fits(){return prepare()!=null;}
    /** Capacity and commit share the same decoded settlement within this synchronous check. */
    public dev.graphassembly.integration.OperationCompletion prepare() {
        if(!valid())return null;
        var before=snapshot();
        var prepared=dev.graphassembly.integration.OperationCompletion.prepare(level,selection.operation());
        boolean fits=prepared.allMaximalOutcomesFit(outputs->CookingLookup.merge(furnace.getItem(2),outputs,furnace.getMaxStackSize())!=null);
        checkCallback(before);
        return fits&&valid()?prepared:null;
    }
    public boolean graph(){return busy&&selection!=null&&selection.handled();}
    public List<ItemStack> snapshot(){return List.of(furnace.getItem(0).copy(),furnace.getItem(1).copy(),furnace.getItem(2).copy());}
    public void checkCallback(List<ItemStack> before) {
        for(int i=0;i<3;i++) {
            if(!ItemStack.matches(before.get(i),furnace.getItem(i)))invalid=true;
        }
        if(!currentMachine()||selection!=null&&selection.operation()!=null&&!selection.operation().current(furnace.getLevel())) {
            invalid=true;
        }
    }
}
