package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.component.WineData; // Your record from Day 4
import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceHolder;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import com.seggellion.britannia_mod.item.WineBottleBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;


public class WineBottleBlockEntity extends BlockEntity implements GrabbyProvenanceHolder {
    private static final String TAG_ORIGIN_STACK = "OriginStack";

    private WineData wineData = WineData.EMPTY;
    private GrabbyInstanceState grabbyState = GrabbyInstanceState.worldPlaced();

    /**
     * The exact item this bottle was placed from, kept verbatim.
     *
     * <p>{@link WineData} is not the whole of a bottle. A stack can also carry a custom name, and
     * nothing stops a future feature adding more. Rebuilding the item from the six wine fields alone
     * therefore quietly discarded everything else, every time a bottle was placed and picked back up.
     *
     * <p>Keeping the original stack means the bottle is restored rather than re-manufactured, and a
     * property invented later survives without anyone remembering to add it here.
     *
     * <p>Empty for bottles that arrived some other way - worldgen, a structure template, an admin in
     * Creative - which is why {@link #portableStack(Item)} still knows how to rebuild.
     */
    private ItemStack originStack = ItemStack.EMPTY;

    public WineBottleBlockEntity(BlockPos pos, BlockState blockState) {
        super(BlockEntityRegistry.WINE_BOTTLE_BE.get(), pos, blockState);
    }

    public void setWineData(WineData data) {
        this.wineData = data;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public WineData getWineData() {
        return wineData;
    }

    /** Records the exact stack this bottle came from. Called during placement. */
    public void setOriginStack(ItemStack stack) {
        this.originStack = stack == null ? ItemStack.EMPTY : stack.copyWithCount(1);
        setChanged();
    }

    public ItemStack getOriginStack() {
        return originStack;
    }

    /** The item this bottle should become when taken back out of the world. */
    public ItemStack portableStack(Item fallbackItem) {
        return portableStack(originStack, wineData, fallbackItem);
    }

    /**
     * Restores the original item where one is known, and otherwise rebuilds from the wine fields.
     *
     * <p>Kept static and free of world state so the choice itself is directly testable.
     *
     * @param originStack  the stack the bottle was placed from, or empty if unknown
     * @param wineData     the bottle's wine fields, used only on the rebuild path
     * @param fallbackItem the item to rebuild as, normally the block's own item
     */
    public static ItemStack portableStack(ItemStack originStack, WineData wineData, Item fallbackItem) {
        if (originStack != null && !originStack.isEmpty()) {
            return originStack.copy();
        }
        ItemStack rebuilt = new ItemStack(fallbackItem);
        WineBottleBlockItem.setWineData(
                rebuilt,
                wineData.wineryName(),
                wineData.grapeType(),
                wineData.year(),
                wineData.quality(),
                wineData.region(),
                wineData.labelColor());
        return rebuilt;
    }

    @Override
    public GrabbyInstanceState grabbyState() {
        return grabbyState;
    }

    @Override
    public void setGrabbyState(GrabbyInstanceState state) {
        this.grabbyState = java.util.Objects.requireNonNull(state, "state");
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        // Save the record manually to NBT
        tag.putString("WineryName", wineData.wineryName());
        tag.putString("GrapeType", wineData.grapeType());
        tag.putInt("Year", wineData.year());
        tag.putInt("Quality", wineData.quality());
        tag.putString("Region", wineData.region());
        tag.putString("LabelColor", wineData.labelColor());
        if (!originStack.isEmpty()) {
            tag.put(TAG_ORIGIN_STACK, originStack.save(registries));
        }
        grabbyState.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        this.wineData = new WineData(
            tag.getString("WineryName"),
            tag.getString("GrapeType"),
            tag.getInt("Year"),
            tag.getInt("Quality"),
            tag.getString("Region"),
            tag.contains("LabelColor") ? tag.getString("LabelColor") : "red"
        );
        this.originStack = tag.contains(TAG_ORIGIN_STACK)
                ? ItemStack.parse(registries, tag.getCompound(TAG_ORIGIN_STACK)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
        this.grabbyState = GrabbyInstanceState.read(tag);
    }


    // Sync for client-side rendering (if you want custom labels later)
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = saveWithoutMetadata(registries);
        // saveWithoutMetadata ran saveAdditional, so the full provenance (including the placer UUID)
        // is in there. Overwrite it with the redacted client form before this goes out to clients.
        grabbyState.writeClient(tag);
        // The client renders from the block state and the wine fields; the origin stack is a
        // server-side restoration detail that nothing on the client reads.
        tag.remove(TAG_ORIGIN_STACK);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}