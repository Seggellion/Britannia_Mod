package com.seggellion.britannia_mod.mixin;

import net.minecraft.server.players.PlayerList;
import net.minecraft.world.level.storage.PlayerDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Test-support only: exposes {@code PlayerList}'s private {@code playerIo} field so a GameTest
 * can load a player's just-saved data back through the REAL vanilla deserialization path ({@link
 * PlayerDataStorage#load}) rather than hand-parsing the on-disk NBT format -- the same
 * bypass-the-live-instance, read-fresh-from-disk technique {@code BankTransferReceiptStore}'s
 * own GameTests already use, applied to player data instead of SavedData. {@code @Accessor} (not
 * {@code @Invoker}) is correct here because the target is a field, not a method. Kept as its
 * own mixin, separate from {@link PlayerListInvokerMixin}, so the production fix's mixin surface
 * carries no test-only accessors.
 */
@Mixin(value = PlayerList.class, remap = false)
public interface PlayerListAccessorMixin {
    @Accessor("playerIo")
    PlayerDataStorage britannia$playerIo();
}
