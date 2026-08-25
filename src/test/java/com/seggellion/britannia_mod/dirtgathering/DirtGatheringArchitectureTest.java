package com.seggellion.britannia_mod.dirtgathering;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtGatheringArchitectureTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir", "."));

    private static String source(String relative) throws Exception {
        return Files.readString(PROJECT.resolve(
                "src/main/java/com/seggellion/britannia_mod/" + relative)).replace("\r\n", "\n");
    }

    @Test
    void transactionIsAHighestPriorityMainHandRightClickOutsideTheBreakPipeline() throws Exception {
        String handler = source("dirtgathering/DirtGatheringInteractionHandler.java");
        assertTrue(handler.contains("PlayerInteractEvent.RightClickBlock"));
        assertTrue(handler.contains("EventPriority.HIGHEST"));
        assertTrue(handler.contains("InteractionHand.MAIN_HAND"));
        assertTrue(handler.contains("setCancellationResult(InteractionResult.SUCCESS)"));
        assertFalse(handler.contains("BlockEvent.BreakEvent"));
    }

    @Test
    void onlyExactVanillaDirtVariantsAndTheExactBritanniaShovelAreOwned() throws Exception {
        String service = source("dirtgathering/DirtGatheringService.java");
        String handler = source("dirtgathering/DirtGatheringInteractionHandler.java");
        String target = source("dirtgathering/DirtGatheringTarget.java");
        String combined = service + handler + target;
        assertTrue(combined.contains("Blocks.DIRT"));
        assertTrue(combined.contains("Blocks.COARSE_DIRT"));
        assertTrue(combined.contains("ToolRegistry.SHOVEL.get()"));
        assertFalse(combined.contains("BlockTags.DIRT"));
        assertTrue(service.contains("DirtGatheringTarget.isGatherable"));
        assertTrue(handler.contains("DirtGatheringTarget.isGatherable"));
    }

    @Test
    void gatheringCannotTouchMiningDepositsOrWorldBlockState() throws Exception {
        String service = source("dirtgathering/DirtGatheringService.java");
        String handler = source("dirtgathering/DirtGatheringInteractionHandler.java");
        String policy = source("dirtgathering/DirtGatheringPolicy.java");
        String combined = service + handler + policy;
        for (String forbidden : List.of(
                "MineableCatalog", "Mineables.", "MiningBreakGate", "MiningSkill",
                "ManagedDeposits", "ManagedDepositExtraction", "BrokenBlockTracker",
                "MiningProvenance", "Resources.resolve", "destroyBlock", "setBlock(")) {
            assertFalse(combined.contains(forbidden), "dirt gathering reached forbidden seam: " + forbidden);
        }
        assertTrue(policy.contains("ManagedExtractionPolicy.actorOf"),
                "the shared real/fake actor classifier should remain authoritative");
        assertTrue(service.contains("new ItemStack(ItemRegistry.DIRT.get())"));
    }

    @Test
    void shovelOverrideOnlySuppressesPredictionAndNeverGrantsOutput() throws Exception {
        String shovel = source("item/QualityShovelItem.java");
        assertTrue(shovel.contains("UseOnContext"));
        assertTrue(shovel.contains("DirtGatheringTarget.isGatherable"));
        assertTrue(shovel.contains("context.getLevel().isClientSide"));
        assertFalse(shovel.contains("DirtGatheringService"));
        assertFalse(shovel.contains("ItemRegistry.DIRT"));
    }

    @Test
    void feedbackUsesTheActionBarAndTheCooldownLivesOnPlayerData() throws Exception {
        String service = source("dirtgathering/DirtGatheringService.java");
        String cooldown = source("dirtgathering/DirtGatheringCooldown.java");
        assertTrue(service.contains("displayClientMessage") && service.contains(", true)"));
        assertFalse(service.contains("sendSystemMessage"));
        assertTrue(cooldown.contains("player.getPersistentData()"));
        assertFalse(cooldown.contains("static final Map"));
        assertFalse(cooldown.contains("getCooldowns()"));
        assertTrue(cooldown.contains("PlayerEvent.Clone"));
    }
}
