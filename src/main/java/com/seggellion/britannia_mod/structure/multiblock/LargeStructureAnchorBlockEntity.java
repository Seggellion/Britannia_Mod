package com.seggellion.britannia_mod.structure.multiblock;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.DataResult;
import com.seggellion.britannia_mod.registry.LargeStructureRegistry;
import com.seggellion.britannia_mod.structure.definition.StructureCatalogue;
import com.seggellion.britannia_mod.structure.item.ShrineItemState;
import java.util.LinkedHashMap;
import java.util.Map;
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

/** Common-side authoritative owner of one persisted logical shrine. */
public final class LargeStructureAnchorBlockEntity extends BlockEntity {
    public static final String STATE_TAG = "shrine_state";
    private static final int MAX_LOAD_DIAGNOSTICS = 1024;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, Boolean> LOAD_DIAGNOSTICS = new LinkedHashMap<>();

    private Optional<PlacedStructureState> placedState = Optional.empty();
    private PlacedStructureStatus structuralStatus = PlacedStructureStatus.UNINITIALIZED;

    public LargeStructureAnchorBlockEntity(BlockPos pos, BlockState state) {
        this(LargeStructureRegistry.LARGE_STRUCTURE.get(), pos, state);
    }

    /** Narrow constructor for persistence/synchronization tests without deferred-registry binding. */
    public LargeStructureAnchorBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public Optional<PlacedStructureState> placedState() {
        return placedState;
    }

    public PlacedStructureStatus structuralStatus() {
        return structuralStatus;
    }

    public PlacedStructureStatus status(StructureCatalogue catalogue) {
        if (structuralStatus != PlacedStructureStatus.VALID || placedState.isEmpty()) {
            return structuralStatus;
        }
        PlacedStructureState state = placedState.orElseThrow();
        if (catalogue.family(state.familyId()).isEmpty()) {
            return PlacedStructureStatus.MISSING_FAMILY_DEFINITION;
        }
        if (catalogue.variant(state.familyId(), state.variantId()).isEmpty()) {
            return PlacedStructureStatus.MISSING_VARIANT_DEFINITION;
        }
        return PlacedStructureStatus.VALID;
    }

    public boolean initialize(PlacedStructureState state) {
        if (state == null || placedState.isPresent() || !facingCompatible(state)) {
            return false;
        }
        placedState = Optional.of(state);
        structuralStatus = PlacedStructureStatus.VALID;
        setChanged();
        return true;
    }

    public void synchronize() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        placedState.ifPresent(state -> PlacedStructureState.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), state)
                .resultOrPartial(message -> diagnostic("save:" + worldPosition.asLong() + ":" + message,
                        "Could not encode shrine state at {}: {}", worldPosition, message))
                .ifPresent(encoded -> tag.put(STATE_TAG, encoded)));
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        placedState = Optional.empty();
        structuralStatus = classify(tag);
        if (structuralStatus != PlacedStructureStatus.VALID) {
            if (structuralStatus != PlacedStructureStatus.UNINITIALIZED) {
                diagnostic("load:" + worldPosition.asLong() + ":" + structuralStatus,
                        "Ignoring {} shrine state at {}", structuralStatus, worldPosition);
            }
            return;
        }
        Tag encoded = tag.get(STATE_TAG);
        DataResult<PlacedStructureState> decoded = PlacedStructureState.CODEC.parse(
                registries.createSerializationContext(NbtOps.INSTANCE), encoded);
        decoded.resultOrPartial(message -> {
            structuralStatus = message.contains("footprint") || message.contains("anchor")
                    ? PlacedStructureStatus.STRUCTURALLY_INVALID : PlacedStructureStatus.MALFORMED;
            diagnostic("decode:" + worldPosition.asLong() + ":" + message,
                    "Ignoring invalid shrine state at {}: {}", worldPosition, message);
        }).ifPresent(state -> {
            if (!facingCompatible(state)) {
                structuralStatus = PlacedStructureStatus.STRUCTURALLY_INVALID;
                diagnostic("facing:" + worldPosition.asLong(),
                        "Shrine persisted facing disagrees with authoritative block state at {}", worldPosition);
            } else {
                placedState = Optional.of(state);
                structuralStatus = PlacedStructureStatus.VALID;
            }
        });
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private boolean facingCompatible(PlacedStructureState state) {
        return getBlockState().hasProperty(LargeStructureAnchorBlock.FACING)
                && getBlockState().getValue(LargeStructureAnchorBlock.FACING) == state.facing();
    }

    private static PlacedStructureStatus classify(CompoundTag root) {
        if (!root.contains(STATE_TAG)) {
            return PlacedStructureStatus.UNINITIALIZED;
        }
        if (!root.contains(STATE_TAG, Tag.TAG_COMPOUND)) {
            return PlacedStructureStatus.MALFORMED;
        }
        CompoundTag state = root.getCompound(STATE_TAG);
        if (!state.contains("schema_version", Tag.TAG_INT)) {
            return PlacedStructureStatus.MALFORMED;
        }
        int schema = state.getInt("schema_version");
        if (schema > ShrineItemState.CURRENT_SCHEMA_VERSION) {
            return PlacedStructureStatus.UNSUPPORTED_FUTURE_SCHEMA;
        }
        if (schema != ShrineItemState.CURRENT_SCHEMA_VERSION
                || !state.contains("family_id", Tag.TAG_STRING)
                || !state.contains("variant_id", Tag.TAG_STRING)
                || !state.contains("facing", Tag.TAG_STRING)
                || !state.contains("placed_footprint", Tag.TAG_LIST)) {
            return PlacedStructureStatus.MALFORMED;
        }
        if (state.getList("placed_footprint", Tag.TAG_COMPOUND).size() != 4) {
            return PlacedStructureStatus.STRUCTURALLY_INVALID;
        }
        return PlacedStructureStatus.VALID;
    }

    private static synchronized void diagnostic(String key, String message, Object... arguments) {
        if (LOAD_DIAGNOSTICS.putIfAbsent(key, Boolean.TRUE) != null) {
            return;
        }
        while (LOAD_DIAGNOSTICS.size() > MAX_LOAD_DIAGNOSTICS) {
            LOAD_DIAGNOSTICS.remove(LOAD_DIAGNOSTICS.keySet().iterator().next());
        }
        LOGGER.warn(message, arguments);
    }
}
