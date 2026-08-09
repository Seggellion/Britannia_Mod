package com.seggellion.britannia_mod.banner.structure;

import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.blockentity.BannerBlockEntity;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Defers chunk-load integrity work until the chunk has reached a safe full server-tick boundary. */
public final class BannerStructureIntegrityHandler {
    private static final BannerStructureIntegrityHandler INSTANCE = new BannerStructureIntegrityHandler();
    private static final Map<ServerLevel, Set<ChunkPos>> PENDING = new LinkedHashMap<>();
    private static boolean registered;

    private BannerStructureIntegrityHandler() {
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
                PENDING.computeIfAbsent(level, ignored -> new HashSet<>()).add(event.getChunk().getPos());
            }
        }
    }

    @SubscribeEvent
    public void afterServerTick(ServerTickEvent.Post event) {
        List<Map.Entry<ServerLevel, Set<ChunkPos>>> work;
        synchronized (PENDING) {
            work = PENDING.entrySet().stream()
                    .map(entry -> Map.entry(entry.getKey(), Set.copyOf(entry.getValue())))
                    .toList();
            PENDING.clear();
        }
        for (Map.Entry<ServerLevel, Set<ChunkPos>> entry : work) {
            for (ChunkPos chunkPos : entry.getValue()) {
                inspectLoadedChunk(entry.getKey(), chunkPos);
            }
        }
    }

    private static void inspectLoadedChunk(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return;
        }
        List<BannerBlockEntity> anchors = chunk.getBlockEntities().values().stream()
                .filter(BannerBlockEntity.class::isInstance)
                .map(BannerBlockEntity.class::cast)
                .toList();
        anchors.forEach(anchor -> BannerStructureIntegrity.checkAnchor(level, anchor.getBlockPos()));

        List<Map.Entry<BlockPos, net.minecraft.world.level.block.state.BlockState>> parts = new ArrayList<>();
        LevelChunkSection[] sections = chunk.getSections();
        for (int sectionIndex = 0; sectionIndex < sections.length; sectionIndex++) {
            LevelChunkSection section = sections[sectionIndex];
            if (!section.maybeHas(state -> state.getBlock() instanceof BannerPartBlock)) {
                continue;
            }
            int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(sectionIndex));
            for (int localY = 0; localY < 16; localY++) {
                for (int localZ = 0; localZ < 16; localZ++) {
                    for (int localX = 0; localX < 16; localX++) {
                        var state = section.getBlockState(localX, localY, localZ);
                        if (state.getBlock() instanceof BannerPartBlock) {
                            parts.add(Map.entry(new BlockPos(chunkPos.getMinBlockX() + localX,
                                    baseY + localY, chunkPos.getMinBlockZ() + localZ), state));
                        }
                    }
                }
            }
        }
        parts.forEach(part -> BannerStructureIntegrity.checkPart(level, part.getKey(), part.getValue()));
    }
}
