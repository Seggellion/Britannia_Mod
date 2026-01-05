package com.seggellion.britannia_mod.item;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import com.seggellion.britannia_mod.registry.ItemRegistry;


public class PitcherJuiceItem extends BlockItem {

    public PitcherJuiceItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 1. READ THE DATA (Before we destroy the item)
        // We check if the item has the "minecraft:custom_data" component
        if (stack.has(DataComponents.CUSTOM_DATA)) {
            // Get the NBT tag from inside the component
            CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
            CompoundTag tag = customData.copyTag();

            // Extract your specific variables
            if (tag.contains("GrapeVariety")) {
                String variety = tag.getString("GrapeVariety");
                int quality = tag.getInt("Quality");

                // Example: Print a message to the player
                if (!level.isClientSide()) {
                    player.displayClientMessage(
                        Component.literal("You poured out " + variety + " juice (Quality: " + quality + ")"), 
                        true
                    );
                }
            }
        }

        // 2. Play Sound
        level.playSound(player, player.getX(), player.getY(), player.getZ(), 
                        SoundEvents.BUCKET_EMPTY, SoundSource.PLAYERS, 1.0F, 1.0F);

        // 3. Swap to Empty Pitcher
        // Replace 'ModItems' with your actual registry class
        ItemStack emptyPitcher = new ItemStack(ItemRegistry.PITCHER_EMPTY.get());
        
        return InteractionResultHolder.sidedSuccess(emptyPitcher, level.isClientSide());
    }
}