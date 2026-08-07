package com.seggellion.britannia_mod.block;

import net.minecraft.world.level.block.state.properties.BooleanProperty;

/**
 * A block whose window furniture (frame, mullion or support) can be flipped to the opposite side of
 * the opening without changing which way the block faces.
 *
 * <p>Deliberately a plain block state property rather than block entity data: the combined state
 * space stays small, persistence and server-to-client sync come for free, and already-placed blocks
 * keep working because the property defaults to the unmirrored model.
 *
 * <p>The interior decorator tool drives this; see
 * {@code InteriorDecoratorToolItem}, which handles it before the generic facing rotation so that
 * right-clicking a window swaps the side instead of spinning the block.
 */
public interface WindowSideToggleable {

    /** The property holding the window side. {@code false} = normal, {@code true} = mirrored. */
    BooleanProperty windowSideProperty();
}
