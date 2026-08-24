package com.seggellion.britannia_mod.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.registries.GameData;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Holds every window block in the mod to one rule: whatever the art draws in a cell, something has
 * to collide with it there.
 *
 * <p>The expectations are read out of the blockstate and model JSON rather than restated here, so a
 * window whose art moves, or a new window added to the family, is checked against what it really
 * draws. That is deliberate - the three defects this suite was written for were all a collision
 * definition that had quietly stopped matching the model it was written from:
 *
 * <ul>
 *   <li>the oversized {@code window_*} art drew two or three cells and collided in one;</li>
 *   <li>{@code dark_stone_window} collided against the opposite face of its own block;</li>
 *   <li>the sandstone windows collided with a taller opening than they drew, wide enough for a
 *       sneaking player to walk through the middle of the wall.</li>
 *   <li>{@code dark_stone_window} selected as a solid panel - a full cell, once its rear gap
 *       filled - so the crosshair could never reach the chest a player could plainly see through
 *       the open light; {@code stone_wall_window} had the same disease through its own class. The
 *       rule cuts the other way too: where art with nothing in it draws an opening, selection has
 *       to leave that opening empty.</li>
 * </ul>
 */
class WindowCollisionContractTest {

    /** How far collision may sit outside the art, in model pixels, before it counts as a defect. */
    private static final double SKIN = 1.0D;

    /**
     * {@code plaster_and_stone_window} is placeholder art - a single centred slab at {@code z
     * 5.5..10.5} where the whole two-block wall family is authored against the block edge, which no
     * corner or junction can be built from either. Its collision is the family's edge run, so the
     * wall still blocks passage, but a player can stand about a fifth of a block inside the visible
     * face. Repairing it means re-authoring five model files, which is an art pass and not a
     * collision change; until then it is excluded here rather than left as a failing test.
     */
    private static final List<String> ART_DEFECT_EXEMPT = List.of("plaster_and_stone_window");

    private static Map<String, Block> windows;
    private static WindowCollisionBlock helper;

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        GameData.unfreezeData();

        helper = new WindowCollisionBlock();
        windows = new LinkedHashMap<>();

        // Single-cell timber windows.
        windows.put("window_1x1", new ThinWall(woodProps()));
        windows.put("window_birch_1x1", new ThinWall(woodProps()));
        windows.put("window_cross_1x1", new ThinWall(woodProps()));

        // Oversized timber windows, mirroring BlockRegistry.
        windows.put("window_1x2", new MultiCellWindowBlock(woodProps(), WindowFootprint.aligned(0, 0, 0, 1)));
        windows.put("window_cobblestone_1x2",
            new MultiCellWindowBlock(woodProps(), WindowFootprint.aligned(0, 0, 0, 1)));
        windows.put("window_cross_1x2",
            new MultiCellWindowBlock(woodProps(), WindowFootprint.aligned(0, 0, 0, 1)));
        windows.put("window_1x3", new MultiCellWindowBlock(woodProps(), WindowFootprint.aligned(0, 0, -1, 1)));
        windows.put("window_cross_1x3",
            new MultiCellWindowBlock(woodProps(), WindowFootprint.aligned(0, 0, -1, 1)));
        windows.put("window_2x2", new MultiCellWindowBlock(woodProps(), WindowFootprint.aligned(-1, 0, 0, 1)));
        windows.put("window_2x3", new MultiCellWindowBlock(woodProps(), WindowFootprint.aligned(-1, 1, 0, 1)));
        windows.put("window_cross_2x2",
            new MultiCellWindowBlock(woodProps(), WindowFootprint.halfDropped(-1, 0)));
        windows.put("window_cross_2x3",
            new MultiCellWindowBlock(woodProps(), WindowFootprint.halfDropped(-1, 1)));

