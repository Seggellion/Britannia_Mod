package com.seggellion.britannia_mod.blockrestore;

import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.server.level.ServerLevel;
import java.util.Map;
import net.minecraft.core.BlockPos;
import java.util.concurrent.ConcurrentHashMap;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockTracker;
import com.seggellion.britannia_mod.blockrestore.BrokenBlockData;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

public class BrokenBlockDataStorage extends SavedData {
    private static final String DATA_NAME = "broken_blocks";
    private final Map<BlockPos, BrokenBlockData> brokenBlocks = new ConcurrentHashMap<>();


public BrokenBlockDataStorage() {
    super();
}

public BrokenBlockDataStorage(CompoundTag tag, HolderLookup.Provider provider) {
    super();
    ListTag list = tag.getList("blocks", Tag.TAG_COMPOUND);
    for (Tag item : list) {
        CompoundTag blockTag = (CompoundTag) item;
        BrokenBlockData data = BrokenBlockData.fromNbt(blockTag);
        this.brokenBlocks.put(data.pos, data);
    }
}


public static BrokenBlockDataStorage get(ServerLevel level) {
    return level.getDataStorage().computeIfAbsent(
        new SavedData.Factory<>(
            () -> new BrokenBlockDataStorage(),                          // for creation
            (tag, provider) -> new BrokenBlockDataStorage(tag, provider) // for loading
        ),
        DATA_NAME
    );
}


    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag list = new ListTag();
        for (BrokenBlockData data : brokenBlocks.values()) {
            list.add(data.toNbt());
        }
        tag.put("blocks", list);
        return tag;
    }

    public Map<BlockPos, BrokenBlockData> getBrokenBlocks() {
        return brokenBlocks;
    }

    public void add(BrokenBlockData data) {
        brokenBlocks.put(data.pos, data);
        setDirty(); // Triggers saving
    }

    public void remove(BlockPos pos) {
        brokenBlocks.remove(pos);
        setDirty();
    }
}
