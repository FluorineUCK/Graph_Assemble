package dev.graphassembly.gametest;

import com.google.gson.JsonParser;
import com.simibubi.create.foundation.recipe.RecipeFinder;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.recipe.GraphIndex;
import dev.graphassembly.recipe.GraphRecipe;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import java.util.List;
import dev.graphassembly.integration.*;
import dev.graphassembly.integration.bridge.*;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.component.*;
import net.minecraft.network.chat.Component;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class BridgeContractsGameTests {
    private static final class CandidateFixture implements AutoCloseable {
        final net.minecraft.server.level.ServerLevel level;
        final List<RecipeHolder<?>> saved;
        final net.minecraft.core.BlockPos pos;
        final GraphRecipe graph;
        final com.simibubi.create.foundation.item.SmartInventory inventory;
        final com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?> source;
        CandidateFixture(GameTestHelper helper) {
            level=helper.getLevel();saved=List.copyOf(level.getRecipeManager().getRecipes());
            pos=helper.absolutePos(new net.minecraft.core.BlockPos(1,1,1));
            level.setBlockAndUpdate(pos,com.simibubi.create.AllBlocks.BASIN.getDefaultState());
            var basin=(com.simibubi.create.content.processing.basin.BasinBlockEntity)level.getBlockEntity(pos);
            basin.initialize();inventory=new com.simibubi.create.foundation.item.SmartInventory(3,basin);
            graph=new GraphRecipe(JsonParser.parseString("""
                {"ingredient":{"item":"minecraft:iron_ingot"},"transitional_item":"minecraft:iron_nugget",
                 "nodes":[{"id":"cut","start":true,"finish":true,"recipe":{"type":"create:cutting"}}],
                 "edges":[],"outputs":[{"results":[{"item":"minecraft:diamond","chance":0.5}]}]}
                """).getAsJsonObject());
            level.getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:bridge_candidate"),graph)));
            var definition=GraphIndex.get(level).graphs().get("test:bridge_candidate");
            if(definition==null)throw new AssertionError(GraphIndex.get(level).disabledReason("test:bridge_candidate"));
            source=NativeSteps.create(level,definition,definition.nodes().get("cut"),new ItemStack(Items.IRON_INGOT));
            source.getIngredients().set(0,new GraphGateIngredient(Ingredient.of(Items.IRON_INGOT),definition.id(),graph.json().toString(),"cut",0).toVanilla());
            source.getRollableResults().add(new com.simibubi.create.content.processing.recipe.ProcessingOutput(new ItemStack(Items.COPPER_INGOT),1));
            inventory.setStackInSlot(0,new ItemStack(Items.IRON_INGOT));
        }
        com.simibubi.create.content.processing.recipe.ProcessingRecipe<?,?> candidate(){return CandidateBinding.prepare(level,source);}
        List<ItemStack> inputs(){return List.of(inventory.getStackInSlot(0));}
        @Override public void close(){level.removeBlock(pos,false);level.getRecipeManager().replaceRecipes(saved);}
    }
    private static void check(boolean valid,String message){if(!valid)throw new AssertionError(message);}

    @GameTest(template="test_empty",batch="bridge_contracts")
    public static void ambiguousRolesDoNotCaptureAndBlockAnotherExplicitBinding(GameTestHelper helper) {
        try(var fixture=new CandidateFixture(helper)) {
            var candidate=fixture.candidate();
            try(var binding=((CandidateBinding.Attached)candidate).graphAssembly$binding()) {
                var other=new ItemStack(Items.IRON_INGOT);other.set(DataComponents.CUSTOM_NAME,Component.literal("distinct"));
                fixture.inventory.setStackInSlot(1,other);
                check(!binding.bind(List.of(fixture.inventory.getStackInSlot(0),fixture.inventory.getStackInSlot(1))),"Ambiguous primary identities must not pick the first slot");
                check(binding.bind(List.of(fixture.inventory.getStackInSlot(1))),"A rejected assignment must not capture a probed origin");
                check(candidate.getIngredients().getFirst().test(fixture.inventory.getStackInSlot(1)),"Only the explicitly selected origin is accepted");
                check(!candidate.getIngredients().getFirst().test(fixture.inventory.getStackInSlot(0)),"Another qualifying input must not replace the binding");
            }
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="bridge_contracts")
    public static void handoffPoliciesDistinguishCarrierTerminalAndByproduct(GameTestHelper helper) {
        var patch=DataComponentPatch.builder().set(GraphAssembly.PROGRESS.get(),"inherited")
            .set(DataComponents.MAX_STACK_SIZE,64).set(DataComponents.CUSTOM_NAME,Component.literal("inherited"))
            .set(DataComponents.RARITY,Rarity.EPIC).build();
        try(var scope=new ResultReceipt.Scope()) {
            var source=new ItemStack(Items.IRON_NUGGET);source.set(GraphAssembly.PROGRESS.get(),"graph");source.set(DataComponents.MAX_STACK_SIZE,1);
            var carrier=ResultReceipt.protect(source,ResultReceipt.Kind.CARRIER,scope);carrier.applyComponents(patch);
            check("graph".equals(carrier.get(GraphAssembly.PROGRESS.get()))&&carrier.getMaxStackSize()==1,"Carrier must retain authoritative progress/stack size");
            check(carrier.getHoverName().getString().equals("inherited"),"Carrier may inherit other native components");
            source=new ItemStack(Items.DIAMOND);source.set(DataComponents.CUSTOM_NAME,Component.literal("terminal"));source.remove(DataComponents.RARITY);
            var terminal=ResultReceipt.protect(source,ResultReceipt.Kind.TERMINAL,scope);terminal.applyComponents(patch);
            check(terminal.getHoverName().getString().equals("terminal")&&!terminal.has(DataComponents.RARITY)&&!terminal.has(GraphAssembly.PROGRESS.get()),"Terminal full definition must retain explicit removals without inherited progress");
            var byproduct=ResultReceipt.protect(new ItemStack(Items.COPPER_INGOT),ResultReceipt.Kind.BYPRODUCT,scope);byproduct.applyComponents(patch);
            check(!byproduct.has(DataComponents.CUSTOM_NAME)&&!byproduct.has(GraphAssembly.PROGRESS.get()),"Byproducts must not inherit primary components");
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="bridge_contracts")
    public static void explicitBindingIsProbeIndependentAndResultsRollOnlyOnce(GameTestHelper helper) {
        try(var fixture=new CandidateFixture(helper)) {
            var candidate=fixture.candidate();
            try(var binding=((CandidateBinding.Attached)candidate).graphAssembly$binding()) {
                var input=fixture.inputs().getFirst();
                check(!candidate.getIngredients().getFirst().test(input),"Unbound probes must not acquire a slot");
                check(candidate.getRollableResults().isEmpty(),"Unbound candidates cannot produce results");
                check(binding.bind(fixture.inputs()),"Host explicitly binds the actual inventory");
                check(candidate.getIngredients().getFirst().test(input),"Bound origin should match");
                check(!binding.bind(fixture.inputs()),"A candidate cannot be rebound");
                var outputs=candidate.getRollableResults();
                check(!outputs.isEmpty()&&outputs==candidate.getRollableResults(),"Repeated output getters share one prepared session");
                var random=net.minecraft.util.RandomSource.create(32);
                var first=outputs.stream().map(out->out.rollOutput(random)).toList();
                random.setSeed(7721);var expected=net.minecraft.util.RandomSource.create(7721).nextLong();
                var repeated=candidate.getRollableResults().stream().map(out->out.rollOutput(random)).toList();
                check(random.nextLong()==expected,"Repeated getters and rolls must not advance RNG");
                for(int i=0;i<first.size();i++)check(ItemStack.matches(first.get(i),repeated.get(i)),"Results must be stable across getters");
                check(input.getCount()==1,"Bridge preparation does not pay for the host");
                fixture.inventory.setStackInSlot(0,input.copy());
                check(candidate.getRollableResults().isEmpty(),"An equal-component replacement is still a different origin");
                check(outputs.getFirst().rollOutput(random).isEmpty(),"Previously returned output handles must also fail stale");
            }
        }
        helper.succeed();
    }
    @GameTest(template="test_empty",batch="bridge_contracts")
    public static void reloadAndClosedHandoffInvalidateCachedProofs(GameTestHelper helper) {
        try(var fixture=new CandidateFixture(helper)) {
            var candidate=fixture.candidate();
            try(var binding=((CandidateBinding.Attached)candidate).graphAssembly$binding()) {
                check(binding.bind(fixture.inputs()),"Fixture binds");var outputs=candidate.getRollableResults();
                fixture.level.getRecipeManager().replaceRecipes(List.of(new RecipeHolder<>(ResourceLocation.parse("test:bridge_candidate"),fixture.graph)));
                check(candidate.getRollableResults().isEmpty()&&outputs.getFirst().rollOutput(fixture.level.random).isEmpty(),"Same-ID holder replacement invalidates the whole session");
            }
        }
        var scope=new ResultReceipt.Scope();
        var output=ResultReceipt.protect(new ItemStack(Items.DIAMOND),ResultReceipt.Kind.BYPRODUCT,scope);
        var copy=output.copy();scope.close();
        copy.applyComponents(DataComponentPatch.builder().set(DataComponents.CUSTOM_NAME,Component.literal("later")).build());
        check(copy.has(DataComponents.CUSTOM_NAME),"Closing a handoff expires detached copies even before inheritance");
        helper.succeed();
    }
    @GameTest(template="test_empty",batch="bridge_contracts")
    public static void preparedCapacityAndPreviewUseIsolatedTemplatesWithoutRandomness(GameTestHelper helper) {
        try(var fixture=new CandidateFixture(helper)) {
            var definition=GraphIndex.get(fixture.level).graphs().get("test:bridge_candidate");
            var op=new GraphOperation(fixture.level,definition,definition.nodes().get("cut"),fixture.inputs().getFirst(),List.of());
            var prepared=GraphCompletion.prepare(fixture.level,op);
            var random=fixture.level.random;random.setSeed(543);var expected=random.nextLong();random.setSeed(543);
            prepared.preview().set(DataComponents.CUSTOM_NAME,Component.literal("mutated preview"));
            check(prepared.allMaximalOutcomesFit(items->{items.forEach(item->item.set(DataComponents.CUSTOM_NAME,Component.literal("mutated check")));return true;}),"Capacity enumerates prepared alternatives");
            check(prepared.allOutcomesFit(item->{},ItemStack::getMaxStackSize,items->true),"Quantity-aware capacity also reuses preparation");
            check(!prepared.preview().has(DataComponents.CUSTOM_NAME),"Callbacks cannot alter cached templates");
            check(random.nextLong()==expected,"Neither preview nor capacity consumes world randomness");
        }
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "bridge_contracts")
    public static void componentHandoffExpiresAfterNativeInheritance(GameTestHelper helper) {
        var item = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND);
        var protectedItem = dev.graphassembly.integration.bridge.ResultReceipt.protect(item, dev.graphassembly.integration.bridge.ResultReceipt.Kind.TERMINAL, new dev.graphassembly.integration.bridge.ResultReceipt.Scope());
        var name = net.minecraft.core.component.DataComponents.CUSTOM_NAME;
        var patch = net.minecraft.core.component.DataComponentPatch.builder()
            .set(name, net.minecraft.network.chat.Component.literal("inherited")).build();
        protectedItem.applyComponents(patch);
        if (protectedItem.has(name)) throw new AssertionError("Native inheritance must not alter terminal components");
        protectedItem.applyComponents(patch);
        if (!protectedItem.has(name)) throw new AssertionError("A completed handoff must not freeze later legitimate mutations");
        helper.succeed();
    }

    @GameTest(template = "test_empty", batch = "bridge_contracts")
    public static void brewingGraphDoesNotBreakCreateRecipeFinder(GameTestHelper helper) {
        var level = helper.getLevel();
        var manager = level.getRecipeManager();
        var saved = List.copyOf(manager.getRecipes());
        var id = ResourceLocation.parse("test:bridge_brewing");
        var graph = new GraphRecipe(JsonParser.parseString("""
            {"ingredient":{"item":"minecraft:potion"},"transitional_item":"minecraft:iron_nugget",
             "nodes":[{"id":"brew","start":true,"finish":true,
               "recipe":{"type":"graph_assembly:brewing","addition":{"item":"minecraft:nether_wart"}}}],
             "edges":[],"outputs":[{"results":[{"item":"minecraft:diamond"}]}]}
            """).getAsJsonObject());
        try {
            manager.replaceRecipes(List.of(new RecipeHolder<>(id, graph)));
            if (!GraphIndex.get(level).graphs().containsKey(id.toString()))
                throw new AssertionError(GraphIndex.get(level).disabledReason(id.toString()));
            for (int attempt = 0; attempt < 2; attempt++) {
                var found = RecipeFinder.get(new Object(), level, holder -> true);
                if (found.size() != 1 || found.getFirst().value() != graph)
                    throw new AssertionError("Native catalog must retain ordinary identities without projecting brewing");
            }
        } finally {
            manager.replaceRecipes(saved);
            RecipeFinder.LISTENER.onResourceManagerReload(level.getServer().getResourceManager());
        }
        helper.succeed();
    }
}
