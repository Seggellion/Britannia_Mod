package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.nudgeable.INudgeable;
import com.seggellion.britannia_mod.block.nudgeable.NudgeableBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class InteriorDecoratorToolItem extends Item {

    public InteriorDecoratorToolItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;

        ItemStack offhand = player.getOffhandItem();
        if (!(offhand.getItem() instanceof InteriorDecoratorToolItem)) {
            return InteractionResult.PASS;
        }

        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState currentState = level.getBlockState(pos);

        if (!(currentState.getBlock() instanceof INudgeable)) {
            return InteractionResult.PASS;
        }

        Direction face = getPlayerFacingDirection(player);

        if (!level.isClientSide) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof NudgeableBlockEntity nudgeable) {
                nudgeable.nudge(face);
                player.displayClientMessage(Component.literal("Nudged " + face.getName()), true);
            }
        }

        return InteractionResult.SUCCESS;
    }

    private Direction getPlayerFacingDirection(Player player) {
        Vec3 lookAngle = player.getLookAngle();

        // Check if looking mostly up or down
        if (Math.abs(lookAngle.y) > 0.5) {
            return lookAngle.y > 0 ? Direction.UP : Direction.DOWN;
        }

        // Otherwise use horizontal direction
        return player.getDirection();
    }
}