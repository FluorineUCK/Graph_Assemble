package dev.graphassembly.integration;

import dev.graphassembly.integration.sequence.NativeSequenceCompletion;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.List;
import java.util.function.*;

/** Prepared device outputs; graph rules and native sequence progression have separate owners. */
public interface OperationCompletion {
    static OperationCompletion prepare(Level level,GraphOperation operation){return prepare(level,operation,ItemStack.EMPTY);}
    static OperationCompletion prepare(Level level,GraphOperation operation,ItemStack nativeCarrier) {
        return operation.sequence()==null?GraphCompletion.prepare(level,operation,nativeCarrier):NativeSequenceCompletion.prepare(level,operation);
    }
    boolean outputsEnabled(Level level);
    boolean fitsSingleItem(Level level);
    boolean terminal();
    int maxStacks();
    ItemStack preview();
    boolean allMaximalOutcomesFit(Predicate<List<ItemStack>> fits);
    boolean allOutcomesFit(Consumer<ItemStack> prepare,ToIntFunction<ItemStack> limit,Predicate<List<ItemStack>> fits);
    List<ItemStack> roll(RandomSource random);
}
