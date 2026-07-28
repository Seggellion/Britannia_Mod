package com.seggellion.britannia_mod.network.payload.banner;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerAssets;
import com.seggellion.britannia_mod.banner.data.BannerContentStatus;
import com.seggellion.britannia_mod.banner.data.BannerDimensions;
import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDefinition;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderMaterial;
import com.seggellion.britannia_mod.banner.renderdata.BannerRenderMount;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** One-way display metadata synchronized on login and server data-pack reload. */
public record S2CBannerRenderDataPayload(BannerRenderDataSnapshot snapshot) implements CustomPacketPayload {
    private static final int MAX_BANNERS = 4096;
    private static final int MAX_MATERIALS = 256;
    private static final int MAX_COLOURS_PER_MATERIAL = 4096;
    private static final int MAX_MOUNTS = 256;
    public static final ResourceLocation TYPE_ID = ResourceLocation.fromNamespaceAndPath(
            BritanniaMod.MODID, "banner_render_data");
    public static final Type<S2CBannerRenderDataPayload> TYPE = new Type<>(TYPE_ID);
    public static final StreamCodec<FriendlyByteBuf, S2CBannerRenderDataPayload> STREAM_CODEC = StreamCodec.of(
            S2CBannerRenderDataPayload::encode, S2CBannerRenderDataPayload::decode);

    public S2CBannerRenderDataPayload {
        java.util.Objects.requireNonNull(snapshot, "snapshot");
    }

    private static void encode(FriendlyByteBuf buffer, S2CBannerRenderDataPayload payload) {
        BannerRenderDataSnapshot snapshot = payload.snapshot;
        buffer.writeVarInt(snapshot.banners().size());
        snapshot.orderedBanners().forEach(definition -> {
            buffer.writeResourceLocation(definition.id().value());
            writeAssets(buffer, definition.assets());
            buffer.writeEnum(definition.contentStatus());
            buffer.writeVarInt(definition.dimensions().widthBlocks());
            buffer.writeVarInt(definition.dimensions().heightBlocks());
            buffer.writeBoolean(definition.dimensions().provisional());
            buffer.writeVarInt(definition.supportedOrientations().size());
            definition.supportedOrientations().forEach(buffer::writeEnum);
            buffer.writeVarInt(definition.supportedMounts().size());
            definition.supportedMounts().forEach(id -> buffer.writeResourceLocation(id.value()));
            buffer.writeVarInt(definition.orientationMountGeometry().size());
            definition.orientationMountGeometry().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey(
                            java.util.Comparator.comparingInt(BannerOrientation::ordinal)))
                    .forEach(entry -> {
                        buffer.writeEnum(entry.getKey());
                        buffer.writeResourceLocation(entry.getValue());
                    });
        });

        buffer.writeVarInt(snapshot.materials().size());
        snapshot.orderedMaterials().forEach(material -> {
            buffer.writeResourceLocation(material.id().value());
            buffer.writeResourceLocation(material.naturalColourId().value());
            buffer.writeResourceLocation(material.paletteId());
            buffer.writeVarInt(material.displaySrgbByColour().size());
            material.displaySrgbByColour().forEach((id, rgb) -> {
                buffer.writeResourceLocation(id.value());
                buffer.writeMedium(rgb & 0xFFFFFF);
            });
        });

