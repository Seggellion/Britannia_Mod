package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.block.CommunityFarmBlock;
import com.seggellion.britannia_mod.block.CommunityHoedFarmBlock;
import com.seggellion.britannia_mod.client.gui.QuestScreenText;
import com.seggellion.britannia_mod.farming.CommunityPlotWindow;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class CommunityFarmBlockEntity extends BlockEntity {
    /**
     * How long a hoed public plot waits for its fertilized dirt.
     *
     * <p>M9 item 2: 3600 ticks (180 s) before, now {@link CommunityPlotWindow#HOE_TO_FERTILIZE_TICKS}
     * (600 s), the same number Rails enforces on the {@code plot_fertilized} step. See
     * {@link CommunityPlotWindow} for why the two have to be one number.
     */
    public static final long PREPARED_EXPIRY_TICKS = CommunityPlotWindow.HOE_TO_FERTILIZE_TICKS;
    private long preparedExpiresAt = 0L;
    /**
     * The countdown mark this plot has already spoken, so a warning is announced once even if the
     * plot is ticked twice at the same game time. Not persisted: after a reload a returning player
     * is told the current mark again, which is the right thing to say to somebody who just arrived.
     */
    private long announcedWarningSeconds = 0L;

    /** How close a player has to be for a plot's countdown or expiry to be news to them. */
    public static final double WARNING_RADIUS = 8.0D;

    public CommunityFarmBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.COMMUNITY_FARM_BLOCK_BE.get(), pos, blockState);
    }

    public void markPrepared(long gameTime) {
        this.preparedExpiresAt = gameTime + PREPARED_EXPIRY_TICKS;
        this.announcedWarningSeconds = 0L;
        setChanged();
    }

    public void clearPreparedExpiry() {
        this.preparedExpiresAt = 0L;
        this.announcedWarningSeconds = 0L;
        setChanged();
    }

    /** True once, for each countdown mark, so a repeated tick cannot repeat the warning. */
    private boolean claimWarning(long seconds) {
        if (announcedWarningSeconds == seconds) {
            return false;
        }
        announcedWarningSeconds = seconds;
        return true;
    }

    /** True when this plot's hoeing has run out; the caller must not take the player's dirt. */
    public boolean preparationExpired(Level level) {
        return CommunityPlotWindow.expired(level.getGameTime(), preparedExpiresAt);
    }

    public long getPreparedExpiresAt() {
        return preparedExpiresAt;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CommunityFarmBlockEntity blockEntity) {
        boolean prepared = state.getBlock() instanceof CommunityHoedFarmBlock
                || (state.getBlock() instanceof CommunityFarmBlock && state.getValue(CommunityFarmBlock.PREPARED));
        if (!prepared) {
            if (blockEntity.preparedExpiresAt != 0L) {
                blockEntity.clearPreparedExpiry();
            }
            return;
        }

        if (blockEntity.preparedExpiresAt <= 0L) {
            blockEntity.markPrepared(level.getGameTime());
            return;
        }

        // M9 item 3: the countdown is spoken on the way down, not only at both ends. The marks and
        // the arithmetic are CommunityPlotWindow's, shared with the seed window, so the two
        // countdowns cannot drift apart.
        long now = level.getGameTime();
        java.util.OptionalLong warning = CommunityPlotWindow.warningAt(now, blockEntity.preparedExpiresAt);
        if (warning.isPresent() && blockEntity.claimWarning(warning.getAsLong())) {
            Player audience = level.getNearestPlayer(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, WARNING_RADIUS, false);
            if (audience != null) {
                audience.displayClientMessage(
                        Component.translatable(QuestScreenText.PLOT_HOED_WARNING, warning.getAsLong())
                                .withStyle(ChatFormatting.YELLOW), true);
            }
        }

        if (blockEntity.preparedExpiresAt > 0L && level.getGameTime() >= blockEntity.preparedExpiresAt) {
            blockEntity.preparedExpiresAt = 0L;
            blockEntity.announcedWarningSeconds = 0L;
            blockEntity.setChanged();
            level.setBlock(pos, BlockRegistry.COMMUNITY_FARM_BLOCK.get().defaultBlockState(), 3);
            // M8 item 8: the countdown was announced when it started and never when it ran out, so
            // a hoed plot silently became grass again while the player was fetching dirt. Message
            // only -- the expiry above is unchanged in timing and effect -- and only to somebody
            // close enough for it to be news.
            Player nearby = level.getNearestPlayer(
                    pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, WARNING_RADIUS, false);
            if (nearby != null) {
                nearby.displayClientMessage(
                        Component.translatable(QuestScreenText.PLOT_EXPIRED)
                                .withStyle(ChatFormatting.YELLOW), true);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("PreparedExpiresAt", preparedExpiresAt);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        preparedExpiresAt = tag.getLong("PreparedExpiresAt");
    }
}
