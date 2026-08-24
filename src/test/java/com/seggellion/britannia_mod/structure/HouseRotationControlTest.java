package com.seggellion.britannia_mod.structure;

import com.seggellion.britannia_mod.client.house.HouseRotationData;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One control rotates the house ghost, and it is the left click.
 *
 * <h2>The defect this exists to make impossible</h2>
 *
 * <p>For one release both mouse buttons rotated. The chain was not obvious:
 *
 * <ol>
 *   <li>the deed's {@code use()} returned {@code InteractionResultHolder.success};</li>
 *   <li>{@code Minecraft.startUseItem} swings the arm for any result that both
 *       {@code consumesAction()} and {@code shouldSwing()};</li>
 *   <li>{@code LivingEntity.swing} calls {@code ItemStack.onEntitySwing};</li>
 *   <li>rotation lives in {@code onEntitySwing}, because rotation <em>is</em> the swing.</li>
 * </ol>
 *
 * <p>So a right-click placed the house and turned the ghost, and the two controls were
 * indistinguishable. The fix is one word — {@code consume} instead of {@code success} — which is
 * exactly the kind of fix that silently regresses. A comment explaining why {@code CONSUME} works
 * is not protection; these tests are.
 *
 * <p>Three things are pinned, and each would independently reintroduce the defect if it changed:
 * the vanilla contract the fix depends on, the deed's use of it, and the fact that rotation has one
 * caller. The one thing no headless test can press is a physical mouse button — see
 * {@code PATCH_18_HOUSING_PRODUCTION_SMOKE_TEST.md} steps 9 and 10.
 */
class HouseRotationControlTest {

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path DEED =
            PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/item/AbstractHouseDeedItem.java");

    @BeforeEach
    void forgetEveryRotation() {
        HouseRotationData.forgetAll();
    }

    // ------------------------------------------------------------------
    // The vanilla contract the fix rests on
    // ------------------------------------------------------------------

    /**
     * {@code CONSUME} consumes the interaction without asking for a swing; {@code SUCCESS} asks.
     *
     * <p>This is the single fact the whole fix depends on, and it belongs to Minecraft rather than
     * to us. If a version bump ever changed it, the deed would start rotating on right-click again
     * with nothing in this repository having been edited. This is the tripwire for that.
     */
    @Test
    void consumeDoesNotSwingAndSuccessDoes() {
        assertTrue(InteractionResult.CONSUME.consumesAction(),
                "CONSUME must still end the interaction, or the off-hand would be tried next");
        assertFalse(InteractionResult.CONSUME.shouldSwing(),
                "CONSUME now requests an arm swing. That swing is what rotated the ghost on "
                        + "right-click; the deed must stop using CONSUME and find another way to "
                        + "end the interaction without swinging.");

        assertTrue(InteractionResult.SUCCESS.consumesAction());
        assertTrue(InteractionResult.SUCCESS.shouldSwing(),
                "SUCCESS no longer swings; the historical explanation of this defect is now wrong "
                        + "and the reasoning in AbstractHouseDeedItem needs revisiting");

        // PASS and FAIL are the other two results the deed can return. Neither may swing either:
        // PASS is the server-side branch, FAIL is "you were not aiming at a block".
        assertFalse(InteractionResult.PASS.shouldSwing());
        assertFalse(InteractionResult.FAIL.shouldSwing());
    }

    /**
     * The holder factories the deed actually calls, checked as the deed calls them.
     *
     * <p>Carrying a {@link String} rather than an {@code ItemStack}: the holder is generic and does
     * not care, and touching {@code ItemStack} here would drag in Minecraft's registries. A test
     * that initialises them without bootstrapping poisons the whole shared test JVM, which this
     * repository has been bitten by before.
     */
    @Test
    void everyResultTheDeedCanReturnLeavesTheArmStill() {
        for (InteractionResultHolder<String> holder : List.of(
                InteractionResultHolder.consume("deed"),
                InteractionResultHolder.pass("deed"),
                InteractionResultHolder.fail("deed"))) {
            assertFalse(holder.getResult().shouldSwing(),
                    holder.getResult() + " swings the arm, which rotates the ghost");
        }
        assertTrue(InteractionResultHolder.success("deed").getResult().shouldSwing(),
                "success() is the one the deed must never return; if it stopped swinging, this "
                        + "test is no longer proving anything");
    }

    // ------------------------------------------------------------------
    // The deed uses it
    // ------------------------------------------------------------------

    @Test
    void theDeedReturnsConsumeAndNeverSuccess() throws IOException {
        String source = read(DEED);
        assertTrue(source.contains("InteractionResultHolder.consume(stack)"),
                "the deed's placement branch no longer returns consume(); if it returns success() "
                        + "again, right-click rotates the ghost");
        assertFalse(source.contains("InteractionResultHolder.success("),
                "the deed returns success() somewhere. Every success() swings the arm, and the "
                        + "swing is the rotation control.");
    }

