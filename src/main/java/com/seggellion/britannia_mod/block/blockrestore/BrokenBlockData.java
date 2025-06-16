package com.seggellion.britannia_mod.blockrestore;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public class BrokenBlockData {
    public final BlockPos pos;
    public final BlockState originalState;
    public final long brokenTime;
    public final UUID playerUUID; // Add player UUID to track who broke the block

    public BrokenBlockData(BlockPos pos, BlockState originalState, long brokenTime, UUID playerUUID) {
        this.pos = pos;
        this.originalState = originalState;
        this.brokenTime = brokenTime;
        this.playerUUID = playerUUID;
    }
public CompoundTag toNbt() {
    CompoundTag tag = new CompoundTag();
    tag.putInt("x", pos.getX());
    tag.putInt("y", pos.getY());
    tag.putInt("z", pos.getZ());
    tag.put("blockState", NbtUtils.writeBlockState(originalState));
    tag.putLong("brokenTime", brokenTime);
    tag.putUUID("playerUUID", playerUUID);
    return tag;
}

public static BrokenBlockData fromNbt(CompoundTag tag) {
    BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
    BlockState state = NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), tag.getCompound("blockState"));
    long time = tag.getLong("brokenTime");
    UUID playerUUID = tag.getUUID("playerUUID");
    return new BrokenBlockData(pos, state, time, playerUUID);
}


}
