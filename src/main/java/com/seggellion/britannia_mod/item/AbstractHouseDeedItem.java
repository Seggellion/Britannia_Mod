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
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.BlockHitResult;

import java.util.Properties;

import com.seggellion.britannia_mod.network.HousePlacementPayload;
import com.seggellion.britannia_mod.network.NetworkHandler;

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
public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
    ItemStack stack = player.getItemInHand(hand);

    /* ---------- CLIENT side: send placement request ---------- */
    if (level.isClientSide && hand == InteractionHand.MAIN_HAND) {
        HitResult hit = player.pick(5.0D, 0.0F, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            player.displayClientMessage(Component.literal("❌ You must aim at a block to place this."), true);
            return InteractionResultHolder.fail(stack);
        }

        BlockPos pos     = ((BlockHitResult) hit).getBlockPos();
        int      rotDeg  = HouseRotationData.getRotation(player);

        HousePlacementPayload payload =
            new HousePlacementPayload(pos, rotDeg, houseStyle.name());

        NetworkHandler.sendToServer(payload); 

        return InteractionResultHolder.success(stack); // swing animation etc.
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
