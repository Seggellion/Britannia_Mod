package com.seggellion.britannia_mod.dye.api;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record PigmentId(ResourceLocation value) implements StableResourceId {
    public static final Codec<PigmentId> CODEC = ResourceLocation.CODEC.xmap(PigmentId::new, PigmentId::value);
    public static final StreamCodec<ByteBuf, PigmentId> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(PigmentId::new, PigmentId::value);

    public PigmentId {
        Objects.requireNonNull(value, "value");
    }

    public static PigmentId of(String namespace, String path) {
        return new PigmentId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static PigmentId parse(String value) {
        return new PigmentId(ResourceLocation.parse(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
