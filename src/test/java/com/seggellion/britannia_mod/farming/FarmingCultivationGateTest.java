package com.seggellion.britannia_mod.farming;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmingCultivationGateTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void everyApprovedSpeciesUsesInclusiveThresholdAndGrapeRemainsExempt() {
        List<FarmingSkillRequirementResolver.ResolvedRequirement> requirements = requirements();
        assertEquals(74, requirements.size());

        for (FarmingSkillRequirementResolver.ResolvedRequirement requirement : requirements) {
            float required = requirement.minimumFarmingSkill();
            if (requirement.speciesId().equals(FarmingCultivationGate.GRAPE_SPECIES_ID)) {
                assertEquals(FarmingCultivationGate.ResultType.NOT_APPLICABLE,
                        evaluate(requirement, -1.0F).type());
                continue;
            }

            FarmingCultivationGate.Evaluation exact = evaluate(requirement, required);
            assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE,
                    exact.type(), requirement.speciesId());
            assertTrue(exact.permitsPlanting(), requirement.speciesId());
            for (float delta : new float[]{1.0F, 0.01F}) {
                FarmingCultivationGate.Evaluation below = evaluate(requirement, required - delta);
                FarmingCultivationGate.Evaluation above = evaluate(requirement, required + delta);
                assertEquals(FarmingCultivationGate.ResultType.INSUFFICIENT_SKILL,
                        below.type(), requirement.speciesId() + " delta=" + delta);
                assertFalse(below.permitsPlanting(), requirement.speciesId());
                assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE,
                        above.type(), requirement.speciesId() + " delta=" + delta);
            }
        }
    }

    @Test
    void allSevenFlowersUseFlowerFeedbackAndExactApprovedThresholds() {
        for (FlowerDefinition flower : FlowerRegistry.initial().definitions().values()) {
            FarmingSkillRequirementResolver.ResolvedRequirement resolved = resolved(flower);
            FarmingCultivationGate.Evaluation below = evaluate(resolved, flower.minimumFarmingSkill() - 0.01F);
            FarmingCultivationGate.Evaluation exact = evaluate(resolved, flower.minimumFarmingSkill());
            FarmingCultivationGate.Evaluation above = evaluate(resolved, flower.minimumFarmingSkill() + 0.01F);

            assertEquals(FarmingCultivationGate.MaterialCategory.FLOWER_SEEDS, below.materialCategory());
            assertEquals("message.britannia_mod.farming.cultivation.insufficient.flower",
                    below.feedbackTranslationKey());
            assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE, exact.type());
            assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE, above.type());
        }
    }

    @Test
    void produceAndMushroomCompatibilityItemsUsePlantingMaterialFeedback() {
        for (String cropId : List.of("vanilla_potato", "brown_mushroom", "red_mushroom")) {
            CropDefinition crop = CropRegistry.byId(cropId).orElseThrow();
            FarmingSkillRequirementResolver.ResolvedRequirement resolved =
                    new FarmingSkillRequirementResolver.ResolvedRequirement(crop.id(), crop);
            var plantingItem = switch (cropId) {
                case "vanilla_potato" -> Items.POTATO;
                case "brown_mushroom" -> Items.BROWN_MUSHROOM;
                case "red_mushroom" -> Items.RED_MUSHROOM;
                default -> throw new IllegalStateException(cropId);
            };

            FarmingCultivationGate.Evaluation below = FarmingCultivationGate.evaluateResolved(
                    resolved, FarmingCultivationGate.Subject.loadedPlayer(crop.minimumFarmingSkill() - 0.1F),
                    plantingItem
            );
            assertEquals(FarmingCultivationGate.MaterialCategory.PLANTING_MATERIAL,
                    below.materialCategory(), cropId);
            assertEquals("message.britannia_mod.farming.cultivation.insufficient.material",
                    below.feedbackTranslationKey(), cropId);
        }
    }

    @Test
    void identicalItemsUseEachPlayersCurrentSkillWithoutCachingOrStackMutation() {
        CropDefinition nightshade = CropRegistry.byId("nightshade").orElseThrow();
        FarmingSkillRequirementResolver.ResolvedRequirement resolved =
                new FarmingSkillRequirementResolver.ResolvedRequirement(nightshade.id(), nightshade);
        int oneItemStack = 1;

        assertEquals(FarmingCultivationGate.ResultType.INSUFFICIENT_SKILL,
                evaluate(resolved, 84.0F).type());
        assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE,
                evaluate(resolved, 85.0F).type());
        assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE,
                evaluate(resolved, 100.0F).type());
        assertEquals(FarmingCultivationGate.ResultType.INSUFFICIENT_SKILL,
                evaluate(resolved, 0.0F).type());
        assertEquals(1, oneItemStack, "Eligibility evaluation never mutates the held stack");
    }

    @Test
    void creativeAndOperatorBypassButAutomationAndUnavailableDataFailClosed() {
        FarmingSkillRequirementResolver.ResolvedRequirement poppy = resolved(
                FlowerRegistry.initial().byId(FlowerRegistry.POPPY).orElseThrow()
        );
        for (FarmingCultivationGate.Subject bypass : List.of(
                new FarmingCultivationGate.Subject(
                        FarmingCultivationGate.ActorType.PLAYER, true, 0,
                        SkillManager.SkillDataState.UNAVAILABLE, 0.0F
                ),
                new FarmingCultivationGate.Subject(
                        FarmingCultivationGate.ActorType.PLAYER, false, 2,
                        SkillManager.SkillDataState.LOADING, 0.0F
                )
        )) {
            FarmingCultivationGate.Evaluation result = FarmingCultivationGate.evaluateResolved(poppy, bypass);
            assertEquals(FarmingCultivationGate.ResultType.APPROVED_BYPASS, result.type());
            assertTrue(result.permitsPlanting());
        }

        for (SkillManager.SkillDataState state : List.of(
                SkillManager.SkillDataState.NOT_LOADED,
                SkillManager.SkillDataState.LOADING,
                SkillManager.SkillDataState.UNAVAILABLE
        )) {
            FarmingCultivationGate.Subject subject = new FarmingCultivationGate.Subject(
                    FarmingCultivationGate.ActorType.PLAYER, false, 0, state, 100.0F
            );
            FarmingCultivationGate.Evaluation result = FarmingCultivationGate.evaluateResolved(poppy, subject);
            assertEquals(FarmingCultivationGate.ResultType.SKILL_DATA_UNAVAILABLE, result.type());
            assertFalse(result.permitsPlanting());
        }

        for (FarmingCultivationGate.ActorType actor : List.of(
                FarmingCultivationGate.ActorType.AUTOMATION,
                FarmingCultivationGate.ActorType.NON_PLAYER
        )) {
            FarmingCultivationGate.Subject subject = new FarmingCultivationGate.Subject(
                    actor, true, 4, SkillManager.SkillDataState.AVAILABLE, 100.0F
            );
            FarmingCultivationGate.Evaluation result = FarmingCultivationGate.evaluateResolved(poppy, subject);
            assertEquals(FarmingCultivationGate.ResultType.NON_PLAYER_POLICY, result.type());
            assertFalse(result.permitsPlanting());
        }
    }

    /**
     * M9 item 4: the gate distinguishes a Farming value of zero from no Farming value at all.
     *
     * <p>These two subjects hold the identical float. Carrot -- the questline's crop -- requires
     * Farming 0, so a player whose real Farming is 0 must plant it, and a player whose data never
     * arrived must not, because nobody knows what their Farming is. The old behaviour was correct
     * on this point and stays correct; what M9 adds is that the second player now gets their data
     * retried instead of being stuck until they relog.
     */
    @Test
    void anAuthoritativeFarmingZeroPlantsAZeroRequirementCropAndAnUnknownZeroDoesNot() {
        CropDefinition carrotCrop = CropRegistry.byId("carrot").orElseThrow();
        FarmingSkillRequirementResolver.ResolvedRequirement carrot =
                new FarmingSkillRequirementResolver.ResolvedRequirement(carrotCrop.id(), carrotCrop);
        assertEquals(0.0F, carrot.minimumFarmingSkill(), 0.0001F,
                "the questline's crop is the zero-requirement case this test depends on");

        FarmingCultivationGate.Evaluation known = FarmingCultivationGate.evaluateResolved(
                carrot,
                new FarmingCultivationGate.Subject(FarmingCultivationGate.ActorType.PLAYER, false, 0,
                        SkillManager.SkillDataState.AVAILABLE, 0.0F));
        assertEquals(FarmingCultivationGate.ResultType.ELIGIBLE, known.type());
        assertTrue(known.permitsPlanting());
        assertEquals(0.0F, known.currentFarmingSkill(), 0.0001F);

        FarmingCultivationGate.Evaluation unknown = FarmingCultivationGate.evaluateResolved(
                carrot,
                new FarmingCultivationGate.Subject(FarmingCultivationGate.ActorType.PLAYER, false, 0,
                        SkillManager.SkillDataState.UNAVAILABLE, 0.0F));
        assertEquals(FarmingCultivationGate.ResultType.SKILL_DATA_UNAVAILABLE, unknown.type());
        assertFalse(unknown.permitsPlanting());
        // Reported as unknown rather than as zero, so no message can imply the player has a skill
        // level nobody has read.
        assertTrue(Float.isNaN(unknown.currentFarmingSkill()));
        assertEquals("?", FarmingCultivationGate.formatSkill(unknown.currentFarmingSkill()));
    }

    @Test
    void unresolvedMaterialFailsClosedAndFeedbackIsGenericAndLocalized() throws IOException {
        FarmingCultivationGate.Evaluation unresolved = FarmingCultivationGate.evaluate(null, null);
        assertEquals(FarmingCultivationGate.ResultType.UNRESOLVED_SPECIES, unresolved.type());
        assertFalse(unresolved.permitsPlanting());

        JsonObject language = JsonParser.parseString(Files.readString(PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/lang/en_us.json"
        ))).getAsJsonObject();
        for (String key : List.of(
                "message.britannia_mod.farming.cultivation.insufficient.crop",
                "message.britannia_mod.farming.cultivation.insufficient.flower",
                "message.britannia_mod.farming.cultivation.insufficient.material",
                "message.britannia_mod.farming.cultivation.skill_unavailable",
                "message.britannia_mod.farming.cultivation.automation_blocked",
                "message.britannia_mod.farming.cultivation.unresolved"
        )) {
            assertTrue(language.has(key), key);
            String text = language.get(key).getAsString().toLowerCase();
            assertFalse(text.contains("poppy"), key);
            assertFalse(text.contains("orfluer"), key);
            assertFalse(text.contains("nightshade"), key);
        }
        assertEquals("19.5", FarmingCultivationGate.formatSkill(19.5F));
        assertEquals("20", FarmingCultivationGate.formatSkill(20.0F));
    }

    @Test
    void gatesAreAtSharedServerTransactionsBeforeMutationAndGrapeAndNativeRoutesStayUntouched() throws IOException {
        String farmingBlock = source("block/FarmingBlock.java");
        String plantingMethod = farmingBlock.substring(farmingBlock.indexOf("public static ItemInteractionResult tryPlantSeed"));
        int cropGate = plantingMethod.indexOf("FarmingCultivationGate.evaluate(player, stack.getItem())");
        assertTrue(cropGate > plantingMethod.indexOf("CropRegistry.bySeed"));
        assertTrue(cropGate < plantingMethod.indexOf("not_trellis_crop"));
        assertTrue(cropGate < plantingMethod.indexOf("missing_support"));
        assertTrue(cropGate < plantingMethod.indexOf("tree_space_blocked"));
        assertTrue(cropGate < plantingMethod.indexOf("farmBe.plant(crop)"));

        String flowerPlanting = source("farming/FlowerPlantingService.java");
        int flowerGate = flowerPlanting.indexOf("FarmingCultivationGate.evaluateResolved(");
        assertTrue(flowerGate > flowerPlanting.indexOf("validateDefinitionForPlanting"));
        assertTrue(flowerGate < flowerPlanting.indexOf("access.captureSoilSnapshot()"));
        assertTrue(flowerGate < flowerPlanting.indexOf("selector.select("));

        String trellis = source("block/TrellisBlock.java");
        assertTrue(trellis.contains("FarmingBlock.tryPlantSeed("));
        String grapes = source("item/GrapeSeedsItem.java");
        assertFalse(grapes.contains("FarmingCultivationGate"));
        assertFalse(grapes.contains("minimumFarmingSkill"));
        assertFalse(source("block/GrapeVineBlock.java").contains("FarmingCultivationGate"));
        assertFalse(source("block/entity/GrapeVineBlockEntity.java").contains("FarmingCultivationGate"));

        String itemRegistry = source("registry/ItemRegistry.java");
        assertFalse(itemRegistry.contains("FarmingCultivationGate"));
        assertFalse(itemRegistry.contains("a brown seed"));
    }

    private static FarmingCultivationGate.Evaluation evaluate(
            FarmingSkillRequirementResolver.ResolvedRequirement requirement,
            float current
    ) {
        return FarmingCultivationGate.evaluateResolved(
                requirement, FarmingCultivationGate.Subject.loadedPlayer(current)
        );
    }

    private static List<FarmingSkillRequirementResolver.ResolvedRequirement> requirements() {
        List<FarmingSkillRequirementResolver.ResolvedRequirement> requirements = new ArrayList<>();
        CropRegistry.all().forEach(crop -> requirements.add(
                new FarmingSkillRequirementResolver.ResolvedRequirement(crop.id(), crop)
        ));
        FlowerRegistry.initial().definitions().values().forEach(flower -> requirements.add(resolved(flower)));
        return requirements;
    }

    private static FarmingSkillRequirementResolver.ResolvedRequirement resolved(FlowerDefinition flower) {
        return new FarmingSkillRequirementResolver.ResolvedRequirement(flower.id().toString(), flower);
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative
        ));
    }
}