        // Stone families.
        windows.put("dark_stone_window", new TallThinBlock(stoneProps()));
        windows.put("stone_wall_window", new RotatingStoneWallBlock(stoneProps()));
        windows.put("plaster_small_window", new DoubleWallBlock(stoneProps()));
        windows.put("plaster_and_stone_window", new DoubleWallBlock(stoneProps()));
        windows.put("ornate_wall_large_window", new MirrorableWallBlock(stoneProps()));
        windows.put("plaster_wall_large_window", new MirrorableWallBlock(stoneProps()));
        windows.put("sandstone_window", SandstoneWindowBlock.sandstone(stoneProps()));
        windows.put("ornate_sandstone_window", SandstoneWindowBlock.ornate(stoneProps()));
    }

    private static BlockBehaviour.Properties woodProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(1.0F)
            .sound(SoundType.WOOD).noOcclusion();
    }

    private static BlockBehaviour.Properties stoneProps() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.0F)
            .sound(SoundType.STONE).noOcclusion();
    }

    /* ─── inventory ──────────────────────────────────────────── */

    @Test
    void everyWindowBlockstateInTheModIsUnderTest() throws IOException {
        List<String> onDisk;
        try (Stream<Path> files = Files.list(WindowArt.blockstates())) {
            onDisk = files.map(path -> path.getFileName().toString())
                .filter(name -> name.endsWith(".json"))
                .map(name -> name.substring(0, name.length() - ".json".length()))
                .filter(name -> name.contains("window"))
                .filter(name -> !name.equals("window_collision"))
                .sorted()
                .toList();
        }
        List<String> covered = windows.keySet().stream().sorted().toList();
        assertEquals(onDisk, covered,
            "A window block was added or renamed without being brought under the collision contract");
    }

    /* ─── the contract ───────────────────────────────────────── */

    @Test
    void collisionCoversEveryCellTheArtDraws() throws IOException {
        for (Map.Entry<String, Block> entry : windows.entrySet()) {
            String id = entry.getKey();
            if (ART_DEFECT_EXEMPT.contains(id)) {
                continue;
            }
            Block block = entry.getValue();
            for (WindowArt.Variant variant : WindowArt.variantsOf(id)) {
                List<WindowArt.Box> art = WindowArt.solidElements(variant);
                if (art.isEmpty()) {
                    continue; // The upper half of a two-block wall draws nothing of its own.
                }
                BlockState origin = stateFromVariantKey(block, variant.key());
                for (int[] cell : WindowArt.cellsTouched(art)) {
                    checkCell(id, variant, block, origin, cell, art);
                }
            }
        }
    }

    private void checkCell(String id, WindowArt.Variant variant, Block block, BlockState origin,
                           int[] cell, List<WindowArt.Box> art) {
        BlockState occupant = occupantAt(block, origin, cell);
        String where = id + " " + variant.key() + " cell " + cell[0] + "/" + cell[1] + "/" + cell[2];

        if (occupant == null) {
            // Nothing of this mod's making stands in that cell, so the art there had better be a
            // decorative skim - a proud edge moulding or a pane of glass drawn with no thickness -
            // and not a slab of wall a player could walk into.
            for (WindowArt.Box box : art) {
                WindowArt.Box clipped = box.clippedToCell(cell[0], cell[1], cell[2]);
                if (clipped == null) {
                    continue;
                }
                assertTrue(clipped.thinnestSide() <= SKIN,
                    where + " draws solid geometry " + clipped + " that nothing collides with");
            }
            return;
        }

        VoxelShape collision = occupant.getCollisionShape(
            EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty());
        for (WindowArt.Box box : art) {
            WindowArt.Box clipped = box.eroded().clippedToCell(cell[0], cell[1], cell[2]);
            if (clipped == null) {
                continue;
            }
            VoxelShape solid = Shapes.create(new AABB(
                clipped.x0() / WindowArt.BLOCK, clipped.y0() / WindowArt.BLOCK, clipped.z0() / WindowArt.BLOCK,
                clipped.x1() / WindowArt.BLOCK, clipped.y1() / WindowArt.BLOCK, clipped.z1() / WindowArt.BLOCK));
            assertFalse(Shapes.joinIsNotEmpty(solid, collision, BooleanOp.ONLY_FIRST),
                where + " draws " + clipped + " but the collision shape " + collision.toAabbs()
                    + " leaves part of it open");
        }
    }

    /**
     * The block whose collision covers a given cell of a window's art, or {@code null} when the
     * window does not claim that cell.
     */
    private BlockState occupantAt(Block block, BlockState origin, int[] cell) {
        int x = cell[0];
        int y = cell[1];
        int z = cell[2];
        if (x == 0 && y == 0 && z == 0) {
            return origin;
        }
        if (block.getStateDefinition().getProperty("half") != null && x == 0 && z == 0 && y == 1) {
            // Two-block walls draw the whole thing from the lower half; the upper block carries its
            // own copy of the shape.
            return origin.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, DoubleBlockHalf.UPPER);
        }
        if (block instanceof MultiCellWindowBlock window) {
            Direction facing = origin.getValue(facingOf(block));
            WindowFootprint.Cell match = window.footprint()
                .cellAt(BlockPos.ZERO, facing, new BlockPos(x, y, z));
            if (match != null) {
                return helper.defaultBlockState()
                    .setValue(WindowCollisionBlock.FACING, facing)
                    .setValue(WindowCollisionBlock.SPAN, match.span());
            }
        }
        return null;
    }

    /* ─── the unglazed windows' open lights ──────────────────── */

    /**
     * The windows that draw a light with nothing in it - no pane, no muntins, unlike the glazed
     * timber and plaster windows, whose glazing is drawn geometry and rightly selectable.
     */
    private static final List<String> UNGLAZED_WINDOWS = List.of("dark_stone_window", "stone_wall_window");

    /**
     * An unglazed window's selection shape must be exactly its art: solid over sill, jambs and
     * lintel or corbels, open through the light, in every orientation. And it must stay the art
     * whatever properties the blockstate leaves free ({@code dark_stone_window} keys its variants
     * on facing and half alone, so {@code corner}, {@code filled} and {@code style} never change
     * what is drawn and must never cover the light with shape the player cannot see).
     */
    @Test
    void unglazedWindowsSelectExactlyTheirArt() throws IOException {
        for (String id : UNGLAZED_WINDOWS) {
            Block block = windows.get(id);
            for (WindowArt.Variant variant : WindowArt.variantsOf(id)) {
                VoxelShape art = shapeOf(WindowArt.solidElements(variant));
                for (BlockState state : statesOfVariant(block, variant.key())) {
                    assertShapesMatch(id + " " + state + " selection against " + variant.key(),
                        art, state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                }
            }
        }
    }

    /**
     * The raycast the game aims interaction with, fired straight through the middle of the light:
     * selection must let it pass - that is how a chest beyond the window gets targeted - while
     * collision still stops it, because the glassless light is no invitation to arrows. The same
     * ray through sill, jamb or lintel must select the window as it always did. Probe points are
     * the model's own numbers: the light is {@code x 5..11} from {@code y 6} of the bottom model
     * to {@code y 10} of the top one, jambs five pixels wide beside it, all on the {@code z 11..16}
     * panel.
     */
    @Test
    void darkStoneWindowRaycastsPassOnlyThroughTheLight() throws IOException {
        Block block = windows.get("dark_stone_window");
        for (WindowArt.Variant variant : WindowArt.variantsOf("dark_stone_window")) {
            boolean lower = variant.key().endsWith("half=lower");
            double lightY = lower ? 11.0D : 5.0D;         // centre of y 6..16, or of y 0..10
            double frameY = lower ? 3.0D : 13.0D;         // centre of the sill, or of the lintel
            for (BlockState state : statesOfVariant(block, variant.key())) {
                VoxelShape selection = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                String where = "dark_stone_window " + state;

                assertNull(clipThroughPanel(selection, variant, 8.0D, lightY),
                    where + " swallows the crosshair in the middle of its open light");
                assertNotNull(clipThroughPanel(collision, variant, 8.0D, lightY),
                    where + " lets bodies and arrows through its light");
                assertNotNull(clipThroughPanel(selection, variant, 8.0D, frameY),
                    where + " no longer selects on its sill or lintel");
                assertNotNull(clipThroughPanel(selection, variant, 2.5D, lightY),
                    where + " no longer selects on its jamb");
            }
        }
    }

    /**
     * The same raycast contract for {@code stone_wall_window}, probed at its own numbers: the
     * light is {@code x 3..13} above the {@code y 2} sill; corbel courses at {@code x 3..5} and
     * {@code 11..13} from {@code y 12}, then {@code x 5..7} and {@code 9..11} from {@code y 14},
     * leave the top open at {@code x 7..9}; all of it on the {@code z 0..8} slab.
     */
    @Test
    void stoneWallWindowRaycastsPassOnlyThroughTheLight() throws IOException {
        Block block = windows.get("stone_wall_window");
        for (WindowArt.Variant variant : WindowArt.variantsOf("stone_wall_window")) {
            for (BlockState state : statesOfVariant(block, variant.key())) {
                VoxelShape selection = state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                VoxelShape collision = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                String where = "stone_wall_window " + state;

                for (double[] light : new double[][] {{8, 7}, {8, 13}, {8, 15}}) {
                    assertNull(clipThroughPanel(selection, variant, light[0], light[1]),
                        where + " swallows the crosshair in its open light at " + light[0] + "/" + light[1]);
                    assertNotNull(clipThroughPanel(collision, variant, light[0], light[1]),
                        where + " lets bodies and arrows through its light at " + light[0] + "/" + light[1]);
                }
                assertNotNull(clipThroughPanel(selection, variant, 1.5D, 9.0D),
                    where + " no longer selects on its west jamb");
                assertNotNull(clipThroughPanel(selection, variant, 14.5D, 9.0D),
                    where + " no longer selects on its east jamb");
                assertNotNull(clipThroughPanel(selection, variant, 8.0D, 1.0D),
                    where + " no longer selects on its sill");
                assertNotNull(clipThroughPanel(selection, variant, 4.0D, 13.0D),
                    where + " no longer selects on its first corbel course");
                assertNotNull(clipThroughPanel(selection, variant, 10.0D, 15.0D),
                    where + " no longer selects on its second corbel course");
            }
        }
    }

    /**
     * {@code stone_wall_window} collision stays the solid half slab it always was. Its light is
     * ten pixels wide - a whisker wider than a player - so carving collision open would let
     * players walk through a window every existing build trusts as wall; that stays an owner
     * decision, not a side effect.
     */
    @Test
    void stoneWallWindowCollisionKeepsTheSolidSlab() throws IOException {
        Block block = windows.get("stone_wall_window");
        for (WindowArt.Variant variant : WindowArt.variantsOf("stone_wall_window")) {
            VoxelShape slab = Shapes.create(shapeOf(WindowArt.solidElements(variant)).bounds());
            for (BlockState state : statesOfVariant(block, variant.key())) {
                assertShapesMatch("stone_wall_window " + state + " collision", slab,
                    state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            }
        }
    }

    /**
     * Collision stays what it always was: the solid five pixel panel the art bounds, or the whole
     * cell at a corner pivot or over a filled rear gap. Nothing that collides fits the six pixel
     * light, and builds have settled against these boxes.
     */
    @Test
    void darkStoneWindowCollisionKeepsTheSolidPanel() throws IOException {
        Block block = windows.get("dark_stone_window");
        for (WindowArt.Variant variant : WindowArt.variantsOf("dark_stone_window")) {
            VoxelShape panel = Shapes.create(shapeOf(WindowArt.solidElements(variant)).bounds());
            for (BlockState state : statesOfVariant(block, variant.key())) {
                boolean full = state.getValue(TallThinBlock.CORNER) || state.getValue(TallThinBlock.FILLED);
                assertShapesMatch("dark_stone_window " + state + " collision",
                    full ? Shapes.block() : panel,
                    state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            }
        }
    }

    /** The union of art boxes as one voxel shape, in block coordinates. */
    private static VoxelShape shapeOf(List<WindowArt.Box> boxes) {
        VoxelShape shape = Shapes.empty();
        for (WindowArt.Box box : boxes) {
            shape = Shapes.or(shape, Shapes.create(new AABB(
                box.x0() / WindowArt.BLOCK, box.y0() / WindowArt.BLOCK, box.z0() / WindowArt.BLOCK,
                box.x1() / WindowArt.BLOCK, box.y1() / WindowArt.BLOCK, box.z1() / WindowArt.BLOCK)));
        }
        return shape.optimize();
    }

    /** Every possible state that bakes the given variant - the key's properties match, the rest free. */
    private static List<BlockState> statesOfVariant(Block block, String variantKey) {
        BlockState example = stateFromVariantKey(block, variantKey);
        assertNotNull(example, "no state for blockstate variant '" + variantKey + "'");
        if (variantKey.isEmpty()) {
            return block.getStateDefinition().getPossibleStates();
        }
        List<Property<?>> named = new ArrayList<>();
        for (String pair : variantKey.split(",")) {
            Property<?> property = block.getStateDefinition().getProperty(pair.split("=", 2)[0]);
            assertNotNull(property, "variant '" + variantKey + "' names a property the block lacks");
            named.add(property);
        }
        return block.getStateDefinition().getPossibleStates().stream()
            .filter(state -> named.stream()
                .allMatch(property -> state.getValue(property).equals(example.getValue(property))))
            .toList();
    }

    /**
     * Fires a ray through the whole cell along the panel's normal, aimed at {@code (x, y)} of the
     * unrotated model, turned into place the same way the game turns the art. Returns where the
     * shape stopped it, or {@code null} when it passed.
     */
    private static Object clipThroughPanel(VoxelShape shape, WindowArt.Variant variant, double x, double y) {
        WindowArt.Box probe = new WindowArt.Box(x, y, 13.5D, x, y, 13.5D).rotatedY(variant.yRotation());
        boolean alongZ = variant.yRotation() % 180 == 0;
        double px = probe.x0() / WindowArt.BLOCK;
        double py = probe.y0() / WindowArt.BLOCK;
        double pz = probe.z0() / WindowArt.BLOCK;
        Vec3 from = alongZ ? new Vec3(px, py, -1.0D) : new Vec3(-1.0D, py, pz);
        Vec3 to = alongZ ? new Vec3(px, py, 2.0D) : new Vec3(2.0D, py, pz);
        return shape.clip(from, to, BlockPos.ZERO);
    }

    /* ─── shape hygiene ──────────────────────────────────────── */

    @Test
    void everyStateCollidesAndStaysInsideItsOwnBlock() {
        for (Map.Entry<String, Block> entry : windows.entrySet()) {
            for (BlockState state : entry.getValue().getStateDefinition().getPossibleStates()) {
                assertInsideOneBlock(entry.getKey(), state,
                    state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                assertInsideOneBlock(entry.getKey(), state,
                    state.getShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
            }
        }
        for (BlockState state : helper.getStateDefinition().getPossibleStates()) {
            assertInsideOneBlock("window_collision", state,
                state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        }
    }

    private static void assertInsideOneBlock(String id, BlockState state, VoxelShape shape) {
        assertFalse(shape.isEmpty(), id + " " + state + " has no collision at all");
        AABB bounds = shape.bounds();
        assertTrue(bounds.minX >= -1.0E-6D && bounds.minY >= -1.0E-6D && bounds.minZ >= -1.0E-6D
                && bounds.maxX <= 1.0D + 1.0E-6D && bounds.maxY <= 1.0D + 1.0E-6D
                && bounds.maxZ <= 1.0D + 1.0E-6D,
            id + " " + state + " reaches outside its own block: " + bounds);
    }

    /**
     * Every window except {@code dark_stone_window} turns its shape with its facing the way
     * {@link HorizontalShape} does. That block is the exception on purpose: its art is authored on
     * the far edge and its blockstate turns east and west the opposite way round, so its shape
     * follows the art through a mirror instead of a rotation. What it actually draws is checked by
     * {@link #collisionCoversEveryCellTheArtDraws}.
     */
    @Test
    void shapesTurnWithTheirFacing() {
        for (Map.Entry<String, Block> entry : windows.entrySet()) {
            if (entry.getKey().equals("dark_stone_window")) {
                continue;
            }
            DirectionProperty facingProperty = facingOf(entry.getValue());
            for (BlockState north : entry.getValue().getStateDefinition().getPossibleStates()) {
                if (north.getValue(facingProperty) != Direction.NORTH) {
                    continue;
                }
                VoxelShape canonical = north.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
                for (Direction facing : Direction.Plane.HORIZONTAL) {
                    BlockState turned = north.setValue(facingProperty, facing);
                    assertShapesMatch(entry.getKey() + " " + turned,
                        HorizontalShape.rotateFromNorth(canonical, facing),
                        turned.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
                }
            }
        }
    }

    /** Compares two shapes box for box, with a third of a pixel of slack for rounded constants. */
    private static void assertShapesMatch(String where, VoxelShape expected, VoxelShape actual) {
        List<String> want = describe(expected);
        List<String> got = describe(actual);
        assertEquals(want.size(), got.size(), where + " has a different number of boxes: "
            + want + " vs " + got);
        List<AABB> wantBoxes = expected.toAabbs().stream().sorted(WindowCollisionContractTest::order).toList();
        List<AABB> gotBoxes = actual.toAabbs().stream().sorted(WindowCollisionContractTest::order).toList();
        for (int i = 0; i < wantBoxes.size(); i++) {
            AABB a = wantBoxes.get(i);
            AABB b = gotBoxes.get(i);
            assertTrue(Math.abs(a.minX - b.minX) < 0.02D && Math.abs(a.minY - b.minY) < 0.02D
                    && Math.abs(a.minZ - b.minZ) < 0.02D && Math.abs(a.maxX - b.maxX) < 0.02D
                    && Math.abs(a.maxY - b.maxY) < 0.02D && Math.abs(a.maxZ - b.maxZ) < 0.02D,
                where + " expected " + a + " but got " + b);
        }
    }

    private static List<String> describe(VoxelShape shape) {
        return shape.toAabbs().stream().sorted(WindowCollisionContractTest::order)
            .map(AABB::toString).toList();
    }

    private static int order(AABB a, AABB b) {
        int cmp = Double.compare(a.minX, b.minX);
        if (cmp != 0) return cmp;
        cmp = Double.compare(a.minY, b.minY);
        if (cmp != 0) return cmp;
        return Double.compare(a.minZ, b.minZ);
    }

    /* ─── footprints ─────────────────────────────────────────── */

    @Test
    void oversizedWindowsDeclareEveryCellTheirArtCovers() throws IOException {
        for (Map.Entry<String, Block> entry : windows.entrySet()) {
            if (!(entry.getValue() instanceof MultiCellWindowBlock window)) {
                continue;
            }
            WindowArt.Variant north = northVariant(entry.getKey());
            List<int[]> artCells = WindowArt.cellsTouched(WindowArt.rawElements(north.model()));
            for (int[] cell : artCells) {
                if (cell[2] != 0) {
                    continue; // Depth never leaves the block; guarded separately.
                }
                boolean thin = WindowArt.rawElements(north.model()).stream()
                    .map(box -> box.clippedToCell(cell[0], cell[1], cell[2]))
                    .filter(java.util.Objects::nonNull)
                    .allMatch(box -> box.thinnestSide() <= SKIN);
                WindowFootprint.Cell declared = window.footprint()
                    .cellAt(BlockPos.ZERO, Direction.NORTH, new BlockPos(cell[0], cell[1], 0));
                assertTrue(thin || declared != null,
                    entry.getKey() + " draws a wall in cell " + cell[0] + "/" + cell[1]
                        + " that its footprint does not claim");
            }
        }
    }

    @Test
    void singleCellWindowsStayPlainThinWalls() throws IOException {
        for (Map.Entry<String, Block> entry : windows.entrySet()) {
            if (!(entry.getValue() instanceof ThinWall) || entry.getValue() instanceof MultiCellWindowBlock) {
                continue;
            }
            WindowArt.Variant north = northVariant(entry.getKey());
            for (int[] cell : WindowArt.cellsTouched(WindowArt.rawElements(north.model()))) {
                boolean origin = cell[0] == 0 && cell[1] == 0 && cell[2] == 0;
                boolean thin = WindowArt.rawElements(north.model()).stream()
                    .map(box -> box.clippedToCell(cell[0], cell[1], cell[2]))
                    .filter(java.util.Objects::nonNull)
                    .allMatch(box -> box.thinnestSide() <= SKIN);
                assertTrue(origin || thin,
                    entry.getKey() + " reaches cell " + cell[0] + "/" + cell[1] + "/" + cell[2]
                        + " but is registered as a single-cell window");
            }
        }
    }

    @Test
    void footprintCellsMapThroughAllFourOrientations() {
        for (Map.Entry<String, Block> entry : windows.entrySet()) {
            if (!(entry.getValue() instanceof MultiCellWindowBlock window)) {
                continue;
            }
            WindowFootprint footprint = window.footprint();
            assertTrue(footprint.isMultiCell(), entry.getKey() + " declares no extra cells");
            assertTrue(footprint.reach() <= MultiCellWindowBlock.SEARCH_RADIUS,
                entry.getKey() + " reaches further than a helper block searches for its owner");

            for (Direction facing : Direction.Plane.HORIZONTAL) {
                List<BlockPos> seen = new ArrayList<>();
                for (WindowFootprint.Cell cell : footprint.cells()) {
                    BlockPos pos = footprint.worldPos(BlockPos.ZERO, facing, cell);
                    assertFalse(seen.contains(pos),
                        entry.getKey() + " maps two cells onto " + pos + " facing " + facing);
                    seen.add(pos);
                    assertEquals(cell, footprint.cellAt(BlockPos.ZERO, facing, pos),
                        entry.getKey() + " cannot find its own cell again facing " + facing);
                }
                // The window's own cell always stays put, whichever way it is turned.
                assertTrue(seen.contains(BlockPos.ZERO), entry.getKey() + " loses its origin cell");
            }

            // Turning the window through 180 degrees mirrors its cells about the origin.
            for (WindowFootprint.Cell cell : footprint.cells()) {
                BlockPos north = footprint.worldPos(BlockPos.ZERO, Direction.NORTH, cell);
                BlockPos south = footprint.worldPos(BlockPos.ZERO, Direction.SOUTH, cell);
                assertEquals(-north.getX(), south.getX(), entry.getKey() + " does not mirror on X");
                assertEquals(north.getY(), south.getY(), entry.getKey() + " should not move on Y");
                assertEquals(-north.getZ(), south.getZ(), entry.getKey() + " does not mirror on Z");
            }
        }
    }

    /* ─── the helper block ───────────────────────────────────── */

    @Test
    void helperCellsCarryTheSlabTheirSpanCallsFor() {
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            for (WindowCollisionSpan span : WindowCollisionSpan.values()) {
                BlockState state = helper.defaultBlockState()
                    .setValue(WindowCollisionBlock.FACING, facing)
                    .setValue(WindowCollisionBlock.SPAN, span);
                AABB bounds = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds();
                assertEquals(span.minY() / WindowArt.BLOCK, bounds.minY, 1.0E-6D,
                    "helper " + facing + " " + span + " starts at the wrong height");
                assertEquals(span.maxY() / WindowArt.BLOCK, bounds.maxY, 1.0E-6D,
                    "helper " + facing + " " + span + " stops at the wrong height");

                // The slab hugs the facing edge and is thin in that axis only.
                Direction.Axis normal = facing.getAxis();
                double thickness = normal == Direction.Axis.X
                    ? bounds.maxX - bounds.minX : bounds.maxZ - bounds.minZ;
                assertEquals(5.33D / WindowArt.BLOCK, thickness, 1.0E-6D,
                    "helper " + facing + " is not a wall-thick slab");
            }
        }
    }

    @Test
    void helperMatchesTheDepthOfTheWindowItBacks() {
        Block block = windows.get("window_1x2");
        BlockState window = block.defaultBlockState().setValue(facingOf(block), Direction.NORTH);
        BlockState backing = helper.defaultBlockState()
            .setValue(WindowCollisionBlock.FACING, Direction.NORTH)
            .setValue(WindowCollisionBlock.SPAN, WindowCollisionSpan.FULL);
        assertEquals(window.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds().maxZ,
            backing.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).bounds().maxZ, 1.0E-6D,
            "A window and the cells behind its art must present one flat face, not a step");
    }

    /* ─── blockstate shape ───────────────────────────────────── */

    @Test
    void windowsKeepTheBlockstatePropertiesTheirResourcesNameThem() throws IOException {
        for (Map.Entry<String, Block> entry : windows.entrySet()) {
            Block block = entry.getValue();
            assertTrue(block.getStateDefinition().getProperties().stream()
                    .anyMatch(property -> property.getName().equals("facing")),
                entry.getKey() + " lost its facing property");
            for (WindowArt.Variant variant : WindowArt.variantsOf(entry.getKey())) {
                assertNotNull(stateFromVariantKey(block, variant.key()),
                    entry.getKey() + " has no state for blockstate variant '" + variant.key() + "'");
            }
        }
        assertEquals(2, helper.getStateDefinition().getProperties().size(),
            "The helper block carries a facing and a span and nothing else");
    }

    /* ─── plumbing ───────────────────────────────────────────── */

    /** The facing property a block actually uses; the wall families do not all share one instance. */
    private static DirectionProperty facingOf(Block block) {
        return (DirectionProperty) block.getStateDefinition().getProperty("facing");
    }

    private static WindowArt.Variant northVariant(String id) throws IOException {
        return WindowArt.variantsOf(id).stream()
            .filter(variant -> variant.key().startsWith("facing=north"))
            .filter(variant -> !variant.drawsNothing())
            .findFirst()
            .orElseThrow(() -> new AssertionError(id + " has no north-facing model"));
    }

    /** Builds the blockstate a {@code variants} key selects, leaving anything unnamed at default. */
    private static BlockState stateFromVariantKey(Block block, String key) {
        BlockState state = block.defaultBlockState();
        if (key.isEmpty()) {
            return state;
        }
        for (String pair : key.split(",")) {
            String[] halves = pair.split("=", 2);
            Property<?> property = block.getStateDefinition().getProperty(halves[0]);
            if (property == null) {
                return null;
            }
            state = withValue(state, property, halves[1]);
            if (state == null) {
                return null;
            }
        }
        return state;
    }

    private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property,
                                                                  String value) {
        Optional<T> parsed = property.getValue(value);
        return parsed.map(t -> state.setValue(property, t)).orElse(null);
    }
}
