
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
            boolean next = !state.getValue(ThinWall.ALT_TEXTURE);
            level.setBlock(pos, state.setValue(ThinWall.ALT_TEXTURE, next), 3);
            LOGGER.debug("🎨 InteriorDecoratorTool toggled wall style at {}", pos);
        }

        /* SUCCESS on server, CONSUME on client so the hand swings once */
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
