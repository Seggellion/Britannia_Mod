package com.seggellion.britannia_mod.block;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Shared root/part block for non-inventory decorative structures.
 *
 * <p>Every occupied cell uses the final public block ID. The {@code part} value encodes a local
 * offset, so any cell can resolve the root without a block entity. Only the root renders the
 * complete model; the other cells provide occupancy and deliberately authored collision.
 */
public class DecorativeMultiblockBlock extends Block {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 26);
    private static final ThreadLocal<Boolean> MUTATING = ThreadLocal.withInitial(() -> false);

    private final int minX;
    private final int minY;
    private final int minZ;
    private final List<Cell> cells;
    private final int rootPart;
    private final List<Map<Direction, VoxelShape>> shapes;

    public DecorativeMultiblockBlock(
            Properties properties,
            int minX,
            int maxX,
            int minY,
            int maxY,
            int minZ,
            int maxZ,
            CellShapeFactory shapeFactory) {
        super(properties);
        if (minX > 0 || maxX < 0 || minY > 0 || maxY < 0 || minZ > 0 || maxZ < 0) {
            throw new IllegalArgumentException("Decorative multiblock ranges must contain the root");
        }
        this.minX = minX;
        this.minY = minY;
        this.minZ = minZ;
        List<Cell> generated = new ArrayList<>();
        List<Map<Direction, VoxelShape>> generatedShapes = new ArrayList<>();
        int discoveredRoot = -1;
        for (int y = minY; y <= maxY; y++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int x = minX; x <= maxX; x++) {
                    int part = generated.size();
                    if (part > 26) {
                        throw new IllegalArgumentException("Decorative multiblock exceeds 27 cells");
                    }
                    Cell cell = new Cell(part, x, y, z);
                    generated.add(cell);
                    if (x == 0 && y == 0 && z == 0) {
                        discoveredRoot = part;
                    }
                    VoxelShape north = shapeFactory.shape(x, y, z);
                    Map<Direction, VoxelShape> rotations = new EnumMap<>(Direction.class);
                    rotations.put(Direction.NORTH, north);
                    rotations.put(Direction.EAST, rotateY(north, 1));
                    rotations.put(Direction.SOUTH, rotateY(north, 2));
                    rotations.put(Direction.WEST, rotateY(north, 3));
                    generatedShapes.add(rotations);
                }
            }
        }
        this.cells = List.copyOf(generated);
        this.rootPart = discoveredRoot;
        this.shapes = List.copyOf(generatedShapes);
        registerDefaultState(stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(PART, rootPart));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, PART);
    }

    public List<Cell> cells() {
        return cells;
    }

    public int minimumY() {
        return minY;
    }

    public boolean isRoot(BlockState state) {
        return state.getValue(PART) == rootPart;
    }

    public BlockState stateFor(Direction facing, Cell cell) {
        return defaultBlockState().setValue(FACING, facing).setValue(PART, cell.part());
    }

    public Cell cell(BlockState state) {
        if (!hasValidPart(state)) {
            throw new IllegalArgumentException("Block state contains an unused decorative multiblock part");
        }
        return cells.get(state.getValue(PART));
    }

    public boolean hasValidPart(BlockState state) {
        int part = state.getValue(PART);
        return part >= 0 && part < cells.size();
    }

    /** Converts the minimum footprint corner selected by the item into the model/root cell. */
    public BlockPos anchorForMinimumPosition(BlockPos minimumPosition, Direction facing) {
        Direction right = facing.getClockWise();
        return minimumPosition
                .relative(right, -minX)
                .relative(facing, minZ)
                .above(-minY);
    }

    public BlockPos worldPosition(BlockPos anchor, Direction facing, Cell cell) {
        return anchor
                .relative(facing.getClockWise(), cell.x())
                .relative(facing.getOpposite(), cell.z())
                .above(cell.y());
    }

    public BlockPos anchorPosition(BlockPos position, BlockState state) {
        Direction facing = state.getValue(FACING);
        Cell cell = cell(state);
        return position
                .relative(facing.getCounterClockWise(), cell.x())
                .relative(facing, cell.z())
                .below(cell.y());
    }

    /** Whether a structure is being reshuffled right now, so its half-finished state is not read. */
    public static boolean isMutating() {
        return MUTATING.get();
    }

    public <T> T duringMutation(Supplier<T> mutation) {
        boolean previous = MUTATING.get();
        MUTATING.set(true);
        try {
            return mutation.get();
        } finally {
            MUTATING.set(previous);
        }
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return isRoot(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return authoredShape(state);
    }

    /**
     * The shape this cell was authored with, before any subclass adds anything to it.
     *
     * <p>Separated from {@link #getShape} so a subclass that answers for a neighbouring structure -
     * a large crate carrying a compact column on its lid - can still ask what its own art measures
     * without calling back into its own override.
     */
    public VoxelShape authoredShape(BlockState state) {
        int part = state.getValue(PART);
        if (part >= shapes.size()) {
            return Shapes.empty();
        }

        return shapes.get(part).get(state.getValue(FACING));
    }

    @Override
    public VoxelShape getCollisionShape(
            BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of();
    }

    @Override
    public ItemStack getCloneItemStack(LevelReader level, BlockPos pos, BlockState state) {
        return new ItemStack(asItem());
    }

    @Override
    public boolean onDestroyedByPlayer(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            boolean willHarvest,
            FluidState fluid) {
        if (level instanceof ServerLevel server && !MUTATING.get() && hasValidPart(state)) {
            dismantle(server, anchorPosition(pos, state), state.getValue(FACING),
                    !player.hasInfiniteMaterials());
            return true;
        }
        return super.onDestroyedByPlayer(state, level, pos, player, willHarvest, fluid);
    }

    @Override
    public void playerDestroy(
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            net.minecraft.world.level.block.entity.BlockEntity entity,
            ItemStack tool) {
        // onDestroyedByPlayer performs the single authoritative teardown and drop.
    }

    @Override
    protected void onExplosionHit(
            BlockState state,
            Level level,
            BlockPos pos,
            Explosion explosion,
            java.util.function.BiConsumer<ItemStack, BlockPos> dropConsumer) {
        if (level instanceof ServerLevel server && !MUTATING.get() && hasValidPart(state)) {
            dismantle(server, anchorPosition(pos, state), state.getValue(FACING), false);
        }
    }

    @Override
    public void onBlockExploded(BlockState state, Level level, BlockPos pos, Explosion explosion) {
        if (level instanceof ServerLevel server && !MUTATING.get() && hasValidPart(state)) {
            dismantle(server, anchorPosition(pos, state), state.getValue(FACING), false);
        }
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (level instanceof ServerLevel server
                && !state.is(newState.getBlock())
                && !MUTATING.get()
                && hasValidPart(state)) {
            dismantle(server, anchorPosition(pos, state), state.getValue(FACING), false);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    protected void neighborChanged(
            BlockState state,
            Level level,
            BlockPos pos,
            Block neighbor,
            BlockPos neighborPos,
            boolean moving) {
        super.neighborChanged(state, level, pos, neighbor, neighborPos, moving);
        if (level instanceof ServerLevel server && !MUTATING.get() && hasValidPart(state)) {
            BlockPos anchor = anchorPosition(pos, state);
            if (!structureMatches(server, anchor, state.getValue(FACING))) {
                dismantle(server, anchor, state.getValue(FACING), false);
            }
        }
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    private boolean structureMatches(ServerLevel level, BlockPos anchor, Direction facing) {
        for (Cell cell : cells) {
            BlockState found = level.getBlockState(worldPosition(anchor, facing, cell));
            if (!found.is(this)
                    || found.getValue(FACING) != facing
                    || found.getValue(PART) != cell.part()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Takes apart the structure anchored here, with the ordinary drops.
     *
     * <p>Exposed because a crate standing on another is physically inside the lower crate's cells, so
     * the block a swing lands on is not always the structure the player was aiming at. The crate
     * decides which one that is; this is how it takes that one apart, on exactly the same terms as if
     * its own cell had been hit.
     */
    protected void destroyStructure(
            ServerLevel level, BlockPos anchor, Direction facing, boolean dropItem) {
        dismantle(level, anchor, facing, dropItem);
    }

    private void dismantle(ServerLevel level, BlockPos anchor, Direction facing, boolean dropItem) {
        duringMutation(() -> {
            beforeDismantle(level, anchor, facing);
            int flags = Block.UPDATE_ALL_IMMEDIATE | Block.UPDATE_SUPPRESS_DROPS;
            for (int index = cells.size() - 1; index >= 0; index--) {
                Cell cell = cells.get(index);
                BlockPos position = worldPosition(anchor, facing, cell);
                BlockState found = level.getBlockState(position);
                if (found.is(this)
                        && found.getValue(FACING) == facing
                        && anchorPosition(position, found).equals(anchor)) {
                    level.setBlock(position, Blocks.AIR.defaultBlockState(), flags);
                }
            }
            return null;
        });
        if (dropItem && asItem() != net.minecraft.world.item.Items.AIR) {
            popResource(level, anchor, new ItemStack(asItem()));
        }
    }

    /** Called exactly once while the authoritative root still exists, before any part is removed. */
    protected void beforeDismantle(ServerLevel level, BlockPos anchor, Direction facing) {
    }

    private static VoxelShape rotateY(VoxelShape shape, int quarterTurnsClockwise) {
        VoxelShape rotated = shape;
        for (int turn = 0; turn < quarterTurnsClockwise; turn++) {
            VoxelShape next = Shapes.empty();
            for (AABB box : rotated.toAabbs()) {
                next = Shapes.or(next, Shapes.create(new AABB(
                        1.0D - box.maxZ,
                        box.minY,
                        box.minX,
                        1.0D - box.minZ,
                        box.maxY,
                        box.maxX)));
            }
            rotated = next;
        }
        return rotated;
    }

    public record Cell(int part, int x, int y, int z) {
    }

    @FunctionalInterface
    public interface CellShapeFactory {
        VoxelShape shape(int x, int y, int z);
    }
}
