package dev.graphassembly.integration;

import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.Level;
import java.util.List;
import java.lang.ref.WeakReference;

/** Pure definition baseline: never resolves graph data or invokes recipe/component codecs. */
public final class RecipeSnapshot {
    private final WeakReference<RecipeManager> manager;
    private final List<RecipeHolder<?>> definitions;

    private RecipeSnapshot(RecipeManager manager) {
        this.manager=new WeakReference<>(manager);
        definitions=List.copyOf(manager.getRecipes());
    }
    public static RecipeSnapshot capture(Level level) {return capture(level.getRecipeManager());}
    public static RecipeSnapshot capture(RecipeManager manager) {return new RecipeSnapshot(manager);}
    public boolean current(Level level) {return current(level.getRecipeManager());}
    public boolean current(RecipeManager currentManager) {
        if(currentManager!=manager.get())return false;
        var now=List.copyOf(currentManager.getRecipes());
        if(now.size()!=definitions.size())return false;
        for(int i=0;i<now.size();i++)if(now.get(i)!=definitions.get(i))return false;
        return true;
    }
}
