package dev.graphassembly.integration.bridge;

import dev.graphassembly.GraphAssembly;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.component.*;
import java.util.HashSet;

/** Transient component handoff. Neither provenance nor a permanent component lock. */
public record ResultReceipt(String progress,Integer stackSize,DataComponentMap full,Scope scope) {
    public enum Kind { CARRIER, TERMINAL, BYPRODUCT }
    /** Shared by copies; a host closes this after native result construction, even on failure. */
    public static final class Scope implements AutoCloseable {
        private boolean active=true;
        @Override public void close(){active=false;}
    }
    public static ItemStack protect(ItemStack source,Kind kind,Scope scope) {
        var out=source.copy();
        if(!out.isEmpty())((InventoryProof.StackMeta)(Object)out).graphAssembly$receipt(new ResultReceipt(
            out.get(GraphAssembly.PROGRESS.get()),out.get(DataComponents.MAX_STACK_SIZE),
            kind==Kind.CARRIER?null:DataComponentMap.builder().addAll(out.getComponents()).build(),scope));
        return out;
    }
    private static <T> void set(ItemStack stack,TypedDataComponent<T> component){stack.set(component.type(),component.value());}
    public void restore(ItemStack stack) {
        if(!scope.active)return;
        if(full!=null) {
            for(var type:new HashSet<>(stack.getComponents().keySet()))if(!full.has(type))stack.remove(type);
            for(var component:full)set(stack,component);
        } else {
            if(progress==null)stack.remove(GraphAssembly.PROGRESS.get());else stack.set(GraphAssembly.PROGRESS.get(),progress);
            if(stackSize==null)stack.remove(DataComponents.MAX_STACK_SIZE);else stack.set(DataComponents.MAX_STACK_SIZE,stackSize);
        }
    }
}
