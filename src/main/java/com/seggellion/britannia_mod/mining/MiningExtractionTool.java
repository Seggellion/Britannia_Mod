package com.seggellion.britannia_mod.mining;

import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Whether a tool may work a Mining-catalogued resource.
 *
 * <h2>How this got here</h2>
 * Milestone 1 created this class because the Mining gate had no tool policy at all and the yield
 * handler had a private one, so a sufficiently skilled player reached vanilla breaking with
 * anything in hand — reachably, with a two-handed axe, which the city and structure handlers both
 * wave through. The fix then was a single Java predicate: {@code instanceof QualityToolItem}.
 *
 * <p>Milestone 2 removed the predicate. Authorization is now the resource's own configured item
 * tag, resolved through {@link Resources#isAuthorizedTool}, exactly as the clay and silica beds
 * have always worked. The Britannia pickaxe is authorised for stone and ore because
 * {@code britannia_mod:mining_pickaxes} lists it, not because of its class; the Britannia shovel
 * is not, because that tag does not list it; the two-handed axe is in no extraction tag at all.
 *
 * <p>The set of accepted items is unchanged by the migration. {@code britannia_mod:pickaxe} is the
 * only registered {@code QualityToolItem} and the only registered {@code BritanniaPickaxeItem}, so
 * the tag holding that one item admits precisely what the predicate admitted.
 *
 * <h2>Why the class remains</h2>
 * It is the Mining-side seam. The gate asks one question of one place, and that place now answers
 * from data — so a future ore with its own pickaxe tag needs no change here, and the deposit side
 * asks the identical question through {@code ManagedDeposits.isAuthorizedTool}. Both are thin
 * calls onto {@link Resources}, which is the single authorization implementation.
 */
public final class MiningExtractionTool {

    private MiningExtractionTool() {
    }

    /**
     * Whether this stack may work the Mining resource standing at {@code state}.
     *
     * <p>A block with no resource definition answers {@code false}: there is no tag to satisfy, so
     * nothing is authorised. That is never a denial in practice, because the gate resolves the
     * resource first and answers NOT_APPLICABLE for unmanaged blocks long before it asks this.
     * Defaulting to false rather than true is deliberate all the same — an unconfigured resource
     * must fail closed, not hand out permission it was never granted.
     */
    public static boolean isAuthorized(BlockState state, ItemStack stack) {
        return Resources.resolve(state)
                .map(definition -> Resources.isAuthorizedTool(definition, stack))
                .orElse(false);
    }

    /** The same question asked of an already-resolved definition. */
    public static boolean isAuthorized(ResourceDefinition definition, ItemStack stack) {
        return Resources.isAuthorizedTool(definition, stack);
    }
}
