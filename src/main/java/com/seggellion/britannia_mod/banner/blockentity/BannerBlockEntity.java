package com.seggellion.britannia_mod.banner.blockentity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.registry.BannerBlockRegistry;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Common-side authoritative owner of the complete placed banner state. */
public final class BannerBlockEntity extends BlockEntity {
    public static final String STATE_TAG = "banner_state";
    private static final Logger LOGGER = LogUtils.getLogger();

    private Optional<BannerInstanceState> bannerState = Optional.empty();
    private boolean structurallyInvalid;

    public BannerBlockEntity(BlockPos pos, BlockState state) {
        this(BannerBlockRegistry.BANNER_BLOCK_ENTITY.get(), pos, state);
    }

    /** Narrow constructor used by codec/synchronization tests without binding the mod deferred registry. */
    public BannerBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Optional<BannerInstanceState> bannerState() {
        return bannerState;
    }

    public boolean setBannerState(BannerInstanceState state) {
        if (state == null) {
            return false;
        }
        bannerState = Optional.of(state);
        structurallyInvalid = false;
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
    }

    public BannerBlockEntityStatus status(RegistrySnapshot snapshot, boolean registryAvailable) {
        if (structurallyInvalid) {
            return BannerBlockEntityStatus.STRUCTURALLY_INVALID;
        }
        if (bannerState.isEmpty()) {
            return BannerBlockEntityStatus.UNCONFIGURED;
        }
        if (!registryAvailable) {
            return BannerBlockEntityStatus.CONFIGURED_MISSING_REFERENCES;
        }
        BannerInstanceState state = bannerState.orElseThrow();
        var definition = snapshot.banners().find(state.bannerDefinitionId());
        var material = snapshot.fabricMaterials().find(state.materialId());
        boolean missing = definition.isEmpty() || material.isEmpty()
                || !snapshot.mounts().contains(state.mountId())
                || state.sourcePigmentId().filter(id -> !snapshot.pigments().contains(id)).isPresent();
        if (material.isPresent()) {
            var palette = snapshot.materialPalettes().find(material.orElseThrow().paletteId());
            missing |= palette.isEmpty() || palette.orElseThrow().entries().stream()
                    .noneMatch(entry -> entry.id().equals(state.resolvedColourId()));
        }
        return missing ? BannerBlockEntityStatus.CONFIGURED_MISSING_REFERENCES
                : BannerBlockEntityStatus.CONFIGURED_VALID;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        bannerState.ifPresent(state -> BannerInstanceState.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), state)
                .resultOrPartial(message -> LOGGER.error(
                        "Could not encode banner block entity state at {}: {}", worldPosition, message))
                .ifPresent(encoded -> tag.put(STATE_TAG, encoded)));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bannerState = Optional.empty();
        structurallyInvalid = false;
        Tag encoded = tag.get(STATE_TAG);
        if (encoded == null) {
            return;
        }
        DataResult<BannerInstanceState> decoded = BannerInstanceState.CODEC.parse(
                registries.createSerializationContext(NbtOps.INSTANCE), encoded);
        decoded.resultOrPartial(message -> {
            structurallyInvalid = true;
            LOGGER.warn("Ignoring structurally invalid banner block entity state at {}: {}", worldPosition, message);
        }).ifPresent(state -> bannerState = Optional.of(state));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
