package dev.graphassembly.integration.sequence;

import com.simibubi.create.AllDataComponents;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.graphassembly.integration.*;
import dev.graphassembly.mixin.create.sequence.SequenceAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.*;

/** Read-only native alternatives; only final commit calls Create's advance/weighted result logic. */
public final class NativeSequenceCompletion implements OperationCompletion {
    private final Level level;
    private final NativeSequences.Stage stage;
    private final ItemStack input;
    private final boolean terminal;
    private final List<ItemStack> alternatives;
    private NativeSequenceCompletion(Level level,GraphOperation operation) {
        this.level=level;stage=operation.sequence();input=operation.primary();
        if(!stage.current(level)||!stage.matches(input))throw new IllegalArgumentException("Stale or ineligible sequence operation");
        var parent=stage.holder().value();var state=input.get(AllDataComponents.SEQUENCED_ASSEMBLY);
        int step=state==null?0:state.step();terminal=(step+1)/parent.getSequence().size()>=parent.getLoops();
        if(terminal)alternatives=parent.resultPool.stream().filter(out->out.getChance()>0).map(out->out.getStack().copy()).toList();
        else {
            var next=parent.getTransitionalItem().copyWithCount(1);
            next.set(AllDataComponents.SEQUENCED_ASSEMBLY,new SequencedAssemblyRecipe.SequencedAssembly(stage.holder().id(),step+1,
                ((float)step+1)/(parent.getSequence().size()*parent.getLoops())));
            alternatives=List.of(next);
        }
    }
    public static NativeSequenceCompletion prepare(Level level,GraphOperation operation){return new NativeSequenceCompletion(level,operation);}
    public boolean outputsEnabled(Level level){return alternatives.stream().allMatch(stack->stack.isItemEnabled(level.enabledFeatures()));}
    public boolean fitsSingleItem(Level level){return outputsEnabled(level)&&alternatives.stream().allMatch(stack->stack.getCount()==1&&stack.getMaxStackSize()>=1);}
    public boolean terminal(){return terminal;}
    public int maxStacks(){return 1;}
    public ItemStack preview(){return alternatives.getFirst().copy();}
    public boolean allMaximalOutcomesFit(Predicate<List<ItemStack>> fits){return alternatives.stream().allMatch(stack->fits.test(List.of(stack.copy())));}
    public boolean allOutcomesFit(Consumer<ItemStack> prepare,ToIntFunction<ItemStack> limit,Predicate<List<ItemStack>> fits) {
        return allMaximalOutcomesFit(items->{items.forEach(prepare);return fits.test(items);});
    }
    public List<ItemStack> roll(RandomSource random) {
        if(!stage.current(level))throw new IllegalStateException("Native sequence changed before commit");
        var result=((SequenceAccess)stage.holder().value()).graphAssembly$advance(stage.holder().id(),input,random);
        return result.isEmpty()?List.of():List.of(result);
    }
}
