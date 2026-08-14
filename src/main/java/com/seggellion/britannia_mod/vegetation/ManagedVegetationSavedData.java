package com.seggellion.britannia_mod.vegetation;

import com.mojang.logging.LogUtils;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Per-dimension durable registry for managed vegetation nodes. */
public final class ManagedVegetationSavedData extends SavedData {
    public static final String DATA_NAME = "britannia_managed_vegetation";
    public static final int SCHEMA_VERSION = 1;
    private static final Logger LOGGER = LogUtils.getLogger();

    private final Map<Long, ManagedVegetationNode> nodes = new LinkedHashMap<>();
    private final Map<Long, LinkedHashSet<Long>> nodesByChunk = new LinkedHashMap<>();
    private final TreeMap<Long, LinkedHashSet<Long>> transitionsByTime = new TreeMap<>();

    public static ManagedVegetationSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ManagedVegetationSavedData::new, ManagedVegetationSavedData::load),
                DATA_NAME
        );
    }

    public static ManagedVegetationSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        ManagedVegetationSavedData data = new ManagedVegetationSavedData();
        if (!tag.contains("SchemaVersion", Tag.TAG_INT)
                || tag.getInt("SchemaVersion") != SCHEMA_VERSION) {
            LOGGER.warn("Ignoring managed vegetation data with missing or unsupported schema version");
            return data;
        }
        if (!tag.contains("Nodes", Tag.TAG_LIST)) {
            return data;
        }
        ListTag list = tag.getList("Nodes", Tag.TAG_COMPOUND);
        for (int index = 0; index < list.size(); index++) {
            try {
                ManagedVegetationNode node = ManagedVegetationNode.fromTag(list.getCompound(index));
                if (data.nodes.containsKey(node.position().asLong())) {
                    throw new IllegalArgumentException("duplicate node position " + node.position().toShortString());
                }
                data.putInternal(node);
            } catch (RuntimeException exception) {
                LOGGER.warn("Skipping invalid managed vegetation node at index {}: {}", index, exception.getMessage());
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putInt("SchemaVersion", SCHEMA_VERSION);
        ListTag list = new ListTag();
        nodes.values().forEach(node -> list.add(node.toTag()));
        tag.put("Nodes", list);
        return tag;
    }

    public boolean register(ManagedVegetationNode node) {
        long key = node.position().asLong();
        if (nodes.containsKey(key)) {
            return false;
        }
        putInternal(node);
        setDirty();
        return true;
    }

    public void update(ManagedVegetationNode node) {
        long key = node.position().asLong();
        ManagedVegetationNode previous = nodes.get(key);
        if (previous == null) {
            throw new IllegalArgumentException("Cannot update an unregistered managed vegetation node");
        }
        if (previous.equals(node)) {
            return;
        }
        removeIndexes(previous);
        putInternal(node);
        setDirty();
    }

    public Optional<ManagedVegetationNode> nodeAt(net.minecraft.core.BlockPos position) {
        return Optional.ofNullable(nodes.get(position.asLong()));
    }

    public boolean remove(net.minecraft.core.BlockPos position) {
        ManagedVegetationNode removed = nodes.remove(position.asLong());
        if (removed == null) {
            return false;
        }
        removeIndexes(removed);
        setDirty();
        return true;
    }

    public Collection<ManagedVegetationNode> snapshot() {
        return List.copyOf(nodes.values());
    }

    public List<ManagedVegetationNode> nodesInChunk(ChunkPos chunkPosition) {
        LinkedHashSet<Long> positions = nodesByChunk.get(chunkPosition.toLong());
        if (positions == null || positions.isEmpty()) {
            return List.of();
        }
        return positions.stream().map(nodes::get).filter(java.util.Objects::nonNull).toList();
    }

    /** Removes up to {@code limit} due runtime entries without changing persisted node state. */
    public List<ManagedVegetationNode> pollDue(long gameTime, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        List<ManagedVegetationNode> due = new ArrayList<>(Math.min(limit, 64));
        while (due.size() < limit && !transitionsByTime.isEmpty()) {
            Map.Entry<Long, LinkedHashSet<Long>> first = transitionsByTime.firstEntry();
            if (first.getKey() > gameTime) {
                break;
            }
            var iterator = first.getValue().iterator();
            while (iterator.hasNext() && due.size() < limit) {
                long position = iterator.next();
                iterator.remove();
                ManagedVegetationNode node = nodes.get(position);
                if (node != null && node.nextTransitionGameTime() == first.getKey()) {
                    due.add(node);
                }
            }
            if (first.getValue().isEmpty()) {
                transitionsByTime.pollFirstEntry();
            }
        }
        return List.copyOf(due);
    }

    private void putInternal(ManagedVegetationNode node) {
        long position = node.position().asLong();
        nodes.put(position, node);
        nodesByChunk.computeIfAbsent(new ChunkPos(node.position()).toLong(), ignored -> new LinkedHashSet<>())
                .add(position);
        if (node.nextTransitionGameTime() != ManagedVegetationNode.NO_TRANSITION) {
            transitionsByTime.computeIfAbsent(node.nextTransitionGameTime(), ignored -> new LinkedHashSet<>())
                    .add(position);
        }
    }

    private void removeIndexes(ManagedVegetationNode node) {
        long position = node.position().asLong();
        long chunk = new ChunkPos(node.position()).toLong();
        LinkedHashSet<Long> chunkNodes = nodesByChunk.get(chunk);
        if (chunkNodes != null) {
            chunkNodes.remove(position);
            if (chunkNodes.isEmpty()) {
                nodesByChunk.remove(chunk);
            }
        }
        if (node.nextTransitionGameTime() != ManagedVegetationNode.NO_TRANSITION) {
            LinkedHashSet<Long> scheduled = transitionsByTime.get(node.nextTransitionGameTime());
            if (scheduled != null) {
                scheduled.remove(position);
                if (scheduled.isEmpty()) {
                    transitionsByTime.remove(node.nextTransitionGameTime());
                }
            }
        }
    }
}
