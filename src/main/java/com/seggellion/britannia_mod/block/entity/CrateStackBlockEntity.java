package com.seggellion.britannia_mod.block.entity;


import com.seggellion.britannia_mod.block.CrateStackBlock;
import com.seggellion.britannia_mod.crate.CratePlacement;
import com.seggellion.britannia_mod.crate.CrateStackLayout;
import com.seggellion.britannia_mod.crate.CrateStackSlice;
import com.seggellion.britannia_mod.crate.CrateVariant;
import com.seggellion.britannia_mod.crate.LogicalCrate;
import com.seggellion.britannia_mod.crate.LogicalCrateContainer;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * One block entity that owns several crates a player sees as separate containers.
 *
 * <h2>Why this is shaped so unusually</h2>
 *
 * <p>Minecraft allows exactly one block entity per position — {@code ChunkAccess.blockEntities} is a
 * {@code Map<BlockPos, BlockEntity>}. The crates this mod ships are shorter than a block: a small
 * crate is 7.15 voxels tall, so two of them stacked come to 14.30 and still fit inside one block of
 * vertical space. Giving each its own position is what produced the visible air gaps this whole
 * design exists to remove, so several crates have to share a position, and therefore share a block
 * entity, while remaining separate inventories to the player.
 *
 * <p>This class is that authority. Cells above the root exist only to hold world space; they never
 * carry a block entity and never own an inventory. Everything about the column — its crates, their
 * identities, their order, their contents — lives here.
 *
 * <h2>Invariants</h2>
 *
 * <ul>
 *   <li>Crate ids are handed out by a counter that only ever climbs, and are never renumbered. A
 *       failed append consumes none.</li>
 *   <li>The crate list is never exposed for mutation; every change goes through a method here so the
 *       cached layout and the saved data cannot disagree with it.</li>
 *   <li>Repacking recomputes geometry and nothing else. No method on this class moves an item stack
 *       from one crate to another.</li>
 *   <li>A column that has fallen to one crate stays a column. Converting it back to a plain crate
 *       block would be a second inventory-carrying representation change, and every one of those is
 *       a chance to duplicate or drop what it carries; promotion happens at most once per column.</li>
 * </ul>
 */
public class CrateStackBlockEntity extends BlockEntity {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final String TAG_CRATES = "Crates";
    private static final String TAG_NEXT_ID = "NextCrateId";
    private static final String TAG_ID = "Id";
    private static final String TAG_VARIANT = "Variant";
    private static final String TAG_FACING = "Facing";
    private static final String TAG_ORIGIN = "OriginOffset";

    private final List<LogicalCrate> crates = new ArrayList<>();
    private int nextCrateId;

    /**
     * Where the bottom crate rests, relative to this cell's floor.
     *
     * <p>Zero for every column that stands on the ground, which is every column saved before
     * foundations existed - so the field is absent from their data and reads back as zero without a
     * migration. Negative for a column resting on a large crate, whose lid is below this cell.
     */
    private int originHundredths;

    /** Recomputed rather than saved: it is a pure function of the crate list. */
    @Nullable
    private CrateStackLayout layout;

    public CrateStackBlockEntity(BlockPos pos, BlockState state) {
        this(BlockRegistry.CRATE_STACK_BLOCK_ENTITY_TYPE.get(), pos, state);
    }

    public CrateStackBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /* ─── reading the column ─────────────────────────────────── */

    public int crateCount() {
        return crates.size();
    }

    public boolean isEmpty() {
        return crates.isEmpty();
    }

    /** The crates from the ground up, as an unmodifiable view. */
    public List<LogicalCrate> crates() {
        return Collections.unmodifiableList(crates);
    }

    @Nullable
    public LogicalCrate crateById(int crateId) {
        for (LogicalCrate crate : crates) {
            if (crate.id() == crateId) {
                return crate;
            }
        }
        return null;
    }

    @Nullable
    public LogicalCrate bottomCrate() {
        return crates.isEmpty() ? null : crates.get(0);
    }

    @Nullable
    public LogicalCrate topCrate() {
        return crates.isEmpty() ? null : crates.get(crates.size() - 1);
    }

    /** The id counter, exposed so persistence tests can hold it to its monotonic contract. */
    public int nextCrateId() {
        return nextCrateId;
    }

    /**
     * Where every crate sits, computed once and kept until the column changes.
     *
     * <p>Cached because a renderer will ask per frame and a shape builder per raycast, and neither
     * should have to walk the column to find out where its crates are.
     */
    public CrateStackLayout layout() {
        if (layout == null) {
            layout = CrateStackLayout.of(crates, originHundredths);
        }
        return layout;
    }

