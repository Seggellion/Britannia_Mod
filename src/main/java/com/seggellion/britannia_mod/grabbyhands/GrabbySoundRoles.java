package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * The three sound roles Grabby Hands needs, resolved from existing vanilla and block-declared sounds.
 *
 * <p>No new {@code .ogg} assets and no new {@code SoundEvent} registrations are required.
 *
 * <h2>Known content defect this class is deliberately not papering over</h2>
 *
 * <p>{@link #destroy(BlockState)} reads the block's own declared {@code SoundType}, which is the
 * right architecture: the block already claims to know what it is made of. Wine bottles declare
 * {@code SoundType.GLASS} and armoires declare {@code SoundType.WOOD}, so those are already correct.
 *
 * <p>Chairs, tables and chests never call {@code .sound(...)} in {@code BlockRegistry}, so they
 * inherit {@code BlockBehaviour.Properties.of()}'s {@code SoundType.STONE} default and currently
 * break with a stone sound. The fix is a one-line {@code .sound(SoundType.WOOD)} per registration,
 * which also corrects their existing place/step sounds. That belongs to content enrollment (M11),
 * not to this resolver — adding a Grabby-only material override here would duplicate data the block
 * already owns and would leave the underlying defect in place.
 */
public final class GrabbySoundRoles {
    private GrabbySoundRoles() {
    }

    /**
     * Played when the server has accepted a pickup and the object is leaving the world.
     *
     * <p>Short and physical, and deliberately not an inventory click, so it cannot be confused with
     * {@link #stow()}.
     */
    public static SoundEvent grab() {
        return SoundEvents.ITEM_FRAME_REMOVE_ITEM;
    }

    /**
     * Played only after the portable item is committed to the player's inventory.
     *
     * <p>The vanilla "this entered your inventory" cue, which players already read as success. Must
     * never play when insertion fails — that is a transaction ordering rule, enforced in M2.
     */
    public static SoundEvent stow() {
        return SoundEvents.ITEM_PICKUP;
    }

    /**
     * Played after an axe destruction transaction commits, never on the confirmation prompt itself.
     *
     * <p>Falls back to a generic wood break only if the state is unavailable.
     */
    public static SoundEvent destroy(@Nullable BlockState state) {
        return state == null ? SoundEvents.WOOD_BREAK : state.getSoundType().getBreakSound();
    }
}
