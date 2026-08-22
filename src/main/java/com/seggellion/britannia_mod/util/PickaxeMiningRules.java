package com.seggellion.britannia_mod.util;

import com.seggellion.britannia_mod.mining.MineableDefinition;
import com.seggellion.britannia_mod.mining.Mineables;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Which blocks the Britannia pickaxe may work, answered from the Mining catalogue.
 *
 * <p>Mining milestone 6. These used to be hard-coded {@code state.is(...)} chains, which meant the
 * managed break flow and the skill gate each carried their own idea of what counts as a mineable —
 * two sources of truth that could drift, and three Java edits to add one rock. Both now read the
 * single catalogue, so a new resource is a data change (design §19).
 *
 * <p>Coverage is unchanged by this delegation: {@code MineableCatalogContractTest} pins the
 * catalogue's ACTIVE block set to exactly the set these chains used to list.
 */
public final class PickaxeMiningRules {
    private PickaxeMiningRules() {}

    public static boolean isAllowedMineableBlock(BlockState state) {
        return Mineables.resolve(state).isPresent();
    }

    public static boolean isAllowedStoneBlock(BlockState state) {
        return isCategory(state, MineableDefinition.Category.STONE);
    }

    public static boolean isAllowedOreBlock(BlockState state) {
        return isCategory(state, MineableDefinition.Category.ORE);
    }

    /** A Mining-governed non-metal worked with the pickaxe -- coal today. */
    public static boolean isAllowedMineralBlock(BlockState state) {
        return isCategory(state, MineableDefinition.Category.MINERAL);
    }

    private static boolean isCategory(BlockState state, MineableDefinition.Category category) {
        return Mineables.resolve(state)
                .map(definition -> definition.category() == category)
                .orElse(false);
    }
}
