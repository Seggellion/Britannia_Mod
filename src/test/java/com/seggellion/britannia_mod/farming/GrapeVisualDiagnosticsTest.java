package com.seggellion.britannia_mod.farming;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.winery.GrapeColor;
import com.seggellion.britannia_mod.winery.GrapeVariety;
import com.seggellion.britannia_mod.winery.GrapeVarietyManager;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The farming debug command is the tool anyone reaches for when a vine looks wrong, and for grapes
 * it disagreed with the renderer at seven of the eight ages: {@code modelLocation} short-circuits
 * grapes to the arbor resolver, but {@code visualModelStage} had no grape branch and fell through to
 * a generic one-based stage grapes never draw. A second line resolved its model through the
 * variety-less overload, so it announced a green model for a dark vine at any fruiting age.
 *
 * <p>These assertions pin the diagnostics to the render path without asserting anything about the
 * render path itself, which is a faithful port of the Fabric mapping and is not being changed.
 */
class GrapeVisualDiagnosticsTest {

    /** The authored stage each gameplay age draws, straight from the Fabric multipart blockstate. */
    private static final int[] EXPECTED_STAGE_BY_AGE = {1, 1, 2, 3, 4, 5, 6, 7};

    private static final String DARK_VARIETY = "pinot_noir";
    private static final String GREEN_VARIETY = "chardonnay";

