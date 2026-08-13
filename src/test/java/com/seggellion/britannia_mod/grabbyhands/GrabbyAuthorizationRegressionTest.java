package com.seggellion.britannia_mod.grabbyhands;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbySources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * The machine-checkable form of the owner's authorization constraint.
 *
 * <p>Grabby Hands must not grant an axe any general block-breaking authority, must not weaken city
 * protection, and must not disturb tree/log/leaf harvesting. It achieves that structurally: it uses
 * {@code PlayerInteractEvent.RightClickBlock}, which fires before every Adventure gate, and never
 * participates in the break pipeline at all.
 *
 * <p>These tests exist so that stays true. A future change that reaches for {@code setGameMode} or
 * hooks {@code BreakEvent} to "make destruction work" fails here rather than in production.
 */
class GrabbyAuthorizationRegressionTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MOD_ROOT = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");
    private static final Path GRABBY_ROOT = MOD_ROOT.resolve("grabbyhands");

    /**
     * Touching any of these would mean Grabby Hands had entered the break pipeline or the game-mode
     * arbitration, which is exactly what it is designed not to do.
     */
    private static final List<String> FORBIDDEN_IN_GRABBY_SOURCES = List.of(
            "setGameMode",
            "mayBuild",
            "BreakEvent",
            "BreakSpeed",
            "blockActionRestricted",
            "GameType");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void noGrabbyHandsClassTouchesTheBreakPipelineOrGameModeArbitration() throws IOException {
        for (Path file : grabbySources()) {
            // Comments are stripped first: the doc comments deliberately name these symbols to explain
            // why Grabby Hands stays away from them, and that documentation is not a violation.
            String source = GrabbySources.stripComments(Files.readString(file, StandardCharsets.UTF_8));
            for (String forbidden : FORBIDDEN_IN_GRABBY_SOURCES) {
                assertFalse(source.contains(forbidden),
                        file.getFileName() + " references " + forbidden
                                + ": Grabby Hands must stay outside the break pipeline and game-mode arbitration");
            }
        }
    }

    @Test
    void grabbyPolicyExposesNoUseOrInteractionPredicate() {
        // The absent method IS the contract: movement permission must never become use permission.
        for (Method method : GrabbyPolicy.class.getDeclaredMethods()) {
            String name = method.getName().toLowerCase(Locale.ROOT);
            for (String banned : List.of("use", "interact", "open", "sit", "access")) {
                if (name.contains(banned)) {
                    fail("GrabbyPolicy." + method.getName() + " looks like a use-permission API;"
                            + " Grabby Hands decides movement only");
                }
            }
        }
    }

    @Test
    void grabbyPolicyNeverComparesThePlacerAgainstTheActor() throws IOException {
        // Mobility marks "managed", not "mine". A placer/actor comparison would quietly turn
        // provenance into an ownership ACL.
        String policy = GrabbySources.stripComments(
                Files.readString(GRABBY_ROOT.resolve("GrabbyPolicy.java"), StandardCharsets.UTF_8));
        assertFalse(policy.contains("placerUuid()"),
                "GrabbyPolicy must not read placerUuid; movement is not restricted to the placer");
    }

    @Test
    void theAxeClassifierKeepsTheSubclassArmThatCatchesTheModsOwnAxe() throws IOException {
        // britannia_mod:two_handed_axe is not in ItemTags.AXES - the mod ships no data/minecraft/tags
        // directory. Dropping the instanceof arm as "redundant" would silently exclude the only axe
        // UltimaCraft players actually carry.
        String source = GrabbySources.stripComments(
                Files.readString(GRABBY_ROOT.resolve("GrabbyAxes.java"), StandardCharsets.UTF_8));
        assertTrue(source.contains("instanceof AxeItem"),
                "GrabbyAxes must keep the AxeItem subclass arm");
    }

    // ------------------------------------------------------------------
    // The existing authorization layers must remain intact
    // ------------------------------------------------------------------

    @Test
    void theCityBoundaryStillForcesAdventureAheadOfAnyToolCheck() throws IOException {
        String source = source("event/CityGameModeHandler.java");
        int cityCheck = source.indexOf("isPlayerInAnyCity");
        int toolCheck = source.indexOf("instanceof TwoHandedAxeItem");
        assertTrue(cityCheck >= 0, "city boundary check missing");
        assertTrue(toolCheck >= 0, "tool check missing");
        assertTrue(cityCheck < toolCheck,
                "the city check must still precede and short-circuit the tool check,"
                        + " otherwise an axe would grant Survival inside cities");
        assertTrue(source.contains("setGameMode(GameType.ADVENTURE)"));
    }

    @Test
    void theAxeRemainsContainedToWoodAtTheToolLevel() throws IOException {
        // Two independent BreakSpeed cancellations plus a zero destroy speed are what stop an axe
        // breaking furniture in any game mode. Grabby Hands relies on none of it, but must not
        // have relaxed any of it either.
        for (String handler : List.of("event/ToolInteractionHandler.java", "event/BreakSpeedHandler.java")) {
            String source = source(handler);
            assertTrue(source.contains("AxeHarvestRules.isAllowedAxeHarvestBlock"), handler);
            assertTrue(source.contains("event.setCanceled(true)"), handler);
        }
        String axe = source("item/TwoHandedAxeItem.java");
        assertTrue(axe.contains("isAllowedAxeHarvestBlock"));
        assertTrue(axe.contains("return 0.0F"), "non-wood destroy speed must stay zero");
    }

    @Test
    void treeAndLeafHarvestingRulesAreUnchanged() throws IOException {
        String rules = source("util/AxeHarvestRules.java");
        for (String token : List.of("BlockTags.LOGS", "BlockTags.LEAVES",
                "FRUIT_TREE_LOGS", "FRUIT_TREE_TRUNKS", "FRUIT_TREE_BRANCHES", "FRUIT_TREE_LEAVES")) {
            assertTrue(rules.contains(token), "AxeHarvestRules lost " + token);
        }
        String chop = source("event/WoodChopEventHandler.java");
        assertTrue(chop.contains("handleAxeHarvest"), "the live tree-harvest path must remain");
    }

    @Test
    void noGrabbyBlockTagLeaksIntoTheAxeHarvestRules() throws IOException {
        String rules = source("util/AxeHarvestRules.java");
        assertFalse(rules.toLowerCase(Locale.ROOT).contains("grabby"),
                "Grabby enrollment must never widen what an axe may harvest through the normal pipeline");
    }

    private static List<Path> grabbySources() throws IOException {
        try (Stream<Path> files = Files.walk(GRABBY_ROOT)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    private static String source(String relative) throws IOException {
        return Files.readString(MOD_ROOT.resolve(relative), StandardCharsets.UTF_8);
    }

}
