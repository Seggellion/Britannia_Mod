package com.seggellion.britannia_mod.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Bridges the real, protected {@code PlayerList#save(ServerPlayer)} -- the vanilla method that
 * forces exactly one connected player's own NBT to disk (via {@code PlayerDataStorage#save},
 * confirmed real by reading both classes directly: a temp-file write opened with {@code
 * StandardOpenOption.SYNC} -- durable to storage before the call returns, the same durability
 * class as {@code BankTransferReceiptStore}'s own fsync guarantee -- followed by a
 * same-directory {@code Files.move} rename and a zero-listener NeoForge event; no chunk saves,
 * no broadcast, no other player touched) -- as a public, ordinary Java call. {@code @Invoker} is
 * the correct annotation because the target is a method, not a field ({@code @Accessor} would
 * be for a field); this mixin is a Java interface, not a class, matching the required Mixin
 * accessor/invoker shape.
 *
 * <p>{@code remap = false} matches this project's own established convention (every existing
 * mixin in this package sets it -- Mojang's official mappings are used directly in this
 * NeoGradle dev environment and the shipped mod, so no SRG-style remapping step applies).
 *
 * <p>Used by {@code BankTransferPlayerDurability}, the single call site every bank-transfer
 * proxy service goes through -- never called directly, so the cast-and-invoke pattern exists in
 * exactly one place.
 */
@Mixin(value = PlayerList.class, remap = false)
public interface PlayerListInvokerMixin {
    @Invoker("save")
    void britannia$invokeSave(ServerPlayer player);
}
