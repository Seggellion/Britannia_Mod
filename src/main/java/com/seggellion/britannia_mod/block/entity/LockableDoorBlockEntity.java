package com.seggellion.britannia_mod.block.entity;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.seggellion.britannia_mod.block.LockableDoorBlock;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import com.seggellion.britannia_mod.structure.StructureRecord;
import com.seggellion.britannia_mod.structure.StructureRegionManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.HolderLookup;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The door’s lock is defined by the {@link StructureRecord} that encloses it,
 * not by this block-entity itself.  That lets a single key unlock every door
 * in the same house.
 */
public class LockableDoorBlockEntity extends BlockEntity {
   private boolean locked = true; 

    public LockableDoorBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.LOCKABLE_DOOR.get(), pos, state);
    }

  public boolean isLocked() {
        return locked;
    }

    public void toggleLock() {
        setLocked(!this.locked);
    }

    /**
     * The flag, and the door state that has to follow it.
     *
     * <p>A door standing open on a pressure plate when its house turns private would
     * otherwise stay open: vanilla only re-evaluates a door when the signal changes, and the
     * signal has not changed -- the lock has. So the lock drives the door rather than only
     * being consulted when somebody clicks it.
     *
     * <p>Loading from NBT sets the field directly and deliberately does not come through
     * here; a block entity being read back has no business writing block states.
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
        setChanged();
        LockableDoorBlock.enforceLock(getLevel(), getBlockPos(), locked);
    }
    

   @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        tag.putBoolean("Locked", locked);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        this.locked = tag.getBoolean("Locked");
    }

    /**
     * @return the UUID of the owning structure (house) or {@code null} if the
     *         door is not inside any registered structure region.
     */
    @Nullable
    public UUID getStructureLockId() {
        Level level = getLevel();
        if (level == null) return null;

        // Convert block coords to chunk coords (>> 4 == /16)
        List<StructureRecord> nearby = StructureRegionManager.getStructuresInChunk(
            this.getBlockPos().getX() >> 4,
            this.getBlockPos().getZ() >> 4
        );

        Vec3 doorCenter = Vec3.atCenterOf(this.getBlockPos());
        for (StructureRecord rec : nearby) {
            if (rec.getFullBox().contains(doorCenter)) {
                return rec.getHouseUuid();
            }
        }
        return null;
    }
}
