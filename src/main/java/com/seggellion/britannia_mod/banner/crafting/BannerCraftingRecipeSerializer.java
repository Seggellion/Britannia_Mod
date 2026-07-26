package com.seggellion.britannia_mod.banner.crafting;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

/** Strict-value persistent and network serialization for the dynamic banner recipe. */
public final class BannerCraftingRecipeSerializer implements RecipeSerializer<BannerCraftingRecipe> {
    private static final Set<String> FIELDS = Set.of(
            "type", "schema_version", "banner_definition_id", "fabric_units");
    private static final MapCodec<BannerCraftingRecipe> VALUE_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version")
                    .forGetter(BannerCraftingRecipe::schemaVersion),
            BannerDefinitionId.CODEC.fieldOf("banner_definition_id")
                    .forGetter(BannerCraftingRecipe::definitionId),
            Codec.intRange(1, BannerCraftingRecipe.MAX_FABRIC_UNITS).fieldOf("fabric_units")
                    .forGetter(BannerCraftingRecipe::fabricUnits)
    ).apply(instance, BannerCraftingRecipe::new));
    private static final MapCodec<BannerCraftingRecipe> CODEC = new MapCodec<>() {
        @Override
        public <T> DataResult<BannerCraftingRecipe> decode(DynamicOps<T> ops, MapLike<T> input) {
            Set<String> seen = new HashSet<>();
            for (var entry : input.entries().toList()) {
                String key = ops.getStringValue(entry.getFirst()).result().orElse(null);
                if (key == null || !FIELDS.contains(key)) {
                    return DataResult.error(() -> "Unknown banner crafting recipe field: " + key);
                }
                if (!seen.add(key)) {
                    return DataResult.error(() -> "Duplicate banner crafting recipe field: " + key);
                }
            }
            return VALUE_CODEC.decode(ops, input);
        }

        @Override
        public <T> RecordBuilder<T> encode(
                BannerCraftingRecipe input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
            return VALUE_CODEC.encode(input, ops, prefix);
        }

        @Override
        public <T> Stream<T> keys(DynamicOps<T> ops) {
            return VALUE_CODEC.keys(ops);
        }
    };

    private static final StreamCodec<RegistryFriendlyByteBuf, BannerCraftingRecipe> STREAM_CODEC = StreamCodec.of(
            (buffer, recipe) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, recipe.schemaVersion());
                BannerDefinitionId.STREAM_CODEC.encode(buffer, recipe.definitionId());
                ByteBufCodecs.VAR_INT.encode(buffer, recipe.fabricUnits());
            },
            buffer -> new BannerCraftingRecipe(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    BannerDefinitionId.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)));

    @Override
    public MapCodec<BannerCraftingRecipe> codec() {
        return CODEC;
    }

    @Override
    public StreamCodec<RegistryFriendlyByteBuf, BannerCraftingRecipe> streamCodec() {
        return STREAM_CODEC;
    }
}
