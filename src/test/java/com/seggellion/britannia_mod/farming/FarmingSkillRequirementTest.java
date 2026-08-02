package com.seggellion.britannia_mod.farming;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FarmingSkillRequirementTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void approvedProposalCatalogDefinitionsAndPlantingMappingsReconcileExactly() throws IOException {
        Map<String, ProposalRow> proposal = proposalRows();
        Map<String, CatalogRow> catalog = catalogRows();
        Map<String, FarmingSkillRequirement> definitions = runtimeDefinitions();

        assertEquals(74, proposal.size());
        assertEquals(74, catalog.size());
        assertEquals(74, definitions.size());
        assertEquals(67, CropRegistry.all().size());
        assertEquals(7, FlowerRegistry.initial().definitions().size());
        assertEquals(74, proposal.values().stream().map(ProposalRow::plantingItemId).distinct().count());
        assertEquals(74, catalog.values().stream().map(CatalogRow::plantingItemId).distinct().count());

        for (String speciesId : proposal.keySet()) {
            assertEquals(catalog.get(speciesId).plantingItemId(), proposal.get(speciesId).plantingItemId(), speciesId);
        }

        List<FarmingSkillRequirementValidator.SpeciesRegistration> registrations = catalog.values().stream()
                .map(row -> new FarmingSkillRequirementValidator.SpeciesRegistration(
                        row.speciesId(), ResourceLocation.parse(row.plantingItemId()), definitions.get(row.speciesId()),
                        row.speciesId().contains(":")
                                ? FarmingSkillRequirementValidator.DefinitionType.FLOWER
                                : FarmingSkillRequirementValidator.DefinitionType.CROP
                ))
                .toList();
        Map<String, Float> approved = proposal.values().stream().collect(Collectors.toMap(
                ProposalRow::speciesId,
                ProposalRow::minimumFarmingSkill,
                (left, right) -> left,
                LinkedHashMap::new
        ));

        FarmingSkillRequirementValidator.validateApprovedRoster(registrations, catalog.keySet(), approved);
    }

    @Test
    void everyRequiredSentinelMatchesTheCommittedProposal() throws IOException {
        Map<String, ProposalRow> proposal = proposalRows();
        Map<String, FarmingSkillRequirement> definitions = runtimeDefinitions();
        Map<String, Float> sentinels = Map.ofEntries(
                Map.entry("carrot", 0.0f),
                Map.entry("lettuce", 0.0f),
                Map.entry("green_onion", 0.0f),
                Map.entry("wheat", 0.0f),
                Map.entry("britannia_mod:campion", 10.0f),
                Map.entry("britannia_mod:poppy", 20.0f),
                Map.entry("britannia_mod:hyacinth", 30.0f),
                Map.entry("britannia_mod:snowdrop", 40.0f),
                Map.entry("britannia_mod:lily", 50.0f),
                Map.entry("britannia_mod:foxglove", 65.0f),
                Map.entry("nightshade", 85.0f),
                Map.entry("mandrake", 90.0f),
                Map.entry("britannia_mod:orfluer", 95.0f)
        );

        for (Map.Entry<String, Float> sentinel : sentinels.entrySet()) {
            assertEquals(sentinel.getValue(), proposal.get(sentinel.getKey()).minimumFarmingSkill(), sentinel.getKey());
            assertEquals(sentinel.getValue(), definitions.get(sentinel.getKey()).minimumFarmingSkill(), sentinel.getKey());
        }
    }

    @Test
    void invalidMissingAndConflictingRequirementDataFailsWithDiagnostics() {
        for (float invalid : new float[]{-1.0f, 100.1f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                    () -> FarmingSkillRequirementValidator.validateValue("test_species", invalid));
            assertTrue(error.getMessage().contains("test_species"));
            assertTrue(error.getMessage().contains(String.valueOf(invalid)));
            assertTrue(error.getMessage().contains(FarmingSkillRequirementValidator.APPROVED_SOURCE));
        }

        FarmingSkillRequirementValidator.SpeciesRegistration missing = new FarmingSkillRequirementValidator.SpeciesRegistration(
                "missing", ResourceLocation.parse("britannia_mod:missing_seeds"), null,
                FarmingSkillRequirementValidator.DefinitionType.CROP
        );
        IllegalArgumentException missingError = assertThrows(IllegalArgumentException.class,
                () -> FarmingSkillRequirementValidator.validateRegistrations(List.of(missing), false));
        assertTrue(missingError.getMessage().contains("missing"));
        assertTrue(missingError.getMessage().contains("no minimum Farming requirement"));

        List<FarmingSkillRequirementValidator.SpeciesRegistration> conflicts = List.of(
                registration("duplicate", "britannia_mod:first", 10.0f),
                registration("duplicate", "britannia_mod:second", 15.0f)
        );
        IllegalArgumentException conflict = assertThrows(IllegalArgumentException.class,
                () -> FarmingSkillRequirementValidator.validateRegistrations(conflicts, false));
        assertTrue(conflict.getMessage().contains("duplicate"));
        assertTrue(conflict.getMessage().contains("conflicting values"));

        List<FarmingSkillRequirementValidator.SpeciesRegistration> sharedItem = List.of(
                registration("first", "britannia_mod:shared", 10.0f),
                registration("second", "britannia_mod:shared", 15.0f)
        );
        IllegalArgumentException shared = assertThrows(IllegalArgumentException.class,
                () -> FarmingSkillRequirementValidator.validateRegistrations(sharedItem, false));
        assertTrue(shared.getMessage().contains("britannia_mod:shared"));
        assertTrue(shared.getMessage().contains("first"));
        assertTrue(shared.getMessage().contains("second"));
    }

    @Test
    void missingExtraAndProposalMismatchAreRejectedWithoutASecondValueTable() throws IOException {
        Map<String, ProposalRow> proposal = proposalRows();
        Map<String, CatalogRow> catalog = catalogRows();
        Map<String, FarmingSkillRequirement> definitions = runtimeDefinitions();
        List<FarmingSkillRequirementValidator.SpeciesRegistration> registrations = new ArrayList<>();
        for (CatalogRow row : catalog.values()) {
            registrations.add(new FarmingSkillRequirementValidator.SpeciesRegistration(
                    row.speciesId(), ResourceLocation.parse(row.plantingItemId()), definitions.get(row.speciesId()),
                    row.speciesId().contains(":")
                            ? FarmingSkillRequirementValidator.DefinitionType.FLOWER
                            : FarmingSkillRequirementValidator.DefinitionType.CROP
            ));
        }
        Map<String, Float> approved = proposal.values().stream().collect(Collectors.toMap(
                ProposalRow::speciesId, ProposalRow::minimumFarmingSkill,
                (left, right) -> left, LinkedHashMap::new
        ));

        Map<String, Float> wrong = new LinkedHashMap<>(approved);
        wrong.put("grapes", 75.0f);
        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> FarmingSkillRequirementValidator.validateApprovedRoster(registrations, catalog.keySet(), wrong));
        assertTrue(mismatch.getMessage().contains("grapes"));
        assertTrue(mismatch.getMessage().contains("80.0"));
        assertTrue(mismatch.getMessage().contains("75.0"));

        Set<String> missingCatalogSpecies = catalog.keySet().stream()
                .filter(id -> !id.equals("grapes"))
                .collect(Collectors.toSet());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> FarmingSkillRequirementValidator.validateApprovedRoster(registrations, missingCatalogSpecies, approved));
        assertTrue(missing.getMessage().contains("extra=[grapes]"));
    }

    @Test
    void poppyOrdinaryRequirementAndStageSevenMasteryRemainIndependent() {
        FlowerDefinition poppy = FlowerRegistry.initial().byId(FlowerRegistry.POPPY).orElseThrow();
        assertEquals(20.0f, poppy.minimumFarmingSkill());
        assertEquals(100.0f, FlowerInteractionService.POPPY_STAGE_SEVEN_SKILL);
        assertFalse(Float.compare(poppy.minimumFarmingSkill(), FlowerInteractionService.POPPY_STAGE_SEVEN_SKILL) == 0);
    }

    @Test
    void fruitTreesUseCropRequirementsAndDoNotDuplicateTheContract() {
        assertEquals(9, FruitTreeRegistry.all().size());
        assertFalse(FarmingSkillRequirement.class.isAssignableFrom(FruitTreeDefinition.class));
        for (FruitTreeDefinition tree : FruitTreeRegistry.all()) {
            assertTrue(CropRegistry.byId(tree.id()).isPresent(), tree.id());
        }
    }

    @Test
    void milestoneSixteenAddsViewerPolicyWithoutChangingGrapePlanting() throws IOException {
        String grapes = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/item/GrapeSeedsItem.java"));
        assertFalse(grapes.contains("FarmingCultivationGate"));
        assertFalse(grapes.contains("minimumFarmingSkill"));
        assertFalse(grapes.contains("a brown seed"));

        String farmingBlock = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/block/FarmingBlock.java"));
        String flowerPlanting = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/farming/FlowerPlantingService.java"));
        assertTrue(farmingBlock.contains("FarmingCultivationGate.evaluate(player, stack.getItem())"));
        assertTrue(flowerPlanting.contains("FarmingCultivationGate.evaluateResolved("));
        assertFalse(farmingBlock.contains("a brown seed"));
        assertFalse(flowerPlanting.contains("a brown seed"));

        String presentation = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/farming/FarmingPlantingItemPresentation.java"));
        String categories = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/farming/FarmingPlantingMaterialCategory.java"));
        assertTrue(categories.contains("GRAPES(\"item.britannia_mod.unidentified_grape_seed\")"));
        assertTrue(presentation.contains("identificationBypass"));

        String commonSetup = Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/ModEventHandler.java"));
        assertTrue(commonSetup.contains("event.enqueueWork(FarmingSkillRequirementValidator::validateRegisteredDefinitions)"));
    }

    private static FarmingSkillRequirementValidator.SpeciesRegistration registration(
            String speciesId,
            String plantingItemId,
            float requirement
    ) {
        return new FarmingSkillRequirementValidator.SpeciesRegistration(
                speciesId,
                ResourceLocation.parse(plantingItemId),
                () -> requirement,
                FarmingSkillRequirementValidator.DefinitionType.CROP
        );
    }

    private static Map<String, FarmingSkillRequirement> runtimeDefinitions() {
        Map<String, FarmingSkillRequirement> definitions = new LinkedHashMap<>();
        CropRegistry.all().forEach(definition -> definitions.put(definition.id(), definition));
        FlowerRegistry.initial().definitions().values().forEach(
                definition -> definitions.put(definition.id().toString(), definition)
        );
        return definitions;
    }

    private static Map<String, ProposalRow> proposalRows() throws IOException {
        Map<String, ProposalRow> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(PROJECT.resolve("FARMING_SKILL_PROGRESSION_PROPOSAL.md"))) {
            if (!line.startsWith("|") || !line.endsWith("|")) {
                continue;
            }
            String[] cells = line.substring(1, line.length() - 1).split("\\|", -1);
            if (cells.length != 23 || !cells[5].trim().matches("\\d+")
                    || !cells[22].trim().equals("APPROVED")) {
                continue;
            }
            ProposalRow row = new ProposalRow(
                    cells[1].trim(), cells[3].trim(), Float.parseFloat(cells[5].trim())
            );
            if (rows.putIfAbsent(row.speciesId(), row) != null) {
                throw new IllegalArgumentException("Duplicate approved proposal species: " + row.speciesId());
            }
        }
        return rows;
    }

    private static Map<String, CatalogRow> catalogRows() throws IOException {
        Map<String, CatalogRow> rows = new LinkedHashMap<>();
        for (String line : Files.readAllLines(PROJECT.resolve("FARMING_CONTENT_MASTER_CATALOG.md"))) {
            if (!line.matches("^\\d+,.*")) {
                continue;
            }
            String[] cells = line.split(",", -1);
            CatalogRow row = new CatalogRow(cells[1].trim(), cells[4].trim());
            if (rows.putIfAbsent(row.speciesId(), row) != null) {
                throw new IllegalArgumentException("Duplicate catalog species: " + row.speciesId());
            }
        }
        return rows;
    }

    private record ProposalRow(String speciesId, String plantingItemId, float minimumFarmingSkill) {
    }

    private record CatalogRow(String speciesId, String plantingItemId) {
    }
}
