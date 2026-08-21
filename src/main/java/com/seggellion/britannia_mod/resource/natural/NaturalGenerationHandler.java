package com.seggellion.britannia_mod.resource.natural;

import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;

/**
 * The one place natural deposits enter the world.
 *
 * <h2>Why not a {@code PlacedFeature}</h2>
 * A vanilla-style feature is the obvious answer and it is the wrong one here. Features run on
 * world-generation worker threads, and a natural deposit has to register itself in the persistent
 * deposit ledger — {@code SavedData} on the level — before it may place anything. Doing that from a
 * generation thread would be a data race against every other chunk generating at the same time, and
 * against the server thread saving. There is no way to make that safe that does not amount to
 * moving the work back onto the server thread, so it is done there in the first place.
 *
 * <p>Nothing is lost by it. The geometry is still deterministic, the distribution is still derived
 * from the world seed, and the deposit still appears as the chunk is generated. What is gained is
 * that the ledger, the restoration store and the block writes are all touched from the one thread
 * that owns them.
 *
 * <h2>New chunks only</h2>
 * {@code isNewChunk()} distinguishes a chunk that has just finished generating from one read back
 * off disk: NeoForge derives it from whether the promoted chunk was a real {@code ProtoChunk}, and
 * a stored full chunk always comes back wrapped as an {@code ImposterProtoChunk}. So an existing
 * world is never retro-populated, which is both the milestone's rule and the reason no per-chunk
 * "already done" marker has to be persisted anywhere.
 */
public final class NaturalGenerationHandler {

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        NaturalDepositService.populate(level, event.getChunk().getPos());
    }
}
