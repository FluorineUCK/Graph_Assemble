package dev.graphassembly.gametest;

import dev.graphassembly.GraphAssembly;
import dev.graphassembly.core.Progress;
import dev.graphassembly.recipe.ProgressStringCodec;
import dev.graphassembly.recipe.Workpiece;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@GameTestHolder(GraphAssembly.ID)
@PrefixGameTestTemplate(false)
public final class ProgressSerializationGameTests {
    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void fullLargeHistorySurvivesBinaryNbtAndNetwork(GameTestHelper helper) throws IOException {
        var checkpoints = new ArrayList<String>();
        for (int visit = 0; visit < 12_000; visit++) checkpoints.add("loop_检查点_🧭_" + visit % 17);
        var progress = new Progress("test:large_progress", "start", "loop", Map.of("worked", 12_000),
            checkpoints, Map.of("loop", 12_000), "finish", List.of("loop", "loop", "loop"));
        var original = Workpiece.write(new ItemStack(Items.IRON_NUGGET), progress);
        String json = original.get(GraphAssembly.PROGRESS.get());
        check(progress.toJson().toString().equals(json), "Current structured and stream encodings must use the same progress JSON");
        check(json.getBytes(StandardCharsets.UTF_8).length > 100 * 1024, "Fixture must exceed 100 KiB of progress");
        check(json.length() > 32_767, "Fixture must exceed the former network string limit");

        var saved = new CompoundTag();
        saved.put("item", original.save(helper.getLevel().registryAccess()));
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(saved, output); }
        CompoundTag restoredTag;
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restoredTag = NbtIo.read(input, NbtAccounter.unlimitedHeap());
        }
        var restored = ItemStack.parse(helper.getLevel().registryAccess(), restoredTag.getCompound("item")).orElseThrow();
        check(progress.equals(Workpiece.read(restored)), "Binary NBT must preserve every checkpoint and all progress fields");
        check(json.equals(restored.get(GraphAssembly.PROGRESS.get())), "NBT must preserve the exact Unicode progress JSON");

        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess(), ConnectionType.NEOFORGE);
        try {
            ItemStack.STREAM_CODEC.encode(buffer, restored);
            var received = ItemStack.STREAM_CODEC.decode(buffer);
            check(progress.equals(Workpiece.read(received)), "ItemStack network sync must preserve the full checkpoint history");
            check(json.equals(received.get(GraphAssembly.PROGRESS.get())), "Network chunks must not split or replace Unicode surrogate pairs");
            check(received.getMaxStackSize() == 1, "Restored workpieces must remain unstackable");
            check(buffer.readableBytes() == 0, "Network round trip must consume the entire encoded stack");
        } finally { buffer.release(); }
        GraphAssembly.LOGGER.info("GameTest fulllargehistorysurvivesbinarynbtandnetwork passed");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void singleStringProgressNbtIsRejected(GameTestHelper helper) {
        var result = ProgressStringCodec.CODEC.parse(NbtOps.INSTANCE, StringTag.valueOf("{}"));
        check(result.error().isPresent(), "Single-string progress NBT must be rejected");
        GraphAssembly.LOGGER.info("GameTest singlestringprogressnbtisrejected passed");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void currentListProgressNbtRoundTripsAndRejectsNonStrings(GameTestHelper helper) {
        String value = "{\"checkpoint\":\"中文🧭\"}";
        var encoded = ProgressStringCodec.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        check(encoded instanceof ListTag, "Current progress NBT must be a list");
        check(value.equals(ProgressStringCodec.CODEC.parse(NbtOps.INSTANCE, encoded).getOrThrow()),
            "Short progress list must round trip");
        var invalid = new ListTag();
        invalid.add(IntTag.valueOf(1));
        check(ProgressStringCodec.CODEC.parse(NbtOps.INSTANCE, invalid).error().isPresent(),
            "Non-string progress list must be rejected");
        GraphAssembly.LOGGER.info("GameTest currentlistprogressnbtroundtripsandrejectsnonstrings passed");
        helper.succeed();
    }

    @GameTest(template = "test_empty", timeoutTicks = 100)
    public static void unicodeChunkBoundarySurvivesBinaryNbtAndNetwork(GameTestHelper helper) throws IOException {
        String value = "\0" + "中".repeat(8_190) + "🧭";
        var encoded = ProgressStringCodec.CODEC.encodeStart(NbtOps.INSTANCE, value).getOrThrow();
        check(encoded instanceof ListTag chunks && chunks.size() == 2,
            "Boundary fixture must split into two NBT strings");
        var saved = new CompoundTag();
        saved.put("progress", encoded);
        var bytes = new ByteArrayOutputStream();
        try (var output = new DataOutputStream(bytes)) { NbtIo.write(saved, output); }
        CompoundTag restored;
        try (var input = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            restored = NbtIo.read(input, NbtAccounter.unlimitedHeap());
        }
        check(value.equals(ProgressStringCodec.CODEC.parse(NbtOps.INSTANCE, restored.get("progress")).getOrThrow()),
            "Binary NBT must preserve null, Chinese, and a surrogate pair at the chunk boundary");

        ByteBuf buffer = Unpooled.buffer();
        try {
            ProgressStringCodec.STREAM_CODEC.encode(buffer, value);
            check(value.equals(ProgressStringCodec.STREAM_CODEC.decode(buffer)),
                "Network framing must preserve null, Chinese, and a surrogate pair at the chunk boundary");
            check(buffer.readableBytes() == 0, "Network framing must consume all bytes");
        } finally { buffer.release(); }
        GraphAssembly.LOGGER.info("GameTest unicodechunkboundarysurvivesbinarynbtandnetwork passed");
        helper.succeed();
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
