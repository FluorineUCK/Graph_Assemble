package dev.graphassembly.integration.vanilla.menu;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import java.util.*;

/** Extracted stonecutter transfer/capacity semantics; no machine or graph matching policy. */
public final class MenuTransfer {
    private MenuTransfer() {}
    public record Plan(List<ItemStack> inventory,ItemStack carried) {}
    public static List<ItemStack> inventory(Player player) {
        var result=new ArrayList<ItemStack>();for(int i=0;i<player.getInventory().getContainerSize();i++)result.add(player.getInventory().getItem(i).copy());return result;
    }
    public static final class Limits {
        private record Entry(ItemStack stack,int[] limits) {}
        final int inventoryLimit;final List<Entry> entries=new ArrayList<>();
        public Limits(int inventoryLimit){this.inventoryLimit=inventoryLimit;}
        public void prepare(ItemStack stack) {
            if(stack.isEmpty())return;
            var existing=entries.stream().filter(e->ItemStack.isSameItemSameComponents(e.stack(),stack)).findFirst().orElse(null);
            int start=existing==null?1:existing.limits().length;if(start>stack.getCount())return;
            int[] limits=existing==null?new int[stack.getCount()+1]:Arrays.copyOf(existing.limits(),stack.getCount()+1);
            for(int count=start;count<=stack.getCount();count++)limits[count]=stack.copyWithCount(count).getMaxStackSize();
            if(existing!=null)entries.remove(existing);entries.add(new Entry(stack.copy(),limits));
        }
        public int limit(ItemStack stack) {
            for(var entry:entries)if(ItemStack.isSameItemSameComponents(entry.stack(),stack)&&stack.getCount()<entry.limits().length)return Math.min(inventoryLimit,entry.limits()[stack.getCount()]);
            throw new IllegalStateException("Unprepared menu capacity");
        }
    }
    public static Plan plan(List<ItemStack> source,ItemStack cursor,List<ItemStack> outputs,ClickType type,int button,Limits limits) {
        var inventory=new ArrayList<>(source.stream().map(ItemStack::copy).toList());var carried=cursor.copy();int first=0;
        if(type==ClickType.PICKUP&&!outputs.isEmpty()) {
            var output=outputs.getFirst();if(output.getCount()>limits.limit(output))return null;
            if(carried.isEmpty())carried=output.copy();
            else if(ItemStack.isSameItemSameComponents(carried,output)&&carried.getCount()+output.getCount()<=limits.limit(carried))carried.grow(output.getCount());else return null;
            first=1;
        } else if(type==ClickType.SWAP) {
            int target=button==40?40:button>=0&&button<9?button:-1;if(target<0||!inventory.get(target).isEmpty())return null;
            if(!outputs.isEmpty()){var output=outputs.getFirst();if(output.getCount()>limits.limit(output))return null;inventory.set(target,output.copy());first=1;}
        }
        for(int j=first;j<outputs.size();j++) {
            var incoming=outputs.get(j).copy();
            for(int pass=0;pass<2;pass++)for(int i=0;i<36&&!incoming.isEmpty();i++) {
                var old=inventory.get(i);if((pass==0)==old.isEmpty()||!old.isEmpty()&&!ItemStack.isSameItemSameComponents(old,incoming))continue;
                int amount=Math.min(incoming.getCount(),limits.limit(incoming)-old.getCount());if(amount<=0)continue;
                if(old.isEmpty())inventory.set(i,incoming.copyWithCount(amount));else old.grow(amount);incoming.shrink(amount);
            }
            if(!incoming.isEmpty())return null;
        }
        return new Plan(inventory,carried);
    }
}
