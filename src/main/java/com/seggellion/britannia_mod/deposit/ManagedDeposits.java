package com.seggellion.britannia_mod.deposit;

import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.Resources;

import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The sediment beds, read from the canonical catalogue.
 *
 * <h2>What changed at milestone 2, and what deliberately did not</h2>
 * This used to hold two hand-written {@code ManagedDeposit} constants. That record was the
 * healthiest resource model in the codebase — it was the only one that expressed the extraction
 * tool as a <em>tag</em> and the yield as configuration — so milestone 0 chose it as the thing to
 * generalise rather than replace. It has now grown into
 * {@link ResourceDefinition} and moved into data, and what is left here is a <b>view</b>: the
 * question "which resources does the shovel-and-bed system govern" answered as
 * {@code family == SEDIMENT} over the one catalogue.
 *
 * <p>Nothing about how a bed behaves changed. {@link ManagedDepositExtraction} still validates
 * before it mutates, still refuses a wrong tool before touching the world, still records the
 * restoration debt from the standing state, and still hands over one configured item. The
 * difference is only that every one of those facts is now read from {@code resources.json}
 * instead of being compiled in — which is what makes clay and silica the evidence that the general
 * architecture grew out of this system rather than beside it.
 *
 * <p>The ore ladder is the parallel case, not a parent one: it resolves through
 * {@code Mineables} and {@code britannia_mod:mining_pickaxes} exactly as these resolve through
 * {@code britannia_mod:clay_shovels} and {@code britannia_mod:silica_shovels}. A pickaxe has no
 * authority over a bed and a shovel none over an ore, and in both directions the tag is why.
 */
public final class ManagedDeposits {

    /**
     * The clay bed.
     *
     * <p>Its id is {@code britannia_mod:clay_deposit} rather than {@code britannia_mod:clay}
     * because that string is already an economic identifier: the housing material data names it as
     * a feedstock, and renaming it to tidy the catalogue would break a live reference for nothing.
     */
    public static final ResourceDefinition CLAY = required("britannia_mod:clay_deposit");

    /**
     * The silica bed: whiter, purer sand than the beach, and the first step of the glass chain.
     *
     * <p>Worked with the same project shovel as clay, through a tag of its own, so widening one
     * harvest never silently widens the other. Its approved 24-hour regeneration lives in the
     * definition; every other resource keeps the historical six.
     */
    public static final ResourceDefinition SILICA_SAND = required("britannia_mod:silica_sand_deposit");

    private ManagedDeposits() {
    }

    private static ResourceDefinition required(String id) {
        return ResourceCatalog.instance().byId(id).orElseThrow(() -> new IllegalStateException(
                "The resource catalogue must define " + id));
    }

    /** Every sediment bed the mod knows about. */
    public static List<ResourceDefinition> all() {
        return ResourceCatalog.instance().family(ResourceDefinition.Family.SEDIMENT);
    }

    /** The deposit this block is, or empty when it is ordinary world or a Mining resource. */
    public static Optional<ResourceDefinition> resolve(BlockState state) {
        return Resources.resolve(state).filter(ResourceDefinition::isSediment);
    }

    /**
     * The deposit named by an authoring command.
     *
     * <p>Three spellings, because an administrator placing test beds should not have to remember
     * which one the catalogue uses: the full id path ({@code silica_sand_deposit}), the material
     * ({@code silica_sand}), or an unambiguous shortening of it ({@code silica}). The last is what
     * anybody actually types, and refusing it was a small piece of friction in the one workflow
     * that puts resources in the world at all.
     *
     * <p>Ambiguity resolves to nothing rather than to whichever entry sorted first. Two deposits
     * sharing a leading word would both be candidates, and quietly placing one of them is worse
     * than saying the name is not specific enough.
     */
    public static Optional<ResourceDefinition> byName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String wanted = name.toLowerCase(Locale.ROOT);

        ResourceDefinition exact = null;
        ResourceDefinition shortened = null;
        int shortenedMatches = 0;
        for (ResourceDefinition deposit : all()) {
            String path = deposit.path();
            String material = path.endsWith("_deposit")
                    ? path.substring(0, path.length() - "_deposit".length())
                    : path;
            if (path.equals(wanted) || material.equals(wanted)) {
                exact = deposit;
            } else if (material.startsWith(wanted + "_")) {
                shortened = deposit;
                shortenedMatches++;
            }
        }
        if (exact != null) return Optional.of(exact);
        return shortenedMatches == 1 ? Optional.of(shortened) : Optional.empty();
    }

    /** Whether this stack is a tool authorized to work this deposit. */
    public static boolean isAuthorizedTool(ResourceDefinition deposit, ItemStack stack) {
        return Resources.isAuthorizedTool(deposit, stack);
    }

    /** The tag that authorizes working this deposit. */
    public static TagKey<Item> extractionTag(ResourceDefinition deposit) {
        return Resources.extractionTag(deposit);
    }

    /** The block a standing bed is made of. */
    public static Block block(ResourceDefinition deposit) {
        return Resources.standingBlock(deposit);
    }

    /** One extraction's worth of yield. */
    public static ItemStack yieldStack(ResourceDefinition deposit) {
        return Resources.yieldStack(deposit);
    }
}
