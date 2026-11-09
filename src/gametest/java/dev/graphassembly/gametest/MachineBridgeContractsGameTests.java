package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.integration.RecipeSnapshot;
import dev.graphassembly.integration.MachineScopes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;
import net.neoforged.neoforge.gametest.*;
import java.util.List;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class MachineBridgeContractsGameTests {
    private static void check(boolean condition,String message) {
        if(!condition)throw new GameTestAssertException(message);
    }
    private static final class Operation implements AutoCloseable {
        final MachineScopes<Operation>.Token token;
        boolean invalid;
        Operation(MachineScopes<Operation>.Token token){this.token=token;}
        @Override public void close(){token.close();}
    }

    @GameTest(template="test_empty",batch="machine_bridge")
    public static void scopeReentryInvalidatesAndDuplicateCloseCannotReleaseSuccessor(GameTestHelper helper) {
        var scopes=new MachineScopes<Operation>(scope->scope.invalid=true);
        var otherOwner=new MachineScopes<Operation>(scope->scope.invalid=true);
        var level=helper.getLevel();var pos=new BlockPos.MutableBlockPos(1,2,3);
        var first=scopes.enter(level,pos,Operation::new);
        pos.set(9,9,9);
        check(scopes.get(level,new BlockPos(1,2,3))==first,"Entry must snapshot mutable positions");
        check(scopes.enter(level,new BlockPos(1,2,3),Operation::new)==null&&first.invalid,"Reentry invalidates the outer operation and returns null");
        try(var independent=otherOwner.enter(level,new BlockPos(1,2,3),Operation::new)) {
            check(independent!=null&&!independent.invalid,"Owners must keep independent registries");
        }
        first.close();
        try(var successor=scopes.enter(level,new BlockPos(1,2,3),Operation::new)) {
            check(successor!=null&&!successor.invalid,"Closing the invalidated scope permits a fresh operation");
            first.close();
            check(scopes.get(level,new BlockPos(1,2,3))==successor,"Duplicate close must not release a later scope");
            try(var elsewhere=scopes.enter(level,pos,Operation::new)) {
                check(elsewhere!=null,"Other positions remain independent");
            }
        }
        check(scopes.get(level,new BlockPos(1,2,3))==null,"Final close releases the position");
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="machine_bridge")
    public static void constructionReentryInvalidatesOuterAndFactoryFailureReleasesPosition(GameTestHelper helper) {
        var scopes=new MachineScopes<Operation>(scope->scope.invalid=true);
        var level=helper.getLevel();var pos=new BlockPos(4,2,3);
        try(var outer=scopes.enter(level,pos,token->{
            check(scopes.enter(level,pos,Operation::new)==null,"Construction must reserve the position before invoking its factory");
            return new Operation(token);
        })) {
            check(outer!=null&&outer.invalid,"Reentry before construction completes must invalidate the resulting outer scope");
        }
        var failure=new IllegalStateException("factory failed");
        try {
            scopes.enter(level,pos,token->{
                check(scopes.enter(level,pos,Operation::new)==null,"Throwing construction still owns the position until it unwinds");
                throw failure;
            });
            throw new GameTestAssertException("Factory failure must propagate");
        } catch(IllegalStateException expected) {
            check(expected==failure,"Preserve the exact factory exception");
        }
        try(var next=scopes.enter(level,pos,Operation::new)) {
            check(next!=null&&!next.invalid,"Factory failure must release its own reservation for reacquisition");
        }
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="machine_bridge")
    public static void identicalHoldersInDifferentManagersDoNotShareAuthorization(GameTestHelper helper) {
        var first=new RecipeManager(helper.getLevel().registryAccess());
        var second=new RecipeManager(helper.getLevel().registryAccess());
        var recipe=new CampfireCookingRecipe("",CookingBookCategory.MISC,Ingredient.of(Items.STICK),new ItemStack(Items.CHARCOAL),0,20);
        var holder=new RecipeHolder<>(ResourceLocation.parse("test:manager_identity"),recipe);
        first.replaceRecipes(List.of(holder));second.replaceRecipes(List.of(holder));
        var snapshot=RecipeSnapshot.capture(first);
        check(snapshot.current(first),"Snapshot must accept its originating manager");
        check(!snapshot.current(second),"Even exact holder identities in another manager cannot authorize this transaction");
        helper.succeed();
    }

    @GameTest(template="test_empty",batch="machine_bridge")
    public static void definitionSnapshotRejectsReplacedSameIdHolder(GameTestHelper helper) {
        var level=helper.getLevel();var manager=level.getRecipeManager();
        var saved=List.copyOf(manager.getRecipes());
        try {
            var id=ResourceLocation.parse("test:machine_snapshot");
            var recipe=new CampfireCookingRecipe("",CookingBookCategory.MISC,Ingredient.of(Items.STICK),new ItemStack(Items.CHARCOAL),0,20);
            var holder=new RecipeHolder<>(id,recipe);
            manager.replaceRecipes(List.of(holder));
            var snapshot=RecipeSnapshot.capture(level);
            check(snapshot.current(level),"Fresh snapshot must remain current");
            manager.replaceRecipes(List.of(holder));
            check(snapshot.current(level),"Reinstalling the identical holder must preserve authorization");
            manager.replaceRecipes(List.of(new RecipeHolder<>(id,recipe)));
            check(!snapshot.current(level),"Same id and same recipe value with a replacement holder must invalidate authorization");
            check(RecipeSnapshot.capture(level).current(level),"Fresh snapshot accepts the replacement definition");
        } finally {manager.replaceRecipes(saved);}
        helper.succeed();
    }
}
