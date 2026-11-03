package dev.graphassembly.gametest;

import com.simibubi.create.content.kinetics.belt.transport.TransportedItemStack;
import com.simibubi.create.content.kinetics.fan.processing.AllFanProcessingTypes;
import com.simibubi.create.content.kinetics.fan.processing.FanProcessing;
import dev.graphassembly.GraphAssembly;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class FanSafetyGameTests {
    @GameTest(template = "test_empty", batch = "graph_fan_safety")
    public static void corruptProgressSurvivesAlreadyEligibleFanAndDirectExecution(GameTestHelper helper) {
        var level = helper.getLevel();
        var type = AllFanProcessingTypes.BLASTING;
        var input = new ItemStack(Items.COBBLESTONE);
        input.set(GraphAssembly.PROGRESS.get(), "{broken saved graph");
        var expected = input.copy();
        var transported = new TransportedItemStack(input.copy());
        transported.processedBy = type; transported.processingTime = 0;
        helper.assertTrue(FanProcessing.applyProcessing(transported, level, type).doesNothing(), "Cached belt processing must stop");
        helper.assertTrue(ItemStack.matches(expected, transported.stack) && transported.processingTime == 0,
            "Belt input/components/countdown must not mutate");
        var entity = new ItemEntity(level, 0, 100, 0, input.copy());
        try {
            var processing = new CompoundTag(); processing.putString("Type", "create:blasting"); processing.putInt("Time", 1);
            var create = new CompoundTag(); create.put("Processing", processing);
            entity.getPersistentData().put("CreateData", create);
            var dataBefore = entity.getPersistentData().copy();
            helper.assertTrue(!FanProcessing.canProcess(entity, type), "Cached entity eligibility must not bypass the guard");
            helper.assertTrue(!FanProcessing.applyProcessing(entity, type), "Direct entity apply must stop too");
            helper.assertTrue(!entity.isRemoved() && ItemStack.matches(expected, entity.getItem()), "Corrupt workpiece must remain recoverable");
            helper.assertTrue(dataBefore.equals(entity.getPersistentData()), "Stopped entity countdown must not mutate");
        } finally { entity.discard(); }
        helper.succeed();
    }
}