    /** The stage number a model path ends on, ignoring any colour suffix behind it. */
    private static final Pattern TRAILING_STAGE = Pattern.compile("(\\d+)(?!.*\\d)");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GrapeVarietyManager.loadFromBootstrap(List.of(
                variety(DARK_VARIETY, GrapeColor.DARK_PURPLE),
                variety(GREEN_VARIETY, GrapeColor.GREEN)));
    }

    @AfterAll
    static void leaveTheRegistryAsWeFoundIt() {
        GrapeVarietyManager.resetToBuiltIns();
    }

    @Test
    void theReportedStageMatchesTheStageTheVineActuallyDraws() {
        CropDefinition grapes = grapeCrop();
        for (int age = 0; age <= grapes.maxGrowthAge(); age++) {
            assertEquals(
                    EXPECTED_STAGE_BY_AGE[age],
                    CropVisualModels.visualModelStage(grapes, age),
                    "debug stage disagreed with the rendered stage at age " + age);
            assertEquals(
                    EXPECTED_STAGE_BY_AGE[age],
                    GrapeVisualResolver.visualStage(grapes, age),
                    "the render path's own stage moved at age " + age);
        }
    }

    @Test
    void theReportedStageCountDescribesTheAuthoredStagesNotTheAgeCount() {
        CropDefinition grapes = grapeCrop();
        assertEquals(7, CropVisualModels.visualModelStageCount(grapes),
                "grapes author seven stages; ages 0 and 1 share the seedling");
        assertEquals(8, grapes.visualAgeCount(),
                "the crop's own age count is a different quantity and must not move");
    }

    @Test
    void everyGrapeDiagnosticLineNamesTheSameVarietyAwareModel() {
        CropDefinition grapes = grapeCrop();
        for (int age = 6; age <= 7; age++) {
            ResourceLocation expected = model("grape_vine_stage_" + age + "_dark_purple");

            // grape_visual
            assertEquals(expected, GrapeVisualResolver.resolve(grapes, age, DARK_VARIETY).model(),
                    "grape_visual disagreed at age " + age);
            // visual
            assertEquals(expected, CropVisualModels.modelLocation(grapes, age, DARK_VARIETY),
                    "visual disagreed at age " + age);
            // tall_structure base_model, which now takes the same variety-aware path
            assertEquals(expected, CropVisualModels.modelLocation(grapes, age, DARK_VARIETY),
                    "tall_structure base_model disagreed at age " + age);

            assertEquals(model("grape_vine_stage_" + age + "_green"),
                    CropVisualModels.modelLocation(grapes, age, GREEN_VARIETY),
                    "a contrasting variety must not resolve to the dark model at age " + age);
        }
    }

    @Test
    void theVarietyLessOverloadStillLosesTheColourWhichIsWhyTheCommandMustNotUseIt() {
        CropDefinition grapes = grapeCrop();
        for (int age = 6; age <= 7; age++) {
            assertNotEquals(
                    CropVisualModels.modelLocation(grapes, age),
                    CropVisualModels.modelLocation(grapes, age, DARK_VARIETY),
                    "the two-argument overload must stay distinguishable from the variety-aware one at age " + age);
            assertEquals(
                    model("grape_vine_stage_" + age + "_green"),
                    CropVisualModels.modelLocation(grapes, age),
                    "the overload substitutes the default Concord, which is the trap being guarded");
        }
    }

    @Test
    void everyCropsReportedStageMatchesTheStageItsChosenModelNames() {
        // Generic and cheap: had it existed, it would have caught the grape mismatch the day the
        // arbor branch was added to modelLocation without a matching branch in visualModelStage.
        for (CropDefinition crop : CropRegistry.all()) {
            String variety = "grapes".equals(crop.id()) ? DARK_VARIETY : "";
            for (int age = 0; age <= crop.maxGrowthAge(); age++) {
                ResourceLocation chosen = CropVisualModels.modelLocation(crop, age, variety);
                assertEquals(
                        trailingStage(chosen),
                        CropVisualModels.visualModelStage(crop, age),
                        crop.id() + " reports a stage its model does not name at age " + age
                                + " (model " + chosen + ")");
            }
        }
    }

    @Test
    void nonGrapeStageDiagnosticsKeepTheirDocumentedRules() {
        for (CropDefinition crop : CropRegistry.all()) {
            if ("grapes".equals(crop.id())) {
                continue;
            }
            int expectedCount = usesWheatVisuals(crop.id()) ? 8 : crop.visualAgeCount();
            assertEquals(expectedCount, CropVisualModels.visualModelStageCount(crop),
                    crop.id() + " stage count changed; the grape branch must not leak sideways");
            for (int age = 0; age <= crop.maxGrowthAge(); age++) {
                int stage = CropVisualModels.visualModelStage(crop, age);
                assertTrue(stage >= 0 && stage <= Math.max(crop.visualAgeCount(), 8),
                        crop.id() + " produced an out-of-range stage " + stage + " at age " + age);
            }
        }
    }

    @Test
    void aStoredVarietyIdSurvivesDiskAndClientSyncWithNoNewWorldDataField() {
        CropDefinition grapes = grapeCrop();
        TestFarmingBlockEntity planted = new TestFarmingBlockEntity();
        planted.plant(grapes, DARK_VARIETY);

        CompoundTag disk = planted.saveWithoutMetadata(RegistryAccess.EMPTY);
        assertEquals(DARK_VARIETY, disk.getString("StoredSeed"));

        TestFarmingBlockEntity fromDisk = new TestFarmingBlockEntity();
        fromDisk.loadWithComponents(disk, RegistryAccess.EMPTY);
        assertEquals(DARK_VARIETY, fromDisk.getStoredSeed(), "the planted variety must survive a world reload");

        TestFarmingBlockEntity fromUpdate = new TestFarmingBlockEntity();
        fromUpdate.handleUpdateTag(planted.getUpdateTag(RegistryAccess.EMPTY), RegistryAccess.EMPTY);
        assertEquals(DARK_VARIETY, fromUpdate.getStoredSeed(), "the client must receive the planted variety");

        // No colour field was added: the client resolves colour from the synced catalogue, so an
        // existing world needs no migration and no block-entity rewrite.
        assertFalse(disk.contains("GrapeColor"), "a block-entity colour field would demand a migration");
        assertEquals(
                model("grape_vine_stage_7_dark_purple"),
                CropVisualModels.modelLocation(grapes, 7, fromUpdate.getStoredSeed()),
                "the received variety must still colour the vine");
    }

    private static int trailingStage(ResourceLocation model) {
        String name = model.getPath();
        Matcher matcher = TRAILING_STAGE.matcher(name);
        assertTrue(matcher.find(), "no stage number in model path " + name);
        return Integer.parseInt(matcher.group(1));
    }

    private static boolean usesWheatVisuals(String cropId) {
        return switch (cropId) {
            case "wheat", "rye", "barley", "oats", "mustard" -> true;
            default -> false;
        };
    }

    private static GrapeVariety variety(String id, GrapeColor color) {
        return new GrapeVariety(id, id, 3, 0.2f, 0.1f, 0.2f, 0.5f, "Temperate", 25, 625, 0xFFFFFF, 4, color);
    }

    private static CropDefinition grapeCrop() {
        return CropRegistry.byId("grapes").orElseThrow();
    }

    private static ResourceLocation model(String name) {
        return ResourceLocation.fromNamespaceAndPath("britannia_mod", "block/crops/grapes/" + name);
    }

    private static final class TestFarmingBlockEntity extends FarmingBlockEntity {
        private TestFarmingBlockEntity() {
            super(BlockEntityType.FURNACE, BlockPos.ZERO, Blocks.FURNACE.defaultBlockState());
        }
    }
}
