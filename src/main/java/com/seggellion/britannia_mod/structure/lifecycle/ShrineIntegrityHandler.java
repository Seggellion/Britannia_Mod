package com.seggellion.britannia_mod.structure.lifecycle;

import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Capped chunk-load queue processed after promotion; it never performs a global structure scan. */
public final class ShrineIntegrityHandler {
    public static final int MAX_CHUNKS_PER_TICK = 64;
    public static final int MAX_PENDING_CHUNKS_PER_LEVEL = 4096;
    private static final ShrineIntegrityHandler INSTANCE = new ShrineIntegrityHandler();
    private static final Map<ServerLevel, LinkedHashSet<ChunkPos>> PENDING = new LinkedHashMap<>();
    private static boolean registered;

    private ShrineIntegrityHandler() {
    }

    public static synchronized void register() {
        if (!registered) {
            NeoForge.EVENT_BUS.register(INSTANCE);
            registered = true;
        }
    }

    @SubscribeEvent
    public void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            synchronized (PENDING) {
                LinkedHashSet<ChunkPos> pending = PENDING.computeIfAbsent(level, ignored -> new LinkedHashSet<>());
                ChunkPos loaded = event.getChunk().getPos();
                for (int deltaX = -1; deltaX <= 1; deltaX++) {
                    for (int deltaZ = -1; deltaZ <= 1; deltaZ++) {
                        pending.add(new ChunkPos(loaded.x + deltaX, loaded.z + deltaZ));
                    }
                }
                while (pending.size() > MAX_PENDING_CHUNKS_PER_LEVEL) {
                    pending.remove(pending.getFirst());
                }
            }
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            synchronized (PENDING) {
                PENDING.remove(level);
            }
        }
    }

    @SubscribeEvent
    public void afterServerTick(ServerTickEvent.Post event) {
        List<Map.Entry<ServerLevel, List<ChunkPos>>> work = new ArrayList<>();
        synchronized (PENDING) {
            int remaining = MAX_CHUNKS_PER_TICK;
            var iterator = PENDING.entrySet().iterator();
            while (iterator.hasNext() && remaining > 0) {
                var entry = iterator.next();
                List<ChunkPos> selected = entry.getValue().stream().limit(remaining).toList();
                selected.forEach(entry.getValue()::remove);
                work.add(Map.entry(entry.getKey(), selected));
                remaining -= selected.size();
                if (entry.getValue().isEmpty()) {
                    iterator.remove();
                }
            }
        }
        work.forEach(entry -> entry.getValue().forEach(chunk -> inspectLoadedChunk(entry.getKey(), chunk)));
    }

    static void inspectLoadedChunk(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return;
        }
        chunk.getBlockEntities().values().stream()
                .filter(LargeStructureAnchorBlockEntity.class::isInstance)
                .map(LargeStructureAnchorBlockEntity.class::cast)
                .forEach(anchor -> ShrineIntegrityService.checkAnchor(level, anchor.getBlockPos()));

        List<Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState>> parts = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.getBlock() instanceof LargeStructurePartBlock)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        var state = section.getBlockState(x, y, z);
                        if (state.getBlock() instanceof LargeStructurePartBlock) {
                            parts.add(Map.entry(new BlockPos(
                                    chunkPos.getMinBlockX() + x, baseY + y, chunkPos.getMinBlockZ() + z), state));
                        }
                    }
                }
            }
        }
        parts.forEach(part -> ShrineIntegrityService.checkPart(level, part.getKey(), part.getValue()));
    }

    static synchronized int pendingCount() {
        synchronized (PENDING) {
            return PENDING.values().stream().mapToInt(Set::size).sum();
        }
    }
}
