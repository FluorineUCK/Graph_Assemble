package dev.graphassembly.integration.projection;

import com.simibubi.create.content.processing.recipe.ProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.SequencedAssemblyRecipe;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.*;
import dev.graphassembly.recipe.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.Level;
import java.util.*;
import java.util.function.Predicate;

/** Per-manager derived definitions. Never writes the RecipeManager or its client synchronization payload. */
public final class NativeRecipeCatalog {
    private static final Map<RecipeManager, State> STATES = new WeakHashMap<>();
    private NativeRecipeCatalog() {}

    public static final class State {
        private List<RecipeHolder<?>> snapshot = List.of();
        private final Set<Recipe<?>> ordinary = Collections.newSetFromMap(new IdentityHashMap<>());
        private Object epoch = new Object();
        private boolean initialized;
        private final Map<Object,List<RecipeHolder<?>>> searches = new WeakHashMap<>();
        private final Map<String,ProcessingRecipe<?,?>> projections = new LinkedHashMap<>();
        private boolean projected;
        private State() {}
        public Object epoch() { return epoch; }
        private void update(Collection<RecipeHolder<?>> recipes) {
            var now = new ArrayList<>(recipes);
            if (initialized && now.size() == snapshot.size()) {
                boolean same = true;
                for (int i=0;i<now.size();i++) if (now.get(i) != snapshot.get(i)) { same=false;break; }
                if (same) return;
            }
            epoch = new Object(); snapshot = List.copyOf(now); ordinary.clear(); searches.clear(); projections.clear(); projected=false; initialized=true;
        }
        private void ordinary(Level level, Recipe<?> source) {
            if (!(source instanceof ProcessingRecipe<?,?> || source instanceof SmithingRecipe || source instanceof SequencedAssemblyRecipe)
                || !ordinary.add(source)) return;
            try { IngredientDecoration.markOrdinary(level, source); }
            catch (RuntimeException error) {
                // Preserve native identity and do not claim codec-bypassing materials are protected.
                GraphAssembly.LOGGER.warn("Native recipe materials could not be marked for {}: {}", source.getClass().getName(), error.getMessage());
            }
        }
    }

    public static synchronized State get(Level level) {
        var manager=level.getRecipeManager();
        var state=STATES.computeIfAbsent(manager, ignored -> new State());
        if (!state.initialized) state.update(manager.getRecipes());
        return state;
    }
    public static synchronized void release(RecipeManager manager) { STATES.remove(manager); }

    /** Create RecipeFinder is a server-side execution lookup, unlike JEI or a global ingredient predicate. */
    public static List<RecipeHolder<?>> find(Object key, Level level, Predicate<RecipeHolder<?>> filter) {
        if (level.isClientSide() || level.getServer()==null || !level.getServer().isSameThread()) return null;
        var state=get(level);
        project(level,state);
        if (key!=null && state.searches.containsKey(key)) return state.searches.get(key);
        var result=new ArrayList<RecipeHolder<?>>();
        for (var entry:state.projections.entrySet()) {
            var hash=com.google.common.hash.Hashing.sha256().hashString(entry.getKey(),java.nio.charset.StandardCharsets.UTF_8).toString();
            RecipeHolder<?> projected = new RecipeHolder<>(GraphAssembly.id("projection/"+hash),entry.getValue());
            if (filter.test(projected)) result.add(projected);
        }
        for (var holder:state.snapshot) {
            if (!filter.test(holder)) continue;
            state.ordinary(level, holder.value());
            result.add(holder);
        }
        if(key!=null)state.searches.put(key,result);
        return result;
    }
    /** Only GraphIndex-validated device paths are published. Discovery alone cannot enable consumption. */
    private static void project(Level level, State state) {
        if(state.projected)return;
        state.projected=true;
        var index=GraphIndex.get(level);
        for(var graph:index.graphs().values()) {
            var json=((GraphRecipe)level.getRecipeManager().byKey(net.minecraft.resources.ResourceLocation.parse(graph.id())).orElseThrow().value()).json().toString();
            for(var node:graph.nodes().values()) {
                if(node.recipe().get("type").getAsString().equals("graph_assembly:anvil"))continue;
                if(node.recipe().get("type").getAsString().equals("minecraft:stonecutting"))continue;
                if(node.recipe().get("type").getAsString().equals("minecraft:smithing_transform")||node.recipe().get("type").getAsString().equals("minecraft:smithing_trim"))continue;
                if(dev.graphassembly.integration.crafting.SpecialCraftingDefinition.SERIALIZERS.contains(node.recipe().get("type").getAsString())
                    || dev.graphassembly.integration.crafting.CraftingDefinition.SERIALIZERS.contains(node.recipe().get("type").getAsString())
                    || dev.graphassembly.integration.furnace.CookingDefinition.SERIALIZERS.contains(node.recipe().get("type").getAsString()))continue;
                if(NativeSteps.EXECUTION_SERIALIZERS.contains(node.recipe().get("type").getAsString()))continue;
                var recipe=NativeSteps.create(level,graph,node,NativeSteps.stack(graph.transitionalItem()));
                var alternatives=new com.google.gson.JsonArray();
                if(node.start())alternatives.add(node.ingredient()==null?graph.ingredient():node.ingredient());
                for(var item:PreviewRecipeData.carriedInputs(graph,node)){var ingredient=new com.google.gson.JsonObject();ingredient.addProperty("item",item);alternatives.add(ingredient);}
                if(!alternatives.isEmpty())recipe.getIngredients().set(0,NativeSteps.ingredient(level,alternatives));
                for(int i=0;i<recipe.getIngredients().size();i++) {
                    var base=recipe.getIngredients().get(i);
                    if(!base.isEmpty())recipe.getIngredients().set(i,new GraphGateIngredient(base,graph.id(),json,node.id(),i).toVanilla());
                }
                state.projections.put(graph.id()+"\u0000"+node.id(),recipe);
            }
        }
    }
    public static ProcessingRecipe<?,?> projection(Level level,String graph,String node) {
        var state=get(level);project(level,state);return state.projections.get(graph+"\u0000"+node);
    }
}
