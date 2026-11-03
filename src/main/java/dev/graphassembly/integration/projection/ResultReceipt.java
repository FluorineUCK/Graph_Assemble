package dev.graphassembly.integration.projection;
import dev.graphassembly.GraphAssembly;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.*;
import java.util.HashSet;

/** Only a transient produced-stack receipt; contains no world, recipe or inventory references. */
public record ResultReceipt(String progress,Integer stackSize,DataComponentMap full) {
    public static ItemStack protect(ItemStack source,boolean full){
        var out=source.copy();if(!out.isEmpty())((InventoryProof.StackMeta)(Object)out).graphAssembly$receipt(new ResultReceipt(out.get(GraphAssembly.PROGRESS.get()),out.get(DataComponents.MAX_STACK_SIZE),full?DataComponentMap.builder().addAll(out.getComponents()).build():null));return out;
    }
    private static <T> void set(ItemStack stack,TypedDataComponent<T> component){stack.set(component.type(),component.value());}
    public void restore(ItemStack stack){
        if(full!=null){for(var type:new HashSet<>(stack.getComponents().keySet()))if(!full.has(type))stack.remove(type);for(var c:full)set(stack,c);return;}
        if(progress==null)stack.remove(GraphAssembly.PROGRESS.get());else stack.set(GraphAssembly.PROGRESS.get(),progress);
        if(stackSize==null)stack.remove(DataComponents.MAX_STACK_SIZE);else stack.set(DataComponents.MAX_STACK_SIZE,stackSize);
    }
}
