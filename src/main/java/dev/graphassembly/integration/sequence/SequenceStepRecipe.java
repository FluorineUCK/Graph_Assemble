package dev.graphassembly.integration.sequence;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.compat.jei.category.sequencedAssembly.SequencedAssemblySubCategory;
import com.simibubi.create.content.processing.recipe.ProcessingOutput;
import com.simibubi.create.content.processing.recipe.ProcessingRecipeParams;
import com.simibubi.create.content.processing.recipe.StandardProcessingRecipe;
import com.simibubi.create.content.processing.sequenced.IAssemblyRecipe;
import com.simibubi.create.foundation.recipe.IRecipeTypeInfo;
import dev.graphassembly.GraphAssembly;
import dev.graphassembly.compat.jei.SequenceBlockHint;
import dev.graphassembly.integration.NativeSteps;
import dev.graphassembly.integration.vanilla.campfire.CampfireDefinition;
import dev.graphassembly.integration.vanilla.campfire.CampfireKind;
import dev.graphassembly.recipe.OperationSpec;
import dev.graphassembly.recipe.RecipeMode;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.items.wrapper.RecipeWrapper;

import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static dev.graphassembly.codec.JsonValidation.fields;
import static dev.graphassembly.codec.JsonValidation.invalid;
import static dev.graphassembly.codec.JsonValidation.object;
import static dev.graphassembly.codec.JsonValidation.requiredText;

/** Native Create sequence descriptor. The hosting device, not matches(), executes its operation. */
public final class SequenceStepRecipe extends StandardProcessingRecipe<RecipeWrapper> implements IAssemblyRecipe {
    private static final IRecipeTypeInfo TYPE_INFO = new IRecipeTypeInfo() {
        @Override public ResourceLocation getId() { return GraphAssembly.id("sequence_step"); }
        @SuppressWarnings("unchecked")
        @Override public <T extends RecipeSerializer<?>> T getSerializer() {
            return (T) GraphAssembly.SEQUENCE_STEP_SERIALIZER.get();
        }
        @SuppressWarnings("unchecked")
        @Override public <I extends RecipeInput, R extends Recipe<I>> RecipeType<R> getType() {
            return (RecipeType<R>) (RecipeType<?>) GraphAssembly.SEQUENCE_STEP_TYPE.get();
        }
    };

    private final JsonObject step;
    private final OperationSpec operation;

    public SequenceStepRecipe(JsonObject step) {
        super(TYPE_INFO, new Params());
        fields(step, "step", "recipe", "recipe_mode", "campfire");
        var recipe = object(step.get("recipe"), "step.recipe");
        String type = requiredText(recipe, "type", "step.recipe");
        if (type.equals("graph_assembly:sequence_step") || type.equals("create:sequenced_assembly"))
            throw invalid("step.recipe.type", "recursive sequence wrappers are unsupported");
        if (NativeSteps.adapter(type) == NativeSteps.Adapter.UNSUPPORTED)
            throw invalid("step.recipe.type", "no verified device adapter for " + type);
        CampfireDefinition.validateRecipePlacement(recipe, "step");
        RecipeMode mode = !step.has("recipe_mode") ? RecipeMode.STEP : switch (requiredText(step, "recipe_mode", "step")) {
            case "step" -> RecipeMode.STEP;
            case "native" -> RecipeMode.NATIVE;
            default -> throw invalid("step.recipe_mode", "expected native or step");
        };
        operation = new OperationSpec(recipe, mode, CampfireDefinition.parse(step, type, "step"));
        this.step = step.deepCopy();
    }

    public JsonObject step() { return step.deepCopy(); }

