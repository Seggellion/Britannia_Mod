package com.seggellion.britannia_mod.vegetation;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ManagedVegetationControllerContractTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));

    @Test
    void controllerIsRegisteredWithoutAPlayerObtainableBlockItem() throws IOException {
        String blocks = source("registry/BlockRegistry.java");
        String items = source("registry/ItemRegistry.java");
        assertTrue(blocks.contains("MANAGED_VEGETATION_CONTROLLER"));
        assertTrue(blocks.contains("noCollission()"));
        assertTrue(blocks.contains("noOcclusion()"));
        assertTrue(blocks.contains(".replaceable()"));
        assertFalse(items.contains("MANAGED_VEGETATION_CONTROLLER"));
    }

    @Test
    void invisibleModelAndBlockstateAreValidJsonAssets() throws IOException {
        Path blockstate = PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/blockstates/managed_vegetation_controller.json"
        );
        Path model = PROJECT.resolve(
                "src/main/resources/assets/britannia_mod/models/block/managed_vegetation_controller.json"
        );
        assertTrue(JsonParser.parseString(Files.readString(blockstate)).isJsonObject());
        assertTrue(JsonParser.parseString(Files.readString(model)).getAsJsonObject()
                .getAsJsonArray("elements").isEmpty());
    }

    @Test
    void permissionedCommandsExposeAdministrationAndDebugPaths() throws IOException {
        String commands = source("commands/ManagedVegetationCommands.java");
        assertTrue(commands.contains("hasPermission(2)"));
        assertTrue(commands.contains("literal(\"add\")"));
        assertTrue(commands.contains("literal(\"addhere\")"));
        assertTrue(commands.contains("literal(\"remove\")"));
        assertTrue(commands.contains("literal(\"inspect\")"));
        assertTrue(commands.contains("literal(\"debug\")"));
        assertTrue(commands.contains("literal(\"spawn\")"));
        assertTrue(commands.contains("spawnFamily(\"grass\""));
        assertTrue(commands.contains("spawnFamily(\"fern\""));
        assertTrue(commands.contains("spawnFamily(\"flower\""));
    }

    @Test
    void ordinaryGrassRandomTicksDiscoverOnlyAirAboveTheSubstrate() throws IOException {
        String mixins = Files.readString(PROJECT.resolve("src/main/resources/britannia_mod.mixins.json"));
        String naturalGrowth = source("mixin/GrassBlockNaturalGrowthMixin.java");
        String service = source("vegetation/ManagedVegetationService.java");
        String placement = source("vegetation/ManagedVegetationPlacementRules.java");

        assertTrue(mixins.contains("GrassBlockNaturalGrowthMixin"));
        assertTrue(naturalGrowth.contains("SpreadingSnowyDirtBlock.class"));
        assertTrue(naturalGrowth.contains("state.is(Blocks.GRASS_BLOCK)"));
        assertTrue(naturalGrowth.contains("grassPosition.above()"));
        assertTrue(service.contains("tryRegisterNaturalNode"));
        assertTrue(placement.contains("BlockState::isAir"));
    }

    @Test
    void adventureClientSendsSwordAttacksForServerOwnedVegetationChecks() throws IOException {
        String mixins = Files.readString(PROJECT.resolve("src/main/resources/britannia_mod.mixins.json"));
        // One mixin now covers every client-side Adventure break exemption, vegetation included:
        // there is a single blockActionRestricted call in startDestroyBlock, and a second
        // @Redirect against it would be a mixin conflict rather than a second feature.
        String adventure = source("mixin/client/ClientAdventureBreakGateMixin.java");
        String handler = source("event/ManagedVegetationInteractionHandler.java");

        assertTrue(mixins.contains("client.ClientAdventureBreakGateMixin"));
        assertTrue(adventure.contains("blockActionRestricted"));
        assertTrue(adventure.contains("gameType == GameType.ADVENTURE"));
        assertTrue(adventure.contains("ManagedVegetationCutTools.canCut"));
        assertTrue(adventure.contains("BlockRegistry.FERN.get()"));
        assertFalse(adventure.contains("Blocks.FERN"));
        assertTrue(handler.contains("ManagedVegetationService.resolveOwnedCutNode"));
        assertTrue(handler.contains("ManagedVegetationCutTools.canCut"));
    }

    @Test
    void placementAndReconciliationContractsPreferConstruction() throws IOException {
        String handler = source("event/ManagedVegetationInteractionHandler.java");
        String manager = source("vegetation/ManagedVegetationManager.java");
        String service = source("vegetation/ManagedVegetationService.java");

        assertTrue(handler.contains("EventPriority.LOWEST"));
        assertTrue(handler.contains("event.isCanceled()"));
        assertTrue(handler.contains("EntityMultiPlaceEvent"));
        assertTrue(handler.contains("retireNodesClaimedByPlacement"));
        assertTrue(manager.contains("ManagedVegetationReconciliationResult.OBSTRUCTED"));
        assertTrue(manager.contains("data.remove(current.position())"));
        assertTrue(service.contains("resolveOwnedCutNode"));
        assertTrue(service.contains("hasLiveRepresentation"));
    }

    @Test
    void everyManagedFernPathUsesTheExistingBritanniaFern() throws IOException {
        String manager = source("vegetation/ManagedVegetationManager.java");
        String service = source("vegetation/ManagedVegetationService.java");
        String config = source("vegetation/ManagedVegetationConfig.java");

        assertTrue(manager.contains("BlockRegistry.FERN.get().defaultBlockState()"));
        assertFalse(manager.contains("Blocks.FERN"));
        assertTrue(service.contains("case FERN -> state.is(BlockRegistry.FERN.get())"));
        assertFalse(service.contains("case FERN -> state.is(Blocks.FERN)"));
        assertTrue(config.contains("Britannia fern (britannia_mod:fern)"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative
        ));
    }
}
