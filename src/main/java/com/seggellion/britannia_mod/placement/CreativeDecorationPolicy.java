package com.seggellion.britannia_mod.placement;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Creative bypasses decorative substrate rules, while occupied space and permissions still apply. */
public final class CreativeDecorationPolicy {
    public static final BooleanProperty ORIGIN = BooleanProperty.create("creative_origin");
    private CreativeDecorationPolicy() {}
    public static boolean creative(Player player) { return player != null && player.isCreative(); }
    public static boolean placedInCreative(BlockState state) { return state.getOptionalValue(ORIGIN).orElse(false); }
    public static BlockState remember(BlockState state, BlockPlaceContext context) {
        return state.setValue(ORIGIN, creative(context.getPlayer()));
    }
    public static boolean canOccupy(BlockPlaceContext context, BlockPos pos, BlockState state) {
        var level = context.getLevel();
        var player = context.getPlayer();
        return level.isInWorldBounds(pos) && level.getWorldBorder().isWithinBounds(pos) && level.hasChunkAt(pos)
                && level.getBlockEntity(pos) == null && level.getBlockState(pos).canBeReplaced(context)
                && (player == null || level.mayInteract(player,pos) && player.mayUseItemAt(pos,context.getClickedFace(),context.getItemInHand()))
                && level.isUnobstructed(state,pos,player == null ? CollisionContext.empty() : CollisionContext.of(player));
    }
}
