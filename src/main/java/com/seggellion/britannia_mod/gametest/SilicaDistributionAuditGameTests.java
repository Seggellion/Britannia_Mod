package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.resource.ResourceCatalog;
import com.seggellion.britannia_mod.resource.ResourceDefinition;
import com.seggellion.britannia_mod.resource.natural.NaturalDepositSelector;
import com.seggellion.britannia_mod.resource.natural.NaturalGeneration;
import com.seggellion.britannia_mod.resource.shape.ShapeConfig;
import com.seggellion.britannia_mod.resource.shape.ShapeRotation;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterList;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * What the shipped silica distribution does to a real Overworld, without generating one.
 *
 * <h2>Why this and not a generated world</h2>
 * A generated-world audit needs the deposits to be dense enough to appear in an area a server can
 * actually produce. Silica's shipped rarity is about one candidate per 1,646 chunks <em>before</em>
 * the biome gate, so a 256-chunk sample — around ten minutes of generation on this machine — is
 * expected to contain zero. Three such samples were run and contained zero, which measures the
 * harness rather than the resource.
 *
 * <p>The biome source and the surface height do not need a world. Both are noise functions over the
 * seed, and both are reachable from the registries the test server already has, so the real
 * Overworld generator can be built here and asked about millions of chunks' worth of terrain in a
 * few seconds. That gives the acceptance rate, the biome mix and the altitude distribution against
 * genuine terrain, for several seeds, reproducibly.
 *
 * <p>What it cannot show is host rejection, which needs real blocks. That is covered by
 * {@code NaturalSilicaGameTests.onlyApprovedHostsBecomeSilica} and reported as a stated gap.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class SilicaDistributionAuditGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Several seeds, so a conclusion is about the configuration rather than about one world. */
    private static final long[] SEEDS = {1L, 987654321L, -4242L};

    /** Owner cells per axis. At a 24-chunk cell this is 384x384 chunks of terrain per seed. */
    private static final int CELL_SPAN = 16;

    private SilicaDistributionAuditGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ResourceDefinition silica() {
        return ResourceCatalog.instance().byId("britannia_mod:silica_sand_deposit").orElseThrow();
    }

    /** The real Overworld generator for one seed, built from the server's own registries. */
    private record Overworld(BiomeSource biomes, NoiseBasedChunkGenerator generator,
                             RandomState randomState) {

        static Overworld forSeed(RegistryAccess registries, long seed) {
            Holder<MultiNoiseBiomeSourceParameterList> preset = registries
                    .registryOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                    .getHolderOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD);
            BiomeSource biomes = MultiNoiseBiomeSource.createFromPreset(preset);

            Holder<NoiseGeneratorSettings> settings = registries
                    .registryOrThrow(Registries.NOISE_SETTINGS)
                    .getHolderOrThrow(NoiseGeneratorSettings.OVERWORLD);
            HolderGetter<NormalNoise.NoiseParameters> noise =
                    registries.lookupOrThrow(Registries.NOISE);

            return new Overworld(biomes,
                    new NoiseBasedChunkGenerator(biomes, settings),
                    RandomState.create(settings.value(), noise, seed));
        }

        Holder<Biome> biomeAt(int x, int y, int z) {
            return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                    QuartPos.fromBlock(z), randomState.sampler());
        }

        int surfaceAt(int x, int z, LevelHeightAccessor height) {
            return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, height, randomState);
        }
    }

    /** One seed's worth of measurements. */
    private record Survey(long seed, int cells, int candidates, int accepted,
                          int rejectedBiome, int rejectedAltitude,
                          Map<String, Integer> biomes, List<Integer> altitudes,
                          List<int[]> origins) {
    }

    private static Survey survey(GameTestHelper helper, long seed) {
        ResourceDefinition silica = silica();
        NaturalGeneration natural = silica.natural().orElseThrow();
        Overworld overworld = Overworld.forSeed(helper.getLevel().registryAccess(), seed);
        TagKey<Biome> allowed = TagKey.create(
                Registries.BIOME, ResourceLocation.parse(natural.biomeTag()));

        int cells = 0;
        int candidates = 0;
        int accepted = 0;
        int rejectedBiome = 0;
        int rejectedAltitude = 0;
        Map<String, Integer> biomes = new LinkedHashMap<>();
        List<Integer> altitudes = new ArrayList<>();
        List<int[]> origins = new ArrayList<>();

        for (int cellX = 0; cellX < CELL_SPAN; cellX++) {
            for (int cellZ = 0; cellZ < CELL_SPAN; cellZ++) {
                cells++;
                var candidate = NaturalDepositSelector.candidateFor(seed, silica, natural, cellX, cellZ);
                if (candidate.isEmpty()) {
                    continue;
                }
                candidates++;
                NaturalDepositSelector.Candidate found = candidate.get();

                // Same order as NaturalDepositService: biome, then altitude. Asked this way round
                // "rejected by altitude" means a candidate in an allowed biome at a height outside
                // the band, which is a tuning signal; the other way round it was mostly ocean floor
                // that the biome gate would have refused anyway.
                int surface = overworld.surfaceAt(found.originX(), found.originZ(), helper.getLevel());
                Holder<Biome> biome = overworld.biomeAt(found.originX(), surface, found.originZ());
                if (!biome.is(allowed)) {
                    rejectedBiome++;
                    continue;
                }
                int y = surface - found.depth();
                if (y < natural.minY() || y > natural.maxY()) {
                    rejectedAltitude++;
                    continue;
                }
                accepted++;
                altitudes.add(y);
                origins.add(new int[] {found.originX(), found.originZ()});
                biomes.merge(biome.unwrapKey()
                        .map(key -> key.location().toString()).orElse("?"), 1, Integer::sum);
            }
        }
        return new Survey(seed, cells, candidates, accepted, rejectedBiome, rejectedAltitude,
                biomes, altitudes, origins);
    }

    /**
     * The shipped distribution, measured against real terrain and printed for the milestone report.
     *
     * <p>Assertions are deliberately loose bands rather than exact numbers: the point is to catch a
     * configuration that has stopped producing anything, or started producing far too much, without
     * failing every time a biome definition shifts by a per cent.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void theShippedSilicaDistributionMeasuredAgainstRealTerrain(GameTestHelper helper) {
        NaturalGeneration natural = silica().natural().orElseThrow();
        int totalCandidates = 0;
        int totalAccepted = 0;

        System.out.println("M7AUDIT ---- silica distribution, real Overworld noise, no chunks generated ----");
        System.out.println(String.format(Locale.ROOT,
                "M7AUDIT config: cell=%d chunks  chance=%.2f  radius=%d-%d  depth=%d+-%d  band=%d..%d  salt=%d",
                natural.cellChunks(), natural.chance(), natural.minRadius(), natural.maxRadius(),
                natural.depth(), natural.depthJitter(), natural.minY(), natural.maxY(),
                natural.salt()));

        for (long seed : SEEDS) {
            Survey survey = survey(helper, seed);
            totalCandidates += survey.candidates();
            totalAccepted += survey.accepted();

            long chunks = (long) survey.cells() * natural.cellChunks() * natural.cellChunks();
            System.out.println(String.format(Locale.ROOT,
                    "M7AUDIT seed %-12d cells=%d chunks=%d candidates=%d accepted=%d "
                            + "rejected(biome)=%d rejected(altitude)=%d",
                    seed, survey.cells(), chunks, survey.candidates(), survey.accepted(),
                    survey.rejectedBiome(), survey.rejectedAltitude()));
            if (survey.accepted() > 0) {
                List<Integer> altitudes = new ArrayList<>(survey.altitudes());
                altitudes.sort(Integer::compareTo);
                System.out.println(String.format(Locale.ROOT,
                        "M7AUDIT   altitude min/median/max = %d/%d/%d   biomes=%s",
                        altitudes.get(0), altitudes.get(altitudes.size() / 2),
                        altitudes.get(altitudes.size() - 1), survey.biomes()));
                System.out.println(String.format(Locale.ROOT,
                        "M7AUDIT   one accepted deposit per %d chunks of Overworld",
                        chunks / survey.accepted()));
                if (survey.origins().size() > 1) {
                    double nearest = Double.MAX_VALUE;
                    for (int i = 0; i < survey.origins().size(); i++) {
                        for (int j = i + 1; j < survey.origins().size(); j++) {
                            double dx = survey.origins().get(i)[0] - survey.origins().get(j)[0];
                            double dz = survey.origins().get(i)[1] - survey.origins().get(j)[1];
                            nearest = Math.min(nearest, Math.sqrt(dx * dx + dz * dz));
                        }
                    }
                    System.out.println(String.format(Locale.ROOT,
                            "M7AUDIT   nearest accepted pair = %.0f blocks", nearest));
                }
            }
        }

        check(totalCandidates > 0, "the distribution produced no candidates at all");
        check(totalAccepted > 0,
                "no candidate anywhere was accepted by the biome gate; silica would never occur");
        double acceptance = totalAccepted / (double) totalCandidates;
        check(acceptance < 0.5,
                "the biome gate accepted " + Math.round(acceptance * 100)
                        + "% of candidates, which is not a curated set of biomes");
        System.out.println(String.format(Locale.ROOT,
                "M7AUDIT total candidates=%d accepted=%d acceptance=%.1f%%",
                totalCandidates, totalAccepted, acceptance * 100.0));
        helper.succeed();
    }

    /** The planned size of the deposits that are actually accepted, for the report's size figures. */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void theAcceptedDepositsAreTheSizeTheReportClaims(GameTestHelper helper) {
        ResourceDefinition silica = silica();
        NaturalGeneration natural = silica.natural().orElseThrow();
        List<Integer> counts = new ArrayList<>();

        // Planned size depends only on the candidate, so every candidate is measured rather than
        // only the accepted ones; the biome gate does not change how big a bed would be.
        for (long seed : SEEDS) {
            for (int cellX = 0; cellX < CELL_SPAN; cellX++) {
                for (int cellZ = 0; cellZ < CELL_SPAN; cellZ++) {
                    NaturalDepositSelector.candidateFor(seed, silica, natural, cellX, cellZ)
                            .ifPresent(candidate -> counts.add(
                                    silica.generation().orElseThrow().shape().planner()
                                            .plan(new ShapeConfig(candidate.radius(),
                                                    ShapeRotation.XZ,
                                                    candidate.plannerSeed(),
                                                    natural.tuning()))
                                            .count()));
                }
            }
        }
        check(!counts.isEmpty(), "no deposits to size");
        counts.sort(Integer::compareTo);
        System.out.println(String.format(Locale.ROOT,
                "M7AUDIT planned cells per deposit: min=%d median=%d mean=%.0f max=%d over %d deposits",
                counts.get(0), counts.get(counts.size() / 2),
                counts.stream().mapToInt(Integer::intValue).average().orElseThrow(),
                counts.get(counts.size() - 1), counts.size()));

        check(counts.get(counts.size() - 1) <= 3_000,
                "a planned deposit reached " + counts.get(counts.size() - 1) + " cells");
        check(counts.get(0) >= 200,
                "a planned deposit was only " + counts.get(0) + " cells");
        helper.succeed();
    }
}
