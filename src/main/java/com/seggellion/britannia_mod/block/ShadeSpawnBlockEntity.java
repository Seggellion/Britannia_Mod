// ShadeSpawnBlockEntity.java
package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.entity.ShadeEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class ShadeSpawnBlockEntity extends BlockEntity {

    private int spawnCooldown = 0;
    private static final int MAX_COOLDOWN = 2000; // Ticks between spawns (10 seconds)
    private static final int MAX_ENTITIES = 5;
    private static final int SPAWN_RADIUS = 15;

public ShadeSpawnBlockEntity(BlockPos pos, BlockState state) {
        super(BlockRegistry.SHADE_SPAWN_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public void tick() {
        if (!this.level.isClientSide) {
            spawnCooldown--;

            if (spawnCooldown <= 0) {
                spawnCooldown = MAX_COOLDOWN;

                // Count existing Shade entities within the spawn radius
                int shadeCount = this.level.getEntitiesOfClass(ShadeEntity.class, 
                    new AABB(this.worldPosition).inflate(SPAWN_RADIUS)).size();

                if (shadeCount < MAX_ENTITIES) {
                    // Spawn a new Shade entity
                    ShadeEntity shadeEntity = EntityRegistry.SHADE_ENTITY.get().create(this.level);
                    if (shadeEntity != null) {
                        double spawnX = this.worldPosition.getX() + 0.5 + (this.level.random.nextDouble() - 0.5) * SPAWN_RADIUS * 2;
                        double spawnY = this.worldPosition.getY() + 1.0;
                        double spawnZ = this.worldPosition.getZ() + 0.5 + (this.level.random.nextDouble() - 0.5) * SPAWN_RADIUS * 2;

                        // Use BlockPos.containing to create a BlockPos from doubles
                        BlockPos spawnPos = BlockPos.containing(spawnX, spawnY, spawnZ);
                        if (this.level.isEmptyBlock(spawnPos)) {
                            shadeEntity.moveTo(spawnX, spawnY, spawnZ, this.level.random.nextFloat() * 360F, 0);
                            // Set home position to restrict movement
                            shadeEntity.setHomePosition(this.worldPosition);
                            this.level.addFreshEntity(shadeEntity);
                        }
                    }
                }
            }
        }
    }
}