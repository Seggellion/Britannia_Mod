package com.seggellion.britannia_mod.grabbyhands;

import com.seggellion.britannia_mod.grabbyhands.testsupport.GrabbySources;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that sitting, stacking, facing and specialized block state are preserved <em>by
 * construction</em> rather than by reimplementation.
 *
 * <p>The behavioural claims of M3 — a moved chair is still sittable, a second player can still sit on
 * it, compatible furniture still stacks, facing survives — all reduce to one mechanical fact: Grabby
 * Hands places through {@code BlockItem.place} and never writes a block state itself. If that stays
 * true, the behaviour cannot regress, because none of it is Grabby Hands' code. These tests keep it
 * true.
 *
 * <p>They do not replace live in-game validation, which the playbook requires separately.
 */
class GrabbyPreservationContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Path MOD_ROOT = PROJECT.resolve("src/main/java/com/seggellion/britannia_mod");
    private static final Path GRABBY_ROOT = MOD_ROOT.resolve("grabbyhands");

    // ------------------------------------------------------------------
    // The mechanism that preserves everything else
    // ------------------------------------------------------------------

    @Test
    void placementGoesThroughTheBlocksOwnNativePath() throws IOException {
        String actor = grabbySource("GrabbyActor.java");
        assertTrue(actor.contains("BlockPlaceContext"),
                "placement must build a real BlockPlaceContext so replaceable/relative targeting works");
        assertTrue(actor.contains("blockItem.place("),
                "placement must call BlockItem.place, which is what runs getStateForPlacement,"
                        + " canSurvive, isUnobstructed, setPlacedBy, the place sound and item consumption");
    }

    @Test
    void noGrabbyClassEverWritesABlockStateItself() throws IOException {
        // A setBlock shortcut would silently drop facing, canSurvive, collision checks, setPlacedBy
        // (and with it a wine bottle's WineData), the native place sound, and item consumption.
        for (String forbidden : List.of(
                "setBlock(", "setBlockAndUpdate(", "destroyBlock(", "dropResources(", "Containers.dropContents")) {
            for (Path file : grabbySources()) {
                assertFalse(GrabbySources.stripComments(
                                Files.readString(file, StandardCharsets.UTF_8)).contains(forbidden),
                        file.getFileName() + " calls " + forbidden
                                + "; placement and removal must stay on the native paths");
            }
        }
    }

    @Test
    void grabbyHandsContainsNoSecondSeatingImplementation() throws IOException {
        for (String forbidden : List.of("LivingSeatEntity", "startRiding", "sittingHeight", "SEAT_ENTITY")) {
            for (Path file : grabbySources()) {
                assertFalse(GrabbySources.stripComments(
                                Files.readString(file, StandardCharsets.UTF_8)).contains(forbidden),
                        file.getFileName() + " references " + forbidden
                                + "; seating belongs to ChairBlock and must not be duplicated");
            }
        }
    }

    @Test
    void removalUsesTheDropFreePathSoPickupCannotAlsoDropLoot() throws IOException {
        String world = grabbySource("GrabbyWorld.java");
        assertTrue(world.contains("removeBlock("),
                "removal must use removeBlock, not destroyBlock, or pickup would also drop loot");
    }

    // ------------------------------------------------------------------
    // The existing furniture behaviour these tests are protecting
    // ------------------------------------------------------------------

    @Test
    void chairSeatingIsStillImplementedByChairBlockAndIsStillOpenToEveryone() throws IOException {
        String chair = modSource("block/ChairBlock.java");
        assertTrue(chair.contains("useWithoutItem"), "seating still hangs off the normal use callback");
        assertTrue(chair.contains("EntityRegistry.SEAT_ENTITY"));
        assertTrue(chair.contains("player.startRiding(seat)"));

        // Public usability: no owner, placer or permission check may appear in the seating path.
        for (String ownership : List.of("getUUID()", "placerUuid", "grabbyState", "GrabbyPolicy")) {
            assertFalse(chair.contains(ownership),
                    "ChairBlock must not consult ownership; anyone may sit on any chair");
        }
    }

    @Test
    void furnitureStackingStillRestsOnFullCubeSupportRatherThanACustomRule() throws IOException {
        // Chairs and tables stack because they never override getShape/getCollisionShape and are
        // therefore full-cube supports. If one gains a custom shape, stacking silently changes and
        // this test is the early warning.
        for (String furniture : List.of("block/ChairBlock.java", "block/RotatableFurnitureBlock.java")) {
            String source = GrabbySources.stripComments(modSource(furniture));
            assertFalse(source.contains("getShape("), furniture + " gained a custom voxel shape");
            assertFalse(source.contains("getCollisionShape("), furniture + " gained a custom collision shape");
        }
    }

    @Test
    void theWineBottleKeepsItsOwnSupportRuleAndItsOwnStateTransfer() throws IOException {
        String bottle = modSource("block/WineBottleBlock.java");
        assertTrue(bottle.contains("canSupportCenter"),
                "the bottle's sturdy-support rule is what stops it floating; it must stay its own");
        assertTrue(bottle.contains("setPlacedBy"), "item to block WineData transfer must remain");
        assertTrue(bottle.contains("getCloneItemStack"), "block to item WineData transfer must remain");
    }

    @Test
    void facingIsRestoredByTheBlockNotByGrabbyHands() throws IOException {
        for (String furniture : List.of(
                "block/ChairBlock.java", "block/RotatableFurnitureBlock.java", "block/WineBottleBlock.java")) {
            assertTrue(modSource(furniture).contains("getStateForPlacement"),
                    furniture + " must keep deciding its own placement facing");
        }
        // Scoped to the transport layer. The host block in grabbyhands/block is a real block and
        // declares its own FACING like any other; what must never happen is the transaction or world
        // seam second-guessing a block's orientation.
        for (Path file : transportSources()) {
            assertFalse(GrabbySources.stripComments(
                            Files.readString(file, StandardCharsets.UTF_8)).contains("FACING"),
                    file.getFileName() + " must not reason about facing itself");
        }
    }

    // ------------------------------------------------------------------
    // Containers
    // ------------------------------------------------------------------

    @Test
    void theContainerSpillHazardIsStillPresentAndStillAnswered() throws IOException {
        // BritanniaChestBlock.onRemove and ArmoireBlock.onRemove drop their whole inventory on any
        // block change. That is correct when something else destroys the container, and it is exactly
        // why a Grabby pickup has to make the container give its contents up first.
        for (String container : List.of("block/BritanniaChestBlock.java", "block/ArmoireBlock.java")) {
            assertTrue(modSource(container).contains("Containers.dropContents"),
                    container + ": the hazard the detach step exists for has changed shape; re-check it");
        }
        for (String entity : List.of("block/entity/BritanniaChestBlockEntity.java",
                "block/entity/ArmoireBlockEntity.java")) {
            String source = GrabbySources.stripComments(modSource(entity));
            assertTrue(source.contains("BlockItem.setBlockEntityData"),
                    entity + " must carry its contents in the portable item");
            // Scoped to the method body on purpose. Asserting that clearContent() appears anywhere in
            // the file passes trivially, because Container declares clearContent() a few lines away -
            // a mutation that stopped detaching went undetected until this was tightened.
            String detachBody = GrabbySources.methodBody(source, "public boolean detachForTransport()");
            assertTrue(detachBody.contains("clearContent()"),
                    entity + ": detachForTransport has to actually empty the container");
            // Contents alone would drop the lock id, the locked flag and the ChestKeySeeded marker,
            // turning every place-and-pickup cycle into a key printer.
            String writeBody = GrabbySources.methodBody(source,
                    "public void writePortableState(ItemStack portable, HolderLookup.Provider registries)");
            assertTrue(writeBody.contains("this.saveAdditional("),
                    entity + ": transporting only the contents would drop the lock state");
        }
    }

    @Test
    void theDeliberateContentExclusionsStand() throws IOException {
        String movableTag = Files.readString(
                PROJECT.resolve("src/main/resources/data/britannia_mod/tags/block/grabby_movable.json"),
                StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);

        // Carrying somebody's locked chest away is allowed by owner decision; the lock must survive the
        // journey, and chopping it open must not be possible.
        assertTrue(movableTag.contains("britannia_lockable_chest"));
        // The trash barrel destroys what is put in it; making it portable would be a griefing tool.
        assertFalse(movableTag.contains("trash_barrel"), "the trash barrel voids items");
        // Deed-placed house fixtures are permanently out, not merely deferred.
        for (String fixture : List.of("double_bed", "chandelier")) {
            assertFalse(movableTag.contains(fixture),
                    fixture + " arrives with a deed and is house content, not carried furniture");
        }
    }

    // ------------------------------------------------------------------

    private static List<Path> grabbySources() throws IOException {
        try (Stream<Path> files = Files.walk(GRABBY_ROOT)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    /** Grabby sources excluding the block and block-entity implementations it owns. */
    private static List<Path> transportSources() throws IOException {
        return grabbySources().stream()
                .filter(path -> {
                    String normalised = path.toString().replace('\\', '/');
                    return !normalised.contains("/grabbyhands/block/")
                            && !normalised.contains("/grabbyhands/blockentity/");
                })
                .toList();
    }

    private static String grabbySource(String fileName) throws IOException {
        return GrabbySources.stripComments(
                Files.readString(GRABBY_ROOT.resolve(fileName), StandardCharsets.UTF_8));
    }

    private static String modSource(String relative) throws IOException {
        return Files.readString(MOD_ROOT.resolve(relative), StandardCharsets.UTF_8);
    }
}
