// LichSpawnBlockEntity.java
package com.seggellion.britannia_mod.block.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import com.seggellion.britannia_mod.entity.LichEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;

public class LichSpawnBlockEntity extends BlockEntity {

    private int spawnCooldown = 0;
    private static final int MAX_COOLDOWN = 2000; // Ticks between spawns (10 seconds)
    private static final int MAX_ENTITIES = 2;
    private static final int SPAWN_RADIUS = 15;

public LichSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.LICH_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public void tick() {
        if (!this.level.isClientSide) {
            spawnCooldown--;

            if (spawnCooldown <= 0) {
                spawnCooldown = MAX_COOLDOWN;

                // Count existing Lich entities within the spawn radius
                int lichCount = this.level.getEntitiesOfClass(LichEntity.class, 
                    new AABB(this.worldPosition).inflate(SPAWN_RADIUS)).size();

                if (lichCount < MAX_ENTITIES) {
                    // Spawn a new Lich entity
                    LichEntity lichEntity = EntityRegistry.LICH_ENTITY.get().create(this.level);
                    if (lichEntity != null) {
                        double spawnX = this.worldPosition.getX() + 0.5 + (this.level.random.nextDouble() - 0.5) * SPAWN_RADIUS * 2;
                        double spawnY = this.worldPosition.getY() + 1.0;
                        double spawnZ = this.worldPosition.getZ() + 0.5 + (this.level.random.nextDouble() - 0.5) * SPAWN_RADIUS * 2;

                        // Use BlockPos.containing to create a BlockPos from doubles
                        BlockPos spawnPos = BlockPos.containing(spawnX, spawnY, spawnZ);
                        if (this.level.isEmptyBlock(spawnPos)) {
                            lichEntity.moveTo(spawnX, spawnY, spawnZ, this.level.random.nextFloat() * 360F, 0);
                            // Set home position to restrict movement
                            lichEntity.setHomePosition(this.worldPosition);
                            this.level.addFreshEntity(lichEntity);
                        }
                    }
                }
            }
        }
    }
}