package com.seggellion.britannia_mod.entity;

import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

import java.util.List;

public class LayEntity extends Entity {

    private static final EntityDimensions SIZE = EntityDimensions.fixed(0.0F, 0.0F);
    private final int slot;
    private final BlockPos basePos;

    // ✅ Constructor used manually when placing the bed
    public LayEntity(Level level, BlockPos bedPos, int slot, Direction facing) {
        super(EntityRegistry.LAY_ENTITY.get(), level);
        this.slot = slot;
        this.basePos = bedPos.immutable();

        double dx = facing.getClockWise().step().x() * 0.45D * (slot == 0 ? -1 : 1);
        double dz = facing.getClockWise().step().z() * 0.45D * (slot == 0 ? -1 : 1);

        this.setPos(bedPos.getX() + 0.5D + dx,
                    bedPos.getY() + 0.6875D,
                    bedPos.getZ() + 0.5D + dz);
    }

    // ✅ Required constructor for registry
    public LayEntity(EntityType<? extends LayEntity> type, Level level) {
        super(type, level);
        this.slot = 0;
        this.basePos = BlockPos.ZERO;
    }

    public static LayEntity find(Level level, BlockPos pos, int slot) {
        List<LayEntity> list = level.getEntitiesOfClass(LayEntity.class,
                new AABB(pos).inflate(1), e -> e.slot == slot && e.basePos.equals(pos));
        return list.isEmpty() ? null : list.get(0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public EntityDimensions getDimensions(Pose pose) { return SIZE; }

    @Override
    public boolean isInvisible() { return true; }

    @Override
    public boolean canCollideWith(Entity e) { return false; }

    @Override
    public boolean canBeCollidedWith() { return false; }

    @Override
    public void tick() {
        super.tick();
        if (this.getPassengers().isEmpty() && !level().isClientSide)
            this.discard();
    }
}