    /** Where this column's bottom crate rests, relative to this cell's floor. */
    public int originHundredths() {
        return originHundredths;
    }

    /**
     * Moves the whole column to a new physical origin.
     *
     * <p>Used when a column is founded on a large crate, and again if that foundation is later
     * removed. Nothing about the crates changes - the same ids, in the same order, holding the same
     * items - only the height their packing starts from.
     */
    public void setOriginHundredths(int origin) {
        if (origin != originHundredths) {
            originHundredths = origin;
            repack();
        }
    }

    /** Whether this column rests on something below its own cell. */
    public boolean hasFoundation() {
        return originHundredths != 0;
    }

    public int totalHeightHundredths() {
        return layout().totalHundredths();
    }

    /** How many world cells this column needs, counting its root. */
    public int requiredCellCount() {
        return layout().requiredCells();
    }

    @Nullable
    public CratePlacement placementOf(int crateId) {
        return layout().placementOf(crateId);
    }

    /**
     * What one of this column's cells has to draw and collide with.
     *
     * <p>The single projection both views read, so the picture and the hitbox cannot drift apart.
     */
    public CrateStackSlice sliceFor(int cell) {
        return CrateStackSlice.of(layout(), crates, cell);
    }

    /* ─── changing the column ────────────────────────────────── */

    /**
     * Adds a crate on top, if it fits.
     *
     * <p>The height cap is checked before anything is allocated, so a refused append leaves the
     * column, its inventories and the id counter exactly as they were — a player who cannot stack one
     * more crate has not silently consumed an identity.
     *
     * @return the new crate's id, or empty if the column has no room
     */
    public OptionalInt appendCrate(CrateVariant variant, Direction facing) {
        int grown = totalHeightHundredths() + variant.heightHundredths();
        if (!CrateStackLayout.withinCap(grown)) {
            return OptionalInt.empty();
        }
        LogicalCrate crate = new LogicalCrate(nextCrateId, variant, facing);
        crates.add(crate);
        nextCrateId++;
        repack();
        return OptionalInt.of(crate.id());
    }

    /**
     * Adds a crate that already has contents, used by promotion.
     *
     * <p>The list is adopted rather than copied, which is what makes promotion a move of storage
     * rather than a transcription of it.
     */
    public OptionalInt appendCrate(CrateVariant variant, Direction facing, NonNullList<ItemStack> items) {
        int grown = totalHeightHundredths() + variant.heightHundredths();
        if (!CrateStackLayout.withinCap(grown)) {
            return OptionalInt.empty();
        }
        LogicalCrate crate = new LogicalCrate(nextCrateId, variant, facing, items);
        crates.add(crate);
        nextCrateId++;
        repack();
        return OptionalInt.of(crate.id());
    }

    /** Whether one more crate of this variant would fit. */
    public boolean canAppend(CrateVariant variant) {
        return CrateStackLayout.withinCap(totalHeightHundredths() + variant.heightHundredths());
    }

    /**
     * Takes one crate out of the column, leaving every other crate's identity and contents alone.
     *
     * @return the crate that was removed, still holding its items, or empty if no such crate
     */
    public Optional<LogicalCrate> removeCrate(int crateId) {
        for (int index = 0; index < crates.size(); index++) {
            if (crates.get(index).id() == crateId) {
                LogicalCrate removed = crates.remove(index);
                repack();
                return Optional.of(removed);
            }
        }
        return Optional.empty();
    }

