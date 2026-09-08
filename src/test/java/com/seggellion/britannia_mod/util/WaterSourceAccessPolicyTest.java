package com.seggellion.britannia_mod.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

class WaterSourceAccessPolicyTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @Test
    void allowedWorldPositionPermitsUnlimitedWaterAccess() {
        assertEquals(
                WaterSourceAccessPolicy.Decision.ALLOWED,
                WaterSourceAccessPolicy.fromWorldPermission(true));
    }

    @Test
    void protectedWorldPositionDeniesAccess() {
        assertEquals(
                WaterSourceAccessPolicy.Decision.DENIED_WORLD,
                WaterSourceAccessPolicy.fromWorldPermission(false));
    }

    @Test
    void thePolicyHasExactlyOneReasonToRefuse() {
        // This is the fact the message site has to respect. The policy is level.mayInteract and
        // nothing else: it knows nothing about buckets, wells or Adventure mode, so any message
        // naming one of those was guessed from the item in hand.
        assertEquals(2, WaterSourceAccessPolicy.Decision.values().length,
                "a new decision needs a message of its own, not a guess at the call site");
    }

    @Test
    void theRefusalMessageDoesNotInventAReasonThePolicyDoesNotHave() {
        // Two ways the guess was wrong, both reachable. A bucket refused by spawn protection was
        // told "A bucket only fills at the Water Well" -- but a well inside the same protection
        // refuses too, and outside protection a bucket fills anywhere. And a player refused while
        // standing on a Water Well was told to "Use the public Water Well".
        String source = read(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/util/WaterSourceInteraction.java"));
        int refusal = source.indexOf("!= WaterSourceAccessPolicy.Decision.ALLOWED");
        assertTrue(refusal >= 0, "the refusal branch moved");
        // Bounded by the branch's own return, so what follows it -- the source classification,
        // which legitimately does ask whether this block is a Water Well -- is not read as part of
        // the refusal.
        int endOfRefusal = source.indexOf(
                "return ItemInteractionResult.sidedSuccess(false);", refusal);
        assertTrue(endOfRefusal > refusal, "the refusal branch no longer returns");
        String branch = source.substring(refusal, endOfRefusal);

        assertTrue(branch.contains("QuestScreenText.WATER_PROTECTED"),
                "the refusal no longer says anything at all");
        assertFalse(branch.contains("WATER_BUCKET_NEEDS_WELL"),
                "the refusal still guesses a bucket-and-well reason the policy cannot know");
        assertFalse(branch.contains("instanceof WaterWellBlock"),
                "the refusal still branches on the block it is standing on");
        assertFalse(branch.contains("Items.BUCKET"),
                "the refusal still branches on the item in hand");
    }

    @Test
    void theProtectionSentenceDoesNotSendThePlayerToTheBlockTheyAreStandingOn() {
        String lang = read(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/lang/en_us.json"));
        int at = lang.indexOf("\"message.britannia_mod.quest.water.protected\"");
        assertTrue(at >= 0, "the protection message is gone");
        String line = lang.substring(at, lang.indexOf('\n', at));
        assertFalse(line.contains("Water Well"),
                "a refusal at the Water Well still tells the player to use the Water Well: " + line);
        assertFalse(lang.contains("water.bucket_needs_well"),
                "the retired bucket message is still in the lang file");
    }

    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
