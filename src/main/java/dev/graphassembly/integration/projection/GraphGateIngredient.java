package dev.graphassembly.integration.projection;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.graphassembly.core.*;
import dev.graphassembly.recipe.Workpiece;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.common.crafting.*;
import net.neoforged.neoforge.registries.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.Arrays;
import java.util.stream.Stream;

/** Pure definition check plus an optional query-local proof. No last-input singleton. */
@EventBusSubscriber(modid="graph_assembly",bus=EventBusSubscriber.Bus.MOD)
public final class GraphGateIngredient implements ICustomIngredient {
    static final MapCodec<GraphGateIngredient> CODEC=RecordCodecBuilder.mapCodec(i->i.group(
        Ingredient.CODEC.fieldOf("base").forGetter(g->g.base),
        Codec.STRING.optionalFieldOf("graph_id","").forGetter(g->g.graphId),
        Codec.STRING.optionalFieldOf("definition","").forGetter(g->g.definition),
        Codec.STRING.optionalFieldOf("node","").forGetter(g->g.nodeId),
        Codec.INT.optionalFieldOf("role",0).forGetter(g->g.role)
    ).apply(i,GraphGateIngredient::new));
    static final IngredientType<GraphGateIngredient> TYPE=new IngredientType<>(CODEC);
    final Ingredient base;final String graphId,definition,nodeId;final int role;final GraphDefinition graph;
    transient java.util.function.Predicate<ItemStack> proof;
    public GraphGateIngredient(Ingredient base,String graphId,String definition,String nodeId,int role){
        this.base=base;this.graphId=graphId;this.definition=definition;this.nodeId=nodeId;this.role=role;
        graph=definition.isEmpty()?null:GraphCodec.parse(graphId,JsonParser.parseString(definition).getAsJsonObject());
    }
    @SubscribeEvent public static void register(RegisterEvent e){e.register(NeoForgeRegistries.Keys.INGREDIENT_TYPES,
        h->h.register(ResourceLocation.parse("graph_assembly:native_graph_input"),TYPE));}
    static Ingredient ordinary(Ingredient base){return new GraphGateIngredient(base,"","","",0).toVanilla();}
    public boolean test(ItemStack stack){
        // An unbound graph template is a definition, never an executable candidate.
        return accepts(stack)&&(graph==null||(proof!=null&&proof.test(stack)));
    }
    private boolean accepts(ItemStack stack){
        if(!base.test(stack))return false;
        if(graph==null)return !Workpiece.has(stack);
        var node=graph.nodes().get(nodeId);if(node==null)return false;
        var requirement=node.workpieceInput(role);
        if(!Workpiece.has(stack))return requirement==null&&(role!=0||node.start());
        if(role!=0&&requirement==null)return false;
        try {var state=Workpiece.read(stack);return state!=null&&GraphEngine.canEnter(graph,state,nodeId)
            &&(requirement==null||requirement.matches(state))&&(role!=0||GraphEngine.allowed(graph,state).contains(nodeId));}
        catch(RuntimeException malformed){return false;}
    }
    public Stream<ItemStack> getItems(){return Arrays.stream(base.getItems()).map(ItemStack::copy);}
    public boolean isSimple(){return false;}
    public IngredientType<?> getType(){return TYPE;}
}
