package dev.graphassembly;

import com.mojang.logging.LogUtils;
import com.simibubi.create.AllCreativeModeTabs;
import com.simibubi.create.AllItems;
import com.mojang.serialization.Codec;
import dev.graphassembly.recipe.GraphRecipe;
import dev.graphassembly.integration.brewing.BrewingRecipe;
import dev.graphassembly.recipe.GraphSerializer;
import dev.graphassembly.recipe.ProgressStringCodec;
import dev.graphassembly.recipe.RuntimeSnapshots;
import dev.graphassembly.filter.GraphFilterItem;
import dev.graphassembly.filter.FilterSelection;
import dev.graphassembly.filter.ConfigureFilterPayload;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

@Mod(GraphAssembly.ID)
public final class GraphAssembly {
    public static final String ID = "graph_assembly";
    public static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<DataComponentType<?>> COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, ID);
    private static final DeferredRegister<RecipeType<?>> TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, ID);
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> PROGRESS = COMPONENTS.register("progress",
        () -> DataComponentType.<String>builder().persistent(ProgressStringCodec.CODEC).networkSynchronized(ProgressStringCodec.STREAM_CODEC).build());
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<String>> FILTER_SELECTION = COMPONENTS.register("filter_selection",
        () -> DataComponentType.<String>builder().persistent(dev.graphassembly.filter.FilterSelectionCodec.CODEC)
            .networkSynchronized(dev.graphassembly.filter.FilterSelectionCodec.STREAM_CODEC).build());
    public static final DeferredHolder<RecipeType<?>, RecipeType<GraphRecipe>> RECIPE_TYPE = TYPES.register("assembly", () -> new RecipeType<>() {
        @Override public String toString() { return ID + ":assembly"; }
    });
    public static final DeferredHolder<RecipeType<?>, RecipeType<dev.graphassembly.integration.anvil.AnvilRecipe>> ANVIL_TYPE = TYPES.register("anvil", () -> new RecipeType<>() {
        @Override public String toString(){return ID+":anvil";}
    });
    public static final DeferredHolder<RecipeSerializer<?>, dev.graphassembly.integration.anvil.AnvilRecipe.Serializer> ANVIL_SERIALIZER = SERIALIZERS.register("anvil",dev.graphassembly.integration.anvil.AnvilRecipe.Serializer::new);
    public static final DeferredHolder<RecipeType<?>, RecipeType<BrewingRecipe>> BREWING_TYPE = TYPES.register("brewing", () -> new RecipeType<>() {
        @Override public String toString() { return ID + ":brewing"; }
    });
    public static final DeferredHolder<RecipeSerializer<?>, BrewingRecipe.Serializer> BREWING_SERIALIZER =
        SERIALIZERS.register("brewing", BrewingRecipe.Serializer::new);

    public static final DeferredHolder<RecipeSerializer<?>, GraphSerializer> SERIALIZER = SERIALIZERS.register("assembly", GraphSerializer::new);

    public static final DeferredHolder<Item, GraphFilterItem> FILTER = ITEMS.register("graph_filter",
            () -> new GraphFilterItem(new Item.Properties().stacksTo(1)
        .component(FILTER_SELECTION.get(), FilterSelection.EMPTY.toJson())));
    public static final DeferredHolder<Item, Item> INCOMPLETE_FILTER = ITEMS.register("incomplete_graph_filter",
        () -> new Item(new Item.Properties().stacksTo(1)));
    public static final DeferredHolder<Item, Item> INFESTED_INSERT = ITEMS.register("infested_filter_insert",
        () -> new Item(new Item.Properties().stacksTo(1)));

    public GraphAssembly(IEventBus bus) {
        COMPONENTS.register(bus);
        TYPES.register(bus);
        SERIALIZERS.register(bus);
        ITEMS.register(bus);
        bus.addListener(ConfigureFilterPayload::register);
        bus.addListener(GraphAssembly::creativeTab);
        NeoForge.EVENT_BUS.addListener(GraphAssembly::serverStopped);
        NeoForge.EVENT_BUS.addListener(GraphAssembly::levelUnloaded);
    }

    private static void serverStopped(ServerStoppedEvent event) {
        RuntimeSnapshots.release(event.getServer().getRecipeManager());
        dev.graphassembly.integration.projection.NativeRecipeCatalog.release(event.getServer().getRecipeManager());
    }
    private static void levelUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && level.isClientSide()) {
            RuntimeSnapshots.release(level.getRecipeManager());
            dev.graphassembly.integration.projection.NativeRecipeCatalog.release(level.getRecipeManager());
        }
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(AllCreativeModeTabs.BASE_CREATIVE_TAB.getKey())) {
            event.insertAfter(AllItems.ATTRIBUTE_FILTER.asStack(), FILTER.get().getDefaultInstance(),
                CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
    }

    public static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath(ID, path); }
}
