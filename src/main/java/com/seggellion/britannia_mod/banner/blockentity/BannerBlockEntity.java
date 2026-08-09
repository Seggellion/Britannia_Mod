package com.seggellion.britannia_mod.banner.blockentity;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.banner.structure.BannerPlacedStructure;
import com.seggellion.britannia_mod.bannerdyeing.diagnostics.BoundedDiagnosticTracker;
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
    public static final String PLACEMENT_TAG = "placed_structure";
    public static final int MAX_LOAD_DIAGNOSTICS = 1024;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final BoundedDiagnosticTracker<String> LOAD_DIAGNOSTICS =
            new BoundedDiagnosticTracker<>(MAX_LOAD_DIAGNOSTICS);

    private Optional<BannerInstanceState> bannerState = Optional.empty();
    private Optional<BannerPlacedStructure> placedStructure = Optional.of(BannerPlacedStructure.legacyOneCell());
    private boolean structurallyInvalid;
    private boolean migratedLegacyPlacement;

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

    public Optional<BannerPlacedStructure> placedStructure() {
        return placedStructure;
    }

    public boolean migratedLegacyPlacement() {
        return migratedLegacyPlacement;
    }

    public boolean structurallyInvalid() {
        return structurallyInvalid;
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

    public boolean setPlacedState(BannerInstanceState state, BannerPlacedStructure structure) {
        if (state == null || structure == null) {
            return false;
        }
        bannerState = Optional.of(state);
        placedStructure = Optional.of(structure);
        structurallyInvalid = false;
        migratedLegacyPlacement = false;
        setChanged();
        return true;
    }

    /**
     * Future-facing authoritative live-state boundary. It changes only the banner instance record and preserves the
     * persisted orientation, footprint, block, and children.
     */
    public boolean setBannerStateAndSynchronize(BannerInstanceState state) {
        if (state == null || level == null || level.isClientSide) {
            return false;
        }
        bannerState = Optional.of(state);
        structurallyInvalid = false;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        return true;
    }

    public void synchronize() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
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
        placedStructure.ifPresent(structure -> BannerPlacedStructure.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), structure)
                .resultOrPartial(message -> LOGGER.error(
                        "Could not encode banner placed structure at {}: {}", worldPosition, message))
                .ifPresent(encoded -> tag.put(PLACEMENT_TAG, encoded)));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        bannerState = Optional.empty();
        placedStructure = Optional.empty();
        structurallyInvalid = false;
        migratedLegacyPlacement = false;
        Tag encoded = tag.get(STATE_TAG);
        if (encoded != null) {
            DataResult<BannerInstanceState> decoded = BannerInstanceState.CODEC.parse(
                    registries.createSerializationContext(NbtOps.INSTANCE), encoded);
            decoded.resultOrPartial(message -> {
                structurallyInvalid = true;
                if (LOAD_DIAGNOSTICS.first("state:" + worldPosition.asLong() + ":" + message)) {
                    LOGGER.warn(
                            "Ignoring structurally invalid banner block entity state at {}: {}",
                            worldPosition, message);
                }
            }).ifPresent(state -> bannerState = Optional.of(state));
        }

        Tag encodedPlacement = tag.get(PLACEMENT_TAG);
        if (encodedPlacement == null) {
            placedStructure = Optional.of(BannerPlacedStructure.legacyOneCell());
            migratedLegacyPlacement = true;
            setChanged();
            return;
        }
        DataResult<BannerPlacedStructure> decodedPlacement = BannerPlacedStructure.CODEC.parse(
                registries.createSerializationContext(NbtOps.INSTANCE), encodedPlacement);
        decodedPlacement.resultOrPartial(message -> {
            structurallyInvalid = true;
            if (LOAD_DIAGNOSTICS.first("placement:" + worldPosition.asLong() + ":" + message)) {
                LOGGER.warn("Ignoring structurally invalid banner placement at {}: {}", worldPosition, message);
            }
        }).ifPresent(structure -> placedStructure = Optional.of(structure));
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
