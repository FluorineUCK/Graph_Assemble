package dev.graphassembly.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import java.util.Map;

public record Node(String id, boolean start, boolean finish, JsonElement ingredient,
                   JsonObject recipe, String transitionalItem, Map<String, Integer> rewards,
                   Integer rewardLimit, boolean checkpoint, BranchMode branchMode,
                   FinishMode finishMode, List<OutputRule> outputs, List<OutputNode> scrapEntries,
                   List<Double> position, List<Double> spherePosition, List<WorkpieceInput> workpieceInputs,
                   List<ProgressAction> progressActions, CampfireKind campfire, boolean nativeRecipe) {
    public Node {
        checkpoint = checkpoint || start || finish;
        ingredient = ingredient == null ? null : ingredient.deepCopy();
        recipe = recipe.deepCopy();
        rewards = Map.copyOf(rewards);
        outputs = List.copyOf(outputs);
        scrapEntries = List.copyOf(scrapEntries);
        position = List.copyOf(position);
        spherePosition = List.copyOf(spherePosition);
        workpieceInputs = List.copyOf(workpieceInputs);
        progressActions = List.copyOf(progressActions);
        java.util.Objects.requireNonNull(campfire);
    }
    /** Backward-compatible step-shorthand constructor. */
    public Node(String id, boolean start, boolean finish, JsonElement ingredient,
                JsonObject recipe, String transitionalItem, Map<String, Integer> rewards,
                Integer rewardLimit, boolean checkpoint, BranchMode branchMode, FinishMode finishMode,
                List<OutputRule> outputs, List<OutputNode> scrapEntries, List<Double> position,
                List<Double> spherePosition, List<WorkpieceInput> workpieceInputs,
                List<ProgressAction> progressActions, CampfireKind campfire) {
        this(id,start,finish,ingredient,recipe,transitionalItem,rewards,rewardLimit,checkpoint,branchMode,
            finishMode,outputs,scrapEntries,position,spherePosition,workpieceInputs,progressActions,campfire,false);
    }
    public Node(String id, boolean start, boolean finish, JsonElement ingredient,
                JsonObject recipe, String transitionalItem, Map<String, Integer> rewards,
                Integer rewardLimit, boolean checkpoint, BranchMode branchMode, FinishMode finishMode,
                List<OutputRule> outputs, List<OutputNode> scrapEntries, List<Double> position,
                List<Double> spherePosition, List<WorkpieceInput> workpieceInputs, List<ProgressAction> progressActions) {
        this(id,start,finish,ingredient,recipe,transitionalItem,rewards,rewardLimit,checkpoint,branchMode,
            finishMode,outputs,scrapEntries,position,spherePosition,workpieceInputs,progressActions,CampfireKind.ANY);
    }
    public Node(String id, boolean start, boolean finish, JsonElement ingredient,
                JsonObject recipe, String transitionalItem, Map<String,Integer> rewards,
                Integer rewardLimit, boolean checkpoint, BranchMode branchMode, FinishMode finishMode,
                List<OutputRule> outputs, List<OutputNode> scrapEntries, List<Double> position,
                List<Double> spherePosition, List<WorkpieceInput> workpieceInputs) {
        this(id,start,finish,ingredient,recipe,transitionalItem,rewards,rewardLimit,checkpoint,branchMode,
            finishMode,outputs,scrapEntries,position,spherePosition,workpieceInputs,List.of());
    }
    public Node(String id, boolean start, boolean finish, JsonElement ingredient,
                JsonObject recipe, String transitionalItem, Map<String, Integer> rewards,
                Integer rewardLimit, boolean checkpoint, BranchMode branchMode,
                FinishMode finishMode, List<OutputRule> outputs, List<WeightedOutput> scrap,
                List<Double> position, List<Double> spherePosition) {
        this(id, start, finish, ingredient, recipe, transitionalItem, rewards, rewardLimit, checkpoint,
            branchMode, finishMode, outputs, new java.util.ArrayList<>(scrap), position, spherePosition, List.of());
    }
    public List<WeightedOutput> scrap() { return OutputTrees.leaves(scrapEntries); }
    /** Full native candidates, whether embedded or referenced, own their input/output lists. */
    public boolean completeRecipe() { return nativeRecipe || recipe.has("recipe_id"); }
    public boolean isCampfire() { return CampfireKind.RECIPE_TYPE.equals(recipe.get("type").getAsString()); }
    public PendingRoute route() {
        return progressActions.stream().map(ProgressAction::route).filter(java.util.Objects::nonNull).findFirst().orElse(null);
    }

    public WorkpieceInput workpieceInput(int itemInput) {
        return workpieceInputs.stream().filter(input -> input.itemInput() == itemInput).findFirst().orElse(null);
    }
    @Override public JsonElement ingredient() { return ingredient == null ? null : ingredient.deepCopy(); }
    @Override public JsonObject recipe() { return recipe.deepCopy(); }
}