    /** Fixed target for the verified serializer family; never queries a world or a recipe manager. */
    public Block machine() {
        String type = operation.recipe().get("type").getAsString();
        return switch (NativeSteps.adapter(type)) {
            case COOKING -> switch (type) {
                case "minecraft:smelting" -> Blocks.FURNACE;
                case "minecraft:blasting" -> Blocks.BLAST_FURNACE;
                case "minecraft:smoking" -> Blocks.SMOKER;
                case "minecraft:campfire_cooking" -> operation.campfire() == CampfireKind.SOUL ? Blocks.SOUL_CAMPFIRE : Blocks.CAMPFIRE;
                default -> throw new IllegalStateException("Unmapped cooking serializer " + type);
            };
            case CRAFTING, SPECIAL_CRAFTING -> Blocks.CRAFTING_TABLE;
            case SMITHING -> Blocks.SMITHING_TABLE;
            case STONECUTTING -> Blocks.STONECUTTER;
            case ANVIL -> Blocks.ANVIL;
            case BREWING -> Blocks.BREWING_STAND;
            case CREATE -> switch (type) {
                case "create:pressing", "create:compacting" -> AllBlocks.MECHANICAL_PRESS.get();
                case "create:cutting" -> AllBlocks.MECHANICAL_SAW.get();
                case "create:deploying" -> AllBlocks.DEPLOYER.get();
                case "create:filling" -> AllBlocks.SPOUT.get();
                case "create:milling" -> AllBlocks.MILLSTONE.get();
                case "create:crushing" -> AllBlocks.CRUSHING_WHEEL.get();
                case "create:emptying" -> AllBlocks.ITEM_DRAIN.get();
                case "create:mixing" -> AllBlocks.MECHANICAL_MIXER.get();
                case "create:splashing", "create:haunting" -> AllBlocks.ENCASED_FAN.get();
                case "create:sandpaper_polishing" -> AllBlocks.DEPLOYER.get();
                default -> throw new IllegalStateException("Unmapped Create serializer " + type);
            };
            case UNSUPPORTED -> throw new IllegalStateException("Unverified sequence step " + type);
        };
    }

    @Override public boolean matches(RecipeWrapper input, Level level) { return false; }
    @Override protected int getMaxInputCount() { return 1; }
    @Override protected int getMaxOutputCount() { return 1; }
    @Override public Component getDescriptionForAssembly() { return machine().getName(); }
    @Override public void addRequiredMachines(Set<ItemLike> machines) { machines.add(machine()); }
    @Override public void addAssemblyIngredients(List<Ingredient> ingredients) {}
    @Override public Supplier<Supplier<SequencedAssemblySubCategory>> getJEISubCategory() {
        // As in Create's assembly recipes, the client class resolves only inside the inner supplier.
        return () -> SequenceBlockHint::new;
    }

    private static final class Params extends ProcessingRecipeParams {
        private Params() {
            // Create's parent initializer replaces slot 0; both lists must already contain that slot.
            ingredients.add(Ingredient.of(Items.BARRIER));
            results.add(new ProcessingOutput(new ItemStack(Items.BARRIER), 1));
        }
    }

    public static final class Serializer implements RecipeSerializer<SequenceStepRecipe> {
        private static final MapCodec<SequenceStepRecipe> CODEC = new MapCodec<>() {
            @Override public <T> DataResult<SequenceStepRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
                try {
                    T raw = input.get("step");
                    if (raw == null) throw invalid("step", "must be an object");
                    return DataResult.success(new SequenceStepRecipe(object(ops.convertTo(JsonOps.INSTANCE, raw), "step")));
                } catch (RuntimeException invalid) {
                    return DataResult.error(() -> "Invalid sequence step: " + invalid.getMessage());
                }
            }
            @Override public <T> RecordBuilder<T> encode(SequenceStepRecipe value, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                // Internal initialized primary input belongs to the parent, never to the public JSON.
                return prefix.add("step", JsonOps.INSTANCE.convertTo(ops, value.step));
            }
            @Override public <T> Stream<T> keys(DynamicOps<T> ops) { return Stream.of(ops.createString("step")); }
        };
        private static final StreamCodec<RegistryFriendlyByteBuf, SequenceStepRecipe> STREAM = StreamCodec.of(
            (buf, value) -> {
                buf.writeUtf(value.step.toString(), 1_048_576);
                Ingredient.CONTENTS_STREAM_CODEC.encode(buf, value.getIngredients().getFirst());
            }, buf -> {
                var recipe = new SequenceStepRecipe(object(JsonParser.parseString(buf.readUtf(1_048_576)), "step"));
                // Parent network decoding does not call initFromSequencedAssembly, unlike its JSON codec.
                recipe.getIngredients().set(0, Ingredient.CONTENTS_STREAM_CODEC.decode(buf));
                return recipe;
            });
        @Override public MapCodec<SequenceStepRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, SequenceStepRecipe> streamCodec() { return STREAM; }
    }
}
