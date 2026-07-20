package com.seggellion.britannia_mod.dye.api;

import com.mojang.serialization.Codec;
import com.seggellion.britannia_mod.bannerdyeing.api.StableResourceId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

public record FabricMaterialId(ResourceLocation value) implements StableResourceId {
    public static final Codec<FabricMaterialId> CODEC =
            ResourceLocation.CODEC.xmap(FabricMaterialId::new, FabricMaterialId::value);
    public static final StreamCodec<ByteBuf, FabricMaterialId> STREAM_CODEC =
            ResourceLocation.STREAM_CODEC.map(FabricMaterialId::new, FabricMaterialId::value);

    public FabricMaterialId {
        Objects.requireNonNull(value, "value");
    }

    public static FabricMaterialId of(String namespace, String path) {
        return new FabricMaterialId(ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public static FabricMaterialId parse(String value) {
        return new FabricMaterialId(ResourceLocation.parse(value));
    }

    @Override
    public String toString() {
        return value.toString();
    }
}
