package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.item.DeedItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.network.chat.Component;

import java.util.Properties;

import com.seggellion.britannia_mod.structure.StructurePlacer;
import com.seggellion.britannia_mod.client.house.HouseRotationData;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public class SmallWoodHouseDeedItem extends DeedItem {
    private static final Logger LOGGER = LogUtils.getLogger();


    public SmallWoodHouseDeedItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        // ✅ Correct way to get blocks
          
        if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
            BlockPos basePos = player.blockPosition();
            int rotation = HouseRotationData.getRotation(player);

            StructurePlacer.placeStructure((ServerLevel) level, basePos, rotation, "small_wood_house.nbt", player);
            HouseRotationData.clear(player);
            stack.shrink(1);
            return InteractionResultHolder.success(stack);
        }

        return InteractionResultHolder.pass(stack);
    }

    @SuppressWarnings("removal")
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (entity.level().isClientSide || !(entity instanceof Player player)) return false;
        if (player.getMainHandItem() == stack) {
            HouseRotationData.rotateClockwise(player);
            player.displayClientMessage(Component.literal("Rotated House to " + HouseRotationData.getRotation(player) + "°"), true);
            return true;
        }
        return false;
    }
}