    /**
     * Recomputes the layout after the column changed.
     *
     * <p>This is the whole of repacking. Crates above a removed crate move down, and they do so
     * because their computed base changed — not because anything about them was rewritten. No
     * {@link LogicalCrate} is touched here at all, which is why repacking can never move, copy or
     * lose an item.
     */
    /**
     * Tells the world the column changed, including every comparator that can see any of its cells.
     *
     * <p>A comparator beside a continuation cell reads the same column as one beside the root, so all
     * of them have to be told. Nothing else notices: a change to what a crate holds moves no block,
     * so without this a comparator would keep reporting whatever was true when something last did.
     */
    @Override
    public void setChanged() {
        super.setChanged();
        if (level == null || level.isClientSide) {
            return;
        }
        for (int cell = 0; cell < Math.max(requiredCellCount(), 1); cell++) {
            BlockPos pos = worldPosition.above(cell);
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof CrateStackBlock) {
                level.updateNeighbourForOutputSignal(pos, state.getBlock());
            }
        }
    }

    public void repack() {
        layout = CrateStackLayout.of(crates, originHundredths);
        setChanged();
    }

    /* ─── containers ─────────────────────────────────────────── */

    /**
     * A container addressing one crate by identity.
     *
     * <p>Returned even for an id that has already gone, because a menu may outlive its crate; the
     * view reports itself empty and invalid rather than resolving to a neighbour.
     */
    public LogicalCrateContainer containerFor(int crateId) {
        return new LogicalCrateContainer(this, crateId);
    }

    /**
     * Sounds a crate where that crate actually is, not where its column starts.
     *
     * <p>A crate can sit three blocks above the root, and a chest sound arriving from the floor when
     * the player opened the crate at eye level reads as a bug even when nothing is wrong. The height
     * comes from the layout, so it follows the crate through every repack.
     */
    public void playCrateSound(int crateId, SoundEvent sound) {
        if (level == null || level.isClientSide) {
            return;
        }
        CratePlacement placement = placementOf(crateId);
        double centreHundredths = placement == null
                ? 0.0D
                : (placement.baseHundredths() + placement.topHundredths()) / 2.0D;
        Vec3 at = new Vec3(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + centreHundredths / (CrateStackLayout.HUNDREDTHS_PER_VOXEL * 16),
                worldPosition.getZ() + 0.5D);
        level.playSound(null, at.x, at.y, at.z, sound, SoundSource.BLOCKS, 1.0F, 1.0F);
    }

    /**
     * Whether a container view for this crate may still be used.
     *
     * <p>Split out from the view so the rules live beside the state they are about.
     */
    public boolean isValidContainerFor(int crateId, Player player) {
        if (isRemoved() || level == null) {
            return false;
        }
        if (level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        if (crateById(crateId) == null) {
            return false;
        }
        return player.canInteractWithBlock(worldPosition, 4.0D);
    }

    /* ─── persistence ────────────────────────────────────────── */

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writeColumn(tag, registries, true);
    }

    /**
     * What a client needs to draw this column, and nothing more.
     *
     * <p>Rendering a column takes a variant, a facing and an order; it does not take the items. A
     * column can hold eight inventories, so sending their contents to everyone who can see it would
     * broadcast up to two hundred item stacks — with components — every time a crate was added, for a
     * picture that never shows them. Menus synchronise their own contents when a player opens one.
     */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        writeColumn(tag, registries, false);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    /* ─── keeping the picture honest ─────────────────────────── */

    /**
     * Redraws the column whenever a client is told what it now holds.
     *
     * <h2>Why this is needed at all</h2>
     *
     * <p>A column's crates are chunk-baked terrain, not a block-entity renderer, and a chunk section
     * is only rebuilt when a block in it changes. Adding a crate that fits inside the cells the column
     * already occupies changes no block at all — the column simply gets taller inside its own space —
     * so the server sends the new layout with a block update carrying the state the client already
     * has. {@code LevelChunk.setBlockState} returns null for a state identical to the one in place,
     * {@code Level.setBlock} gives up on that null before it reaches {@code sendBlockUpdated}, and
     * {@code sendBlockUpdated} is the only route to {@code LevelRenderer.blockChanged}. The section is
     * therefore never marked dirty, and the crate stays invisible until something unrelated disturbs
     * it — while being fully present in every other respect, because menus, targeting and collision
     * all read this block entity, which did receive the update.
     *
     * <p>{@code requestModelDataUpdate} does not close the gap: it records the position for a model
     * data refresh and nothing more. So the redraw is asked for explicitly, once the new column is
     * loaded, for every cell the column occupies — each cell bakes its own slice, so each one's
     * section has to be rebuilt.
     */
    @Override
    public void onDataPacket(
            Connection net, ClientboundBlockEntityDataPacket packet, HolderLookup.Provider registries) {
        super.onDataPacket(net, packet, registries);
        redrawColumn();
    }

    /** The same redraw for the copy that arrives with a chunk rather than as a change. */
    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        redrawColumn();
    }

    /**
     * Marks every section this column draws into as needing a rebuild.
     *
     * <p>Client only, and deliberately routed through {@code sendBlockUpdated} rather than
     * {@code setBlocksDirty}: on a client level the former reaches
     * {@code LevelRenderer.blockChanged}, which marks the surrounding sections dirty unconditionally,
     * while the latter is gated on the two states rendering differently — and here they are the same
     * state, which is the entire problem.
     */
    private void redrawColumn() {
        if (level == null || !level.isClientSide) {
            return;
        }
        // From the foundation cell below the root, which draws whatever hangs into it, up through
        // every cell the column owns.
        int lowest = hasFoundation() ? -1 : 0;
        for (int cell = lowest; cell < Math.max(requiredCellCount(), 1); cell++) {
            BlockPos pos = worldPosition.above(cell);
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
        }
    }

    /**
     * @param withItems whether to include inventories, which only the save path wants
     */
    private void writeColumn(CompoundTag tag, HolderLookup.Provider registries, boolean withItems) {
        ListTag saved = new ListTag();
        for (LogicalCrate crate : crates) {
            CompoundTag entry = new CompoundTag();
            entry.putInt(TAG_ID, crate.id());
            entry.putString(TAG_VARIANT, crate.variant().serializedName());
            entry.putString(TAG_FACING, crate.facing().getSerializedName());
            if (withItems) {
                crate.saveItemsTo(entry, registries);
            }
            saved.add(entry);
        }
        tag.put(TAG_CRATES, saved);
        tag.putInt(TAG_NEXT_ID, nextCrateId);
        // Written only when it is not the default, so a freestanding column's data is byte for byte
        // what it was before foundations existed.
        if (originHundredths != 0) {
            tag.putInt(TAG_ORIGIN, originHundredths);
        }
    }

    /**
     * Reads a column back, correcting what can be corrected and refusing what cannot.
     *
     * <h2>Policy for malformed data</h2>
     *
     * <p>The rule throughout is that nothing here may invent items or lose them silently:
     *
     * <ul>
     *   <li><b>Unknown variant</b> — the entry is dropped and logged. A variant decides an inventory
     *       size, and reading a saved inventory at a guessed size is how contents go missing.</li>
     *   <li><b>Duplicate id</b> — the first entry wins and later ones are dropped and logged. Two
     *       crates answering to one id would make every menu, packet and target ambiguous.</li>
     *   <li><b>Wrong slot count</b> — {@code ContainerHelper} fills what it recognises and leaves the
     *       rest empty; a saved slot beyond the variant's size is dropped rather than allowed to
     *       shift the others.</li>
     *   <li><b>Non-horizontal or unreadable facing</b> — defaults to north. Facing is cosmetic and a
     *       bad one must not cost a crate.</li>
     *   <li><b>Over the height cap</b> — crates are kept from the bottom up until the next would not
     *       fit, and the remainder are dropped and logged. Truncating from the top keeps the column
     *       a player sees closest to what was saved.</li>
     *   <li><b>{@code NextCrateId} at or below a live id</b> — raised past every id present, so the
     *       counter cannot hand out an identity that is already in use.</li>
     * </ul>
     */
    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        crates.clear();
        layout = null;
        // Absent for every column saved before foundations existed, which is exactly right for them.
        originHundredths = tag.getInt(TAG_ORIGIN);

        Set<Integer> seenIds = new HashSet<>();
        int height = 0;
        int highestId = -1;
        ListTag saved = tag.getList(TAG_CRATES, Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size(); index++) {
            CompoundTag entry = saved.getCompound(index);
            int id = entry.getInt(TAG_ID);

            Optional<CrateVariant> variant = CrateVariant.byName(entry.getString(TAG_VARIANT));
            if (variant.isEmpty()) {
                LOGGER.warn("[crate-stack] {} holds a crate of unknown variant '{}'; dropping that "
                        + "entry rather than guessing its inventory size",
                        worldPosition, entry.getString(TAG_VARIANT));
                continue;
            }
            if (!seenIds.add(id)) {
                LOGGER.warn("[crate-stack] {} holds more than one crate with id {}; keeping the first",
                        worldPosition, id);
                continue;
            }
            int grown = height + variant.get().heightHundredths();
            if (!CrateStackLayout.withinCap(grown)) {
                LOGGER.warn("[crate-stack] {} is taller than the {} voxel cap; dropping crate {} and "
                        + "everything above it",
                        worldPosition, CrateStackLayout.MAX_HEIGHT_HUNDREDTHS / 100, id);
                break;
            }

            NonNullList<ItemStack> items =
                    NonNullList.withSize(variant.get().slotCount(), ItemStack.EMPTY);
            ContainerHelper.loadAllItems(entry, items, registries);
            crates.add(new LogicalCrate(id, variant.get(), readFacing(entry), items));
            height = grown;
            highestId = Math.max(highestId, id);
        }

        nextCrateId = tag.getInt(TAG_NEXT_ID);
        if (!crates.isEmpty() && originHundredths != 0) {
            LOGGER.debug("[crate-stack] {} loaded on a foundation at {} hundredths",
                    worldPosition, originHundredths);
        }
        if (nextCrateId <= highestId) {
            LOGGER.warn("[crate-stack] {} would reissue crate ids from {}; raising past {}",
                    worldPosition, nextCrateId, highestId);
            nextCrateId = highestId + 1;
        }
        layout = CrateStackLayout.of(crates, originHundredths);
    }

    private static Direction readFacing(CompoundTag entry) {
        Direction facing = Direction.byName(entry.getString(TAG_FACING));
        return facing == null || facing.getAxis().isVertical() ? Direction.NORTH : facing;
    }
}
