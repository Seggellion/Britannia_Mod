package com.seggellion.britannia_mod.structure.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.CollisionProfile;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MilestoneEightShapeAuditTest {
    private static LargeStructureAnchorBlock anchor;
    private static LargeStructurePartBlock part;

    @BeforeAll
    static void bootstrap() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        anchor = MilestoneTwoRegisteredTestContent.anchor();
        part = MilestoneTwoRegisteredTestContent.part();
    }

    @Test
    void auditsAllFourAnchorStatesAndAllSeventyTwoRepresentablePartStates() {
        List<BlockState> anchorStates = anchor.getStateDefinition().getPossibleStates();
        List<BlockState> partStates = part.getStateDefinition().getPossibleStates();
        assertEquals(4, anchorStates.size());
        assertEquals(72, partStates.size());

        List<String> signatures = new ArrayList<>();
        anchorStates.forEach(state -> signatures.add(auditSolidCell(state)));
        partStates.forEach(state -> signatures.add(auditSolidCell(state)));
        assertEquals(1, signatures.stream().distinct().count(),
                "No facing or encoded offset may change physical behavior");
    }

    @Test
    void everyCatalogueEntryUsesTheFinalSolidCellProfileWithoutChangingShapes() {
        var catalogue = ShrineMonolithDefinitions.catalogue();
        assertEquals(2, catalogue.families().size());
        catalogue.families().forEach(family -> {
            assertEquals(CollisionProfile.SOLID_CELL, family.collisionProfile());
            family.variants().forEach(variant ->
                    assertEquals(CollisionProfile.SOLID_CELL, variant.collisionProfile()));
        });
        String anchorSignature = auditSolidCell(anchor.defaultBlockState());
        String partSignature = auditSolidCell(part.defaultBlockState());
        catalogue.families().stream().flatMap(family -> family.variants().stream()).forEach(variant -> {
            assertEquals(anchorSignature, auditSolidCell(anchor.defaultBlockState()));
            assertEquals(partSignature, auditSolidCell(part.defaultBlockState()));
        });
    }

    @Test
    void shapeCodeHasNoRendererModelTextureRenderBoundsOrOffsetDependency() throws Exception {
        for (String file : List.of("LargeStructureAnchorBlock.java", "LargeStructurePartBlock.java")) {
            String source = Files.readString(Path.of(
                    "src/main/java/com/seggellion/britannia_mod/structure/multiblock/" + file));
            assertTrue(source.contains("Shapes.block()"));
            for (String forbidden : List.of(
                    "client.renderer", "ShrineRenderer", "ShrineGeoModel", "ShrineRenderSelection",
                    "ShrineRenderTransform", "RenderBounds", "AABB", "modelResource", "textureResource",
                    "renderOffset")) {
                assertFalse(source.contains(forbidden), file + " must not depend on " + forbidden);
            }
        }
    }

    private static String auditSolidCell(BlockState state) {
        var level = EmptyBlockGetter.INSTANCE;
        var pos = BlockPos.ZERO;
        List<VoxelShape> shapes = List.of(
                state.getShape(level, pos),
                state.getShape(level, pos, CollisionContext.empty()),
                state.getCollisionShape(level, pos),
                state.getCollisionShape(level, pos, CollisionContext.empty()),
                state.getOcclusionShape(level, pos),
                state.getVisualShape(level, pos, CollisionContext.empty()),
                state.getBlockSupportShape(level, pos));
        shapes.forEach(MilestoneEightShapeAuditTest::assertFullFiniteLocalCell);
        VoxelShape interaction = state.getInteractionShape(level, pos);
        assertTrue(interaction.isEmpty(), "Inherited interaction shape is intentionally empty");
        for (Direction direction : Direction.values()) {
            assertTrue(state.isFaceSturdy(level, pos, direction));
            assertFullFiniteLocalCell(state.getFaceOcclusionShape(level, pos, direction));
        }
        for (PathComputationType pathType : PathComputationType.values()) {
            assertFalse(state.isPathfindable(pathType), "SOLID_CELL blocks all " + pathType + " pathfinding");
        }
        assertFalse(state.canBeReplaced());
        assertFalse(state.canBeReplaced(Fluids.WATER));
        assertEquals(PushReaction.BLOCK, state.getPistonPushReaction());
        assertTrue(state.getFluidState().isEmpty());
        assertFalse(state.getBlock() instanceof SimpleWaterloggedBlock);
        return shapes.stream().map(shape -> shape.toAabbs().toString()).toList().toString()
                + interaction.toAabbs()
                + state.getPistonPushReaction() + state.getFluidState() + state.canBeReplaced();
    }

    private static void assertFullFiniteLocalCell(VoxelShape shape) {
        assertFalse(shape.isEmpty());
        assertEquals(1, shape.toAabbs().size());
        for (AABB box : shape.toAabbs()) {
            for (double coordinate : List.of(
                    box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ)) {
                assertTrue(Double.isFinite(coordinate), "Shape coordinate must be finite");
            }
            assertTrue(box.minX >= 0 && box.minY >= 0 && box.minZ >= 0);
            assertTrue(box.maxX <= 1 && box.maxY <= 1 && box.maxZ <= 1);
            assertTrue(box.minX <= box.maxX && box.minY <= box.maxY && box.minZ <= box.maxZ);
            assertEquals(new AABB(0, 0, 0, 1, 1, 1), box);
        }
    }
}
