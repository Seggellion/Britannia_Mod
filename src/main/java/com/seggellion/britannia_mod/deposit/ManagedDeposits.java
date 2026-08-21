package com.seggellion.britannia_mod.deposit;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.util.ModTags;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;
import java.util.Optional;

/**
 * Every managed deposit the mod knows about, and the question "is this block one of them".
 *
 * <h2>Where this sits</h2>
 * The mod already had a managed-resource loop before clay: an administrator places a resource
 * block, a player works it, the block goes away, and {@code BlockRestoreHandler} brings it back
 * six hours later. That loop is in two halves, and only one of them was ore-shaped.
 *
 * <ul>
 *   <li><b>Shared already.</b> {@code BrokenBlockTracker} / {@code BrokenBlockDataStorage} /
 *       {@code BlockRestoreHandler} take a position and a block state and know nothing about ore,
 *       tools or skills. Clay joins this half unchanged — no second scheduler, no second store,
 *       and {@code /brokenblocks} lists a pending clay bed beside a pending vein.</li>
 *   <li><b>Ore-shaped.</b> {@code Mineables} resolves a block <em>type</em> to a Mining skill
 *       requirement, and the pickaxe is welded into three separate places. Clay must not be in
 *       it: a Mining requirement is the wrong skill, STONE/ORE is the wrong category, and a
 *       block-type rule would make every vanilla clay block in the world an economic deposit the
 *       moment it was added.</li>
 * </ul>
 *
 * <p>So this catalogue is the second front half, not a second system: it answers which block is a
 * deposit and what works it, and then hands over to the shared half. Adding the silica sand
 * deposit later is one entry in {@link #ALL} and its block; it is not another architecture.
 */
public final class ManagedDeposits {

    /**
     * The clay bed.
     *
     * <p>One clay ball per bed, which is the yield every other managed resource here already
     * uses — a worked stone block gives one graded stone, an ore block one purity ore, a wild
     * node one item. Vanilla's four-per-block is a loot rule for a material anyone may dig;
     * this is a deposit whose whole purpose is that supply is controlled. House recipes consume
     * clay by the dozens, but that is what they cost, not what a bed contains.
     *
     * <p>{@code minecraft:clay_ball} rather than a new item. Rails prices the commodity, not the
     * item, and a {@code britannia_mod:raw_clay} would be a second name for the same substance
     * whose only purpose was to be scarce — scarcity lives in the deposit.
     */
    public static final ManagedDeposit CLAY = new ManagedDeposit(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "clay_deposit"),
            BlockRegistry.CLAY_DEPOSIT::get,
            ModTags.Items.CLAY_SHOVELS,
            () -> Items.CLAY_BALL,
            1
    );

    /**
     * The silica bed: whiter, purer sand than the beach, and the first step of the glass chain.
     *
     * <p>What it yields is deliberately NOT the Architect's commodity. {@code silica_sand} is
     * feedstock; a player fires it into raw glass and sells that. Rails already draws the same
     * line — {@code glass|raw|sand} is accepted only by the dormant Glassblower, while
     * {@code glass|raw|raw_glass} is what the live mason buys — so the processing step is a real
     * one rather than a formality a fallback buyer would erase.
     */
    public static final ManagedDeposit SILICA_SAND = new ManagedDeposit(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "silica_sand_deposit"),
            BlockRegistry.SILICA_SAND_DEPOSIT::get,
            ModTags.Items.SILICA_SHOVELS,
            ItemRegistry.SILICA_SAND::get,
            1
    );

    private static final List<ManagedDeposit> ALL = List.of(CLAY, SILICA_SAND);

    private ManagedDeposits() {
    }

    public static List<ManagedDeposit> all() {
        return ALL;
    }

    /** The deposit this block is, or empty when it is ordinary world. */
    public static Optional<ManagedDeposit> resolve(BlockState state) {
        if (state == null) return Optional.empty();
        for (ManagedDeposit deposit : ALL) {
            if (state.is(deposit.block().get())) return Optional.of(deposit);
        }
        return Optional.empty();
    }

    /**
     * The deposit named by an authoring command.
     *
     * <p>Three spellings, because an administrator placing test beds should not have to remember
     * which one the registry uses: the full id path ({@code silica_sand_deposit}), the material
     * ({@code silica_sand}), or an unambiguous shortening of it ({@code silica}). The last is what
     * anybody actually types, and refusing it was a small piece of friction in the one workflow
     * that puts resources in the world at all.
     *
     * <p>Ambiguity resolves to nothing rather than to whichever entry sorted first. Two deposits
     * sharing a leading word would both be candidates, and quietly placing one of them is worse
     * than saying the name is not specific enough.
     */
    public static Optional<ManagedDeposit> byName(String name) {
        if (name == null || name.isBlank()) return Optional.empty();
        String wanted = name.toLowerCase(java.util.Locale.ROOT);

        ManagedDeposit exact = null;
        ManagedDeposit shortened = null;
        int shortenedMatches = 0;
        for (ManagedDeposit deposit : ALL) {
            String path = deposit.id().getPath();
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
    public static boolean isAuthorizedTool(ManagedDeposit deposit, ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.is(deposit.extractionTool());
    }
}
