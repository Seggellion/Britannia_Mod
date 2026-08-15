package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.mining.MineableDefinition;
import com.seggellion.britannia_mod.mining.Mineables;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Resource identity and quality rolls for a completed Mining break.
 *
 * <p>Mining milestone 6: the resource names come from the Mining catalogue's {@code drop} field
 * instead of a second hard-coded block chain, so the name a block yields, the tier that gates it
 * and the commodity it sells as are all declared in one place. Every name is byte-identical to the
 * chain this replaced — {@code MineableCatalogContractTest} pins them.
 */
public final class BlockBreakUtils {

    private BlockBreakUtils() {}

    /** Display name of the ore a block yields, or {@code "unknown"} when Mining does not manage it. */
    public static String deduceOreType(BlockState state) {
        return dropNameFor(state, MineableDefinition.Category.ORE, "unknown");
    }

    /** Display name of the stone a block yields, or {@code "Unknown"} when Mining does not manage it. */
    public static String deduceStoneType(BlockState state) {
        return dropNameFor(state, MineableDefinition.Category.STONE, "Unknown");
    }

    private static String dropNameFor(BlockState state, MineableDefinition.Category category, String fallback) {
        return Mineables.resolve(state)
                .filter(definition -> definition.category() == category)
                .map(MineableDefinition::dropName)
                .orElse(fallback);
    }

    public static int generateStoneGrade() {
        return 1 + RandomSource.create().nextInt(5); // random 1..5
    }

    public static int generateRandomPurity() {
        return 1 + RandomSource.create().nextInt(5); // random 1..5
    }

    public static double generateRandomWeight(double min, double max) {
        return min + RandomSource.create().nextDouble() * (max - min);
    }
}
