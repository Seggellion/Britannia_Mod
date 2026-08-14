package com.seggellion.britannia_mod.wildresource;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Per-dimension spawn cadence and placed-node accounting for wild resources. */
public final class WildResourceSavedData extends SavedData {
    public static final String DATA_NAME = "britannia_wild_resources";
    public static final int SCHEMA_VERSION = 1;
    public static final long UNSCHEDULED = Long.MAX_VALUE;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<ChunkResourceKey, Long> nextAttempts = new LinkedHashMap<>();
    private final Map<Long, WildResourceNode> nodes = new LinkedHashMap<>();
    private final Map<Long, LinkedHashSet<Long>> nodesByChunk = new LinkedHashMap<>();

    public static WildResourceSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(WildResourceSavedData::new, WildResourceSavedData::load),
                DATA_NAME
        );
    }

    public static WildResourceSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        WildResourceSavedData data = new WildResourceSavedData();
        if (!tag.contains("SchemaVersion", Tag.TAG_INT) || tag.getInt("SchemaVersion") != SCHEMA_VERSION) {
            LOGGER.warn("Ignoring wild resource data with missing or unsupported schema version");
            return data;
        }
        ListTag schedules = tag.getList("Schedules", Tag.TAG_COMPOUND);
        for (int index = 0; index < schedules.size(); index++) {
            try {
                CompoundTag schedule = schedules.getCompound(index);
                ResourceLocation id = requireId(schedule, "ResourceId");
                long chunk = schedule.getLong("Chunk");
                long nextAttempt = schedule.getLong("NextAttempt");
                if (nextAttempt < 0) {
                    throw new IllegalArgumentException("negative next attempt");
                }
                data.nextAttempts.put(new ChunkResourceKey(chunk, id), nextAttempt);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid wild resource schedule at index {}: {}", index, exception.getMessage());
            }
        }
        ListTag savedNodes = tag.getList("Nodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < savedNodes.size(); index++) {
            try {
                WildResourceNode node = WildResourceNode.fromTag(savedNodes.getCompound(index));
                if (data.nodes.containsKey(node.position().asLong())) {
                    throw new IllegalArgumentException("duplicate node position " + node.position().toShortString());
                }
                data.putNode(node);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid wild resource node at index {}: {}", index, exception.getMessage());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag schedules = new ListTag();
        nextAttempts.forEach((key, nextAttempt) -> {
            CompoundTag schedule = new CompoundTag();
            schedule.putLong("Chunk", key.chunk());
            schedule.putString("ResourceId", key.resourceId().toString());
            schedule.putLong("NextAttempt", nextAttempt);
            schedules.add(schedule);
        });
        tag.put("Schedules", schedules);
        ListTag savedNodes = new ListTag();
        nodes.values().forEach(node -> savedNodes.add(node.toTag()));
        tag.put("Nodes", savedNodes);
        return tag;
    }

    public long nextAttempt(ChunkPos chunk, ResourceLocation resourceId) {
        return nextAttempts.getOrDefault(new ChunkResourceKey(chunk.toLong(), resourceId), UNSCHEDULED);
    }

    public void scheduleAttempt(ChunkPos chunk, ResourceLocation resourceId, long gameTime) {
        if (gameTime < 0) {
            throw new IllegalArgumentException("gameTime cannot be negative");
        }
        ChunkResourceKey key = new ChunkResourceKey(chunk.toLong(), resourceId);
        if (nextAttempts.getOrDefault(key, -1L) != gameTime) {
            nextAttempts.put(key, gameTime);
            setDirty();
        }
    }

    public boolean registerNode(WildResourceNode node) {
        if (nodes.containsKey(node.position().asLong())) {
            return false;
        }
        putNode(node);
        setDirty();
        return true;
    }

    public Optional<WildResourceNode> nodeAt(BlockPos position) {
        return Optional.ofNullable(nodes.get(position.asLong()));
    }

    public Optional<WildResourceNode> removeNode(BlockPos position) {
        WildResourceNode removed = nodes.remove(position.asLong());
        if (removed == null) {
            return Optional.empty();
        }
        LinkedHashSet<Long> chunkNodes = nodesByChunk.get(new ChunkPos(position).toLong());
        if (chunkNodes != null) {
            chunkNodes.remove(position.asLong());
            if (chunkNodes.isEmpty()) {
                nodesByChunk.remove(new ChunkPos(position).toLong());
            }
        }
        setDirty();
        return Optional.of(removed);
    }

    public List<WildResourceNode> nodesInChunk(ChunkPos chunk) {
        LinkedHashSet<Long> positions = nodesByChunk.get(chunk.toLong());
        if (positions == null) {
            return List.of();
        }
        List<WildResourceNode> result = new ArrayList<>(positions.size());
        positions.stream().map(nodes::get).filter(java.util.Objects::nonNull).forEach(result::add);
        return List.copyOf(result);
    }

    public int countInChunk(ChunkPos chunk, ResourceLocation resourceId) {
        return (int) nodesInChunk(chunk).stream().filter(node -> node.resourceId().equals(resourceId)).count();
    }

    public boolean hasSameTypeWithin(ResourceLocation resourceId, BlockPos position, int radius) {
        if (radius <= 0) {
            return false;
        }
        int chunkRadius = (radius + 15) / 16;
        ChunkPos center = new ChunkPos(position);
        long radiusSquared = (long) radius * radius;
        for (int chunkX = center.x - chunkRadius; chunkX <= center.x + chunkRadius; chunkX++) {
            for (int chunkZ = center.z - chunkRadius; chunkZ <= center.z + chunkRadius; chunkZ++) {
                for (WildResourceNode node : nodesInChunk(new ChunkPos(chunkX, chunkZ))) {
                    if (node.resourceId().equals(resourceId)
                            && node.position().distSqr(position) < radiusSquared) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void putNode(WildResourceNode node) {
        nodes.put(node.position().asLong(), node);
        nodesByChunk.computeIfAbsent(new ChunkPos(node.position()).toLong(), ignored -> new LinkedHashSet<>())
                .add(node.position().asLong());
    }

    private static ResourceLocation requireId(CompoundTag tag, String key) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString(key));
        if (id == null) {
            throw new IllegalArgumentException("Invalid resource id in " + key);
        }
        return id;
    }

    private record ChunkResourceKey(long chunk, ResourceLocation resourceId) {
    }
}
