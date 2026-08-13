package com.seggellion.britannia_mod.grabbyhands;

/**
 * Implemented by a BlockEntity that can carry Grabby Hands provenance.
 *
 * <p>Every object in the initial enrollment set already has a BlockEntity — chairs and tables through
 * {@code NudgeableBlockEntity}, plus {@code WineBottleBlockEntity}, {@code BritanniaChestBlockEntity}
 * and {@code ArmoireBlockEntity} — so provenance needs no new block, no new BlockEntity type, no
 * {@code SavedData}, and no per-tick scanning. It rides along in NBT the block entity already writes.
 *
 * <p>A BlockEntity that does not implement this interface reads as
 * {@link GrabbyInstanceState#worldPlaced()} — protected. That is the intended answer for every block
 * type Grabby Hands has not enrolled.
 */
public interface GrabbyProvenanceHolder {
    /** Never null; defaults to {@link GrabbyInstanceState#worldPlaced()}. */
    GrabbyInstanceState grabbyState();

    /** Replaces provenance and marks the block entity dirty. */
    void setGrabbyState(GrabbyInstanceState state);
}
