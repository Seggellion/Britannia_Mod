package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.worldgen.NoiseVeinOreAuthority;
import com.seggellion.britannia_mod.worldgen.VanillaFeaturePolicy;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Milestone 8.5: the noise-vein path can no longer put vanilla iron or copper in the ground.
 *
 * <h2>What "cannot" means here</h2>
 * Not "was not observed in a sample". The vein rule is a pure function from a position to a block
 * state, and every state it can return is enumerable: two ores, two raw blocks, two fillers, and
 * null. The authority is applied to that whole set and the ore states are shown to be unreachable
 * on the way out, so the guarantee is about the contract rather than about a sample size.
 *
 * <p>The empirical half — a fixed-seed 256-chunk before/after over identical bounds — is in the
 * milestone report. It agrees, and it is what shows the veins are still there.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class NoiseVeinAuthorityGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private NoiseVeinAuthorityGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    /** Every block state {@code OreVeinifier.VeinType} can emit, plus the decline. */
    private static List<BlockState> everythingAVeinCanProduce() {
        List<BlockState> states = new ArrayList<>();
        states.add(Blocks.COPPER_ORE.defaultBlockState());
        states.add(Blocks.RAW_COPPER_BLOCK.defaultBlockState());
        states.add(Blocks.GRANITE.defaultBlockState());
        states.add(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState());
        states.add(Blocks.RAW_IRON_BLOCK.defaultBlockState());
        states.add(Blocks.TUFF.defaultBlockState());
        return states;
    }

    /* ------------------------------------------------------------------ */
    /*  Absolute ore authority                                             */
    /* ------------------------------------------------------------------ */

    /**
     * No ore state survives the authority, and the guarantee is total rather than probabilistic.
     *
     * <p>Named individually as well as swept, because these four block ids are the milestone.
     */
    @GameTest(template = TEMPLATE)
    public static void theVeinPathCanNeverProduceVanillaIronOrCopper(GameTestHelper helper) {
        for (BlockState produced : everythingAVeinCanProduce()) {
            BlockState after = NoiseVeinOreAuthority.substitute(produced);
            check(!after.is(Blocks.COPPER_ORE), "a vein could still produce copper ore");
            check(!after.is(Blocks.DEEPSLATE_COPPER_ORE), "a vein could still produce deepslate copper ore");
            check(!after.is(Blocks.IRON_ORE), "a vein could still produce iron ore");
            check(!after.is(Blocks.DEEPSLATE_IRON_ORE), "a vein could still produce deepslate iron ore");
            check(!after.is(Blocks.RAW_COPPER_BLOCK), "a vein could still produce a raw copper block");
            check(!after.is(Blocks.RAW_IRON_BLOCK), "a vein could still produce a raw iron block");
        }
        helper.succeed();
    }

    /** The two ore variants the milestone names, including the deepslate forms. */
    @GameTest(template = TEMPLATE)
    public static void allFourNamedOreBlocksAreDenied(GameTestHelper helper) {
        // The copper vein emits the stone-variant copper ore and the iron vein the deepslate
        // variant; the other two are named by the milestone and asserted absent from the output of
        // every input, which is stronger than asserting the vein never emitted them.
        for (Block denied : List.of(Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE)) {
            for (BlockState produced : everythingAVeinCanProduce()) {
                check(!NoiseVeinOreAuthority.substitute(produced).is(denied),
                        denied + " is still reachable through the vein path");
            }
        }
        helper.succeed();
    }

    /** A decline stays a decline, so the generator's default-block fallback is unchanged. */
    @GameTest(template = TEMPLATE)
    public static void aCellTheVeinDeclinesIsStillDeclined(GameTestHelper helper) {
        check(NoiseVeinOreAuthority.substitute(null) == null,
                "a null result was turned into a block, which would fill the world with it");
        helper.succeed();
    }

    /** Deterministic: the same input always gives the same output, with no randomness involved. */
    @GameTest(template = TEMPLATE)
    public static void theSubstitutionIsDeterministic(GameTestHelper helper) {
        for (BlockState produced : everythingAVeinCanProduce()) {
            BlockState first = NoiseVeinOreAuthority.substitute(produced);
            for (int attempt = 0; attempt < 32; attempt++) {
                check(NoiseVeinOreAuthority.substitute(produced) == first,
                        produced + " substituted differently on attempt " + attempt);
            }
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Terrain preservation                                               */
    /* ------------------------------------------------------------------ */

    /**
     * Ore becomes the vein's own filler, not stone.
     *
     * <p>The difference between this and returning {@code minecraft:stone} is the difference
     * between a granite bank with no copper in it and artificial stone tubes running through the
     * underground.
     */
    @GameTest(template = TEMPLATE)
    public static void oreBecomesTheVeinsOwnFillerRatherThanStone(GameTestHelper helper) {
        check(NoiseVeinOreAuthority.substitute(Blocks.COPPER_ORE.defaultBlockState())
                        .is(Blocks.GRANITE),
                "copper ore was not replaced by the copper vein's granite");
        check(NoiseVeinOreAuthority.substitute(Blocks.RAW_COPPER_BLOCK.defaultBlockState())
                        .is(Blocks.GRANITE),
                "a raw copper block was not replaced by granite");
        check(NoiseVeinOreAuthority.substitute(Blocks.DEEPSLATE_IRON_ORE.defaultBlockState())
                        .is(Blocks.TUFF),
                "deepslate iron ore was not replaced by the iron vein's tuff");
        check(NoiseVeinOreAuthority.substitute(Blocks.RAW_IRON_BLOCK.defaultBlockState())
                        .is(Blocks.TUFF),
                "a raw iron block was not replaced by tuff");

        for (Map.Entry<Block, Block> entry : NoiseVeinOreAuthority.substitutions().entrySet()) {
            check(!entry.getValue().defaultBlockState().is(Blocks.STONE),
                    entry.getKey() + " is replaced by plain stone, which paints tubes underground");
        }
        helper.succeed();
    }

    /** Filler passes through untouched, so the vein keeps its geology. */
    @GameTest(template = TEMPLATE)
    public static void theVeinsFillerGeologyPassesThroughUntouched(GameTestHelper helper) {
        check(NoiseVeinOreAuthority.substitute(Blocks.GRANITE.defaultBlockState()).is(Blocks.GRANITE),
                "granite filler was altered");
        check(NoiseVeinOreAuthority.substitute(Blocks.TUFF.defaultBlockState()).is(Blocks.TUFF),
                "tuff filler was altered");
        // Anything else the rule might ever return is left alone too.
        for (Block other : List.of(Blocks.STONE, Blocks.DEEPSLATE, Blocks.DIORITE,
                Blocks.ANDESITE, Blocks.WATER, Blocks.AIR)) {
            check(NoiseVeinOreAuthority.substitute(other.defaultBlockState()).is(other),
                    other + " was substituted, but it is not a denied ore");
        }
        helper.succeed();
    }

    /**
     * The substitution map still matches what {@code OreVeinifier.VeinType} actually declares.
     *
     * <p>The mapping is written out by hand, so it can fall out of step with the vanilla table.
     * Copper's filler is granite and iron's is tuff in 1.21.1; if a future version changes either,
     * this is where it should be noticed.
     */
    @GameTest(template = TEMPLATE)
    public static void theSubstitutionMapMatchesTheVanillaVeinTable(GameTestHelper helper) {
        Map<Block, Block> substitutions = NoiseVeinOreAuthority.substitutions();
        check(substitutions.size() == 4,
                "the vein table has " + substitutions.size() + " entries, not the four ore states");
        check(substitutions.get(Blocks.COPPER_ORE) == Blocks.GRANITE, "copper filler");
        check(substitutions.get(Blocks.RAW_COPPER_BLOCK) == Blocks.GRANITE, "copper raw filler");
        check(substitutions.get(Blocks.DEEPSLATE_IRON_ORE) == Blocks.TUFF, "iron filler");
        check(substitutions.get(Blocks.RAW_IRON_BLOCK) == Blocks.TUFF, "iron raw filler");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Dimension scope                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Only the Overworld runs ore veins at all, so only the Overworld can be affected.
     *
     * <p>Read from the live registry rather than asserted: {@code NoiseChunk} builds the vein rule
     * only when the dimension's noise settings enable ore veins, so a dimension that has them off
     * never reaches this milestone's code. If a future version switched them on for the Nether,
     * this fails and says so.
     */
    @GameTest(template = TEMPLATE)
    public static void onlyTheOverworldEnablesOreVeins(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.NOISE_SETTINGS);

        check(registry.getOrThrow(NoiseGeneratorSettings.OVERWORLD).oreVeinsEnabled(),
                "the Overworld no longer enables ore veins; this milestone has nothing to do");

        for (ResourceKey<NoiseGeneratorSettings> key : List.of(
                NoiseGeneratorSettings.NETHER, NoiseGeneratorSettings.END,
                NoiseGeneratorSettings.CAVES, NoiseGeneratorSettings.FLOATING_ISLANDS)) {
            NoiseGeneratorSettings settings = registry.get(key);
            if (settings == null) {
                continue;
            }
            check(!settings.oreVeinsEnabled(),
                    key.location() + " now enables ore veins, so this change has escaped the"
                            + " Overworld and its terrain effect is unaudited");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The rest of the suppression is untouched                           */
    /* ------------------------------------------------------------------ */

    /**
     * Milestone 5's placed-feature suppression is exactly as it was.
     *
     * <p>The risk this guards is the one the milestone brief calls out: editing generator behaviour
     * and accidentally restoring vanilla diamond, gold, coal, redstone, lapis or emerald.
     */
    @GameTest(template = TEMPLATE)
    public static void theFeatureSuppressionPolicyIsUnchanged(GameTestHelper helper) {
        VanillaFeaturePolicy policy = VanillaFeaturePolicy.instance();
        check(policy.suppressed().size() == 19,
                "the suppressed feature set is now " + policy.suppressed().size() + ", not 19");
        for (String family : List.of("coal", "iron", "gold", "gold_badlands", "redstone",
                "diamond", "lapis", "emerald", "copper")) {
            boolean present = policy.suppressedEntries().stream()
                    .anyMatch(entry -> entry.family().equals(family));
            check(present, family + " is no longer suppressed at the feature level");
        }
        // And the milestone 5 record of what the features could not reach is still true of the
        // families, even though the route is now closed -- the entries describe the mechanism.
        check(policy.leakingFamilies().equals(java.util.Set.of("copper", "iron")),
                "the recorded unreachable families changed: " + policy.leakingFamilies());
        helper.succeed();
    }
}
