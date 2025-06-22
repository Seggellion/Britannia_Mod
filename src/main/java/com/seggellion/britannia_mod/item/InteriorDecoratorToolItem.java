package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.ThinWall;
import com.seggellion.britannia_mod.block.CarpetDummyBlock;
import com.seggellion.britannia_mod.block.CarpetTeleporterBlock;
import com.seggellion.britannia_mod.block.nudgeable.INudgeable;
import com.seggellion.britannia_mod.block.nudgeable.NudgeableBlockEntity;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

public class InteriorDecoratorToolItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public InteriorDecoratorToolItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level      = ctx.getLevel();
        BlockPos pos     = ctx.getClickedPos();
        Player player    = ctx.getPlayer();
        ItemStack inHand = ctx.getItemInHand();
        BlockState state = level.getBlockState(pos);

        if (player == null) return InteractionResult.PASS;

        // Nudge logic: only if offhand also holds InteriorDecoratorTool
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof InteriorDecoratorToolItem) {
            if (state.getBlock() instanceof INudgeable) {
                Direction face = getPlayerFacingDirection(player);
                if (!level.isClientSide()) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof NudgeableBlockEntity nudgeable) {
                        nudgeable.nudge(face);
                        player.displayClientMessage(Component.literal("Nudged " + face.getName()), true);
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        // Rotate horizontal blocks (except ThinWall)
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            if (!(state.getBlock() instanceof ThinWall)) {
                if (!level.isClientSide()) {
                    Direction cur  = state.getValue(HorizontalDirectionalBlock.FACING);
                    Direction next = cur.getClockWise();
                    level.setBlock(pos, state.setValue(HorizontalDirectionalBlock.FACING, next), 3);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }

        // CarpetTeleporterBlock style cycling
        if (state.getBlock() instanceof CarpetTeleporterBlock) {
            LOGGER.info("🎨 InteriorDecoratorTool carpet teleporter");
            if (!level.isClientSide()) {
                int currentStyle = state.getValue(CarpetTeleporterBlock.STYLE);
                int nextStyle = (currentStyle + 1) % 5;
                level.setBlock(pos, state.setValue(CarpetTeleporterBlock.STYLE, nextStyle), 4);

                for (int dz = -1; dz <= 1; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        if (dx == 0 && dz == 0) continue;
                        BlockPos targetPos = pos.offset(dx, 0, dz);
                        BlockState targetState = level.getBlockState(targetPos);
                        if (targetState.getBlock() instanceof CarpetDummyBlock) {
                            level.setBlock(targetPos,
                                targetState.setValue(CarpetDummyBlock.STYLE, nextStyle), 3);
                        }
                    }
                }

                LOGGER.debug("🎨 InteriorDecoratorTool cycled carpet style at {}", pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // ThinWall style cycling
        if (state.getBlock() instanceof ThinWall wall) {
            if (!level.isClientSide()) {
                int next = (state.getValue(ThinWall.STYLE) + 1) % 3;
                level.setBlock(pos, state.setValue(ThinWall.STYLE, next), 3);
                LOGGER.info("🎨 InteriorDecoratorTool toggled wall style at {}", pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        return InteractionResult.PASS;
    }

    private Direction getPlayerFacingDirection(Player player) {
        Vec3 lookAngle = player.getLookAngle();
        if (Math.abs(lookAngle.y) > 0.5) {
            return lookAngle.y > 0 ? Direction.UP : Direction.DOWN;
        }
        return player.getDirection();
    }
}
