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
import com.seggellion.britannia_mod.structure.HouseStyle;
import com.seggellion.britannia_mod.structure.HouseSize;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Properties;

import com.seggellion.britannia_mod.structure.StructurePlacer;
import com.seggellion.britannia_mod.client.house.HouseRotationData;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

public abstract class AbstractHouseDeedItem extends Item {

    private final HouseStyle houseStyle;

    protected AbstractHouseDeedItem(HouseStyle style, Properties props) {
        super(props);
        this.houseStyle = style;
    }
    public HouseStyle getHouseStyle() {
        return houseStyle;
    }

   
    /* ---------- Right‑click = place ---------- */
   @Override
public InteractionResultHolder<ItemStack> use(Level level,
                                              Player player,
                                              InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);

    if (!level.isClientSide && hand == InteractionHand.MAIN_HAND) {
        BlockPos origin = player.blockPosition();
        int rotationDeg = HouseRotationData.getRotation(player);

        boolean placed = StructurePlacer.placeStructure(
            (ServerLevel) level, origin, rotationDeg, houseStyle, player);

        HouseRotationData.clear(player);

        if (placed) {
            stack.shrink(1); // consume deed
            return InteractionResultHolder.success(stack);
        } else {
            return InteractionResultHolder.fail(stack); // do not consume
        }
    }

    return InteractionResultHolder.pass(stack);
}


    /* ---------- Left‑click = rotate ---------- */
    @SuppressWarnings("removal")
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        if (!(entity instanceof Player player) || !player.level().isClientSide)
            return false;

        if (player.getMainHandItem() == stack) {
            HouseRotationData.rotateClockwise(player);
            player.displayClientMessage(Component.literal(
                    "Rotated to " + HouseRotationData.getRotation(player) + "°"), true);
            return true;
        }
        return false;
    }
}
