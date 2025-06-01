
package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.ThinWall;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.block.CarpetDummyBlock;
import com.seggellion.britannia_mod.block.CarpetTeleporterBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;

public class InteriorDecoratorToolItem extends Item {
    private static final Logger LOGGER = LogUtils.getLogger();

    public InteriorDecoratorToolItem(Properties props) {
        super(props);
    }


    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level        = ctx.getLevel();
        BlockPos pos       = ctx.getClickedPos();
        Player player      = ctx.getPlayer();
        ItemStack inHand   = ctx.getItemInHand();
        BlockState state   = level.getBlockState(pos);

   
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            // Don’t rotate walls here – they already use STYLE cycling
            if (state.getBlock() instanceof ThinWall) {
                // fall through to existing ThinWall branch
            } else {
                if (!level.isClientSide()) {
                    Direction cur  = state.getValue(HorizontalDirectionalBlock.FACING);
                    Direction next = cur.getClockWise();           // ⇧ shift‑right‑click? use getCounterClockWise()
                    level.setBlock(pos, state.setValue(
                            HorizontalDirectionalBlock.FACING, next), 3);
                }
                return InteractionResult.sidedSuccess(level.isClientSide());
            }
        }

        if (state.getBlock() instanceof CarpetTeleporterBlock) {
            LOGGER.info("🎨 InteriorDecoratorTool carpet teleporter");
            if (!level.isClientSide()) {
                int currentStyle = state.getValue(CarpetTeleporterBlock.STYLE);
                int nextStyle = (currentStyle + 1) % 5;
                level.setBlock(pos, state.setValue(CarpetTeleporterBlock.STYLE, nextStyle), 4);

                // Update surrounding dummy slices
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


     /* Only affect ThinWall blocks */
        if (!(state.getBlock() instanceof ThinWall wall)) {
            return InteractionResult.PASS;
        }

        /* Spectators should not modify the world */
        if (player != null && player.isSpectator()) {
            return InteractionResult.PASS;
        }

        /* Server‑side: flip the ALT_TEXTURE bit and notify clients */
        if (!level.isClientSide()) {
            int next = (state.getValue(ThinWall.STYLE) + 1) % 3;
            level.setBlock(pos, state.setValue(ThinWall.STYLE, next), 3);
            LOGGER.info("🎨 InteriorDecoratorTool toggled wall style at {}", pos);
        }


        /* SUCCESS on server, CONSUME on client so the hand swings once */
       return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
