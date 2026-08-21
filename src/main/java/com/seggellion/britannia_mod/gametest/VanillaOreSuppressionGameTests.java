package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.worldgen.VanillaFeaturePolicy;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * OreVein milestone 5: proof that the denied vanilla mineral features are gone from Overworld
 * generation, and that everything else is still there.
 *
 * <h2>Why this reads the registry rather than scanning generated chunks</h2>
 * The project's GameTest harness runs {@code WorldPresets.FLAT} — {@code GameTestServer.create}
 * hard-codes it — so no ore feature generates in this world at all. Counting ore blocks in a
 * generated region here would return zero for the suppressed minerals <em>and</em> zero for the
 * allowed geology, and would prove nothing about either.
 *
 * <p>So these assert on the thing generation actually consults: each biome's
 * {@code GenerationSettings}, after NeoForge has applied the biome modifiers. That is not a proxy
 * for what the chunk generator does — it is the input the chunk generator reads. It is also
 * <em>exhaustive</em> over every biome in the registry, where a sampled region could only ever be
 * statistical, and a rare ore missing from one chunk proves nothing.
 *
 * <p>An empirical fixed-seed audit against a real (non-flat) world remains worth doing, and belongs
 * to the M9 rollout rehearsal where a real world exists.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class VanillaOreSuppressionGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    private VanillaOreSuppressionGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static Registry<Biome> biomes(ServerLevel level) {
        return level.registryAccess().registryOrThrow(Registries.BIOME);
    }

    /** The feature ids a biome runs in one decoration step, after biome modifiers. */
    private static Set<String> featuresInStep(Biome biome, GenerationStep.Decoration step) {
        List<HolderSet<PlacedFeature>> perStep = biome.getGenerationSettings().features();
        int index = step.ordinal();
        if (index >= perStep.size()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Holder<PlacedFeature> holder : perStep.get(index)) {
            holder.unwrapKey().map(ResourceKey::location).map(ResourceLocation::toString).ifPresent(ids::add);
        }
        return ids;
    }

    /* ------------------------------------------------------------------ */
    /*  Suppression                                                        */
    /* ------------------------------------------------------------------ */

    /**
     * No Overworld biome runs a denied mineral feature any more.
     *
     * <p>Exhaustive: every biome in the registry tagged {@code #minecraft:is_overworld}, every
     * suppressed feature. A single biome retaining one variant is a failure, and it is named.
     */
    @GameTest(template = TEMPLATE)
    public static void noOverworldBiomeStillRunsADeniedMineralFeature(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Set<String> suppressed = VanillaFeaturePolicy.instance().suppressed();
        List<String> survivors = new ArrayList<>();
        int overworldBiomes = 0;

        for (Holder<Biome> holder : biomes(level).holders().toList()) {
            if (!holder.is(BiomeTags.IS_OVERWORLD)) continue;
            overworldBiomes++;
            String name = holder.unwrapKey().map(key -> key.location().toString()).orElse("?");

            for (GenerationStep.Decoration step : GenerationStep.Decoration.values()) {
                for (String feature : featuresInStep(holder.value(), step)) {
                    if (suppressed.contains(feature)) {
                        survivors.add(name + " still runs " + feature + " in " + step);
                    }
                }
            }
        }

        check(overworldBiomes > 30,
                "expected the full Overworld biome set, found only " + overworldBiomes);
        check(survivors.isEmpty(),
                "denied vanilla mineral features survived suppression: " + survivors);
        helper.succeed();
    }

    /**
     * Suppression actually did something.
     *
     * <p>Guards against the failure the test above cannot see: if the biome modifier silently failed
     * to load, or the biome list were empty, "no biome runs a denied feature" would pass trivially.
     * A plains biome carries the default ore set, so before suppression it ran coal, iron and the
     * rest — and it must still be carrying a populated {@code underground_ores} list afterwards.
     */
    @GameTest(template = TEMPLATE)
    public static void suppressionRemovedSomethingRatherThanFindingNothing(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Biome plains = biomes(level).get(ResourceLocation.parse("minecraft:plains"));
        check(plains != null, "the plains biome must exist");

        Set<String> ores = featuresInStep(plains, GenerationStep.Decoration.UNDERGROUND_ORES);
        check(!ores.isEmpty(),
                "plains must still run underground_ores features; an empty list would mean the whole "
                        + "step was removed rather than selected features");
        check(!ores.contains("minecraft:ore_coal_upper"), "coal must be gone from plains");
        check(!ores.contains("minecraft:ore_iron_middle"), "iron must be gone from plains");
        check(ores.contains("minecraft:ore_granite_upper"),
                "granite must still be there, so this list is real and only minerals were removed");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  What survives                                                      */
    /* ------------------------------------------------------------------ */

    /**
     * The Mining ladder's rock still generates, in every Overworld biome that had it.
     *
     * <p>The check that would catch the worst possible version of this milestone: removing the whole
     * {@code underground_ores} step, or widening the removal by family, and silently deleting
     * andesite, diorite, granite and tuff — all four of which carry Mining requirements.
     */
    @GameTest(template = TEMPLATE)
    public static void theMiningLaddersRockStillGeneratesEverywhereItDid(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> rock = List.of(
                "minecraft:ore_andesite_upper", "minecraft:ore_andesite_lower",
                "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower",
                "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
                "minecraft:ore_tuff", "minecraft:ore_gravel", "minecraft:ore_dirt");

        // Biomes that carry the default underground variety. Plains is the canonical one.
        Biome plains = biomes(level).get(ResourceLocation.parse("minecraft:plains"));
        Set<String> ores = featuresInStep(plains, GenerationStep.Decoration.UNDERGROUND_ORES);
        for (String feature : rock) {
            check(ores.contains(feature), feature + " was removed from plains; the Mining "
                    + "progression and world formation depend on it");
        }

        // And nowhere in the Overworld did any of it get removed.
        Set<String> everywhere = new TreeSet<>();
        for (Holder<Biome> holder : biomes(level).holders().toList()) {
            if (!holder.is(BiomeTags.IS_OVERWORLD)) continue;
            everywhere.addAll(featuresInStep(holder.value(), GenerationStep.Decoration.UNDERGROUND_ORES));
        }
        for (String feature : rock) {
            check(everywhere.contains(feature),
                    feature + " no longer appears in any Overworld biome at all");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Nether                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * The Nether is untouched: every one of its ore features still runs.
     *
     * <p>Protected twice over — the modifier targets {@code #minecraft:is_overworld}, and it
     * constrains itself to {@code underground_ores} while every Nether ore is attached in
     * {@code underground_decoration}. This proves the outcome rather than the intent.
     */
    @GameTest(template = TEMPLATE)
    public static void netherOreGenerationIsUnchanged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        List<String> netherOres = List.of(
                "minecraft:ore_gold_nether", "minecraft:ore_quartz_nether",
                "minecraft:ore_gravel_nether", "minecraft:ore_blackstone",
                "minecraft:ore_magma", "minecraft:ore_ancient_debris_large",
                "minecraft:ore_debris_small");

        Biome wastes = biomes(level).get(ResourceLocation.parse("minecraft:nether_wastes"));
        check(wastes != null, "the nether wastes biome must exist");
        Set<String> decoration =
                featuresInStep(wastes, GenerationStep.Decoration.UNDERGROUND_DECORATION);

        for (String feature : netherOres) {
            check(decoration.contains(feature),
                    feature + " was removed from the Nether; the first-pass Nether policy is to "
                            + "suppress nothing");
        }

        // And no Nether biome lost anything the policy declared out of scope.
        Set<String> outOfScope = VanillaFeaturePolicy.instance().outOfScope();
        int netherBiomes = 0;
        for (Holder<Biome> holder : biomes(level).holders().toList()) {
            if (!holder.is(BiomeTags.IS_NETHER)) continue;
            netherBiomes++;
            check(!holder.is(BiomeTags.IS_OVERWORLD),
                    "a Nether biome must not be in the Overworld tag the modifier targets");
        }
        check(netherBiomes >= 5, "expected the Nether biome set, found " + netherBiomes);
        check(!outOfScope.isEmpty(), "the out-of-scope set records what was deliberately spared");
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Blocks, as opposed to features                                     */
    /* ------------------------------------------------------------------ */

    /**
     * Suppression targets generation, not block identity.
     *
     * <p>An ore block placed by anything other than biome decoration — a structure template, an
     * operator, a test, an old chunk that already contains one — is untouched. This is the
     * difference between removing a feature and the "generate then erase" world scan milestone 1
     * disabled, which could not tell those apart and is never coming back.
     */
    @GameTest(template = TEMPLATE)
    public static void deliberatelyPlacedOreBlocksAreUntouched(GameTestHelper helper) {
        List<net.minecraft.world.level.block.Block> placed = List.of(
                Blocks.COAL_ORE, Blocks.IRON_ORE, Blocks.GOLD_ORE, Blocks.DIAMOND_ORE,
                Blocks.DEEPSLATE_REDSTONE_ORE, Blocks.EMERALD_ORE, Blocks.COPPER_ORE,
                BlockRegistry.SILVER_ORE.get(), BlockRegistry.CLAY_DEPOSIT.get());

        BlockPos cell = new BlockPos(1, 1, 1);
        for (net.minecraft.world.level.block.Block block : placed) {
            helper.setBlock(cell, block);
            helper.assertBlockPresent(block, cell);
        }

        // Several standing at once, left alone across ticks.
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.IRON_ORE);
        helper.setBlock(new BlockPos(2, 1, 1), Blocks.GOLD_ORE);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.COAL_ORE);
        helper.runAfterDelay(10L, () -> {
            helper.assertBlockPresent(Blocks.IRON_ORE, new BlockPos(1, 1, 1));
            helper.assertBlockPresent(Blocks.GOLD_ORE, new BlockPos(2, 1, 1));
            helper.assertBlockPresent(Blocks.COAL_ORE, new BlockPos(1, 1, 2));
            helper.succeed();
        });
    }

    /* ------------------------------------------------------------------ */
    /*  Classification completeness, against the live registry             */
    /* ------------------------------------------------------------------ */

    /**
     * Every vanilla ore placement the running game knows about is classified.
     *
     * <p>The unit test reads the decompiled sources this project compiles against; this reads the
     * live registry, which is what actually generates the world and would also see a feature added
     * by another mod's datapack. Scoped to {@code minecraft:ore_*} plus the one non-ore feature
     * sharing the governed step, so unrelated vegetation cannot cause a false failure.
     */
    @GameTest(template = TEMPLATE)
    public static void everyVanillaOrePlacementInTheLiveRegistryIsClassified(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Registry<PlacedFeature> features =
                level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        VanillaFeaturePolicy policy = VanillaFeaturePolicy.instance();

        List<String> unclassified = new ArrayList<>();
        int governed = 0;
        for (ResourceLocation id : features.keySet()) {
            if (!"minecraft".equals(id.getNamespace()) || !id.getPath().startsWith("ore_")) {
                continue;
            }
            governed++;
            if (policy.bucketOf(id.toString()).isEmpty()) {
                unclassified.add(id.toString());
            }
        }

        check(governed > 30, "expected the vanilla ore placements in the registry, found " + governed);
        check(unclassified.isEmpty(),
                "these vanilla ore placements are neither suppressed, allowed nor out of scope: "
                        + unclassified);

        // Nothing classified may have gone missing from the registry either.
        List<String> vanished = new ArrayList<>();
        for (String feature : policy.classified()) {
            ResourceLocation id = ResourceLocation.parse(feature);
            if ("minecraft".equals(id.getNamespace()) && !features.containsKey(id)) {
                vanished.add(feature);
            }
        }
        check(vanished.isEmpty(),
                "the policy names features this version does not have: " + vanished);
        helper.succeed();
    }

    /**
     * The route feature removal cannot reach is still there, and is now controlled anyway.
     *
     * <p>{@code ore_veins_enabled} lives on the Overworld's noise settings, not on any biome, so the
     * chunk generator writes copper and iron veins directly and no amount of feature removal touches
     * them. That was milestone 5's finding, and it remains true of the <em>mechanism</em>: the veins
     * still run, and still lay down granite and tuff.
     *
     * <p>What changed at milestone 8.5 is what they are made of. The ore results are substituted for
     * the vein's own filler, so the route no longer produces economy. The policy entries therefore
     * stay — "feature removal cannot reach this" is still the fact worth recording — but each must
     * now name what does control it, or the shipped data is describing a production gap that has
     * been closed.
     */
    @GameTest(template = TEMPLATE)
    public static void theNoiseOreVeinRouteIsRecordedAndResolved(GameTestHelper helper) {
        VanillaFeaturePolicy policy = VanillaFeaturePolicy.instance();
        check(!policy.unreachableSources().isEmpty(),
                "the policy records no unreachable source, but the veins are still generating");
        check(policy.everyUnreachableSourceIsResolved(),
                "a recorded unreachable source does not say what brought it under control, so the"
                        + " shipped policy still describes an open gap");

        NoiseGeneratorSettings overworld = helper.getLevel().registryAccess()
                .registryOrThrow(Registries.NOISE_SETTINGS)
                .get(NoiseGeneratorSettings.OVERWORLD);
        check(overworld != null, "the overworld noise settings are missing from the registry");
        check(overworld.oreVeinsEnabled(),
                "ore veins are switched off; the unreachable entries in the vanilla feature policy "
                        + "are now stale and should be removed");

        // The blocks the policy tells an audit to look for must be real blocks, or the audit is
        // searching for nothing and will always come back clean.
        Registry<Block> blocks = helper.getLevel().registryAccess().registryOrThrow(Registries.BLOCK);
        List<String> missing = new ArrayList<>();
        for (String block : policy.unreachableBlocks()) {
            if (!blocks.containsKey(ResourceLocation.parse(block))) {
                missing.add(block);
            }
        }
        check(missing.isEmpty(), "the policy expects blocks this version does not have: " + missing);
        helper.succeed();
    }
}
