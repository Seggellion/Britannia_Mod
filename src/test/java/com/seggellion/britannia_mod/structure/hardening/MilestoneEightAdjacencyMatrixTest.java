package com.seggellion.britannia_mod.structure.hardening;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.seggellion.britannia_mod.structure.definition.ShrineMonolithDefinitions;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Family;
import com.seggellion.britannia_mod.structure.definition.StructureDefinition.Variant;
import com.seggellion.britannia_mod.structure.definition.StructureGeometry.LocalOffset;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlock;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructureAnchorBlockEntity;
import com.seggellion.britannia_mod.structure.multiblock.LargeStructurePartBlock;
import com.seggellion.britannia_mod.structure.multiblock.PlacedStructureState;
import com.seggellion.britannia_mod.structure.placement.ShrinePlacementPlanner;
import com.seggellion.britannia_mod.structure.testsupport.MilestoneTwoRegisteredTestContent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.properties.AttachFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.block.state.properties.WallSide;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.AABB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class MilestoneEightAdjacencyMatrixTest {
    private static final BlockPos ANCHOR = new BlockPos(100, 70, 100);
    private static final int PHASE_COUNT = 6;
    private static LargeStructureAnchorBlock anchor;
    private static LargeStructurePartBlock part;

    @BeforeAll
    static void bootstrap() {
        MilestoneTwoRegisteredTestContent.ensureRegistered();
        anchor = MilestoneTwoRegisteredTestContent.anchor();
        part = MilestoneTwoRegisteredTestContent.part();
    }

    @Test
    void exactOccupancyAndLayerPerimetersHoldForEveryVariantFacingAndStatePhase() throws Exception {
        int logicalCases = 0;
        for (Family family : ShrineMonolithDefinitions.catalogue().families()) {
            for (Variant variant : family.variants()) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    Set<BlockPos> occupied = occupied(family, facing);
                    assertEquals(family.dimensions().cellCount(), occupied.size());
                    Map<Integer, Set<BlockPos>> perimeters = perimeterByLayer(occupied);
                    if (family.id().equals(ShrineMonolithDefinitions.SHRINE)) {
                        assertEquals(4, occupied.size());
                        assertEquals(Set.of(70), perimeters.keySet());
                        assertEquals(8, perimeters.get(70).size());
                    } else {
                        assertEquals(18, occupied.size());
                        assertEquals(Set.of(70, 71, 72), perimeters.keySet());
                        perimeters.values().forEach(layer -> assertEquals(10, layer.size()));
                        assertEquals(30, perimeters.values().stream().mapToInt(Set::size).sum());
                    }
                    for (PlacedStructureState phase : phases(family, variant, facing)) {
                        assertEquals(family.id(), phase.familyId());
                        assertEquals(facing, phase.facing());
                        assertEquals(family.footprint(), phase.footprint());
                        assertEquals(occupied, occupied(phase, ANCHOR));
                        assertEquals(perimeters, perimeterByLayer(occupied(phase, ANCHOR)));
                        logicalCases++;
                    }
                }
            }
        }
        assertEquals((9 + 2) * 4 * PHASE_COUNT, logicalCases);
    }

    @Test
    void exhaustiveRegisteredNeighborStatesNeverOverlapOrMutateRepresentativeStructures() throws Exception {
        List<BlockState> neighbors = neighborStates();
        assertTrue(neighbors.size() >= 80, "Matrix must retain broad registered vanilla coverage");
        int logicalCases = 0;
        for (Family family : ShrineMonolithDefinitions.catalogue().families()) {
            List<Variant> representatives = family.variants();
            for (Variant variant : representatives) {
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    for (PlacedStructureState phase : phases(family, variant, facing)) {
                        Map<BlockPos, BlockState> structure = structureStates(phase, ANCHOR);
                        Map<BlockPos, BlockState> snapshot = Map.copyOf(structure);
                        for (BlockPos neighborPos : perimeter(structure.keySet())) {
                            for (BlockState neighbor : neighbors) {
                                assertFalse(neighbor.canBeReplaced(), "Matrix states are deliberate installed controls");
                                assertNoCollisionOverlap(structure, neighborPos, neighbor);
                                Map<BlockPos, BlockState> simulated = new HashMap<>(structure);
                                simulated.put(neighborPos, neighbor);
                                snapshot.forEach((pos, state) -> assertEquals(state, simulated.get(pos)));
                                simulated.remove(neighborPos);
                                assertEquals(snapshot, simulated);
                                logicalCases++;
                            }
                        }
                    }
                }
            }
        }
        int perimeterSlots = (9 * 4 * 8) + (2 * 4 * 30);
        assertEquals(perimeterSlots * PHASE_COUNT * neighbors.size(), logicalCases);
    }

    @Test
    void stairOrientationHalfAndDirectCornerShapeMatrixIsCompleteAndCellLocal() {
        List<BlockState> stairs = stairStates();
        assertEquals(40, stairs.size());
        Set<Direction> facings = new HashSet<>();
        Set<Half> halves = new HashSet<>();
        Set<StairsShape> shapes = new HashSet<>();
        for (BlockState stair : stairs) {
            facings.add(stair.getValue(StairBlock.FACING));
            halves.add(stair.getValue(StairBlock.HALF));
            shapes.add(stair.getValue(StairBlock.SHAPE));
            assertLocalXZ(stair, BlockPos.ZERO);
        }
        assertEquals(Set.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST), facings);
        assertEquals(Set.of(Half.BOTTOM, Half.TOP), halves);
        assertEquals(Set.of(StairsShape.STRAIGHT, StairsShape.INNER_LEFT, StairsShape.INNER_RIGHT,
                StairsShape.OUTER_LEFT, StairsShape.OUTER_RIGHT), shapes);
        // Corner variants are direct registered block-state shape evidence (level C), not live placement.
    }

    @Test
    void supportedAttachmentControlsUseActualVanillaStatesAndSolidExteriorFaces() {
        BlockState structure = anchor.defaultBlockState();
        for (Direction face : Direction.values()) {
            assertTrue(structure.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, face));
        }
        List<BlockState> attachments = neighborStates().stream()
                .filter(state -> state.getBlock() instanceof ButtonBlock
                        || state.getBlock() instanceof LeverBlock
                        || state.getBlock() instanceof WallTorchBlock
                        || state.getBlock() instanceof LadderBlock)
                .toList();
        assertEquals(16, attachments.size());
        attachments.forEach(state -> assertLocalXZ(state, BlockPos.ZERO));
        assertFalse(part.defaultBlockState().canBeReplaced());
    }

    private static List<PlacedStructureState> phases(
            Family family, Variant variant, Direction facing) throws Exception {
        Variant next = family.variants().get((variant.cyclePosition() + 1) % family.variants().size());
        PlacedStructureState initial = new PlacedStructureState(
                family.id(), variant.id(), facing, family.footprint());
        PlacedStructureState cycled = new PlacedStructureState(
                family.id(), next.id(), facing, family.footprint());
        LargeStructureAnchorBlockEntity source = entity(facing);
        assertTrue(source.initialize(cycled));
        CompoundTag saved = source.saveWithoutMetadata(RegistryAccess.EMPTY);
        LargeStructureAnchorBlockEntity diskReceiver = entity(facing);
        diskReceiver.loadWithComponents(saved, RegistryAccess.EMPTY);
        PlacedStructureState disk = diskReceiver.placedState().orElseThrow();
        CompoundTag update = source.getUpdateTag(RegistryAccess.EMPTY);
        LargeStructureAnchorBlockEntity tagReceiver = entity(facing);
        tagReceiver.handleUpdateTag(update, RegistryAccess.EMPTY);
        PlacedStructureState updateTag = tagReceiver.placedState().orElseThrow();
        var constructor = ClientboundBlockEntityDataPacket.class.getDeclaredConstructor(
                BlockPos.class, BlockEntityType.class, CompoundTag.class);
        constructor.setAccessible(true);
        ClientboundBlockEntityDataPacket packet = constructor.newInstance(
                source.getBlockPos(), source.getType(), update.copy());
        LargeStructureAnchorBlockEntity packetReceiver = entity(facing);
        packetReceiver.onDataPacket(null, packet, RegistryAccess.EMPTY);
        PlacedStructureState updatePacket = packetReceiver.placedState().orElseThrow();
        PlacedStructureState missingResourceFallback = initial;
        return List.of(initial, cycled, disk, updateTag, updatePacket, missingResourceFallback);
    }

    private static LargeStructureAnchorBlockEntity entity(Direction facing) {
        return new LargeStructureAnchorBlockEntity(
                MilestoneTwoRegisteredTestContent.blockEntityType(), BlockPos.ZERO,
                anchor.defaultBlockState().setValue(LargeStructureAnchorBlock.FACING, facing));
    }

    private static Set<BlockPos> occupied(Family family, Direction facing) {
        return family.footprint().stream()
                .map(offset -> ShrinePlacementPlanner.worldPosition(ANCHOR, facing, offset))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Set<BlockPos> occupied(PlacedStructureState state, BlockPos anchorPos) {
        return state.footprint().stream()
                .map(offset -> ShrinePlacementPlanner.worldPosition(anchorPos, state.facing(), offset))
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private static Map<Integer, Set<BlockPos>> perimeterByLayer(Set<BlockPos> occupied) {
        Map<Integer, Set<BlockPos>> byLayer = new LinkedHashMap<>();
        for (BlockPos pos : perimeter(occupied)) {
            byLayer.computeIfAbsent(pos.getY(), ignored -> new HashSet<>()).add(pos);
        }
        return byLayer;
    }

    private static Set<BlockPos> perimeter(Set<BlockPos> occupied) {
        Set<BlockPos> perimeter = new HashSet<>();
        for (BlockPos cell : occupied) {
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                BlockPos candidate = cell.relative(direction);
                if (!occupied.contains(candidate)) perimeter.add(candidate.immutable());
            }
        }
        return Set.copyOf(perimeter);
    }

    private static Map<BlockPos, BlockState> structureStates(PlacedStructureState state, BlockPos anchorPos) {
        Map<BlockPos, BlockState> cells = new HashMap<>();
        for (LocalOffset offset : state.footprint()) {
            BlockPos pos = ShrinePlacementPlanner.worldPosition(anchorPos, state.facing(), offset);
            cells.put(pos, offset.equals(LocalOffset.ANCHOR)
                    ? anchor.defaultBlockState().setValue(LargeStructureAnchorBlock.FACING, state.facing())
                    : part.stateFor(state.facing(), offset));
        }
        return Map.copyOf(cells);
    }

    private static void assertNoCollisionOverlap(
            Map<BlockPos, BlockState> structure, BlockPos neighborPos, BlockState neighbor) {
        List<AABB> neighborBoxes = neighbor.getCollisionShape(
                        EmptyBlockGetter.INSTANCE, neighborPos).toAabbs().stream()
                .map(box -> box.move(neighborPos.getX(), neighborPos.getY(), neighborPos.getZ())).toList();
        for (var entry : structure.entrySet()) {
            List<AABB> structureBoxes = entry.getValue().getCollisionShape(
                            EmptyBlockGetter.INSTANCE, entry.getKey()).toAabbs().stream()
                    .map(box -> box.move(entry.getKey().getX(), entry.getKey().getY(), entry.getKey().getZ())).toList();
            for (AABB neighborBox : neighborBoxes) {
                for (AABB structureBox : structureBoxes) {
                    assertFalse(neighborBox.intersects(structureBox));
                }
            }
        }
    }

    private static void assertLocalXZ(BlockState state, BlockPos pos) {
        state.getShape(EmptyBlockGetter.INSTANCE, pos).toAabbs().forEach(box -> {
            assertTrue(box.minX >= 0 && box.minZ >= 0);
            assertTrue(box.maxX <= 1 && box.maxZ <= 1);
        });
        state.getCollisionShape(EmptyBlockGetter.INSTANCE, pos).toAabbs().forEach(box -> {
            assertTrue(box.minX >= 0 && box.minZ >= 0);
            assertTrue(box.maxX <= 1 && box.maxZ <= 1);
        });
    }

    private static List<BlockState> neighborStates() {
        List<BlockState> states = new ArrayList<>();
        states.add(Blocks.STONE.defaultBlockState());
        states.addAll(stairStates());
        for (SlabType type : SlabType.values()) {
            states.add(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type));
        }
        BlockState wall = Blocks.COBBLESTONE_WALL.defaultBlockState();
        states.add(wall);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            states.add(wall.setValue(wallProperty(direction), WallSide.LOW));
            states.add(wall.setValue(wallProperty(direction), WallSide.TALL));
            states.add(wall.setValue(wallProperty(direction), WallSide.LOW)
                    .setValue(wallProperty(direction.getOpposite()), WallSide.LOW));
        }
        BlockState fence = Blocks.OAK_FENCE.defaultBlockState();
        states.add(fence);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            states.add(fence.setValue(fenceProperty(direction), true));
        }
        states.add(fence.setValue(FenceBlock.NORTH, true).setValue(FenceBlock.EAST, true)
                .setValue(FenceBlock.SOUTH, true).setValue(FenceBlock.WEST, true));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            states.add(Blocks.OAK_FENCE_GATE.defaultBlockState()
                    .setValue(FenceGateBlock.FACING, direction)
                    .setValue(FenceGateBlock.IN_WALL, true));
            states.add(Blocks.OAK_FENCE_GATE.defaultBlockState()
                    .setValue(FenceGateBlock.FACING, direction)
                    .setValue(FenceGateBlock.OPEN, true));
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            states.add(Blocks.STONE_BUTTON.defaultBlockState()
                    .setValue(ButtonBlock.FACE, AttachFace.WALL).setValue(ButtonBlock.FACING, direction));
            states.add(Blocks.LEVER.defaultBlockState()
                    .setValue(LeverBlock.FACE, AttachFace.WALL).setValue(LeverBlock.FACING, direction));
            states.add(Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, direction));
            states.add(Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, direction));
        }
        return List.copyOf(states);
    }

    private static EnumProperty<WallSide> wallProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> WallBlock.NORTH_WALL;
            case EAST -> WallBlock.EAST_WALL;
            case SOUTH -> WallBlock.SOUTH_WALL;
            case WEST -> WallBlock.WEST_WALL;
            default -> throw new IllegalArgumentException("Horizontal direction required");
        };
    }

    private static BooleanProperty fenceProperty(Direction direction) {
        return switch (direction) {
            case NORTH -> FenceBlock.NORTH;
            case EAST -> FenceBlock.EAST;
            case SOUTH -> FenceBlock.SOUTH;
            case WEST -> FenceBlock.WEST;
            default -> throw new IllegalArgumentException("Horizontal direction required");
        };
    }

    private static List<BlockState> stairStates() {
        List<BlockState> states = new ArrayList<>();
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (Half half : Half.values()) {
                for (StairsShape shape : StairsShape.values()) {
                    states.add(Blocks.OAK_STAIRS.defaultBlockState()
                            .setValue(StairBlock.FACING, facing)
                            .setValue(StairBlock.HALF, half)
                            .setValue(StairBlock.SHAPE, shape));
                }
            }
        }
        return List.copyOf(states);
    }
}
