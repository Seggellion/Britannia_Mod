package com.seggellion.britannia_mod.farming;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowerProtectionBypassTest {
    private static final Path PROJECT = Path.of(System.getProperty("britannia.projectDir"));
    private static final Set<FlowerMutationReason> PLAYER_PATHS = EnumSet.of(
            FlowerMutationReason.PLANTING,
            FlowerMutationReason.CARE,
            FlowerMutationReason.HARVEST,
            FlowerMutationReason.SWORD_CUTBACK,
            FlowerMutationReason.PERMANENT_UPROOT,
            FlowerMutationReason.POPPY_STAGE_SEVEN,
            FlowerMutationReason.NORMAL_BREAK,
            FlowerMutationReason.REPLACEMENT
    );

    @Test
    void everyDirectPlayerPathDeniesOrdinaryActorsAndAllowsCreativeOrOperatorTwo() {
        for (FlowerMutationReason reason : PLAYER_PATHS) {
            assertFalse(FlowerProtectionService.mayMutate(true, false, 0, reason), reason.name());
            assertFalse(FlowerProtectionService.mayMutate(true, false, 1, reason), reason.name());
            assertTrue(FlowerProtectionService.mayMutate(true, true, 0, reason), reason.name());
            assertTrue(FlowerProtectionService.mayMutate(true, false, 2, reason), reason.name());
            assertTrue(FlowerProtectionService.mayMutate(false, false, 0, reason), reason.name());
        }
    }

    @Test
    void environmentalPistonAndSystemReasonsFollowTheApprovedNonBypassPolicy() {
        for (FlowerMutationReason reason : Set.of(FlowerMutationReason.EXPLOSION, FlowerMutationReason.FLUID)) {
            assertFalse(FlowerProtectionService.mayMutate(true, false, 0, reason), reason.name());
            assertFalse(FlowerProtectionService.mayMutate(true, true, 4, reason), reason.name());
            assertTrue(FlowerProtectionService.mayMutate(false, false, 0, reason), reason.name());
        }
        assertFalse(FlowerProtectionService.mayMutate(false, false, 0, FlowerMutationReason.PISTON));
        assertFalse(FlowerProtectionService.mayMutate(true, true, 4, FlowerMutationReason.PISTON));
        for (FlowerMutationReason reason : Set.of(
                FlowerMutationReason.ADMIN_REMOVE,
                FlowerMutationReason.SYSTEM_MUTATION,
                FlowerMutationReason.ADMIN_COMMAND,
                FlowerMutationReason.WORLD_GENERATION)) {
            assertTrue(FlowerProtectionService.mayMutate(true, false, 0, reason), reason.name());
        }
    }

    @Test
    void allImplementedMutationHooksRouteThroughCentralPolicyAndCorrectDeniedClients() throws IOException {
        String service = source("farming/FlowerInteractionService.java");
        String handler = source("event/FlowerInteractionHandler.java");
        String block = source("block/FlowerBlock.java");

        for (String reason : new String[]{"CARE", "HARVEST", "SWORD_CUTBACK", "PERMANENT_UPROOT",
                "POPPY_STAGE_SEVEN", "NORMAL_BREAK", "REPLACEMENT"}) {
            assertTrue(service.contains("FlowerMutationReason." + reason), reason);
        }
        assertTrue(handler.contains("FlowerMutationReason.NORMAL_BREAK"));
        assertTrue(handler.contains("FlowerMutationReason.EXPLOSION"));
        assertTrue(handler.contains("FlowerMutationReason.FLUID"));
        assertTrue(handler.contains("sendBlockUpdated"));
        assertTrue(handler.contains("event.setCanceled(true)"));
        assertTrue(block.contains("return PushReaction.BLOCK"));
        assertFalse(service.contains("level.isClientSide) {\n            stack.hurtAndBreak"));
    }

    private static String source(String relative) throws IOException {
        return Files.readString(PROJECT.resolve("src/main/java/com/seggellion/britannia_mod").resolve(relative),
                StandardCharsets.UTF_8);
    }
}
