package com.seggellion.britannia_mod.block.nudgeable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class NudgeableBlockEntity extends BlockEntity {
    private Vec3 offset = Vec3.ZERO;

    protected NudgeableBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    public void nudge(Direction direction) {
        Vec3 delta = Vec3.atLowerCornerOf(direction.getNormal()).scale(1.0 / 16.0);
        offset = offset.add(delta);
        offset = clampOffset(offset);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    private Vec3 clampOffset(Vec3 offset) {
        double maxOffset = 16.0 / 16.0; // 16 voxels = 1 full block
        return new Vec3(
                Mth.clamp(offset.x, -maxOffset, maxOffset),
                Mth.clamp(offset.y, -maxOffset, maxOffset),
                Mth.clamp(offset.z, -maxOffset, maxOffset)
        );
    }

    public Vec3 getOffset() {
        return offset;
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putDouble("OffsetX", offset.x);
        tag.putDouble("OffsetY", offset.y);
        tag.putDouble("OffsetZ", offset.z);
    }

    @Override
    public void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        offset = new Vec3(
                tag.getDouble("OffsetX"),
                tag.getDouble("OffsetY"),
                tag.getDouble("OffsetZ")
        );
    }

    @Override
    public CompoundTag getUpdateTag(net.minecraft.core.HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putDouble("OffsetX", offset.x);
        tag.putDouble("OffsetY", offset.y);
        tag.putDouble("OffsetZ", offset.z);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        offset = new Vec3(
                tag.getDouble("OffsetX"),
                tag.getDouble("OffsetY"),
                tag.getDouble("OffsetZ")
        );
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}