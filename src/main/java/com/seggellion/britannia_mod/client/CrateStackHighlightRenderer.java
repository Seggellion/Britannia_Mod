package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.CrateBlock;
import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.crate.CrateFoundation;
import com.seggellion.britannia_mod.crate.CrateStackShapes;
import com.seggellion.britannia_mod.crate.CrateStackSlice;
import com.seggellion.britannia_mod.crate.CrateStackTargetResolver;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHighlightEvent;

/**
 * Outlines the one crate a player is pointing at, rather than everything sharing its cell.
 *
 * <h2>What was wrong</h2>
 *
 * <p>Vanilla draws the selection box from {@code BlockState.getShape}, and a cell of a column returns
 * every crate inside it — so aiming at one crate of a stack outlined the whole cell's worth. On a
 * large crate carrying a column it was worse: that block's shape is its own lid plus the crates
 * resting on it, so pointing at a small crate drew a box around the large crate's lid as well, and
 * pointing at the lid drew one around the crate. Two structures with two separate inventories, shown
 * as one object.
 *
 * <p>The shape cannot simply be narrowed, because the same shape is what the ray is tested against —
 * narrowing it would make crates unclickable. So the outline is drawn here instead, from the crate the
 * target resolver actually picked, which is the same crate the click will open and the swing will
 * break.
 *
 * <p>Purely cosmetic and client-only. Nothing here decides what is hit, what opens or what breaks; if
 * it declines to draw, vanilla's outline is used unchanged.
 */
@EventBusSubscriber(modid = BritanniaMod.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CrateStackHighlightRenderer {

    /** Vanilla's own outline colour, so a crate looks like every other block. */
    private static final float ALPHA = 0.4F;

    private CrateStackHighlightRenderer() {
    }

    @SubscribeEvent
    public static void onRenderBlockHighlight(RenderHighlightEvent.Block event) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        BlockHitResult hit = event.getTarget();
        VoxelShape shape = outlineFor(level, hit);
        if (shape == null || shape.isEmpty()) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        Vec3 camera = event.getCamera().getPosition();
        LevelRenderer.renderVoxelShape(
                event.getPoseStack(),
                event.getMultiBufferSource().getBuffer(RenderType.lines()),
                shape,
                pos.getX() - camera.x,
                pos.getY() - camera.y,
                pos.getZ() - camera.z,
                0.0F, 0.0F, 0.0F, ALPHA,
                false);
        event.setCanceled(true);
    }

    /**
     * The shape to outline, or null to leave the highlight alone.
     *
     * <p>Null rather than an empty shape for "not ours", so that a hit this cannot make sense of falls
     * back to vanilla instead of drawing nothing at all.
     */
    @Nullable
    private static VoxelShape outlineFor(Level level, BlockHitResult hit) {
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof CrateStackBlock) {
            return CrateStackTargetResolver.resolve(level, hit)
                    .map(target -> oneCrate(
                            target.stack().sliceFor(state.getValue(CrateStackBlock.PART)),
                            target.crateId()))
                    .orElse(null);
        }

        if (state.getBlock() instanceof CrateBlock crate
                && CrateFoundation.carriesOverhang(crate, state)) {
            Optional<CrateFoundation.Founded> founded =
                    CrateFoundation.columnOn(level, pos, state);
            if (founded.isEmpty()) {
                return null;
            }
            // Above the lid is the column's business; at or below it, this crate's own.
            return CrateStackTargetResolver.resolve(level, hit)
                    .map(target -> oneCrate(founded.get().overhang(), target.crateId()))
                    .orElseGet(() -> crate.authoredShape(state));
        }

        return null;
    }

    /** One crate's shape within the cell that drew it, or null if that cell does not draw it. */
    @Nullable
    private static VoxelShape oneCrate(CrateStackSlice slice, int crateId) {
        for (CrateStackSlice.Entry entry : slice.entries()) {
            if (entry.crateId() == crateId) {
                return CrateStackShapes.cellShape(new CrateStackSlice(List.of(entry)));
            }
        }
        return null;
    }
}
