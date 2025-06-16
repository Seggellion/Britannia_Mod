package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.client.house.HouseRotationData;
import com.seggellion.britannia_mod.structure.HouseStyle;
import com.seggellion.britannia_mod.structure.StructurePlacer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

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
        HitResult hit = player.pick(5.0D, 0.0F, false);

        if (hit.getType() == HitResult.Type.BLOCK) {
            BlockPos  targetPos  = ((BlockHitResult) hit).getBlockPos();
            int       rotationDeg = HouseRotationData.getRotation(player);

            boolean placed = StructurePlacer.placeStructure(
                    (ServerLevel) level,
                    targetPos,
                    rotationDeg,
                    houseStyle,
                    player
            );

            if (placed) {
                stack.shrink(1);          // consume deed
                HouseRotationData.clear(player);
                return InteractionResultHolder.success(stack);   // ▶︎ only on success
            } else {
                return InteractionResultHolder.fail(stack);      // ▶︎ no swing → no rotate
            }
        }

        player.displayClientMessage(
            Component.literal("❌ You must aim at a block to place this."), true);
        return InteractionResultHolder.fail(stack);
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