        buffer.writeVarInt(snapshot.mounts().size());
        snapshot.orderedMounts().forEach(mount -> {
            buffer.writeResourceLocation(mount.id().value());
            buffer.writeResourceLocation(mount.geometry());
            buffer.writeResourceLocation(mount.texture());
        });
    }

    private static S2CBannerRenderDataPayload decode(FriendlyByteBuf buffer) {
        int bannerCount = readBoundedCount(buffer, MAX_BANNERS, "banner");
        Map<BannerDefinitionId, BannerRenderDefinition> banners = new LinkedHashMap<>();
        for (int index = 0; index < bannerCount; index++) {
            BannerDefinitionId id = new BannerDefinitionId(buffer.readResourceLocation());
            BannerAssets assets = readAssets(buffer);
            BannerContentStatus status = buffer.readEnum(BannerContentStatus.class);
            BannerDimensions dimensions = new BannerDimensions(
                    buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
            int orientationCount = readBoundedCount(buffer, 2, "orientation");
            java.util.List<BannerOrientation> orientations = new java.util.ArrayList<>(orientationCount);
            for (int orientationIndex = 0; orientationIndex < orientationCount; orientationIndex++) {
                orientations.add(buffer.readEnum(BannerOrientation.class));
            }
            int supportedMountCount = readBoundedCount(buffer, 32, "supported mount");
            java.util.List<MountId> supportedMounts = new java.util.ArrayList<>(supportedMountCount);
            for (int mountIndex = 0; mountIndex < supportedMountCount; mountIndex++) {
                supportedMounts.add(new MountId(buffer.readResourceLocation()));
            }
            int orientationMountCount = readBoundedCount(buffer, 2, "orientation mount geometry");
            Map<BannerOrientation, ResourceLocation> orientationMountGeometry = new LinkedHashMap<>();
            for (int mountIndex = 0; mountIndex < orientationMountCount; mountIndex++) {
                BannerOrientation orientation = buffer.readEnum(BannerOrientation.class);
                ResourceLocation previous = orientationMountGeometry.put(
                        orientation, buffer.readResourceLocation());
                requireUnique(previous, orientation);
            }
            BannerRenderDefinition previous = banners.put(id,
                    new BannerRenderDefinition(id, assets, status, dimensions, orientations,
                            supportedMounts, orientationMountGeometry));
            requireUnique(previous, id);
        }

        int materialCount = readBoundedCount(buffer, MAX_MATERIALS, "material");
        Map<FabricMaterialId, BannerRenderMaterial> materials = new LinkedHashMap<>();
        for (int index = 0; index < materialCount; index++) {
            FabricMaterialId id = new FabricMaterialId(buffer.readResourceLocation());
            ResolvedColourId natural = new ResolvedColourId(buffer.readResourceLocation());
            ResourceLocation palette = buffer.readResourceLocation();
            int colourCount = readBoundedCount(buffer, MAX_COLOURS_PER_MATERIAL, "colour");
            Map<ResolvedColourId, Integer> colours = new LinkedHashMap<>();
            for (int colourIndex = 0; colourIndex < colourCount; colourIndex++) {
                ResolvedColourId colourId = new ResolvedColourId(buffer.readResourceLocation());
                Integer previous = colours.put(colourId, buffer.readUnsignedMedium());
                requireUnique(previous, colourId);
            }
            BannerRenderMaterial previous = materials.put(id,
                    new BannerRenderMaterial(id, natural, palette, colours));
            requireUnique(previous, id);
        }

        int mountCount = readBoundedCount(buffer, MAX_MOUNTS, "mount");
        Map<MountId, BannerRenderMount> mounts = new LinkedHashMap<>();
        for (int index = 0; index < mountCount; index++) {
            MountId id = new MountId(buffer.readResourceLocation());
            BannerRenderMount previous = mounts.put(id,
                    new BannerRenderMount(id, buffer.readResourceLocation(), buffer.readResourceLocation()));
            requireUnique(previous, id);
        }
        return new S2CBannerRenderDataPayload(new BannerRenderDataSnapshot(banners, materials, mounts));
    }

    private static void writeAssets(FriendlyByteBuf buffer, BannerAssets assets) {
        buffer.writeResourceLocation(assets.geometry());
        buffer.writeResourceLocation(assets.baseTexture());
        buffer.writeResourceLocation(assets.dyeMask());
    }

    private static BannerAssets readAssets(FriendlyByteBuf buffer) {
        return new BannerAssets(buffer.readResourceLocation(), buffer.readResourceLocation(),
                buffer.readResourceLocation());
    }

    private static int readBoundedCount(FriendlyByteBuf buffer, int maximum, String label) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid synchronized " + label + " count: " + count);
        }
        return count;
    }

    private static void requireUnique(Object previous, Object id) {
        if (previous != null) {
            throw new IllegalArgumentException("Duplicate synchronized render-data ID " + id);
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
