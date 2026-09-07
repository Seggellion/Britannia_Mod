package com.seggellion.britannia_mod.block;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import java.util.*;

/** Reconcile saved fence state after a full chunk becomes available, never loading new chunks. */
@EventBusSubscriber(modid = BritanniaMod.MODID)
public final class WoodenFenceLoadHandler {
    private static final Map<ServerLevel, Set<ChunkPos>> PENDING = new LinkedHashMap<>();

    @SubscribeEvent
    public static void loaded(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) synchronized (PENDING) {
            var pos = event.getChunk().getPos();
            var queue = PENDING.computeIfAbsent(level, ignored -> new LinkedHashSet<>());
            queue.add(pos);
            // A newly loaded junction can change a run extending into its loaded neighbours.
            queue.add(new ChunkPos(pos.x-1, pos.z)); queue.add(new ChunkPos(pos.x+1, pos.z));
            queue.add(new ChunkPos(pos.x, pos.z-1)); queue.add(new ChunkPos(pos.x, pos.z+1));
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        synchronized (PENDING) { PENDING.keySet().removeIf(level -> level.getServer() == event.getServer()); }
    }

    @SubscribeEvent
    public static void tick(ServerTickEvent.Post event) {
        var work = new ArrayList<Map.Entry<ServerLevel, ChunkPos>>();
        synchronized (PENDING) {
            int budget = 4;
            for (var entry : PENDING.entrySet()) {
                if (entry.getKey().getServer() != event.getServer()) continue;
                var positions = entry.getValue().iterator();
                while (positions.hasNext() && budget > 0) {
                    work.add(Map.entry(entry.getKey(), positions.next())); positions.remove(); budget--;
                }
            }
            PENDING.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
        for (var entry : work) inspect(entry.getKey(), entry.getValue());
    }

    /** Public for the same real chunk-load boundary in the GameTest harness. */
    public static void inspect(ServerLevel level, ChunkPos pos) {
        var chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
        if (chunk == null) return;
        var sections = chunk.getSections();
        for (int i = 0; i < sections.length; i++) {
            var section = sections[i];
            if (!section.maybeHas(state -> state.getBlock() instanceof WoodenFenceBlock)) continue;
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
            for (int y=0; y<16; y++) for (int z=0; z<16; z++) for (int x=0; x<16; x++) {
                var state = section.getBlockState(x,y,z);
                if (state.getBlock() instanceof WoodenFenceBlock fence)
                    level.scheduleTick(new BlockPos(pos.getMinBlockX()+x,baseY+y,pos.getMinBlockZ()+z), fence, 1);
            }
        }
    }
}
