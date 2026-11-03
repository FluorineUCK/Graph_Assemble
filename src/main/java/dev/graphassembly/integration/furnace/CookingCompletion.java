package dev.graphassembly.integration.furnace;

import com.google.gson.JsonObject;
import dev.graphassembly.core.*;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import java.util.*;

/** Shared prepared graph settlement for native machines/menus: external codecs run before RNG. */
public final class CookingCompletion {
    private record Key(String item,JsonObject components) {}
    private final CompletionPlan plan;
    private final Map<Key,ItemStack> templates;
    private final ItemStack carrier;
    private CookingCompletion(CompletionPlan plan,Map<Key,ItemStack> templates,ItemStack carrier){this.plan=plan;this.templates=templates;this.carrier=carrier;}
    public static CookingCompletion prepare(Level level,GraphOperation operation) {
        return prepare(level,operation,ItemStack.EMPTY);
    }
    /** Prepared native result changes only the nonterminal carrier, never primary graph state. */
    public static CookingCompletion prepare(Level level,GraphOperation operation,ItemStack nativeCarrier) {
        var primary=operation.primary();String item=BuiltInRegistries.ITEM.getKey(primary.getItem()).toString();
        var plan=GraphEngine.plan(operation.graph(),Workpiece.read(level,primary),operation.node().id(),item);
        var templates=new HashMap<Key,ItemStack>();
        if(plan.settled())for(var output:OutputTrees.leaves(plan.pool())) {
            var key=new Key(output.item(),output.components());
            // Decode each distinct complete component definition before the final validation/RNG boundary.
            templates.computeIfAbsent(key,ignored->NativeSteps.output(level,output));
        }
        var carrier=plan.settled()?ItemStack.EMPTY:!nativeCarrier.isEmpty()?nativeCarrier.copy():plan.item().equals(item)?primary:NativeSteps.stack(plan.item());
        return new CookingCompletion(plan,templates,carrier);
    }
    /** Every possible decoded outcome is checked before drawing any graph randomness. */
    public boolean outputsEnabled(Level level) {
        return (carrier.isEmpty()||carrier.isItemEnabled(level.enabledFeatures()))
            &&templates.values().stream().allMatch(stack->stack.isItemEnabled(level.enabledFeatures()));
    }
    public boolean fitsSingleSlot(Level level) {
        if (!outputsEnabled(level)) return false;
        if (!plan.settled()) return !carrier.isEmpty() && carrier.getCount()==1 && carrier.getMaxStackSize()>=1;
        return OutputTrees.allMaximalOutcomesFit(plan.pool(), outputs -> {
            long count=0;
            for(var output:outputs) {
                var template=templates.get(new Key(output.item(),output.components()));
                if(template==null || template.isEmpty() || output.count()>template.getMaxStackSize())return false;
                count+=output.count();
            }
            return count<=1;
        });
    }
    public List<ItemStack> roll(RandomSource random) {
        var completion=plan.roll(random::nextDouble);
        if(!completion.settled())return List.of(Workpiece.write(carrier,completion.state()));
        return completion.outputs().stream().map(output->{
            var template=templates.get(new Key(output.item(),output.components()));
            if(template==null)throw new IllegalStateException("Unprepared graph outcome");
            return template.copyWithCount(output.count());
        }).toList();
    }
}
