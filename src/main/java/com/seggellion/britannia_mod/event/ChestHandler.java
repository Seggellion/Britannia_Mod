package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.registry.ToolRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;

public class ChestHandler {

    private static final Logger LOGGER = LogUtils.getLogger();

private static final Map<BlockPos, ChestConfig> CHEST_CONFIGURATIONS = new HashMap<>();

private static final BlockPos FISHING_BARREL_POS = new BlockPos(5213, 66, 8912);

    private static class ChestConfig {
        ItemStack itemStack;
        Direction direction;

        public ChestConfig(ItemStack itemStack, Direction direction) {
            this.itemStack = itemStack;
            this.direction = direction;
        }
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        ServerLevel serverLevel = event.getServer().getLevel(ServerLevel.OVERWORLD);
        if (serverLevel != null) {
            // Initialize chest configurations here to ensure items are registered
            CHEST_CONFIGURATIONS.put(new BlockPos(5161, 72, 4317), new ChestConfig(createFishingRod(), Direction.EAST));
            CHEST_CONFIGURATIONS.put(new BlockPos(5052, 67, 3912), new ChestConfig(createSeggellionsAxe(), Direction.WEST));
            CHEST_CONFIGURATIONS.put(new BlockPos(4560, 143, 4185), new ChestConfig(createRustedPickaxe(), Direction.SOUTH));
            CHEST_CONFIGURATIONS.forEach((pos, chestConfig) -> setupChest(serverLevel, pos, chestConfig));
        }
    }

@SubscribeEvent
public void onServerTickPre(ServerTickEvent.Pre event) {
    ServerLevel serverLevel = event.getServer().getLevel(ServerLevel.OVERWORLD);
    if (serverLevel != null) {
        CHEST_CONFIGURATIONS.forEach((pos, chestConfig) -> ensureChestContainsItem(serverLevel, pos, chestConfig.itemStack));
    ensureBarrelContainsItem(serverLevel, FISHING_BARREL_POS, createFishingRod());
    }
}


    private static void setupChest(ServerLevel serverLevel, BlockPos chestPos, ChestConfig chestConfig) {
        serverLevel.setBlock(chestPos, Blocks.CHEST.defaultBlockState(), 3);
        BlockEntity blockEntity = serverLevel.getBlockEntity(chestPos);

    Direction chestFacing = chestConfig.direction;
    serverLevel.setBlock(chestPos, Blocks.CHEST.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, chestFacing), 3);

        if (blockEntity instanceof ChestBlockEntity chestEntity) {
            // Validate the item stack
            ItemStack validatedItem = validateItemStack(chestConfig.itemStack);

            // Set the item in the chest
            chestEntity.setItem(0, validatedItem);
            chestEntity.setChanged();
        LOGGER.info("Chest at {} initialized with {} facing {}", chestPos, chestConfig.itemStack.getHoverName().getString(), chestFacing);
        } else {
            LOGGER.warn("Failed to initialize chest at {}: BlockEntity is not a chest", chestPos);
        }
    }
    private static void ensureBarrelContainsItem(ServerLevel serverLevel, BlockPos barrelPos, ItemStack itemStack) {
    BlockEntity blockEntity = serverLevel.getBlockEntity(barrelPos);

    if (blockEntity instanceof BarrelBlockEntity barrelEntity) {
        ItemStack barrelItem = barrelEntity.getItem(0);

        if (barrelItem.isEmpty() || !isMatchingItem(barrelItem, itemStack)) {
            ItemStack validatedItem = validateItemStack(itemStack);

            barrelEntity.setItem(0, validatedItem);
            barrelEntity.setChanged();

            LOGGER.info("Repopulated barrel at {} with {}", barrelPos, validatedItem.getHoverName().getString());
        }
    } else {
       // LOGGER.warn("No barrel found at {}. Found block entity: {}", barrelPos, blockEntity);
    }
}

private static void ensureChestContainsItem(ServerLevel serverLevel, BlockPos chestPos, ItemStack itemStack) {
    BlockEntity blockEntity = serverLevel.getBlockEntity(chestPos);

    if (blockEntity instanceof ChestBlockEntity chestEntity) {
        ItemStack chestItem = chestEntity.getItem(0);
        if (chestItem.isEmpty() || !isMatchingItem(chestItem, itemStack)) {
            // Validate the item stack
            ItemStack validatedItem = validateItemStack(itemStack);

            // Set the item in the chest
            chestEntity.setItem(0, validatedItem);
            chestEntity.setChanged();
            LOGGER.info("Repopulated chest at {} with {}", chestPos, validatedItem.getHoverName().getString());
        }
    } else {
        LOGGER.warn("Failed to access chest at {}: BlockEntity is not a chest", chestPos);
    }
}

private static ItemStack validateItemStack(ItemStack itemStack) {
    if (itemStack.isEmpty()) {
        LOGGER.warn("Attempted to use an empty ItemStack");
        return ItemStack.EMPTY;
    }

    // Copy the ItemStack to preserve its data
    ItemStack validatedItem = itemStack.copy();

    // Ensure the components are valid
    DataComponentMap components = validatedItem.getComponents();
    if (components == null || components.isEmpty()) {
        LOGGER.warn("ItemStack {} has no valid components, defaulting to EMPTY", validatedItem.getHoverName().getString());
        return ItemStack.EMPTY;
    }

    LOGGER.info("Validated ItemStack with components: {}", components);
    return validatedItem;
}


    private static boolean isMatchingItem(ItemStack stack, ItemStack referenceStack) {
        return stack.getItem() == referenceStack.getItem()
                && stack.getHoverName().getString().equals(referenceStack.getHoverName().getString());
    }


    private static ItemStack createFishingRod() {
        ItemStack fishingRod = new ItemStack(Items.FISHING_ROD);

        // Build and apply data components
        DataComponentMap.Builder builder = DataComponentMap.builder();
        builder.set(DataComponents.CUSTOM_NAME, Component.literal("Ordinary Fishing Rod"));

        // Apply components to the ItemStack
        fishingRod.applyComponents(builder.build());

        return fishingRod;
    }

    private static ItemStack createSeggellionsAxe() {
        ItemStack axe = new ItemStack(ItemRegistry.TWO_HANDED_AXE.get());

        // Build and apply data components
        DataComponentMap.Builder builder = DataComponentMap.builder();
        builder.set(DataComponents.CUSTOM_NAME, Component.literal("Seggellion's Axe"));

        // Apply components to the ItemStack
        axe.applyComponents(builder.build());

        // Set durability
        int maxDurability = axe.getMaxDamage();
        int halfDamage = maxDurability / 2;
        axe.setDamageValue(halfDamage);

        // Log for debugging
        LOGGER.info("Created Seggellion's Axe with components: {}", axe.getComponents());

        return axe;
    }

    private static ItemStack createRustedPickaxe() {
        // Create pickaxe with initialized quality & material
        ItemStack pickaxe = ToolRegistry.createPickaxe(UOMetalToolMaterial.IRON, 1); // Rusted = low quality

        // Add custom name
        pickaxe.set(DataComponents.CUSTOM_NAME, Component.literal("A rusted Iron Pickaxe"));

        // Set durability to half
        int maxDurability = pickaxe.getMaxDamage();
        pickaxe.setDamageValue(maxDurability / 2);

        LOGGER.info("Created Rusted pickaxe: {}", pickaxe.getComponents());

        return pickaxe;
    }


}
