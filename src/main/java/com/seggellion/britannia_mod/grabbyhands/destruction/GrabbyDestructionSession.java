package com.seggellion.britannia_mod.grabbyhands.destruction;

import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;
import java.util.UUID;

/**
 * One outstanding "do you wish to destroy this?" question.
 *
 * <p>Modelled on {@code dye.preview.DyePreviewSession}, which solves the same problem for a confirmed
 * destructive action: the server keeps the authority, the client is handed nothing but an id, and the
 * server re-checks everything when the answer comes back.
 *
 * <p>The snapshots matter. Without them a player could open the prompt, swap their axe or watch the
 * block change, and still confirm a destruction that was authorised against different facts.
 */
public record GrabbyDestructionSession(
        UUID sessionId,
        UUID playerId,
        BlockPos position,
        BlockState expectedState,
        ItemStack expectedTool,
        long createdAtMillis,
        long expiresAtMillis
) {
    public GrabbyDestructionSession {
        Objects.requireNonNull(sessionId, "sessionId");
        Objects.requireNonNull(playerId, "playerId");
        position = Objects.requireNonNull(position, "position").immutable();
        Objects.requireNonNull(expectedState, "expectedState");
        expectedTool = Objects.requireNonNull(expectedTool, "expectedTool").copy();
        if (createdAtMillis < 0 || expiresAtMillis <= createdAtMillis) {
            throw new IllegalArgumentException("A confirmation must expire after it was issued");
        }
    }

    public boolean expired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public boolean belongsTo(UUID candidate) {
        return playerId.equals(candidate);
    }

    /** Whether the world still matches what the player was asked about. */
    public boolean stateUnchanged(BlockState actual) {
        return expectedState.equals(actual);
    }

    /** Whether the player is still holding the axe the prompt was authorised against. */
    public boolean toolUnchanged(ItemStack actual) {
        return ItemStack.isSameItemSameComponents(expectedTool, actual);
    }

    @Override
    public ItemStack expectedTool() {
        return expectedTool.copy();
    }
}
