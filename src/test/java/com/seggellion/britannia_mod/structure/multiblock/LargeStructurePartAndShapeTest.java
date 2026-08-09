package com.seggellion.britannia_mod.structure.multiblock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.seggellion.britannia_mod.structure.definition.StructureGeometry;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class LargeStructurePartAndShapeTest {
    private static LargeStructureAnchorBlock anchor;
    private static LargeStructurePartBlock part;

    @BeforeAll
    static void bootstrap() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        anchor = MilestoneTwoRegisteredTestContent.anchor();
        part = MilestoneTwoRegisteredTestContent.part();
    }

    @Test
    void partEncodesEveryFutureOffsetForEveryFacingAndReversesToAnchor() {
        BlockPos anchor = new BlockPos(-31, 70, 16);
        int encoded = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (int y = 0; y <= StructureGeometry.MAX_LOCAL_Y; y++) {
                for (int z = 0; z <= StructureGeometry.MAX_LOCAL_Z; z++) {
                    for (int x = 0; x <= StructureGeometry.MAX_LOCAL_X; x++) {
                        LocalOffset offset = new LocalOffset(x, y, z);
                        if (offset.equals(LocalOffset.ANCHOR)) continue;
                        var state = part.stateFor(facing, offset);
                        BlockPos world = com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner
                                .worldPosition(anchor, facing, offset);
                        assertEquals(offset, LargeStructurePartBlock.localOffset(state));
                        assertEquals(facing, state.getValue(LargeStructurePartBlock.FACING));
                        assertEquals(anchor, LargeStructurePartBlock.anchorPosition(world, state));
                        encoded++;
                    }
                }
            }
        }
        assertEquals(68, encoded);
    }

    @Test
    void anchorOffsetVerticalFacingAndOutOfRangeOffsetsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> part.stateFor(Direction.NORTH, LocalOffset.ANCHOR));
        assertThrows(IllegalArgumentException.class,
                () -> part.stateFor(Direction.UP, new LocalOffset(1, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> part.stateFor(Direction.NORTH, new LocalOffset(3, 0, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> part.stateFor(Direction.NORTH, new LocalOffset(1, 3, 0)));
        assertThrows(IllegalArgumentException.class,
                () -> part.stateFor(Direction.NORTH, new LocalOffset(1, 0, 2)));
    }

    @Test
    void anchorAndEveryPartShapeStayInsideTheirSingleCell() {
        assertCollisionProfile(anchor.defaultBlockState());
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (LocalOffset offset : java.util.List.of(
                    new LocalOffset(1, 0, 0),
                    new LocalOffset(0, 0, 1),
                    new LocalOffset(2, 2, 1))) {
                var state = part.stateFor(facing, offset);
                assertCollisionProfile(state);
            }
        }
    }

    @Test
    void partsHaveNoBlockEntityOrAuthoritativeFamilyVariantStateAndBothBlocksAreImmovable()
            throws Exception {
        assertFalse(EntityBlock.class.isAssignableFrom(part.getClass()));
        assertEquals(PushReaction.BLOCK, part.defaultBlockState().getPistonPushReaction());
        assertEquals(PushReaction.BLOCK, anchor.defaultBlockState().getPistonPushReaction());
        String source = Files.readString(Path.of(System.getProperty("britannia.projectDir", "."),
                "src/main/java/com/seggellion/britannia_mod/structure/multiblock/LargeStructurePartBlock.java"));
        assertFalse(source.contains("newBlockEntity"));
        assertFalse(source.contains("FamilyId"));
        assertFalse(source.contains("VariantId"));
        assertFalse(source.contains("PlacedStructureState"));
    }

    private static void assertUnitCell(net.minecraft.world.phys.AABB bounds) {
        assertEquals(0.0, bounds.minX);
        assertEquals(0.0, bounds.minY);
        assertEquals(0.0, bounds.minZ);
        assertEquals(1.0, bounds.maxX);
        assertEquals(1.0, bounds.maxY);
        assertEquals(1.0, bounds.maxZ);
    }

    private static void assertCollisionProfile(net.minecraft.world.level.block.state.BlockState state) {
        assertUnitCell(state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds());
        assertUnitCell(state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds());
        assertUnitCell(state.getVisualShape(
                EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty()).bounds());
        assertUnitCell(state.getOcclusionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds());
        assertFalse(state.canOcclude());
        assertFalse(state.canBeReplaced());
        assertFalse(state.isPathfindable(PathComputationType.LAND));
        for (Direction direction : Direction.values()) {
            assertEquals(true, state.isFaceSturdy(
                    EmptyBlockGetter.INSTANCE, BlockPos.ZERO, direction));
        }
    }
}
