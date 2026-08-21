package com.seggellion.britannia_mod.worldgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.mining.MineableCatalog;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * OreVein milestone 5: what UltimaCraft says about vanilla geology, and whether the mechanism
 * agrees with it.
 *
 * <p>The classification is the source of truth; the biome modifier is derived from it. These prove
 * the two cannot drift apart, that the stone families the Mining ladder depends on are on the
 * protected side, and that the Nether set is deliberately untouched rather than forgotten.
 */
class VanillaFeaturePolicyTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MODIFIER = PROJECT.resolve(
            "src/main/resources/data/britannia_mod/neoforge/biome_modifier/suppress_vanilla_overworld_ores.json");

    private static VanillaFeaturePolicy policy() {
        return VanillaFeaturePolicy.instance();
    }

    private static JsonObject modifier() throws Exception {
        return JsonParser.parseString(Files.readString(MODIFIER)).getAsJsonObject();
    }

    /* ------------------------------------------------------------------ */
    /*  The mechanism matches the policy                                   */
    /* ------------------------------------------------------------------ */

    /**
     * The biome modifier removes exactly what the policy says to remove — no more, no less.
     *
     * <p>The anti-drift rule. Adding a feature to the modifier without classifying it, or
     * classifying one as suppressed and forgetting the modifier, both fail here rather than in a
     * world months later.
     */
    @Test
    void theBiomeModifierRemovesExactlyTheSuppressedSet() throws Exception {
        JsonArray features = modifier().getAsJsonArray("features");
        Set<String> inModifier = new LinkedHashSet<>();
        for (JsonElement element : features) {
            assertTrue(inModifier.add(element.getAsString()),
                    "the modifier lists " + element.getAsString() + " twice");
        }
        assertEquals(policy().suppressed(), inModifier,
                "the modifier and the classification must describe the same removal");
    }

    /** It is the right kind of modifier, scoped to the Overworld and to one step. */
    @Test
    void theModifierIsScopedToOverworldUndergroundOres() throws Exception {
        JsonObject modifier = modifier();
        assertEquals("neoforge:remove_features", modifier.get("type").getAsString());
        assertEquals("#minecraft:is_overworld", modifier.get("biomes").getAsString(),
                "suppression must cover the whole Overworld, not a hand-picked biome subset");
        assertEquals(VanillaFeaturePolicy.GOVERNED_STEP, modifier.get("steps").getAsString(),
                "constraining the step is the second belt: the Nether ores live in a different one");
    }

    /* ------------------------------------------------------------------ */
    /*  What is suppressed                                                 */
    /* ------------------------------------------------------------------ */

    /**
     * Every economic mineral family, with every one of its height and distribution variants.
     *
     * <p>Named individually because a family with one variant missed is the failure that looks like
     * success: iron would still generate from {@code ore_iron_small} while the audit said iron was
     * suppressed.
     */
    @Test
    void everyEconomicMineralFamilyIsFullySuppressed() {
        assertEquals(Set.of(
                "minecraft:ore_coal_upper", "minecraft:ore_coal_lower",
                "minecraft:ore_iron_upper", "minecraft:ore_iron_middle", "minecraft:ore_iron_small",
                "minecraft:ore_gold", "minecraft:ore_gold_lower", "minecraft:ore_gold_extra",
                "minecraft:ore_redstone", "minecraft:ore_redstone_lower",
                "minecraft:ore_diamond", "minecraft:ore_diamond_medium",
                "minecraft:ore_diamond_large", "minecraft:ore_diamond_buried",
                "minecraft:ore_lapis", "minecraft:ore_lapis_buried",
                "minecraft:ore_emerald",
                "minecraft:ore_copper", "minecraft:ore_copper_large"),
                policy().suppressed());
        assertEquals(19, policy().suppressed().size());
    }

    /**
     * The two special distributions are not forgotten.
     *
     * <p>Badlands gold and mountain emerald are attached by their own methods
     * ({@code addExtraGold}, {@code addExtraEmeralds}) rather than by {@code addDefaultOres}, so a
     * suppression list built only from the default set would leave both generating.
     */
    @Test
    void theSpecialDistributionsAreSuppressedToo() {
        assertTrue(policy().suppressed().contains("minecraft:ore_gold_extra"),
                "badlands gold is attached separately and would otherwise survive");
        assertTrue(policy().suppressed().contains("minecraft:ore_emerald"),
                "mountain emerald is attached separately and would otherwise survive");
    }

    /** Copper has two mutually exclusive variants; dripstone caves get the large one. */
    @Test
    void bothCopperVariantsAreSuppressed() {
        assertTrue(policy().suppressed().contains("minecraft:ore_copper"));
        assertTrue(policy().suppressed().contains("minecraft:ore_copper_large"),
                "addDefaultOres picks ORE_COPPER_LARGE for dripstone caves");
    }

    /* ------------------------------------------------------------------ */
    /*  What must survive                                                  */
    /* ------------------------------------------------------------------ */

    /**
     * The Mining ladder's rock is on the protected side.
     *
     * <p>The single easiest mistake in this milestone: andesite, diorite, granite and tuff sit in
     * the same {@code underground_ores} step as the economic minerals, and every one of them is an
     * ACTIVE mineable carrying a Mining requirement. Removing the step, or widening the removal by
     * family name, would delete the mid-tier progression outright.
     */
    @Test
    void theMiningLaddersRockFamiliesAreExplicitlyAllowed() {
        for (String feature : List.of(
                "minecraft:ore_andesite_upper", "minecraft:ore_andesite_lower",
                "minecraft:ore_diorite_upper", "minecraft:ore_diorite_lower",
                "minecraft:ore_granite_upper", "minecraft:ore_granite_lower",
                "minecraft:ore_tuff")) {
            assertTrue(policy().allowed().contains(feature), feature + " must survive suppression");
            assertFalse(policy().suppressed().contains(feature),
                    feature + " is part of the Mining progression and must never be suppressed");
        }
    }

    /** And those rock families really are ACTIVE mineables, so the reason above is a fact. */
    @Test
    void thoseRockFamiliesAreActiveMineables() {
        for (String mineable : List.of("andesite", "diorite", "granite", "tuff")) {
            assertTrue(MineableCatalog.instance().byId(mineable)
                            .map(definition -> definition.active()).orElse(false),
                    mineable + " is expected to be an ACTIVE Mining resource");
        }
    }

    /** World formation and the lush-caves clay stay; none of them are economic minerals. */
    @Test
    void worldFormationFeaturesAreAllowed() {
        for (String feature : List.of("minecraft:ore_gravel", "minecraft:ore_dirt",
                "minecraft:ore_clay", "minecraft:underwater_magma")) {
            assertTrue(policy().allowed().contains(feature), feature + " must survive");
        }
    }

    /* ------------------------------------------------------------------ */
    /*  Nether                                                             */
    /* ------------------------------------------------------------------ */

    /**
     * The first-pass Nether suppression set is empty, and that is recorded rather than implied.
     *
     * <p>Every Nether ore feature is classified out-of-scope, so "we decided not to" is
     * distinguishable from "we never looked". They are also in a different decoration step, which
     * the modifier constrains, so they are protected twice over.
     */
    @Test
    void theNetherSuppressionSetIsEmptyAndRecorded() {
        List<String> netherFeatures = List.of(
                "minecraft:ore_gold_nether", "minecraft:ore_quartz_nether",
                "minecraft:ore_gravel_nether", "minecraft:ore_blackstone",
                "minecraft:ore_magma", "minecraft:ore_soul_sand",
                "minecraft:ore_gold_deltas", "minecraft:ore_quartz_deltas",
                "minecraft:ore_ancient_debris_large", "minecraft:ore_debris_small");

        for (String feature : netherFeatures) {
            assertEquals("out_of_scope", policy().bucketOf(feature).orElse("unclassified"),
                    feature + " must be deliberately out of scope, not suppressed and not forgotten");
            assertFalse(policy().suppressed().contains(feature),
                    "the first-pass Nether policy is to suppress nothing");
        }
    }

    /**
     * Blackstone is Nether-only, which corrects an earlier report.
     *
     * <p>The M3 report listed {@code ore_blackstone} among Overworld features that must stay
     * allowed. Registry inspection shows it is attached only by {@code addNetherDefaultOres}, in
     * {@code underground_decoration}. It is out of scope rather than Overworld-allowed.
     */
    @Test
    void blackstoneIsNetherOnlyAndThereforeOutOfScope() {
        assertEquals("out_of_scope", policy().bucketOf("minecraft:ore_blackstone").orElse("unclassified"));
        assertFalse(policy().allowed().contains("minecraft:ore_blackstone"));
    }

    /* ------------------------------------------------------------------ */
    /*  Structure of the classification                                    */
    /* ------------------------------------------------------------------ */

    /** Nothing is in two buckets, and the loader refuses a file where something is. */
    @Test
    void aFeatureCannotBeInTwoBuckets() {
        int total = policy().suppressedEntries().size()
                + policy().allowedEntries().size()
                + policy().outOfScopeEntries().size();
        assertEquals(total, policy().classified().size(), "every entry must be its own feature");

        String clashing = """
                {"schema": 1,
                 "suppressed": [{"feature": "minecraft:ore_coal_upper", "family": "coal", "step": "underground_ores"}],
                 "allowed":    [{"feature": "minecraft:ore_coal_upper", "family": "coal", "step": "underground_ores"}],
                 "out_of_scope": []}""";
        assertTrue(assertThrows(IllegalStateException.class,
                        () -> VanillaFeaturePolicy.parse(new StringReader(clashing)))
                .getMessage().contains("classified both"));
    }

    /** A suppressed feature must say which step it is removed from, and it must be the governed one. */
    @Test
    void aSuppressedFeatureMustDeclareTheGovernedStep() {
        String wrongStep = """
                {"schema": 1,
                 "suppressed": [{"feature": "minecraft:ore_coal_upper", "family": "coal", "step": "vegetal_decoration"}],
                 "allowed": [], "out_of_scope": []}""";
        assertTrue(assertThrows(IllegalStateException.class,
                        () -> VanillaFeaturePolicy.parse(new StringReader(wrongStep)))
                .getMessage().contains("must declare step"));
    }

    @Test
    void malformedPolicyDataFailsCleanly() {
        assertTrue(assertThrows(IllegalStateException.class, () -> VanillaFeaturePolicy.parse(
                        new StringReader("{\"schema\": 9, \"suppressed\": [], \"allowed\": [], \"out_of_scope\": []}")))
                .getMessage().contains("Unsupported vanilla feature policy schema"));

        assertTrue(assertThrows(IllegalStateException.class, () -> VanillaFeaturePolicy.parse(
                        new StringReader("{\"schema\": 1, \"allowed\": [], \"out_of_scope\": []}")))
                .getMessage().contains("missing 'suppressed'"));

        assertTrue(assertThrows(IllegalStateException.class, () -> VanillaFeaturePolicy.parse(
                        new StringReader("{\"schema\": 1, \"suppressed\": [], \"allowed\": [], \"out_of_scope\": []}")))
                .getMessage().contains("suppresses nothing"));
    }

    /** Every entry carries a family, and the allowed ones say why they are kept. */
    @Test
    void everyAllowedEntryExplainsWhyItSurvives() {
        for (VanillaFeaturePolicy.Entry entry : policy().allowedEntries()) {
            assertFalse(entry.reason().isBlank(),
                    entry.feature() + " must record why it is deliberately kept");
        }
        for (VanillaFeaturePolicy.Entry entry : policy().outOfScopeEntries()) {
            assertFalse(entry.reason().isBlank(),
                    entry.feature() + " must record why it is deliberately untouched");
        }
    }

    /**
     * The classification covers every vanilla ore placement this Minecraft version defines.
     *
     * <p>Read from the decompiled {@code OrePlacements} source in the build directory, which is the
     * version this project actually compiles against — so a Minecraft update that adds a variant
     * fails here rather than generating an unsuppressed ore. Scoped to {@code OrePlacements} rather
     * than every placed feature, so unrelated vegetation cannot cause a false failure.
     */
    @Test
    void everyVanillaOrePlacementOfThisVersionIsClassified() throws Exception {
        Path orePlacements = PROJECT.resolve("build/neoform/neoFormJoined1.21.1-20240808.144430/steps/"
                + "unzipSources/unpacked/net/minecraft/data/worldgen/placement/OrePlacements.java");
        if (!Files.exists(orePlacements)) {
            return; // Decompiled sources are a build artefact; the GameTest covers the live registry.
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("createKey\\(\"([a-z0-9_]+)\"\\)")
                .matcher(Files.readString(orePlacements));

        List<String> unclassified = new ArrayList<>();
        int seen = 0;
        while (matcher.find()) {
            seen++;
            String feature = "minecraft:" + matcher.group(1);
            if (policy().bucketOf(feature).isEmpty()) {
                unclassified.add(feature);
            }
        }
        assertTrue(seen > 30, "expected to find the vanilla ore placements, found " + seen);
        assertTrue(unclassified.isEmpty(),
                "these vanilla ore placements are neither suppressed, allowed nor out of scope: "
                        + unclassified);
    }

    /* ------------------------------------------------------------------ */
    /*  The part remove_features cannot reach                              */
    /* ------------------------------------------------------------------ */

    /**
     * The chunk generator writes copper and iron ore veins itself, before any decoration step runs,
     * so the biome modifier never sees them. A fixed-seed world audit found exactly this: every
     * suppressed placed feature gone, and vanilla copper ore still in the ground between y0 and y50
     * alongside raw copper blocks at roughly the 2% rate OreVeinifier uses. The policy has to say so
     * out loud, because the alternative is somebody re-discovering it as a suppression bug.
     */
    @Test
    void theNoiseOreVeinsAreRecordedAsUnreachable() {
        Set<String> mechanisms = new LinkedHashSet<>();
        policy().unreachableSources().forEach(source -> mechanisms.add(source.mechanism()));

        assertEquals(Set.of("minecraft:ore_veinifier/copper", "minecraft:ore_veinifier/iron"),
                mechanisms,
                "the two noise ore veins are the only vanilla mineral sources that are not features");
    }

    /** The height bands are the ones OreVeinifier.VeinType declares, so an audit knows where to look. */
    @Test
    void eachUnreachableSourceRecordsWhereAndWhatItWrites() {
        VanillaFeaturePolicy.UnreachableSource copper = unreachable("minecraft:ore_veinifier/copper");
        assertEquals(0, copper.minY());
        assertEquals(50, copper.maxY());
        assertEquals(List.of("minecraft:copper_ore", "minecraft:raw_copper_block"), copper.produces());

        VanillaFeaturePolicy.UnreachableSource iron = unreachable("minecraft:ore_veinifier/iron");
        assertEquals(-60, iron.minY());
        assertEquals(-8, iron.maxY());
        assertEquals(List.of("minecraft:deepslate_iron_ore", "minecraft:raw_iron_block"), iron.produces());
    }

    /**
     * Copper and iron are both families the policy claims to suppress. That is the whole point of
     * recording the leak: the claim is only true of the feature route.
     */
    @Test
    void theLeakingFamiliesAreFamiliesWeClaimToOwn() {
        assertEquals(Set.of("copper", "iron"), policy().leakingFamilies());
        for (String family : policy().leakingFamilies()) {
            assertTrue(familyIsSuppressed(family),
                    family + " leaks but is not suppressed, so recording it makes no sense");
        }
    }

    /** A mechanism is not a feature id, so it must never appear in the modifier's removal list. */
    @Test
    void theBiomeModifierDoesNotPretendToRemoveTheVeins() throws Exception {
        Set<String> removed = new LinkedHashSet<>();
        for (JsonElement element : modifier().getAsJsonArray("features")) {
            removed.add(element.getAsString());
        }
        for (VanillaFeaturePolicy.UnreachableSource source : policy().unreachableSources()) {
            assertFalse(removed.contains(source.mechanism()),
                    source.mechanism() + " is not a placed feature and cannot be removed");
            assertTrue(policy().bucketOf(source.mechanism()).isEmpty(),
                    source.mechanism() + " must not be filed as a classified feature");
        }
    }

    /** Recording a leak for a family nobody suppresses would be noise, so the loader refuses it. */
    @Test
    void anUnreachableSourceMustBelongToASuppressedFamily() {
        String json = minimalPolicy().replace("\"unreachable\": []",
                "\"unreachable\": [{\"mechanism\": \"minecraft:made_up\", \"family\": \"tuff\", "
                        + "\"produces\": [\"minecraft:tuff\"], \"min_y\": 0, \"max_y\": 1}]");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> VanillaFeaturePolicy.parse(new StringReader(json)));
        assertTrue(failure.getMessage().contains("not suppressed"), failure.getMessage());
    }

    /** An entry that does not say what block to look for cannot be audited, so it is rejected. */
    @Test
    void anUnreachableSourceMustNameTheBlocksItWrites() {
        String json = minimalPolicy().replace("\"unreachable\": []",
                "\"unreachable\": [{\"mechanism\": \"minecraft:made_up\", \"family\": \"coal\", "
                        + "\"produces\": [], \"min_y\": 0, \"max_y\": 1}]");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> VanillaFeaturePolicy.parse(new StringReader(json)));
        assertTrue(failure.getMessage().contains("blocks it produces"), failure.getMessage());
    }

    private VanillaFeaturePolicy.UnreachableSource unreachable(String mechanism) {
        return policy().unreachableSources().stream()
                .filter(source -> source.mechanism().equals(mechanism))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no unreachable source " + mechanism));
    }

    private boolean familyIsSuppressed(String family) {
        return policy().suppressedEntries().stream().anyMatch(entry -> entry.family().equals(family));
    }

    /** A smallest-possible valid policy, for the loader-rejection tests to mutate. */
    private static String minimalPolicy() {
        return "{\"schema\": 1,"
                + "\"suppressed\": [{\"feature\": \"minecraft:ore_coal_upper\", \"family\": \"coal\", "
                + "\"step\": \"underground_ores\"}],"
                + "\"allowed\": [],"
                + "\"out_of_scope\": [],"
                + "\"unreachable\": []}";
    }
}
