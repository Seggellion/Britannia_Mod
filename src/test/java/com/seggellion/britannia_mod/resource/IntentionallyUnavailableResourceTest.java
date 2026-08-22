package com.seggellion.britannia_mod.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.mining.MineableCatalog;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Four vanilla families are denied and deliberately never replaced.
 *
 * <h2>Why absence needs a test</h2>
 * Redstone, lapis, vanilla diamond and vanilla emerald are suppressed in new chunks and get no
 * managed resource. That is a decision, not an oversight — but a decision that is only recorded as
 * the absence of files looks exactly like an oversight to the next person, who "fixes" it by adding
 * the obvious missing ore. These assertions are how the decision survives that.
 *
 * <p>Owner decisions, milestone 11:
 * <ul>
 *   <li><b>Redstone</b> — no repository gameplay depends on it. No managed resource.</li>
 *   <li><b>Lapis</b> — no crafting or enchanting use exists in the mod. No managed resource.</li>
 *   <li><b>Diamond</b> — not part of the geological economy. Britannia's diamond is the Ultima
 *       Online gem {@code blue_diamond}, which is a different item and stays.</li>
 *   <li><b>Emerald</b> — same: {@code perfect_emerald} is the gem, and vanilla emerald is villager
 *       currency that conflicts with the Rails economy.</li>
 * </ul>
 *
 * <p>Behavioural and data assertions only — no scanning of source text. What matters is that the
 * catalogues do not contain these resources and that the suppression policy still denies them, and
 * both are readable as data.
 */
@DisplayName("redstone, lapis, vanilla diamond and vanilla emerald stay unavailable on purpose")
final class IntentionallyUnavailableResourceTest {

    private static final Path PROJECT = Paths.get(System.getProperty("britannia.projectDir", "."));

    /** Resource paths that must never appear in the catalogues. */
    private static final List<String> UNAVAILABLE =
            List.of("redstone", "lapis", "diamond", "emerald");

    /** The vanilla blocks that must never become a managed economic identity. */
    private static final List<String> UNMANAGED_VANILLA_BLOCKS = List.of(
            "minecraft:redstone_ore", "minecraft:deepslate_redstone_ore",
            "minecraft:lapis_ore", "minecraft:deepslate_lapis_ore",
            "minecraft:diamond_ore", "minecraft:deepslate_diamond_ore",
            "minecraft:emerald_ore", "minecraft:deepslate_emerald_ore");

    /** The vanilla items that must never be a Mining yield. */
    private static final List<String> UNMANAGED_VANILLA_ITEMS = List.of(
            "minecraft:redstone", "minecraft:lapis_lazuli",
            "minecraft:diamond", "minecraft:emerald");

    private static MineableCatalog mineables;

    @BeforeAll
    static void loadShippedCatalog() throws IOException {
        mineables = MineableCatalog.parse(Files.newBufferedReader(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/mining/mineables.json")));
    }

    @Test
    @DisplayName("none of the four is a managed resource")
    void noneHasAResourceDefinition() {
        for (String path : UNAVAILABLE) {
            assertTrue(ResourceCatalog.instance().byPath(path).isEmpty(),
                    path + " must not have a resource definition; it is denied in new chunks and"
                            + " intentionally has no managed replacement");
        }
    }

    @Test
    @DisplayName("none of the four is on the Mining ladder")
    void noneIsMineable() {
        for (String path : UNAVAILABLE) {
            assertTrue(mineables.byId(path).isEmpty(),
                    path + " must not be a mineable; it carries no Mining requirement because it is"
                            + " not a Britannia resource");
        }
    }

    @Test
    @DisplayName("no vanilla block of theirs is claimed by any managed resource")
    void noVanillaBlockIsManaged() {
        for (String block : UNMANAGED_VANILLA_BLOCKS) {
            assertTrue(mineables.resolveBlock(block).isEmpty(),
                    block + " must stay ordinary vanilla terrain. Claiming it would make every"
                            + " legacy chunk's deposits economic and retro-populate the economy from"
                            + " terrain the project never generated");
            for (ResourceDefinition definition : ResourceCatalog.instance().all()) {
                assertFalse(definition.blockIds().contains(block),
                        block + " is governed by resource " + definition.id());
            }
        }
    }

    @Test
    @DisplayName("none of the four is produced by any managed extraction")
    void noneIsAConfiguredYield() {
        for (ResourceDefinition definition : ResourceCatalog.instance().all()) {
            definition.yield().itemId().ifPresent(item ->
                    assertFalse(UNMANAGED_VANILLA_ITEMS.contains(item),
                            definition.id() + " yields " + item + ", which the owner decided is not"
                                    + " an UltimaCraft Mining output"));
        }
    }

    /**
     * Nothing generates automatically for them — nor for anything else.
     *
     * <p>Since the milestone 11 amendment this is true of every resource, because automatic
     * distribution no longer exists at all: deposits come from Rails rows. The assertion is kept
     * in its resource-specific form anyway, because it is the one a future reader will look for
     * when asking "is redstone really absent, or did someone quietly add a vein?".
     */
    @Test
    @DisplayName("nothing generates automatically for them")
    void noneGeneratesAutomatically() {
        for (String path : UNAVAILABLE) {
            assertTrue(ResourceCatalog.instance().byPath(path).isEmpty(),
                    path + " must not exist as a resource at all, so it cannot be generated");
        }
    }

    /**
     * The suppression must still be there. Deciding not to replace a family only makes sense while
     * the family is actually denied — if suppression were dropped, "intentionally unavailable"
     * would silently become "vanilla, unmanaged and free", which is the state milestone 1 removed
     * coal for being in.
     */
    @Test
    @DisplayName("their vanilla generation is still suppressed")
    void vanillaGenerationRemainsSuppressed() throws IOException {
        String policy = Files.readString(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/worldgen/vanilla_feature_policy.json"));
        for (String feature : List.of("ore_redstone", "ore_lapis", "ore_diamond", "ore_emerald")) {
            assertTrue(policy.contains("minecraft:" + feature + "\""),
                    feature + " must remain in the suppression policy");
        }
    }

    /**
     * Britannia's own gems are a separate identity and must not be swept up in any of this.
     *
     * <p>{@code blue_diamond} and {@code perfect_emerald} are Ultima Online gems used by the
     * blacksmithing catalogue. They share a word with the vanilla items and nothing else, and the
     * owner decision was explicit that they must not be conflated or removed.
     */
    @Test
    @DisplayName("the Britannia gems are untouched and remain distinct")
    void britanniaGemsRemainSeparate() throws IOException {
        String craftables = Files.readString(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/blacksmithing/craftables.json"));
        for (String gem : List.of("blue_diamond", "perfect_emerald")) {
            assertTrue(craftables.contains("\"" + gem + "\""),
                    gem + " must remain a blacksmithing ingredient; it is Britannia's gem and is"
                            + " unrelated to the vanilla mineral of a similar name");
            assertTrue(ResourceCatalog.instance().byPath(gem).isEmpty(),
                    gem + " is an item, not a geological resource");
        }
    }
}
