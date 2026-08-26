package com.seggellion.britannia_mod.mixin;

import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Diagnostics only: exposes the pending-teleport field so {@code /grabby debug} can report it.
 *
 * <p>{@code ServerGamePacketListenerImpl.handleUseItemOn} returns without doing anything at all
 * while {@code awaitingPositionFromClient} is set — the server is waiting for the client to
 * acknowledge a teleport, and until it does, every block-use packet is discarded with no message
 * and no log line. It is one of the handful of ways an interaction can die above the event bus, so a
 * diagnosis that could not see it would have a blind spot exactly where the defect being chased
 * lives.
 *
 * <p>{@code @Accessor} on a field, read-only, and nothing in the mod writes it. This deliberately
 * does not touch {@code isUnderSpawnProtection} or any other decision: a diagnostic that changed the
 * behaviour it is diagnosing would be worse than no diagnostic. Kept separate from the production
 * mixins for the same reason {@link PlayerListAccessorMixin} is.
 */
@Mixin(value = ServerGamePacketListenerImpl.class, remap = false)
public interface ServerGamePacketListenerAccessorMixin {
    @Accessor("awaitingPositionFromClient")
    @Nullable
    Vec3 britannia$awaitingPositionFromClient();
}
