package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.block.entity.WineBarrelBlockEntity;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import com.seggellion.britannia_mod.network.payload.BottlingPayload;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class ServerPayloadHandler {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void handleBottling(final BottlingPayload payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            BlockPos pos = payload.pos();

            // 1. Security Check
            if (player.distanceToSqr(pos.getX(), pos.getY(), pos.getZ()) > 64.0) return;
            
            // 2. Barrel Check
            if (player.level().getBlockEntity(pos) instanceof WineBarrelBlockEntity barrel && barrel.isReady()) {

                // 3. Identify Bottle Type
                ItemStack heldItem = player.getMainHandItem();
                Item heldDef = heldItem.getItem();
                ItemStack resultBottle;

                if (heldDef == ItemRegistry.WINE_BOTTLE_GREEN.get()) {
                    resultBottle = new ItemStack(ItemRegistry.WINE_BOTTLE_GREEN.get());
                } else if (heldDef == ItemRegistry.WINE_BOTTLE_BROWN.get()) {
                    resultBottle = new ItemStack(ItemRegistry.WINE_BOTTLE_BROWN.get());
                } else if (heldDef == ItemRegistry.WINE_BOTTLE_CLEAR.get()) {
                    resultBottle = new ItemStack(ItemRegistry.WINE_BOTTLE_CLEAR.get());
                } else {
                    return; // Invalid item
                }
                heldItem.shrink(1);

                // 4. Construct Data
                String wineryName = player.getName().getString() + " " + payload.suffix();
                
                // Calculate Year
                int year = (int) (player.level().getDayTime() / 24000L / 365L) + 1;

                // --- CHANGED: Get Region from Barrel ---
                // We no longer calculate this based on where the barrel is standing.
                // We trust the data that came with the juice.
                String regionName = barrel.getRegion();
                
                LOGGER.info("Bottling Wine. Variety: {}, Region: {}, Year: {}", barrel.getVariety(), regionName, year);

                // 5. Write Data to Bottle
                WineBottleBlockItem.setWineData(
                    resultBottle, 
                    wineryName, 
                    barrel.getVariety(), 
                    year, 
                    barrel.getQuality(), 
                    regionName,   
                    payload.labelColor() 
                );

                // 6. Give & Reset
                if (!player.getInventory().add(resultBottle)) {
                    player.drop(resultBottle, false);
                }
                barrel.decrementBottleCount();
                player.level().playSound(null, pos, SoundEvents.BOTTLE_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        });
    }
}