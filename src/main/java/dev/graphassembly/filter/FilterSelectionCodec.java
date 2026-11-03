package dev.graphassembly.filter;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Existing string storage/wire shape, with the same bounded configuration on both paths. */
public final class FilterSelectionCodec {
    private FilterSelectionCodec() {}
    public static String validated(String json) { return FilterConfiguration.parse(json).toJson(); }
    private static DataResult<String> checked(String json) {
        try { return DataResult.success(validated(json)); }
        catch (FilterConfiguration.Invalid e) { return DataResult.error(e::getMessage); }
    }
    public static final Codec<String> CODEC = Codec.STRING.flatXmap(FilterSelectionCodec::checked, FilterSelectionCodec::checked);
    public static final StreamCodec<ByteBuf, String> STREAM_CODEC = ByteBufCodecs.stringUtf8(FilterConfiguration.MAX_JSON_BYTES)
        .map(FilterSelectionCodec::validated, FilterSelectionCodec::validated);
}
