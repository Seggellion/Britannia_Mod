package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyActor;
import com.seggellion.britannia_mod.grabbyhands.testsupport.FakeGrabbyWorld;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbySources;
import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbyTagBinding;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * M10: protection is scoped to movement and destruction, and never to ordinary use.
 *
 * <p>The two halves of this milestone pull in opposite directions and both matter. Grabby Hands must
 * honour the world's existing protections — houses, security, staff overrides — while never inventing
 * a new reason a player cannot simply <em>use</em> something somebody else put down.
 */
class GrabbyProtectionAuditTest {
    private static final BlockPos POS = new BlockPos(8, 64, 8);
    private static final BlockPos SUPPORT = new BlockPos(8, 63, 8);
    private static final UUID PLACER = UUID.fromString("aaaa0000-bbbb-cccc-dddd-eeeeffff0000");

    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MOD_ROOT = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");

    private static BlockState enrolled;

    private List<String> audio;
    private FakeGrabbyWorld world;
    private FakeGrabbyActor actor;

    @BeforeAll
    static void bootstrapAndEnrol() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrabbyTagBinding.enrol(Blocks.OAK_STAIRS);
        enrolled = Blocks.OAK_STAIRS.defaultBlockState();
    }

    @AfterAll
    static void clearEnrollment() {
        GrabbyTagBinding.clear();
    }

    @BeforeEach
    void setUp() {
        GrabbyMutationGuard.reset();
        audio = new ArrayList<>();
        world = new FakeGrabbyWorld(audio);
        actor = new FakeGrabbyActor(audio);
    }

    private void placePlayerObject() {
        world.place(POS, enrolled, GrabbyInstanceState.playerPlaced(PLACER, 4L),
                new ItemStack(Items.OAK_STAIRS));
    }

    private static BlockHitResult hit() {
        return new BlockHitResult(Vec3.atCenterOf(SUPPORT).add(0, 0.5, 0), Direction.UP, SUPPORT, false);
    }

    private static ItemStack axe() {
        return new ItemStack(Items.IRON_AXE);
    }

    // ------------------------------------------------------------------
    // Houses
    // ------------------------------------------------------------------

    @Test
    void aPlayerMayFurnishGroundThatIsNotSomebodyElsesHouse() {
        actor.placing(world, POS, enrolled, new ItemStack(Items.OAK_STAIRS));

        assertEquals(GrabbyPlacementOutcome.SUCCESS,
                GrabbyPlacementTransaction.execute(
                        world, actor, new ItemStack(Items.OAK_STAIRS), hit()).outcome());
    }

    @Test
    void everyOperationIsRefusedInsideSomebodyElsesHouse() {
        // Placement, pickup and destruction must all consult the same rule; a gap in any one of them
        // would be a way around the other two.
        placePlayerObject();
        FakeGrabbyActor intruder = new FakeGrabbyActor(audio).insideSomebodyElsesHouse();

        assertEquals(GrabbyPickupOutcome.DENIED_BY_POLICY,
                GrabbyPickupTransaction.execute(world, intruder, POS).outcome());
        assertEquals(GrabbyDestructionOutcome.DENIED_BY_POLICY,
                GrabbyDestructionTransaction.execute(world, intruder, axe(), POS).outcome());

        FakeGrabbyActor placer = new FakeGrabbyActor(audio)
                .insideSomebodyElsesHouse().placing(world, POS.above(), enrolled, new ItemStack(Items.OAK_STAIRS));
        assertEquals(GrabbyPlacementOutcome.DENIED_BY_POLICY,
                GrabbyPlacementTransaction.execute(
                        world, placer, new ItemStack(Items.OAK_STAIRS), hit()).outcome());
    }

    @Test
    void houseOwnershipIsDelegatedRatherThanDuplicated() throws IOException {
        String policy = Files.readString(
                MOD_ROOT.resolve("grabbyhands/GrabbyPolicy.java"), StandardCharsets.UTF_8);
        assertTrue(policy.contains("StructureRegionManager.getStructuresInChunk"),
                "house ownership must come from the existing registry, not a second copy");
        assertTrue(policy.contains("record.getOwnerUuid()"),
                "and from the record's own owner field");
    }

    // ------------------------------------------------------------------
    // Staff override
    // ------------------------------------------------------------------

    @Test
    void staffOverrideMatchesTheProjectsExistingDefinition() {
        // Creative or operator level two, the same rule FlowerProtectionService uses. Grabby Hands
        // does not invent a third notion of "administrator".
        assertTrue(GrabbyPolicy.isAdministrator(true, 0));
        assertTrue(GrabbyPolicy.isAdministrator(false, 2));
        assertFalse(GrabbyPolicy.isAdministrator(false, 1));
        assertEquals(2, GrabbyPolicy.ADMIN_PERMISSION_LEVEL);
    }

    @Test
    void staffMayActInsideSomebodyElsesHouse() {
        placePlayerObject();
        FakeGrabbyActor staff = new FakeGrabbyActor(audio).insideSomebodyElsesHouse().operator();

        assertEquals(GrabbyPickupOutcome.SUCCESS,
                GrabbyPickupTransaction.execute(world, staff, POS).outcome());
    }

    @Test
    void staffMayRearrangeSceneryThatOrdinaryPlayersCannotTouch() {
        world.placeRaw(POS, enrolled, new ItemStack(Items.OAK_STAIRS));

        assertEquals(GrabbyPickupOutcome.NOT_GRABBY_MANAGED,
                GrabbyPickupTransaction.execute(world, actor, POS).outcome());
        assertTrue(GrabbyPolicy.mayMutate(false, true, 0, false, GrabbyMutationReason.PICKUP),
                "staff may move world decoration; ordinary players may not");
    }

    // ------------------------------------------------------------------
    // Security
    // ------------------------------------------------------------------

    @Test
    void aLockedContainerCannotBeChoppedOpen() {
        // Otherwise chopping would spill the contents with no key, no lockpicks and no skill check,
        // and the lock system would be decorative.
        placePlayerObject();
        world.secured();

        GrabbyDestructionResult result = GrabbyDestructionTransaction.execute(world, actor, axe(), POS);

        assertEquals(GrabbyDestructionOutcome.SECURED, result.outcome());
        assertEquals(0, result.objectsDestroyed());
        assertTrue(world.occupied(POS));
        assertTrue(audio.isEmpty());
    }

    @Test
    void aSecuredRefusalFallsThroughSoLockpickingStillGetsItsTurn() {
        // Consuming the interaction here would mean an axe in the hand silently disables lockpicking.
        assertFalse(GrabbyDestructionOutcome.SECURED.handled(),
                "a secured refusal must not consume the interaction");
    }

    @Test
    void aLockedContainerMayStillBeCarriedAway() {
        // Owner decision: the lock protects the contents, not the box. A thief walks off with a chest
        // they still cannot open.
        placePlayerObject();
        world.secured();

        assertEquals(GrabbyPickupOutcome.SUCCESS,
                GrabbyPickupTransaction.execute(world, actor, POS).outcome());
    }

    @Test
    void anUnlockedContainerIsOrdinaryFurnitureAgain() {
        placePlayerObject();

        assertEquals(GrabbyDestructionOutcome.SUCCESS,
                GrabbyDestructionTransaction.execute(world, actor, axe(), POS).outcome());
    }

    @Test
    void theChestReportsItsSecurityFromItsOwnLockField() throws IOException {
        String chest = Files.readString(
                MOD_ROOT.resolve("block/entity/BritanniaChestBlockEntity.java"), StandardCharsets.UTF_8);
        assertTrue(chest.contains("public boolean securedAgainstDestruction()"));
        assertTrue(chest.contains("return this.locked;"),
                "security must track the real lock, not a Grabby-side copy of it");
    }

    // ------------------------------------------------------------------
    // Public usability: the absences that matter
    // ------------------------------------------------------------------

    @Test
    void anyPlayerMayPickUpAnyPlayerPlacedObject() {
        // Provenance marks "Grabby-managed", never "yours".
        placePlayerObject();
        FakeGrabbyActor stranger = new FakeGrabbyActor(audio);

        assertEquals(GrabbyPickupOutcome.SUCCESS,
                GrabbyPickupTransaction.execute(world, stranger, POS).outcome());
        assertEquals(1, stranger.inventory().size());
    }

    @Test
    void thePolicyCoreTakesNoPlacerArgumentAtAll() {
        // The strongest form of the guarantee: there is no parameter through which ownership could
        // leak into the decision.
        for (Method method : GrabbyPolicy.class.getDeclaredMethods()) {
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(parameter == UUID.class,
                        "GrabbyPolicy." + method.getName() + " accepts a UUID; movement must not"
                                + " depend on who placed the object");
            }
        }
    }

    @Test
    void noBlockInTheEnrolledSetConsultsGrabbyOwnershipInItsUsePath() throws IOException {
        // Sitting, opening and inspecting belong to the blocks. If any of them started asking Grabby
        // Hands who placed the object, placement would quietly become a private claim on it.
        for (String source : List.of(
                "block/ChairBlock.java",
                "block/RotatableFurnitureBlock.java",
                "block/WineBottleBlock.java",
                "block/BritanniaChestBlock.java",
                "block/ArmoireBlock.java",
                "block/CandelabraBlock.java")) {
            String text = Files.readString(MOD_ROOT.resolve(source), StandardCharsets.UTF_8);
            for (String ownership : List.of("placerUuid", "GrabbyPolicy", "grabbyManaged", "GrabbyInstanceState")) {
                assertFalse(text.contains(ownership),
                        source + " consults " + ownership + " in its own behaviour; use must stay public");
            }
        }
    }

    @Test
    void grabbyHandsExposesNoUsePermissionApiAnywhere() throws IOException {
        // Not just GrabbyPolicy: nothing in the package may grow a use predicate.
        try (var files = Files.walk(MOD_ROOT.resolve("grabbyhands"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                // Comments are stripped: GrabbyPolicy's own javadoc names these methods precisely to
                // record that they must never exist, and saying so is not the same as declaring one.
                String text = GrabbySources.stripComments(
                        Files.readString(file, StandardCharsets.UTF_8)).toLowerCase(Locale.ROOT);
                for (String banned : List.of("mayuse", "mayinteract", "mayopen", "maysit", "canuse")) {
                    if (text.contains(banned)) {
                        fail(file.getFileName() + " looks like it declares " + banned
                                + "; Grabby Hands decides movement only");
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Deliberate non-restrictions, recorded so they stay deliberate
    // ------------------------------------------------------------------

    @Test
    void cityBoundsDoNotRestrictPlacement() throws IOException {
        // Owner decision: cities are fair game for littering. Recorded as a test so that if somebody
        // later adds a city check, they do it on purpose rather than by drifting into it.
        String policy = Files.readString(
                MOD_ROOT.resolve("grabbyhands/GrabbyPolicy.java"), StandardCharsets.UTF_8);
        assertFalse(policy.contains("CityRegistry"),
                "placement inside cities is deliberately unrestricted; adding a city check is a"
                        + " product decision, not a bug fix");
    }

    @Test
    void houseCoOwnersAreNotConsultedBecauseNoSuchSystemExists() throws IOException {
        // HouseLotBlockEntity.getAccessList() looks like a co-owner list but is only ever initialised
        // empty in StructurePlacer and read by nothing. Delegating to it would be delegating to a stub.
        String placer = Files.readString(
                MOD_ROOT.resolve("structure/StructurePlacer.java"), StandardCharsets.UTF_8);
        assertTrue(placer.contains("setAccessList(new ArrayList<>())"),
                "if the access list has become real, Grabby Hands should start honouring it");
    }
}
