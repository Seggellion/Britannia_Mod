package com.seggellion.britannia_mod.structure.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.FamilyId;
import com.seggellion.britannia_mod.structure.definition.StructureIdentity.VariantId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Immutable configured state carried by the one shared shrine item. */
public record ShrineItemState(int schemaVersion, FamilyId familyId, VariantId variantId) {
    public static final int CURRENT_SCHEMA_VERSION = 1;
    private static final Codec<Integer> SCHEMA_CODEC = Codec.INT.validate(version ->
            version == CURRENT_SCHEMA_VERSION
                    ? DataResult.success(version)
                    : DataResult.error(() -> "Unsupported schema_version " + version));
    private static final Codec<String> ID_CODEC = Codec.STRING.validate(value ->
            value == null || value.isBlank()
                    ? DataResult.error(() -> "Stable structure IDs must not be blank")
                    : DataResult.success(value));

    public static final Codec<ShrineItemState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            SCHEMA_CODEC.fieldOf("schema_version").forGetter(ShrineItemState::schemaVersion),
            ID_CODEC.xmap(FamilyId::new, FamilyId::value)
                    .fieldOf("family_id").forGetter(ShrineItemState::familyId),
            ID_CODEC.xmap(VariantId::new, VariantId::value)
                    .fieldOf("variant_id").forGetter(ShrineItemState::variantId)
    ).apply(instance, ShrineItemState::new));

    public static final StreamCodec<ByteBuf, ShrineItemState> STREAM_CODEC = StreamCodec.ofMember(
            ShrineItemState::encode, ShrineItemState::decode);

    public ShrineItemState {
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported schema_version " + schemaVersion);
        }
        Objects.requireNonNull(familyId, "familyId");
        Objects.requireNonNull(variantId, "variantId");
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.VAR_INT.encode(buffer, schemaVersion);
        ByteBufCodecs.STRING_UTF8.encode(buffer, familyId.value());
        ByteBufCodecs.STRING_UTF8.encode(buffer, variantId.value());
    }

    private static ShrineItemState decode(ByteBuf buffer) {
        return new ShrineItemState(
                ByteBufCodecs.VAR_INT.decode(buffer),
                new FamilyId(ByteBufCodecs.STRING_UTF8.decode(buffer)),
                new VariantId(ByteBufCodecs.STRING_UTF8.decode(buffer)));
    }
}
