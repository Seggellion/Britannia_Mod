package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.block.GrapeVineBlock;
import com.seggellion.britannia_mod.block.FarmingBlock;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.block.entity.GrapeVineBlockEntity;
import com.seggellion.britannia_mod.item.GrapesItem;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import com.mojang.logging.LogUtils;
import java.util.Comparator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/**
 * Converts vines left over from the retired standalone grape block into the farming plot's grape
 * crop, which is now the only way grapes exist.
 *
 * <p>The conversion is lazy and bounded rather than a world sweep: a legacy vine only converts when
 * the chunk holding it is loaded and the block either receives a random tick or is touched by a
 * player. Nothing scans for vines, nothing runs per tick, and a chunk that is never loaded is never
 * visited.
 *
 * <p>Ordering matters for safety. The legacy stack is torn down first, with drops suppressed and
 * neighbour notification withheld, and only then is the crop planted. A half-migrated state is
 * therefore never observable, the torn-down blocks cannot re-enter this method, and neither the
 * removal nor the planting can cascade into neighbouring vines.
 */
public final class LegacyGrapeVineMigration {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Update clients, suppress drops, and do not notify neighbours. */
    private static final int SILENT_REMOVAL_FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    /** A legacy vine stood at most three blocks tall via its old height_stage property. */
    private static final int MAX_LEGACY_STACK_HEIGHT = 3;

    private LegacyGrapeVineMigration() {
    }

    /**
     * Converts the legacy vine at {@code pos}, or the stack it belongs to, exactly once.
     *
     * @return true when a legacy vine was consumed, whether or not a crop could be planted
     */
    public static boolean migrate(Level level, BlockPos pos) {
        if (level == null || level.isClientSide) {
            return false;
        }
        if (!(level.getBlockState(pos).getBlock() instanceof GrapeVineBlock)) {
            return false;
        }

        BlockPos anchor = findAnchor(level, pos);
        BlockState anchorState = level.getBlockState(anchor);
        if (!(anchorState.getBlock() instanceof GrapeVineBlock vine)) {
            return false;
        }

        LegacyVine captured = capture(level, anchor, anchorState, vine);
        int removed = tearDownStack(level, anchor);
        if (removed == 0) {
            return false;
        }

        BlockPos soilPos = anchor.below();
        if (level.getBlockState(soilPos).getBlock() instanceof FarmingBlock
                && level.getBlockEntity(soilPos) instanceof FarmingBlockEntity farmBe
                && !farmBe.hasCrop()) {
            CropDefinition grapes = CropRegistry.byId("grapes").orElse(null);
            if (grapes != null) {
                farmBe.plantMigratedCrop(grapes, captured.varietyId(), captured.age());
                level.setBlock(soilPos, level.getBlockState(soilPos).setValue(FarmingBlock.HAS_SEEDS, true), 3);
                LOGGER.info("[grape migration] Converted legacy vine at {} to a {} grape crop at stage {}",
                        anchor, captured.varietyId(), captured.age());
                return true;
            }
        }

        // Nothing can host the crop here, so return the plant's materials rather than deleting them.
        // Only the anchor refunds, so a three-block stack cannot pay out three times.
        refund(level, anchor, captured);
        LOGGER.info("[grape migration] Removed legacy vine at {} with no farming plot beneath it; refunded its materials",
                anchor);
        return true;
    }

    /**
     * Resolves the variety a legacy vine should carry forward.
     *
     * <p>The vine's block entity holds the authoritative id and wins whenever it still names a
     * variety of the colour the blockstate recorded. Otherwise the colour decides, because colour is
     * what the player actually saw on that vine.
     *
     * <p>A legacy vine records a colour but not which of that colour's varieties it was, so where a
     * shard defines several varieties sharing a colour the exact variety cannot be reconstructed.
     * The lowest id of that colour is chosen rather than the registry's own default: the registry
     * iterates a hash map, so its answer depends on which other varieties happen to be loaded, and a
     * vine could migrate differently on two shards or after a catalogue update. Sorting by id makes
     * the outcome depend on nothing but the colour.
     */
    public static String resolveVarietyId(String storedVarietyId, GrapeColor stateColor) {
        if (storedVarietyId != null && !storedVarietyId.isBlank()) {
            GrapeVariety stored = GrapeVarietyManager.getVarietyOrNull(storedVarietyId);
            if (stored != null && stored.colorType() == stateColor) {
                return storedVarietyId;
            }
        }
        return GrapeVarietyManager.getAllVarieties().stream()
                .filter(variety -> variety.colorType() == stateColor)
                .map(GrapeVariety::id)
                .min(Comparator.naturalOrder())
                .orElseGet(() -> GrapeVarietyManager.getDefaultVarietyIdForColor(stateColor));
    }

    private static LegacyVine capture(Level level, BlockPos anchor, BlockState state, GrapeVineBlock vine) {
        GrapeColor color = state.getValue(GrapeVineBlock.COLOR);
        int age = state.getValue(CropBlock.AGE);
        String storedVarietyId = level.getBlockEntity(anchor) instanceof GrapeVineBlockEntity vineBe
                ? vineBe.getVariety()
                : null;
        return new LegacyVine(resolveVarietyId(storedVarietyId, color), age, age >= vine.getMaxAge());
    }

    private static BlockPos findAnchor(Level level, BlockPos pos) {
        BlockPos anchor = pos;
        for (int step = 0; step < MAX_LEGACY_STACK_HEIGHT; step++) {
            BlockPos below = anchor.below();
            if (!(level.getBlockState(below).getBlock() instanceof GrapeVineBlock)) {
                return anchor;
            }
            anchor = below;
        }
        return anchor;
    }

    private static int tearDownStack(Level level, BlockPos anchor) {
        int removed = 0;
        for (int offset = 0; offset < MAX_LEGACY_STACK_HEIGHT; offset++) {
            BlockPos pos = anchor.above(offset);
            if (!(level.getBlockState(pos).getBlock() instanceof GrapeVineBlock)) {
                break;
            }
            level.removeBlockEntity(pos);
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), SILENT_REMOVAL_FLAGS);
            removed++;
        }
        return removed;
    }

    private static void refund(Level level, BlockPos pos, LegacyVine captured) {
        Block.popResource(level, pos, new ItemStack(ItemRegistry.TRELLIS_ITEM.get()));
        if (captured.mature()) {
            ItemStack grapes = new ItemStack(ItemRegistry.GRAPES.get(), 10);
            GrapesItem.setVariety(grapes, captured.varietyId());
            Block.popResource(level, pos, grapes);
        }
    }

    private record LegacyVine(String varietyId, int age, boolean mature) {
    }
}
