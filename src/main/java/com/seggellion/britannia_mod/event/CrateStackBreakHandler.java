package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.crate.CrateStackBreakTargets;
import com.seggellion.britannia_mod.crate.CrateStackTargetResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Notices which crate a player has started breaking.
 *
 * <h2>Why a swing needs a memory</h2>
 *
 * <p>Breaking a block takes time, and a column can change while it does. Another player can remove a
 * crate lower down, and everything above slides. Deciding the victim at the end — by looking at where
 * the ray now lands — would destroy whichever crate had moved into that height. So the crate is
 * identified when the swing starts and remembered by its stable id until the swing finishes.
 *
 * <p>{@code PlayerInteractEvent.LeftClickBlock} is the right hook because NeoForge fires it as the
 * very first statement of {@code ServerPlayerGameMode.handleBlockBreakAction}, with the action that
 * caused it, so START, ABORT and STOP are all distinguishable without a mixin or a custom packet.
 *
 * <h2>This authorises nothing</h2>
 *
 * <p>Capturing a target is bookkeeping. Whether the break may happen at all is still decided
 * afterwards by reach, spawn protection, adventure rules, house and region policy, and
 * {@code BlockEvent.BreakEvent} — every one of which runs after this and can stop the break, in which
 * case no crate is ever removed. Nothing here is cancelled, and nothing here grants permission.
 */
public final class CrateStackBreakHandler {

    /**
     * Remembers the crate a swing began on, and forgets it when the swing ends.
     */
    @SubscribeEvent
    public void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        switch (event.getAction()) {
            case START -> capture(player, event.getPos());
            case ABORT -> CrateStackBreakTargets.clear(player);
            default -> {
                // STOP is the completion of a swing; the target has to survive until the block's own
                // destruction runs and consumes it.
            }
        }
    }

    private static void capture(ServerPlayer player, BlockPos clicked) {
        if (!(player.level().getBlockState(clicked).getBlock() instanceof CrateStackBlock)) {
            CrateStackBreakTargets.clear(player);
            return;
        }
        BlockPos root = CrateStackBlock.rootOf(clicked, player.level().getBlockState(clicked));

        // A held mouse button does not stop when a crate breaks: the client sees the block go and
        // immediately begins on whatever it now aims at, which on a column is the next crate down.
        if (CrateStackBreakTargets.withinCascadeGuard(player, root)) {
            CrateStackBreakTargets.clear(player);
            return;
        }

        // The packet says which cell was clicked, not where on it. The server does its own raycast,
        // at the player's real interaction range, and only trusts it if it agrees with the packet -
        // otherwise a stale or spoofed aim could pick a crate the player is not looking at.
        HitResult aimed = player.pick(player.blockInteractionRange(), 0.0F, false);
        if (!(aimed instanceof BlockHitResult hit)
                || hit.getType() != HitResult.Type.BLOCK
                || !CrateStackBlock.rootOf(hit.getBlockPos(),
                        player.level().getBlockState(hit.getBlockPos())).equals(root)) {
            CrateStackBreakTargets.clear(player);
            return;
        }

        CrateStackTargetResolver.resolve(player.level(), hit).ifPresentOrElse(
                target -> CrateStackBreakTargets.capture(
                        player, target.root(), target.crateId(), clicked),
                () -> CrateStackBreakTargets.clear(player));
    }

    /** A player who leaves takes their half-finished swing with them. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        CrateStackBreakTargets.clear(event.getEntity().getUUID());
    }

    /** Changing dimension abandons whatever was being broken in the last one. */
    @SubscribeEvent
    public void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        CrateStackBreakTargets.clear(event.getEntity().getUUID());
    }
}
