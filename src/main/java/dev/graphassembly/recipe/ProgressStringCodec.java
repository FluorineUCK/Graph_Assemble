package dev.graphassembly.recipe;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/** Lossless framing for progress JSON without a single NBT/network string size limit. */
public final class ProgressStringCodec {
    // Modified UTF-8 uses at most three bytes per UTF-16 unit: each NBT string stays
    // below 65,535 bytes, including CJK, null characters and supplementary code points.
    private static final int CHUNK_CHARACTERS = 8_192;

    /** Persist progress as a list of independently safe chunks. */
    public static final Codec<String> CODEC = Codec.STRING.listOf().xmap(
        chunks -> String.join("", chunks), ProgressStringCodec::split);

    public static final StreamCodec<ByteBuf, String> STREAM_CODEC = ByteBufCodecs.stringUtf8(CHUNK_CHARACTERS)
        .apply(ByteBufCodecs.list()).map(chunks -> String.join("", chunks), ProgressStringCodec::split);

    private ProgressStringCodec() {}

    private static List<String> split(String value) {
        List<String> chunks = new ArrayList<>();
        for (int start = 0; start < value.length();) {
            int end = start + Math.min(CHUNK_CHARACTERS, value.length() - start);
            // Standard UTF-8 encodes a supplementary character as one code point.
            // Splitting its surrogate pair would irreversibly replace both halves.
            if (end < value.length() && Character.isHighSurrogate(value.charAt(end - 1))
                && Character.isLowSurrogate(value.charAt(end))) end--;
            chunks.add(value.substring(start, end));
            start = end;
        }
        return chunks;
    }
}
