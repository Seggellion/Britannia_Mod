package com.seggellion.britannia_mod.item;

import com.seggellion.britannia_mod.block.BlankSignHolder;
import com.seggellion.britannia_mod.block.DoubleBedBlock;
import com.seggellion.britannia_mod.block.ThinWall;
import com.seggellion.britannia_mod.block.CarpetDummyBlock;
import com.seggellion.britannia_mod.block.CarpetTeleporterBlock;
import com.seggellion.britannia_mod.block.MirrorableWallBlock;
import com.seggellion.britannia_mod.banner.block.BannerBlock;
import com.seggellion.britannia_mod.banner.block.BannerPartBlock;
import com.seggellion.britannia_mod.banner.interaction.BannerMountCycleService;
import com.seggellion.britannia_mod.block.nudgeable.INudgeable;
import com.seggellion.britannia_mod.block.nudgeable.NudgeableBlockEntity;
import com.seggellion.britannia_mod.registry.LargeStructureRegistry;
import com.seggellion.britannia_mod.structure.interaction.ShrineVariantCycleService;
import com.mojang.logging.LogUtils;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
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

        // Large-structure ownership must win over the generic horizontal-facing rotation below.
        if (state.is(LargeStructureRegistry.LARGE_STRUCTURE_ANCHOR.get())
                || state.is(LargeStructureRegistry.LARGE_STRUCTURE_PART.get())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.FAIL;
            }
            ShrineVariantCycleService.Result result = ShrineVariantCycleService.cycle(
                    serverLevel, serverPlayer, inHand, pos, state);
            return result == ShrineVariantCycleService.Result.SUCCESS
                    ? InteractionResult.sidedSuccess(false) : InteractionResult.FAIL;
        }

        // Banner ownership must also win over the generic rotation: a banner cell's FACING is
        // structural (it binds parts to their anchor and the anchor to its wall), so spinning
        // one cell in place would orphan it from its own banner. The decorator's action on a
        // banner is instead the mount-material cycle, resolved through the banner's anchor so
        // any section of a multi-block banner behaves identically, with either hand.
        if (state.getBlock() instanceof BannerBlock || state.getBlock() instanceof BannerPartBlock) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) {
                return InteractionResult.FAIL;
            }
            BannerMountCycleService.Result result = BannerMountCycleService.cycle(
                    serverLevel, serverPlayer, inHand, pos, state);
            return result.handled() ? InteractionResult.sidedSuccess(false) : InteractionResult.FAIL;
        }

        // Nudge logic: only if offhand also holds InteriorDecoratorTool
        ItemStack offhand = player.getOffhandItem();
        if (offhand.getItem() instanceof InteriorDecoratorToolItem) {
            if (state.getBlock() instanceof INudgeable) {
                Direction face = getPlayerFacingDirection(player);
                if (!level.isClientSide()) {
                    BlockEntity be = level.getBlockEntity(pos);
                    if (be instanceof NudgeableBlockEntity nudgeable) {
                        if (!nudgeable.allowYNudging && (face == Direction.UP || face == Direction.DOWN)) {
                            return InteractionResult.FAIL;
                        }
                        nudgeable.nudge(face);
                        player.displayClientMessage(Component.literal("Nudged " + face.getName()), true);
                    }
                }
                return InteractionResult.SUCCESS;
            }
        }

        // Mirror an asymmetric wall feature - a window's frame, or the timber support on
        // plaster_wall_and_support_blank - to the other side of the block.
        //
        // Driven from the OFF hand so it does not compete with rotation. Holding the tool normally
        // and right-clicking still spins the block; putting it in the left hand and clicking flips
        // the feature. This has to sit above the generic facing rotation below, because these
        // blocks all carry HorizontalDirectionalBlock.FACING and would otherwise just spin.
        if (ctx.getHand() == InteractionHand.OFF_HAND
            && state.getBlock() instanceof MirrorableWallBlock) {

            if (!level.isClientSide()) {
                level.setBlock(pos,
                    state.setValue(MirrorableWallBlock.MIRRORED,
                                   !state.getValue(MirrorableWallBlock.MIRRORED)),
                    Block.UPDATE_ALL);
                level.playSound(null, pos, state.getSoundType().getPlaceSound(),
                    SoundSource.BLOCKS, 0.5f, 1.2f);
                LOGGER.info("🎨 InteriorDecoratorTool mirrored wall feature at {}", pos);
            }
            return InteractionResult.sidedSuccess(level.isClientSide());
        }

        // Rotate horizontal blocks (except ThinWall, DoubleBedBlock, and BlankSignHolder)
        if (state.hasProperty(HorizontalDirectionalBlock.FACING)) {
            if (!(state.getBlock() instanceof ThinWall) 
             && !(state.getBlock() instanceof DoubleBedBlock)
             && !(state.getBlock() instanceof BlankSignHolder)) {
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

        // BlankSignHolder style cycling
        if (state.getBlock() instanceof BlankSignHolder signHolder) {
            if (!level.isClientSide()) {
                int next = (state.getValue(BlankSignHolder.STYLE) + 1) % 6;
                level.setBlock(pos, state.setValue(BlankSignHolder.STYLE, next), 3);
                LOGGER.info("🎨 InteriorDecoratorTool toggled sign holder style at {}", pos);
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
