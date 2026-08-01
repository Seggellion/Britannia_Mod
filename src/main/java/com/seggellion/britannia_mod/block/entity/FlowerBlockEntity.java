package com.seggellion.britannia_mod.block.entity;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.block.FlowerBlock;
import com.seggellion.britannia_mod.farming.FlowerColor;
import com.seggellion.britannia_mod.farming.FlowerDefinition;
import com.seggellion.britannia_mod.farming.FlowerGrowthEvaluation;
import com.seggellion.britannia_mod.farming.FlowerGrowthEvaluator;
import com.seggellion.britannia_mod.farming.FlowerGrowthState;
import com.seggellion.britannia_mod.farming.FlowerPersistentState;
import com.seggellion.britannia_mod.farming.FlowerRegistry;
import com.seggellion.britannia_mod.farming.FlowerResetReason;
import com.seggellion.britannia_mod.farming.FarmingClimateResolver;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.Optional;

/** Persistent, synchronized identity for the generic FlowerBlock. */
public final class FlowerBlockEntity extends BlockEntity {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String STATE_KEY = "FlowerState";

    @Nullable
    private FlowerPersistentState flowerState;

    public FlowerBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.FLOWER_BLOCK_BE.get(), pos, blockState);
    }

    public boolean initialize(FlowerPersistentState proposedState) {
        if (flowerState != null || proposedState == null || !isValidInitialization(proposedState)) {
            return false;
        }
        flowerState = proposedState;
        setChanged();
        return true;
    }

    public boolean isInitialized() {
        return flowerState != null;
    }

    public Optional<FlowerPersistentState> flowerState() {
        return Optional.ofNullable(flowerState);
    }

    public FlowerColor visualColor() {
        return flowerState == null
                ? new FlowerColor(0xFFFFFF)
                : flowerState.visualColor(FlowerRegistry.initial());
    }

    public int hydration() {
        return flowerState == null ? 0 : flowerState.soil().hydration();
    }

    public Optional<FlowerGrowthEvaluation> evaluateGrowth(ServerLevel level) {
        if (flowerState == null) {
            return Optional.empty();
        }
        FlowerDefinition definition = FlowerRegistry.initial().byId(flowerState.speciesId()).orElse(null);
        if (definition == null) {
            return Optional.empty();
        }
        return Optional.of(FlowerGrowthEvaluator.evaluate(
                flowerState,
                definition,
                FarmingClimateResolver.resolve(level, worldPosition),
                worldPosition.getY()
        ));
    }

    /** Applies one server random-tick evaluation after shared weather handling. */
    public boolean tickGrowth(ServerLevel level, int currentHydration) {
        if (flowerState == null || level == null || level.isClientSide) {
            return false;
        }

        FlowerPersistentState before = flowerState;
        if (before.soil().hydration() != currentHydration) {
            flowerState = before.withSoil(before.soil().withHydration(currentHydration));
        }

        FlowerDefinition definition = FlowerRegistry.initial().byId(flowerState.speciesId()).orElse(null);
        if (definition == null) {
            FlowerGrowthState growth = flowerState.growthState();
            if (!growth.blocked()) {
                flowerState = flowerState.withGrowth(flowerState.growthStage(), new FlowerGrowthState(
                        growth.progress(), growth.tickProgress(), true
                ));
            }
        } else {
            FlowerGrowthEvaluation evaluation = FlowerGrowthEvaluator.evaluate(
                    flowerState,
                    definition,
                    FarmingClimateResolver.resolve(level, worldPosition),
                    worldPosition.getY()
            );
            flowerState = FlowerGrowthEvaluator.advance(flowerState, evaluation);
        }

        if (!flowerState.equals(before)) {
            setChangedAndSync();
            return true;
        }
        return false;
    }

    /** Central perennial reset foundation; player interaction callers are Milestone 6. */
    public boolean resetToStageOne(FlowerResetReason reason) {
        if (reason == null || flowerState == null || level == null || level.isClientSide) {
            return false;
        }
        FlowerPersistentState reset = FlowerGrowthEvaluator.resetToStageOne(flowerState);
        if (reset.equals(flowerState)) {
            return false;
        }
        flowerState = reset;
        setChangedAndSync();
        LOGGER.debug("[flower growth] reset species={} reason={} at {}",
                flowerState.speciesId(), reason, worldPosition);
        return true;
    }

    public void setChangedAndSync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private boolean isValidInitialization(FlowerPersistentState state) {
        FlowerRegistry registry = FlowerRegistry.initial();
        FlowerDefinition definition = registry.byId(state.speciesId()).orElse(null);
        if (definition == null
                || state.growthStage() != 1
                || !registry.isAllowedColor(definition, state.color())) {
            return false;
        }
        BlockState blockState = getBlockState();
        return blockState.getBlock() instanceof FlowerBlock
                && blockState.getValue(FlowerBlock.HYDRATION) == state.soil().hydration()
                && blockState.getValue(FlowerBlock.FERTILIZER) == state.soil().fertilizerLevel();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (flowerState != null) {
            tag.put(STATE_KEY, flowerState.toTag());
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        flowerState = null;
        if (!tag.contains(STATE_KEY)) {
            return;
        }
        try {
            flowerState = FlowerPersistentState.fromTag(tag.getCompound(STATE_KEY), FlowerRegistry.initial());
        } catch (IllegalArgumentException exception) {
            LOGGER.warn("[flower persistence] Rejected corrupt flower state at {}: {}",
                    worldPosition, exception.getMessage());
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        if (flowerState != null) {
            tag.put(STATE_KEY, clientStateTag(flowerState));
        }
        return tag;
    }

    public static CompoundTag clientStateTag(FlowerPersistentState state) {
        return state.toClientTag();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
