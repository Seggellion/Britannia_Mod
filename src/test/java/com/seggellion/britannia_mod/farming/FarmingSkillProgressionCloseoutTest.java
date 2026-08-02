package com.seggellion.britannia_mod.farming;

import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Final cross-system reconciliation for the approved Farming progression. */
class FarmingSkillProgressionCloseoutTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));
    private static final Component EXACT_NAME = Component.literal("Exact Species Name");
    private static final List<String> NATIVE_EXCLUSIONS = List.of(
            "vanilla_potato", "wheat", "brown_mushroom", "red_mushroom",
            "vanilla_pumpkin", "vanilla_melon"
    );

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void catalogProposalRuntimeCultivationAndPresentationPoliciesReconcile() throws IOException {
        Map<String, ApprovedRow> approved = approvedRows();
        Map<String, String> catalog = catalogPlantingItems();
        List<FarmingSkillRequirementResolver.ResolvedRequirement> runtime = runtimeRequirements();

        assertEquals(74, approved.size());
        assertEquals(74, catalog.size());
        assertEquals(74, runtime.size());
        assertEquals(74, approved.values().stream().map(ApprovedRow::plantingItemId).distinct().count());
        assertEquals(74, catalog.values().stream().distinct().count());

        int cultivationPolicies = 0;
        int presentationPolicies = 0;
        int nativeExclusions = 0;
        for (FarmingSkillRequirementResolver.ResolvedRequirement resolved : runtime) {
            ApprovedRow row = approved.get(resolved.speciesId());
            assertTrue(row != null, "Missing approved row: " + resolved.speciesId());
            assertEquals(row.plantingItemId(), catalog.get(resolved.speciesId()), resolved.speciesId());
            assertEquals(row.minimumFarmingSkill(), resolved.minimumFarmingSkill(), resolved.speciesId());
            assertTrue(resolved.minimumFarmingSkill() >= 0.0F && resolved.minimumFarmingSkill() <= 100.0F,
                    resolved.speciesId());

            Item item = policyItem(resolved.speciesId());
            FarmingPlantingMaterialCategory category = FarmingPlantingMaterialCategory.resolve(resolved, item);
            if (NATIVE_EXCLUSIONS.contains(resolved.speciesId())) {
                nativeExclusions++;
                assertEquals(FarmingPlantingMaterialCategory.NATIVE_VANILLA_OUTSIDE_SCOPE,
                        category, resolved.speciesId());
            } else {
                assertTrue(category.hasUnidentifiedPresentation(), resolved.speciesId());
            }
            presentationPolicies++;

            FarmingCultivationGate.Evaluation exact = cultivation(resolved, resolved.minimumFarmingSkill());
            assertEquals("grapes".equals(resolved.speciesId())
                            ? FarmingCultivationGate.ResultType.NOT_APPLICABLE
                            : FarmingCultivationGate.ResultType.ELIGIBLE,
                    exact.type(), resolved.speciesId());
            cultivationPolicies++;
        }

        assertEquals(74, cultivationPolicies);
        assertEquals(74, presentationPolicies);
        assertEquals(6, nativeExclusions);
    }

    @Test
    void identificationAndCultivationUseTheSameInclusiveThresholdAndStatePolicy() {
        int unifiedPolicies = 0;
        for (FarmingSkillRequirementResolver.ResolvedRequirement resolved : runtimeRequirements()) {
            ItemStack stack = new ItemStack(policyItem(resolved.speciesId()), 1);
            ItemStack before = stack.copy();
            float required = resolved.minimumFarmingSkill();

            if (NATIVE_EXCLUSIONS.contains(resolved.speciesId())) {
                var nativePresentation = presentation(resolved, stack, required - 0.01F, false,
                        SkillManager.SkillDataState.AVAILABLE);
                assertFalse(nativePresentation.applicable(), resolved.speciesId());
                assertTrue(nativePresentation.identified(), resolved.speciesId());
                assertEquals(EXACT_NAME, nativePresentation.displayName(), resolved.speciesId());
                continue;
            }

            if ("grapes".equals(resolved.speciesId())) {
                assertFalse(presentation(resolved, stack, required - 0.01F, false,
                        SkillManager.SkillDataState.AVAILABLE).identified());
                assertTrue(presentation(resolved, stack, required, false,
                        SkillManager.SkillDataState.AVAILABLE).identified());
                assertEquals(FarmingCultivationGate.ResultType.NOT_APPLICABLE,
                        cultivation(resolved, required - 1.0F).type());
                assertEquals(FarmingCultivationGate.ResultType.NOT_APPLICABLE,
                        cultivation(resolved, required + 1.0F).type());
                continue;
            }

            for (float delta : new float[]{1.0F, 0.01F}) {
                assertFalse(presentation(resolved, stack, required - delta, false,
                        SkillManager.SkillDataState.AVAILABLE).identified(), resolved.speciesId());
                assertEquals(FarmingCultivationGate.ResultType.INSUFFICIENT_SKILL,
                        cultivation(resolved, required - delta).type(), resolved.speciesId());
                assertTrue(presentation(resolved, stack, required + delta, false,
                        SkillManager.SkillDataState.AVAILABLE).identified(), resolved.speciesId());
                assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE,
                        cultivation(resolved, required + delta).type(), resolved.speciesId());
            }

            assertTrue(presentation(resolved, stack, required, false,
                    SkillManager.SkillDataState.AVAILABLE).identified(), resolved.speciesId());
            assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE,
                    cultivation(resolved, required).type(), resolved.speciesId());

            assertFalse(presentation(resolved, stack, 100.0F, false,
                    SkillManager.SkillDataState.UNAVAILABLE).identified(), resolved.speciesId());
            assertEquals(FarmingCultivationGate.ResultType.SKILL_DATA_UNAVAILABLE,
                    cultivation(resolved, 100.0F, SkillManager.SkillDataState.UNAVAILABLE, false, 0).type(),
                    resolved.speciesId());

            assertTrue(presentation(resolved, stack, 0.0F, true,
                    SkillManager.SkillDataState.LOADING).identified(), resolved.speciesId());
            assertEquals(FarmingCultivationGate.ResultType.APPROVED_BYPASS,
                    cultivation(resolved, 0.0F, SkillManager.SkillDataState.LOADING, false, 2).type(),
                    resolved.speciesId());

            assertEquals(1, stack.getCount(), resolved.speciesId());
            assertTrue(ItemStack.isSameItemSameComponents(before, stack), resolved.speciesId());
            unifiedPolicies++;
        }

        assertEquals(67, unifiedPolicies, "74 policies minus six native exclusions and Grapes");
    }

    @Test
    void poppyMasteryAndServerTransactionBoundariesRemainIndependent() throws IOException {
        FlowerDefinition poppy = FlowerRegistry.initial().byId(FlowerRegistry.POPPY).orElseThrow();
        assertEquals(20.0F, poppy.minimumFarmingSkill());
        assertEquals(100.0F, FlowerInteractionService.POPPY_STAGE_SEVEN_SKILL);

        String farmingBlock = source("block/FarmingBlock.java");
        int cropGate = farmingBlock.indexOf("FarmingCultivationGate.evaluate(player, stack.getItem())");
        assertTrue(cropGate >= 0);
        assertTrue(cropGate < farmingBlock.indexOf("farmBe.plant(crop)", cropGate));

        String flowerPlanting = source("farming/FlowerPlantingService.java");
        int flowerGate = flowerPlanting.indexOf("FarmingCultivationGate.evaluateResolved(");
        assertTrue(flowerGate >= 0);
        assertTrue(flowerGate < flowerPlanting.indexOf("access.captureSoilSnapshot()", flowerGate));
        assertTrue(flowerGate < flowerPlanting.indexOf("selector.select(", flowerGate));
        assertTrue(flowerGate < flowerPlanting.indexOf("heldStack.shrink(1)", flowerGate));

        String poppyMastery = source("farming/FlowerInteractionService.java");
        assertTrue(poppyMastery.contains("state.growthStage() == 6"));
        assertTrue(poppyMastery.contains("stack.is(ModTags.Items.SKINNING_KNIVES)"));
        assertTrue(poppyMastery.contains("FlowerMutationReason.POPPY_STAGE_SEVEN"));
    }

    private static FarmingPlantingItemPresentation.PresentationResult presentation(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved,
            ItemStack stack,
            float current,
            boolean bypass,
            SkillManager.SkillDataState state
    ) {
        return FarmingPlantingItemPresentation.resolveResolved(
                new FarmingPlantingItemPresentation.ViewerState(state, current, bypass, 1L),
                stack, EXACT_NAME, FarmingPlantingItemPresentation.Surface.ITEM_NAME, resolved
        );
    }

    private static FarmingCultivationGate.Evaluation cultivation(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved,
            float current
    ) {
        return cultivation(resolved, current, SkillManager.SkillDataState.AVAILABLE, false, 0);
    }

    private static FarmingCultivationGate.Evaluation cultivation(
            FarmingSkillRequirementResolver.ResolvedRequirement resolved,
            float current,
            SkillManager.SkillDataState state,
            boolean creative,
            int permissionLevel
    ) {
        return FarmingCultivationGate.evaluateResolved(resolved, new FarmingCultivationGate.Subject(
                FarmingCultivationGate.ActorType.PLAYER, creative, permissionLevel, state, current
        ));
    }

    private static List<FarmingSkillRequirementResolver.ResolvedRequirement> runtimeRequirements() {
        List<FarmingSkillRequirementResolver.ResolvedRequirement> result = new ArrayList<>();
        CropRegistry.all().forEach(crop -> result.add(
                new FarmingSkillRequirementResolver.ResolvedRequirement(crop.id(), crop)));
        FlowerRegistry.initial().definitions().values().forEach(flower -> result.add(
                new FarmingSkillRequirementResolver.ResolvedRequirement(flower.id().toString(), flower)));
        return result;
    }

    private static Item policyItem(String speciesId) {
        return switch (speciesId) {
            case "vanilla_potato" -> Items.POTATO;
            case "wheat" -> Items.WHEAT_SEEDS;
            case "brown_mushroom" -> Items.BROWN_MUSHROOM;
            case "red_mushroom" -> Items.RED_MUSHROOM;
            case "vanilla_pumpkin" -> Items.PUMPKIN_SEEDS;
            case "vanilla_melon" -> Items.MELON_SEEDS;
            default -> Items.STICK;
        };
    }

    private static Map<String, ApprovedRow> approvedRows() throws IOException {
        Map<String, ApprovedRow> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(PROJECT.resolve("FARMING_SKILL_PROGRESSION_PROPOSAL.md"))) {
            if (!line.startsWith("|") || !line.endsWith("|")) {
                continue;
            }
            String[] cells = line.substring(1, line.length() - 1).split("\\|", -1);
            if (cells.length != 23 || !cells[5].trim().matches("\\d+")
                    || !cells[22].trim().equals("APPROVED")) {
                continue;
            }
            ApprovedRow row = new ApprovedRow(cells[3].trim(), Float.parseFloat(cells[5].trim()));
            assertTrue(rows.putIfAbsent(cells[1].trim(), row) == null,
                    "Duplicate approved species: " + cells[1].trim());
        }
        return rows;
    }

    private static Map<String, String> catalogPlantingItems() throws IOException {
        Map<String, String> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(PROJECT.resolve("FARMING_CONTENT_MASTER_CATALOG.md"))) {
            if (!line.matches("^\\d+,.*")) {
                continue;
            }
            String[] cells = line.split(",", -1);
            assertTrue(rows.putIfAbsent(cells[1].trim(), cells[4].trim()) == null,
                    "Duplicate catalog species: " + cells[1].trim());
        }
        return rows;
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod/" + relative));
    }

    private record ApprovedRow(String plantingItemId, float minimumFarmingSkill) {
    }
}
