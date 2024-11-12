package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.entity.ShadeEntity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.EntityEvent.Size;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ShadeEntitySizeHandler {
    private static final Logger LOGGER = LogManager.getLogger();

    @SubscribeEvent
    public static void onShadeEntitySize(Size event) {
        if (event.getEntity() instanceof ShadeEntity shadeEntity) {
            Pose currentPose = event.getPose();

            // Define the new dimensions for the Shade entity
            double adjustedWidth = 0.6D;   // Adjust the width appropriately for the model
            double adjustedHeight = 1.8D;  // Adjust the height to fit the model correctly
            
            // Apply an upward offset to eliminate the empty space below the model
            double bottomOffset = 0.6D;    // You may need to adjust this value based on trial and error

            EntityDimensions newSize = EntityDimensions.scalable((float) adjustedWidth, (float) adjustedHeight);
            event.setNewSize(newSize);

            // Log output for debugging purposes
            LOGGER.info("ShadeEntitySizeHandler called for Entity: {}, Pose: {}, Width: {}, Height: {}, Bottom Offset: {}",
                event.getEntity(), currentPose, adjustedWidth, adjustedHeight, bottomOffset);

            // Now let's adjust the bounding box to ensure it reflects the proper dimensions and offset
            double entityX = shadeEntity.getX();
            double entityY = shadeEntity.getY() + bottomOffset;  // Adjust the Y position upwards
            double entityZ = shadeEntity.getZ();

            shadeEntity.setBoundingBox(new net.minecraft.world.phys.AABB(
                entityX - adjustedWidth / 2.0,
                entityY,
                entityZ - adjustedWidth / 2.0,
                entityX + adjustedWidth / 2.0,
                entityY + adjustedHeight,
                entityZ + adjustedWidth / 2.0
            ));

            LOGGER.info("Adjusted Bounding Box for Shade Entity: {}", shadeEntity.getBoundingBox());
        }
    }
}