    /**
     * A deed cannot break a block.
     *
     * <p>Rotation is a left-click, and a left-click at a block is also an attack on it. That was
     * harmless while nothing a player held could break anything in Adventure mode — but an owner
     * inside their own house is now lent exactly that, so without this a player rotating a ghost
     * indoors would knock their own walls down while doing it.
     */
    @Test
    void theDeedRefusesToAttackBlocksSoRotatingIndoorsBreaksNothing() throws IOException {
        String source = read(DEED);
        assertTrue(source.contains("public boolean canAttackBlock("),
                "the deed no longer overrides canAttackBlock, so left-clicking to rotate inside a "
                        + "house you own would start breaking it");
        int override = source.indexOf("public boolean canAttackBlock(");
        String body = source.substring(override, source.indexOf('}', override));
        assertTrue(body.contains("return false"),
                "canAttackBlock must refuse unconditionally: " + body);
    }

    // ------------------------------------------------------------------
    // Rotation has exactly one writer
    // ------------------------------------------------------------------

    /**
     * Nothing but the swing may turn the ghost.
     *
     * <p>A second pathway is how this defect happened in the first place — not by anyone writing a
     * second rotate call, but by a second thing reaching the one that existed. Counting the callers
     * is the cheapest guard against either.
     */
    @Test
    void rotationHasExactlyOneCallerAndItIsTheSwingHandler() throws IOException {
        Pattern call = Pattern.compile("HouseRotationData\\s*\\.\\s*rotateClockwise\\s*\\(");
        int callers = 0;
        StringBuilder where = new StringBuilder();

        try (Stream<Path> sources = Files.walk(PROJECT.resolve("src/main/java"))) {
            for (Path file : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                if (file.endsWith("HouseRotationData.java")) continue;  // the implementation itself
                Matcher matcher = call.matcher(read(file));
                while (matcher.find()) {
                    callers++;
                    where.append("\n  ").append(PROJECT.relativize(file));
                }
            }
        }

        assertEquals(1, callers,
                "rotation must have exactly one caller, and it must be onEntitySwing. Found:" + where);

        String deed = read(DEED);
        int swing = deed.indexOf("public boolean onEntitySwing(");
        assertTrue(swing >= 0, "the swing handler is gone; what rotates the ghost now?");
        assertTrue(deed.indexOf("HouseRotationData.rotateClockwise(") > swing,
                "the one rotate call is no longer inside onEntitySwing");
    }

    // ------------------------------------------------------------------
    // The state machine
    // ------------------------------------------------------------------

    @Test
    void oneSwingIsExactlyOneQuarterTurnAndFourSwingsComeBackAround() {
        UUID player = UUID.randomUUID();
        assertEquals(0, HouseRotationData.getRotation(player),
                "a player who has not swung yet is facing the default");

        assertEquals(90, rotateAndRead(player));
        assertEquals(180, rotateAndRead(player));
        assertEquals(270, rotateAndRead(player));
        assertEquals(0, rotateAndRead(player), "the fourth swing wraps rather than reaching 360");
        assertEquals(90, rotateAndRead(player), "and the cycle continues");
    }

    /** Every value the cycle can hold is one a structure template can actually be placed at. */
    @Test
    void everyRotationInTheCycleIsAPlaceableOne() {
        int degrees = 0;
        for (int step = 0; step < 8; step++) {
            degrees = HouseRotationData.nextRotation(degrees);
            assertTrue(List.of(0, 90, 180, 270).contains(degrees),
                    "rotation reached " + degrees + ", which StructureUtils.getRotation cannot honour");
        }
    }

    @Test
    void oneSwingerDoesNotTurnAnotherPlayersGhost() {
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();

        HouseRotationData.rotateClockwise(alice);
        HouseRotationData.rotateClockwise(alice);

        assertEquals(180, HouseRotationData.getRotation(alice));
        assertEquals(0, HouseRotationData.getRotation(bob), "Bob's ghost turned when Alice swung");
    }

    /**
     * The rotation a player selected is the one placement is asked for.
     *
     * <p>The payload carries {@code getRotation(player)} verbatim, so what the ghost showed and what
     * the server builds cannot drift apart. {@code HousePlacedRotationGameTests} takes it from there
     * and proves the built house is actually turned that way.
     */
    @Test
    void thePlacementRequestCarriesTheRotationTheGhostIsShowing() throws IOException {
        String deed = read(DEED);
        assertTrue(deed.contains("HouseRotationData.getRotation(player)"),
                "the deed no longer reads the selected rotation when building the placement payload");
        int read = deed.indexOf("HouseRotationData.getRotation(player)");
        int payload = deed.indexOf("new HousePlacementPayload(");
        assertTrue(read >= 0 && payload > read,
                "the rotation is read after the payload is built, so the payload cannot be carrying it");
    }

    private static int rotateAndRead(UUID player) {
        HouseRotationData.rotateClockwise(player);
        return HouseRotationData.getRotation(player);
    }

    private static String read(Path path) throws IOException {
        return Files.readString(path, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }
}
