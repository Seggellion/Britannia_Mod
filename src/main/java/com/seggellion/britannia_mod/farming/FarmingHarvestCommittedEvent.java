package com.seggellion.britannia_mod.farming;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;

/** Non-cancellable server fact, emitted once after a successful root lifecycle commit.
 * Future quest listeners can opt in; no backend quest or reward is created here.
 * Administrative testing is identified explicitly so reward listeners can ignore it.
 */
public final class FarmingHarvestCommittedEvent extends Event {
    private final ServerPlayer player;
    private final BlockPos root;
    private final String speciesId;
    private final boolean administrative;
    public FarmingHarvestCommittedEvent(ServerPlayer player, BlockPos root, String speciesId, boolean administrative) {
        this.player=player; this.root=root.immutable(); this.speciesId=speciesId; this.administrative=administrative;
    }
    public ServerPlayer player() { return player; }
    public BlockPos root() { return root; }
    public String speciesId() { return speciesId; }
    public boolean administrative() { return administrative; }
}
