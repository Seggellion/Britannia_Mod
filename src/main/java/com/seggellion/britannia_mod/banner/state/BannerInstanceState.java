package com.seggellion.britannia_mod.banner.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

public record BannerInstanceState(
        int schemaVersion,
        BannerDefinitionId bannerDefinitionId,
        FabricMaterialId materialId,
        ResolvedColourId resolvedColourId,
        Optional<PigmentId> sourcePigmentId,
        MountId mountId) {
    public static final Codec<BannerInstanceState> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(BannerInstanceState::schemaVersion),
            BannerDefinitionId.CODEC.fieldOf("banner_definition_id").forGetter(BannerInstanceState::bannerDefinitionId),
            FabricMaterialId.CODEC.fieldOf("material_id").forGetter(BannerInstanceState::materialId),
            ResolvedColourId.CODEC.fieldOf("resolved_colour_id").forGetter(BannerInstanceState::resolvedColourId),
            PigmentId.CODEC.optionalFieldOf("source_pigment_id").forGetter(BannerInstanceState::sourcePigmentId),
            MountId.CODEC.fieldOf("mount_id").forGetter(BannerInstanceState::mountId)
    ).apply(instance, BannerInstanceState::new));
    public static final StreamCodec<ByteBuf, BannerInstanceState> STREAM_CODEC = StreamCodec.ofMember(
            BannerInstanceState::encode, BannerInstanceState::decode);

    public BannerInstanceState {
        DataCodecs.requireCurrentSchema(schemaVersion);
        Objects.requireNonNull(bannerDefinitionId, "bannerDefinitionId");
        Objects.requireNonNull(materialId, "materialId");
        Objects.requireNonNull(resolvedColourId, "resolvedColourId");
        sourcePigmentId = Objects.requireNonNull(sourcePigmentId, "sourcePigmentId");
        Objects.requireNonNull(mountId, "mountId");
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.VAR_INT.encode(buffer, schemaVersion);
        BannerDefinitionId.STREAM_CODEC.encode(buffer, bannerDefinitionId);
        FabricMaterialId.STREAM_CODEC.encode(buffer, materialId);
        ResolvedColourId.STREAM_CODEC.encode(buffer, resolvedColourId);
        buffer.writeBoolean(sourcePigmentId.isPresent());
        sourcePigmentId.ifPresent(pigment -> PigmentId.STREAM_CODEC.encode(buffer, pigment));
        MountId.STREAM_CODEC.encode(buffer, mountId);
    }

    private static BannerInstanceState decode(ByteBuf buffer) {
        int schemaVersion = ByteBufCodecs.VAR_INT.decode(buffer);
        BannerDefinitionId definitionId = BannerDefinitionId.STREAM_CODEC.decode(buffer);
        FabricMaterialId materialId = FabricMaterialId.STREAM_CODEC.decode(buffer);
        ResolvedColourId colourId = ResolvedColourId.STREAM_CODEC.decode(buffer);
        Optional<PigmentId> pigmentId = buffer.readBoolean()
                ? Optional.of(PigmentId.STREAM_CODEC.decode(buffer))
                : Optional.empty();
        MountId mountId = MountId.STREAM_CODEC.decode(buffer);
        return new BannerInstanceState(schemaVersion, definitionId, materialId, colourId, pigmentId, mountId);
    }
}
