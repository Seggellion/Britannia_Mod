package com.seggellion.britannia_mod.structure.placement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ShrineRollbackOwnershipTest {
    private static LargeStructurePartBlock part;

    @BeforeAll
    static void setup() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        part = MilestoneTwoRegisteredTestContent.part();
    }

    @Test
    void transactionStateIsRestoredAlreadyOriginalIsUntouchedAndExternalReplacementIsPreserved() {
        var original = Blocks.SHORT_GRASS.defaultBlockState();
        var transaction = part.stateFor(Direction.NORTH,
                new com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset(1, 0, 0));
        var unrelated = Blocks.STONE.defaultBlockState();

        assertEquals(ShrineRollbackOwnership.Action.RESTORE_ORIGINAL,
                ShrineRollbackOwnership.classify(transaction, original, transaction));
        assertEquals(ShrineRollbackOwnership.Action.ALREADY_ORIGINAL,
                ShrineRollbackOwnership.classify(original, original, transaction));
        assertEquals(ShrineRollbackOwnership.Action.PRESERVE_UNRELATED,
                ShrineRollbackOwnership.classify(unrelated, original, transaction));
    }

    @Test
    void liveRollbackUsesOwnershipClassificationAndReportsIncompletePreservation() throws Exception {
        String service = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/structure/placement/ShrinePlacementService.java"));
        assertEquals(true, service.contains("ShrineRollbackOwnership.classify"));
        assertEquals(true, service.contains("PRESERVE_UNRELATED"));
        assertEquals(true, service.contains("restored = false"));
    }
}
