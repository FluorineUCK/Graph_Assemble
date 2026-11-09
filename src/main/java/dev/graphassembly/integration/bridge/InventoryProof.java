package dev.graphassembly.integration.bridge;

import java.lang.ref.WeakReference;
import java.util.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/** Server-only transient provenance, not serialized data or inferred machine slot semantics. */
public final class InventoryProof {
    public interface StackMeta {
        Origin graphAssembly$origin(); void graphAssembly$origin(Origin o);
        ResultReceipt graphAssembly$receipt(); void graphAssembly$receipt(ResultReceipt r);
    }
    public static Object epoch(){var s=ServerLifecycleHooks.getCurrentServer();return s!=null&&s.isSameThread()&&s.overworld()!=null?NativeRecipeCatalog.get(s.overworld()).epoch():null;}
    public static final class Origin {
        final WeakReference<IItemHandler> owner;
        final WeakReference<ItemStack> actual;
        final WeakReference<BlockEntity> machine;
        final int slot,count;
        final net.minecraft.core.component.DataComponentPatch components;
        final Object epoch;
        boolean graphClaimed;
        Origin(IItemHandler owner,BlockEntity machine,int slot,ItemStack actual,Object epoch){
            this.owner=new WeakReference<>(owner);this.actual=new WeakReference<>(actual);this.slot=slot;this.count=actual.getCount();
            this.machine=new WeakReference<>(machine);
            components=actual.getComponentsPatch();this.epoch=epoch;
        }
        boolean matches(ItemStack s,Object e){return epoch==e&&actual.get()==s&&count==s.getCount()&&components.equals(s.getComponentsPatch());}
        ItemStack live(){var h=owner.get();return h==null?ItemStack.EMPTY:h.getStackInSlot(slot);}
        ServerLevel level(){var block=machine.get();return block!=null&&!block.isRemoved()&&block.getLevel() instanceof ServerLevel level?level:null;}
        boolean valid(){if(epoch!=InventoryProof.epoch()||level()==null)return false;var stack=live();return !stack.isEmpty()&&((StackMeta)(Object)stack).graphAssembly$origin()==this;}
    }
    public static void read(IItemHandler owner,BlockEntity machine,Map<Integer,Origin> origins,int slot,ItemStack stack){
        var e=epoch();if(e==null||machine==null||stack.isEmpty()){origins.remove(slot);return;}
        var o=origins.get(slot);if(o==null||!o.matches(stack,e)){o=new Origin(owner,machine,slot,stack,e);origins.put(slot,o);}
        ((StackMeta)(Object)stack).graphAssembly$origin(o);
        // A stored stack is no longer an intermediate result receiving template components.
        ((StackMeta)(Object)stack).graphAssembly$receipt(null);
    }
    public static void copied(ItemStack a,ItemStack b){if(b.isEmpty()||a==b)return;var from=(StackMeta)(Object)a;var to=(StackMeta)(Object)b;to.graphAssembly$origin(from.graphAssembly$origin());to.graphAssembly$receipt(from.graphAssembly$receipt());}
    public static boolean staleComparison(ItemStack a,ItemStack b){
        var x=((StackMeta)(Object)a).graphAssembly$origin();var y=((StackMeta)(Object)b).graphAssembly$origin();
        if(x==null||y==null||(!x.graphClaimed&&!y.graphClaimed))return false;
        var owner=x.owner.get();if(owner==null||owner!=y.owner.get()||x.slot!=y.slot)return false;
        // A live inventory read refreshes its token after reload/change. Comparing two
        // detached snapshots of the same revision must still be reflexive and vanilla-like.
        return epoch()!=null&&x!=y;
    }
}
