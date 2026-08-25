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

    /**
     * The protection this used to pin — CityGameModeHandler checking the city boundary before an
     * axe could earn Survival — is now structural rather than an ordering: nothing grants
     * Survival at all. That handler is retired, the zone rule holds every non-operator in
     * adventure unconditionally (no held tool exempts anyone), and in-city mining is refused
     * explicitly by the Mining gate instead of by a game-mode accident. This asserts each of
     * those, so a future change that reintroduces a tool-based game-mode grant fails here.
     */
    @Test
    void cityProtectionNoLongerDependsOnGameModeSwitching() throws IOException {
        assertFalse(Files.exists(MOD_ROOT.resolve("event/CityGameModeHandler.java")),
                "the Survival-switch handler must stay retired");

        String zone = source("structure/SurvivalZoneHandler.java");
        assertTrue(zone.contains("setGameMode(GameType.ADVENTURE)"),
                "the zone rule must still return players to adventure");
        assertFalse(zone.contains("instanceof TwoHandedAxeItem"),
                "no held tool may exempt anyone from adventure");
        assertFalse(zone.contains("instanceof QualityToolItem"),
                "no held tool may exempt anyone from adventure");
        assertFalse(zone.contains("setGameMode(GameType.SURVIVAL)"),
                "nothing may put a player into Survival for a tool");

        String gate = source("mining/MiningGateHandler.java");
        assertTrue(gate.contains("isPlayerInAnyCity"),
                "in-city mining must be refused explicitly now that adventure can dig");
    }

    @Test
    void theAxeRemainsContainedToWoodAtTheToolLevel() throws IOException {
        // A BreakSpeed cancellation plus a zero destroy speed are what stop an axe breaking
        // furniture in any game mode. Grabby Hands relies on neither, but must not have relaxed
        // either.
        //
        // This used to check two handlers. BreakSpeedHandler was a byte-for-byte duplicate of
        // ToolInteractionHandler's logic that was imported but never registered and carried no
        // @EventBusSubscriber, so it never fired: dead weight, and a standing invitation to fix a
        // rule in the copy that does nothing. It is gone, and its absence is asserted so the
        // duplicate cannot quietly return.
        String handler = "event/ToolInteractionHandler.java";
        String source = source(handler);
        assertTrue(source.contains("AxeHarvestRules.isAllowedAxeHarvestBlock"), handler);
        assertTrue(source.contains("event.setCanceled(true)"), handler);
        assertFalse(Files.exists(MOD_ROOT.resolve("event/BreakSpeedHandler.java")),
                "the unregistered duplicate break-speed handler must stay deleted");
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
