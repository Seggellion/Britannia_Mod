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
 * Milestone 10A: what the three metals' natural distributions actually do to a real Overworld.
 *
 * <h2>Method</h2>
 * The same technique milestone 7 established for silica and milestone 9 widened: the real Overworld
 * biome source and chunk generator are built from the server's own registries and queried directly,
 * so millions of chunks' worth of terrain can be assessed in seconds without generating a single
 * chunk. That is what makes a rarity claim about a resource occurring once every few hundred chunks
 * measurable at all.
 *
 * <p>Host acceptance is the one thing this cannot see — it needs real blocks — and it is reported as
 * a stated gap rather than estimated. Host <em>policy</em> is proven separately against real block
 * states in {@code NaturalSilicaGameTests.onlyApprovedHostsBecomeSilica}, which exercises the same
 * {@code MaterializationService} these three go through.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class MetalDistributionAuditGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** Several seeds, so a conclusion is about the configuration rather than about one world. */
    private static final long[] SEEDS = {1L, 987654321L, -4242L, 24301L, 606L};

    /** Owner cells per axis, per seed. Chunks covered scales with each resource's cell size. */
    private static final int CELL_SPAN = 14;

    private MetalDistributionAuditGameTests() {
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }

    private static ResourceDefinition resource(String path) {
        return ResourceCatalog.instance().byPath(path).orElseThrow();
    }

    /** The real Overworld generator for one seed. */
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
            HolderGetter<NormalNoise.NoiseParameters> noise = registries.lookupOrThrow(Registries.NOISE);
            return new Overworld(biomes, new NoiseBasedChunkGenerator(biomes, settings),
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

    /** One resource's measurements over all seeds. */
    private record Audit(String label, long chunks, int candidates, int accepted,
                         int rejectedBiome, int rejectedAltitude,
                         List<Integer> sizes, List<Integer> altitudes,
                         Map<String, Integer> biomes, List<Double> nearest, int multiChunk) {
    }

    private static Audit audit(GameTestHelper helper, String path, NaturalGeneration override) {
        ResourceDefinition resource = resource(path);
        NaturalGeneration natural = override != null ? override : resource.natural().orElseThrow();
        TagKey<Biome> allowed = TagKey.create(
                Registries.BIOME, ResourceLocation.parse(natural.biomeTag()));

        long chunks = 0;
        int candidates = 0;
        int accepted = 0;
        int rejectedBiome = 0;
        int rejectedAltitude = 0;
        int multiChunk = 0;
        List<Integer> sizes = new ArrayList<>();
        List<Integer> altitudes = new ArrayList<>();
        Map<String, Integer> biomes = new LinkedHashMap<>();
        List<Double> nearest = new ArrayList<>();

        for (long seed : SEEDS) {
            Overworld overworld = Overworld.forSeed(helper.getLevel().registryAccess(), seed);
            chunks += (long) CELL_SPAN * CELL_SPAN * natural.cellChunks() * natural.cellChunks();
            List<int[]> origins = new ArrayList<>();

            for (int cellX = 0; cellX < CELL_SPAN; cellX++) {
                for (int cellZ = 0; cellZ < CELL_SPAN; cellZ++) {
                    var found = NaturalDepositSelector.candidateFor(seed, resource, natural, cellX, cellZ);
                    if (found.isEmpty()) {
                        continue;
                    }
                    candidates++;
                    NaturalDepositSelector.Candidate candidate = found.get();

                    int surface = overworld.surfaceAt(
                            candidate.originX(), candidate.originZ(), helper.getLevel());
                    Holder<Biome> biome =
                            overworld.biomeAt(candidate.originX(), surface, candidate.originZ());
                    if (!biome.is(allowed)) {
                        rejectedBiome++;
                        continue;
                    }
                    int y = surface - candidate.depth();
                    if (y < natural.minY() || y > natural.maxY()) {
                        rejectedAltitude++;
                        continue;
                    }
                    accepted++;
                    altitudes.add(y);
                    origins.add(new int[] {candidate.originX(), candidate.originZ()});
                    biomes.merge(biome.unwrapKey().map(key -> key.location().getPath()).orElse("?"),
                            1, Integer::sum);

                    var plan = resource.generation().orElseThrow().shape().planner().plan(
                            new ShapeConfig(candidate.radius(), ShapeRotation.XZ,
                                    candidate.plannerSeed(), natural.tuning()));
                    sizes.add(plan.count());

                    int minChunkX = (candidate.originX() - candidate.radius()) >> 4;
                    int maxChunkX = (candidate.originX() + candidate.radius()) >> 4;
                    int minChunkZ = (candidate.originZ() - candidate.radius()) >> 4;
                    int maxChunkZ = (candidate.originZ() + candidate.radius()) >> 4;
                    if (minChunkX != maxChunkX || minChunkZ != maxChunkZ) {
                        multiChunk++;
                    }
                }
            }
            for (int i = 0; i < origins.size(); i++) {
                double best = Double.MAX_VALUE;
                for (int j = 0; j < origins.size(); j++) {
                    if (i == j) continue;
                    double dx = origins.get(i)[0] - origins.get(j)[0];
                    double dz = origins.get(i)[1] - origins.get(j)[1];
                    best = Math.min(best, Math.sqrt(dx * dx + dz * dz));
                }
                if (best < Double.MAX_VALUE) {
                    nearest.add(best);
                }
            }
        }
        return new Audit(path, chunks, candidates, accepted, rejectedBiome, rejectedAltitude,
                sizes, altitudes, biomes, nearest, multiChunk);
    }

    private static void print(Audit audit, String prefix) {
        List<Integer> sizes = new ArrayList<>(audit.sizes());
        sizes.sort(Integer::compareTo);
        List<Integer> altitudes = new ArrayList<>(audit.altitudes());
        altitudes.sort(Integer::compareTo);
        List<Double> nearest = new ArrayList<>(audit.nearest());
        nearest.sort(Double::compareTo);

        double per1k = audit.accepted() * 1000.0 / audit.chunks();
        System.out.println(String.format(Locale.ROOT,
                "%s %-8s chunks=%d cells=%d candidates=%d accepted=%d (biome-rej=%d alt-rej=%d)"
                        + "  %.2f deposits/1k chunks  1 per %d chunks",
                prefix, audit.label(), audit.chunks(), CELL_SPAN * CELL_SPAN * SEEDS.length,
                audit.candidates(), audit.accepted(), audit.rejectedBiome(), audit.rejectedAltitude(),
                per1k, audit.accepted() == 0 ? 0 : audit.chunks() / audit.accepted()));
        if (sizes.isEmpty()) {
            return;
        }
        System.out.println(String.format(Locale.ROOT,
                "%s %-8s planned cells min/median/mean/max = %d/%d/%.0f/%d"
                        + "  multi-chunk=%.0f%%",
                prefix, audit.label(), sizes.get(0), sizes.get(sizes.size() / 2),
                sizes.stream().mapToInt(Integer::intValue).average().orElseThrow(),
                sizes.get(sizes.size() - 1), 100.0 * audit.multiChunk() / sizes.size()));
        System.out.println(String.format(Locale.ROOT,
                "%s %-8s altitude min/median/max = %d/%d/%d  nearest min/median = %.0f/%.0f blocks"
                        + "  biomes=%d distinct",
                prefix, audit.label(), altitudes.get(0), altitudes.get(altitudes.size() / 2),
                altitudes.get(altitudes.size() - 1),
                nearest.isEmpty() ? 0 : nearest.get(0),
                nearest.isEmpty() ? 0 : nearest.get(nearest.size() / 2),
                audit.biomes().size()));
    }

    /* ------------------------------------------------------------------ */
    /*  The shipped configurations                                         */
    /* ------------------------------------------------------------------ */

    /**
     * Iron, gold and copper measured against real terrain across five seeds.
     *
     * <p>Assertions are the bands that would make a configuration unusable — a resource that occurs
     * nowhere, or one so common it is underfoot. The exact rates are balancing values and are
     * reported rather than pinned.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 900)
    public static void theShippedMetalDistributionsMeasuredAgainstRealTerrain(GameTestHelper helper) {
        System.out.println("M10A ---- natural metal distribution, real Overworld noise, no chunks generated ----");
        for (String path : List.of("iron", "copper", "gold")) {
            NaturalGeneration natural = resource(path).natural().orElseThrow();
            System.out.println(String.format(Locale.ROOT,
                    "M10A %-8s config: cell=%d chunks chance=%.2f radius=%d-%d depth=%d+-%d"
                            + " band=%d..%d salt=%d biomes=%s",
                    path, natural.cellChunks(), natural.chance(), natural.minRadius(),
                    natural.maxRadius(), natural.depth(), natural.depthJitter(),
                    natural.minY(), natural.maxY(), natural.salt(), natural.biomeTag()));

            Audit audit = audit(helper, path, null);
            print(audit, "M10A");

            check(audit.accepted() > 0, path + " is accepted nowhere across five seeds");
            double per1k = audit.accepted() * 1000.0 / audit.chunks();
            check(per1k < 50.0, path + " occurs " + per1k + " times per 1000 chunks; that is underfoot");
            check(per1k > 0.5, path + " occurs " + per1k + " times per 1000 chunks; that is unfindable");
        }
        helper.succeed();
    }

    /**
     * The copper radius comparison, including the curated maximum of 22 as a reference.
     *
     * <p>Milestone 9 measured cluster at radius 22 as roughly 15,000 planned cells — an order of
     * magnitude above every other family. That value is not wrong for a curated set piece, but it
     * must not become the default size of an ordinary natural copper body just because it already
     * existed. This prints the alternatives that were compared before choosing.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 900)
    public static void theCopperRadiusChoiceIsJustifiedAgainstItsAlternatives(GameTestHelper helper) {
        NaturalGeneration shipped = resource("copper").natural().orElseThrow();
        System.out.println("M10A ---- copper natural radius comparison ----");

        int[][] bands = {{6, 7}, {8, 10}, {12, 14}, {16, 18}, {22, 22}};
        for (int[] band : bands) {
            NaturalGeneration candidate = new NaturalGeneration(
                    shipped.dimensionId(), shipped.biomeTag(), shipped.cellChunks(), shipped.chance(),
                    band[0], band[1], shipped.depth(), shipped.depthJitter(),
                    shipped.minY(), shipped.maxY(), shipped.salt(), shipped.tuning());
            Audit audit = audit(helper, "copper", candidate);
            List<Integer> sizes = new ArrayList<>(audit.sizes());
            sizes.sort(Integer::compareTo);
            System.out.println(String.format(Locale.ROOT,
                    "M10A copper r=%2d-%2d  cells min/median/mean/max = %5d/%5d/%6.0f/%5d"
                            + "  multi-chunk=%3.0f%%  %s",
                    band[0], band[1], sizes.get(0), sizes.get(sizes.size() / 2),
                    sizes.stream().mapToInt(Integer::intValue).average().orElseThrow(),
                    sizes.get(sizes.size() - 1),
                    100.0 * audit.multiChunk() / sizes.size(),
                    band[0] == shipped.minRadius() && band[1] == shipped.maxRadius()
                            ? "<== SHIPPED" : ""));
        }

        check(shipped.maxRadius() < 22,
                "natural copper uses the curated maximum radius, which milestone 9 measured at"
                        + " ~15,000 cells -- an order of magnitude above every other family");
        check(shipped.maxRadius() <= resource("copper").generation().orElseThrow().maxRadius(),
                "the natural radius band escapes the curated band the planner validates against");
        helper.succeed();
    }

    /**
     * Natural tuning cannot resize a curated deposit.
     *
     * <p>The coupling the milestone brief asked about, tested rather than argued: a natural radius
     * band and a curated radius are separate configuration, and a Rails row asking for 22 still gets
     * 22 after natural copper was tuned to a much smaller body.
     */
    @GameTest(template = TEMPLATE)
    public static void naturalTuningDoesNotResizeCuratedDeposits(GameTestHelper helper) {
        ResourceDefinition copper = resource("copper");
        ResourceDefinition.Generation curated = copper.generation().orElseThrow();
        NaturalGeneration natural = copper.natural().orElseThrow();

        check(curated.maxRadius() == 22, "the curated copper maximum moved from 22");
        check(natural.maxRadius() == 10, "natural copper's maximum is no longer 10");

        // A curated row at the legacy radius still plans the legacy body.
        var curatedPlan = curated.shape().planner().plan(
                new ShapeConfig(22, ShapeRotation.XZ, 12345L, natural.tuning()));
        var naturalPlan = curated.shape().planner().plan(
                new ShapeConfig(natural.maxRadius(), ShapeRotation.XZ, 12345L, natural.tuning()));

        check(curatedPlan.count() > 10_000,
                "a curated radius-22 copper deposit now plans only " + curatedPlan.count()
                        + " cells; natural tuning has silently shrunk curated deposits");
        check(naturalPlan.count() < 2_000,
                "a natural copper deposit plans " + naturalPlan.count() + " cells");
        System.out.println("M10A copper: curated r=22 plans " + curatedPlan.count()
                + " cells, natural r=" + natural.maxRadius() + " plans " + naturalPlan.count()
                + " -- the two are independent configuration");
        helper.succeed();
    }

    /**
     * Each metal's distribution is independent of the others.
     *
     * <p>Retuning one resource must not move another's deposits. The salts are what guarantee it,
     * and this measures the guarantee rather than assuming it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 600)
    public static void eachMetalsDistributionIsIndependentOfTheOthers(GameTestHelper helper) {
        List<String> paths = List.of("iron", "copper", "gold", "silica_sand_deposit");
        java.util.Set<Integer> salts = new java.util.LinkedHashSet<>();
        for (String path : paths) {
            salts.add(resource(path).natural().orElseThrow().salt());
        }
        check(salts.size() == paths.size(),
                "two naturally generating resources share a salt: " + salts);

        // Two resources on the same grid with different salts must not co-occur more than chance.
        NaturalGeneration ironNatural = resource("iron").natural().orElseThrow();
        NaturalGeneration copperOnIronGrid = new NaturalGeneration(
                ironNatural.dimensionId(), ironNatural.biomeTag(), ironNatural.cellChunks(),
                ironNatural.chance(), ironNatural.minRadius(), ironNatural.maxRadius(),
                ironNatural.depth(), ironNatural.depthJitter(), ironNatural.minY(),
                ironNatural.maxY(), resource("copper").natural().orElseThrow().salt(),
                ironNatural.tuning());

        int cells = 0;
        int both = 0;
        int onlyIron = 0;
        int onlyCopper = 0;
        int sameOrigin = 0;
        for (int cellX = 0; cellX < 60; cellX++) {
            for (int cellZ = 0; cellZ < 60; cellZ++) {
                cells++;
                var iron = NaturalDepositSelector.candidateFor(
                        5L, resource("iron"), ironNatural, cellX, cellZ);
                var copper = NaturalDepositSelector.candidateFor(
                        5L, resource("copper"), copperOnIronGrid, cellX, cellZ);
                if (iron.isPresent() && copper.isPresent()) {
                    both++;
                    if (iron.get().originX() == copper.get().originX()
                            && iron.get().originZ() == copper.get().originZ()) {
                        sameOrigin++;
                    }
                    check(iron.get().instanceId() != copper.get().instanceId(),
                            "two resources share a deposit identity in the same cell");
                } else if (iron.isPresent()) {
                    onlyIron++;
                } else if (copper.isPresent()) {
                    onlyCopper++;
                }
            }
        }
        double observed = both / (double) cells;
        double expected = ((both + onlyIron) / (double) cells) * ((both + onlyCopper) / (double) cells);
        System.out.println(String.format(Locale.ROOT,
                "M10A independence: co-occurrence %.3f against %.3f predicted by chance;"
                        + " %d of %d shared cells put the two at the same origin",
                observed, expected, sameOrigin, both));

        check(Math.abs(observed - expected) < 0.06,
                "two salts co-occur " + observed + " against " + expected + " expected;"
                        + " the distributions are correlated");
        check(sameOrigin < Math.max(1, both / 10),
                sameOrigin + " of " + both + " shared cells stacked the two deposits at one origin");
        helper.succeed();
    }
}
