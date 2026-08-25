package com.seggellion.britannia_mod.deposit;

import com.seggellion.britannia_mod.util.ModTags;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clay as an economic resource, checked where it can be checked without a world.
 *
 * <p>Rails has priced {@code clay|raw|clay} since Housing Milestone 9 and the mason is the only
 * trader who buys it, but the mod had no clay in it at all — no item, no block, no row in the
 * classifier — so the trade route existed on one side only. These pin the three halves of the
 * fix that are pure data: what the deposit is, what it yields, and what that yield tells the
 * economy it is. What the deposit actually does to the world is in
 * {@code ManagedClayDepositGameTests}, because it needs one.
 */
class ManagedClayDepositTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /* ------------------------------------------------------------------ */
    /*  The deposit                                                        */
    /* ------------------------------------------------------------------ */

    @Test
    void theClayBedIsAManagedDepositThatYieldsOneVanillaClayBall() {
        assertEquals("britannia_mod:clay_deposit", ManagedDeposits.CLAY.id());
        assertEquals(Items.CLAY_BALL, ManagedDeposits.yieldStack(ManagedDeposits.CLAY).getItem(),
                "the deposit must yield the same item the economy classifier knows about");
        assertEquals(1, ManagedDeposits.CLAY.yield().count(),
                "one bed is one unit, as a worked stone block is one graded stone and an ore "
                        + "block one purity ore; house recipes are consumption, not node yield");
    }

    @Test
    void theAuthorizingToolIsAShovelTag() {
        assertEquals(ModTags.Items.CLAY_SHOVELS, ManagedDeposits.extractionTag(ManagedDeposits.CLAY));
        assertEquals("britannia_mod:clay_shovels", ManagedDeposits.CLAY.extractionToolTag(),
                "the authorising tag is configured data, not a compiled-in constant");
    }

    /**
     * The tag holds the project shovel and nothing else.
     *
     * <p>Asserted against the shipped JSON rather than against a bound tag, which needs a server.
     * The in-world tool matrix is a GameTest; this is the data the GameTest will read.
     */
    @Test
    void onlyTheProjectShovelIsAuthorized() throws IOException {
        String tag = Files.readString(PROJECT.resolve(
                "src/main/resources/data/britannia_mod/tags/item/clay_shovels.json"),
                StandardCharsets.UTF_8);
        assertTrue(tag.contains("britannia_mod:britannia_shovel"), tag);
        assertFalse(tag.contains("minecraft:"),
                "a vanilla shovel is not an authorized tool, exactly as a vanilla sword is not a "
                        + "skinning knife: " + tag);
    }

    /**
     * The deposit is in the Mining catalogue for its skill requirement, and for nothing else.
     *
     * <p>This assertion used to be the inverse: clay was deliberately absent, because joining the
     * catalogue then meant inheriting a STONE/ORE category and the pickaxe. The owner's
     * skill-progression decision brought the beds in — every managed geological resource requires
     * Mining, and the requirement number lives in exactly one file — but under a SEDIMENT
     * category of their own, so nothing else came with it. What the old test protected is still
     * pinned here: the mineable row claims only the authored block (a river of
     * {@code minecraft:clay} is still worthless), and the tool authority is still the resource's
     * shovel tag, never the pickaxe.
     */
    @Test
    void clayIsInTheMiningCatalogueForItsRequirementOnly() {
        com.seggellion.britannia_mod.mining.MineableDefinition clay =
                com.seggellion.britannia_mod.mining.MineableCatalog.instance()
                        .byId("clay").orElseThrow(() -> new AssertionError(
                                "the clay bed must carry a Mining requirement"));
        assertEquals(com.seggellion.britannia_mod.mining.MineableDefinition.Category.SEDIMENT,
                clay.category(), "the bed joins under its own category, not the pickaxe's");
        assertEquals(java.util.List.of("britannia_mod:clay_deposit"), clay.blockIds(),
                "only the authored bed block; vanilla clay stays out of the economy");
        assertEquals(0.0f, clay.requiredMining(), 0.0f,
                "clay stays at 0.0 so a new player can still supply their first house");
    }

    /**
     * The three spellings an administrator might reach for, and the one that must not resolve.
     *
     * <p>Milestone 10 puts test beds in the world by hand, and the name typed at the console is
     * the whole interface to that. {@code silica} is what anybody writes for
     * {@code silica_sand_deposit}; refusing it was friction in the only workflow that places a
     * resource at all.
     */
    @Test
    void theAuthoringCommandTakesTheNameAnAdministratorWouldType() {
        assertTrue(ManagedDeposits.byName("clay").isPresent());
        assertTrue(ManagedDeposits.byName("clay_deposit").isPresent());
        assertEquals("britannia_mod:silica_sand_deposit",
                ManagedDeposits.byName("silica_sand_deposit").orElseThrow().id());
        assertEquals("britannia_mod:silica_sand_deposit",
                ManagedDeposits.byName("silica_sand").orElseThrow().id());
        assertEquals("britannia_mod:silica_sand_deposit",
                ManagedDeposits.byName("silica").orElseThrow().id(),
                "the short name is what /manageddeposit place silica has to accept");
        assertTrue(ManagedDeposits.byName("gravel").isEmpty());
        assertTrue(ManagedDeposits.byName("").isEmpty());
        assertTrue(ManagedDeposits.byName(null).isEmpty());
    }

    /**
     * The mod never names the buyer.
     *
     * <p>Rails' policy fixture is the authority on who accepts what, and it says the stone trader
     * and nobody else. The classifier's whole job is to say what the item is.
     */
    @Test
    void railsPolicySendsRawClayToTheStoneTraderAndFiredBrickToNobody() throws IOException {
        String fixture = Files.readString(PROJECT.resolve(
                "src/test/resources/wire_contract/trader_policy_parity.json"), StandardCharsets.UTF_8);
        com.google.gson.JsonObject root =
                com.google.gson.JsonParser.parseString(fixture).getAsJsonObject();
        com.google.gson.JsonArray probes = root.getAsJsonArray("probes");

        int rawClay = -1;
        int firedBrick = -1;
        for (int index = 0; index < probes.size(); index++) {
            com.google.gson.JsonObject probe = probes.get(index).getAsJsonObject();
            // One probe deliberately carries a null subcategory, so every read is null-safe.
            if (!"clay".equals(text(probe, "category"))) continue;
            if ("raw".equals(text(probe, "subcategory"))) rawClay = index;
            if ("processed".equals(text(probe, "subcategory"))) firedBrick = index;
        }
        assertTrue(rawClay >= 0, "the fixture no longer carries a clay|raw|clay probe");
        assertTrue(firedBrick >= 0, "the fixture no longer carries the fired-brick negative probe");

        com.google.gson.JsonObject verdicts = root.getAsJsonObject("verdicts");
        java.util.List<String> buyers = new java.util.ArrayList<>();
        java.util.List<String> brickBuyers = new java.util.ArrayList<>();
        for (String trader : verdicts.keySet()) {
            com.google.gson.JsonArray answers = verdicts.getAsJsonArray(trader);
            if (answers.get(rawClay).getAsBoolean()) buyers.add(trader);
            if (answers.get(firedBrick).getAsBoolean()) brickBuyers.add(trader);
        }
        assertEquals(java.util.List.of("stone_trader"), buyers,
                "the buyer of raw clay changed, and the mod's supply path was built for the mason");
        assertTrue(brickBuyers.isEmpty(),
                "somebody now buys fired brick as a commodity: " + brickBuyers);
    }

    private static String text(com.google.gson.JsonObject object, String member) {
        com.google.gson.JsonElement value = object.get(member);
        return value == null || value.isJsonNull() ? null : value.getAsString();
    }
}
