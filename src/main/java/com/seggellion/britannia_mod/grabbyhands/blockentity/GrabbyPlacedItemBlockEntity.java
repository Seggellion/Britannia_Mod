package com.seggellion.britannia_mod.grabbyhands.blockentity;

import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyPayloadHolder;
import com.seggellion.britannia_mod.grabbyhands.GrabbyProvenanceHolder;
import com.seggellion.britannia_mod.registry.GrabbyRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Objects;

/**
 * Holds one complete {@link ItemStack} as a physical world object.
 *
 * <p>The payload is stored and restored <em>opaquely</em>. This class never inspects what the item
 * is, never enumerates its components, and never rebuilds it from a registry ID. That is the whole
 * point: a future item property cannot become a data-loss bug here, because nothing here knows what
 * properties exist.
 *
 * <p>The stack is synced to clients because the renderer needs it. Provenance is synced in its
 * redacted form, so the placer's UUID stays server-side.
 */
public class GrabbyPlacedItemBlockEntity extends BlockEntity
        implements GrabbyProvenanceHolder, GrabbyPayloadHolder {

    private static final String TAG_PAYLOAD = "Payload";

    private ItemStack payload = ItemStack.EMPTY;
    private GrabbyInstanceState grabbyState = GrabbyInstanceState.worldPlaced();

    public GrabbyPlacedItemBlockEntity(BlockPos pos, BlockState state) {
        super(GrabbyRegistry.PLACED_ITEM_BLOCK_ENTITY.get(), pos, state);
    }

    // ------------------------------------------------------------------
    // Payload
    // ------------------------------------------------------------------

    @Override
    public ItemStack grabbyPayload() {
        return payload;
    }

    @Override
    public void setGrabbyPayload(ItemStack payload) {
        this.payload = Objects.requireNonNull(payload, "payload").copy();
        synchronise();
    }

    @Override
    public void detachGrabbyPayload() {
        this.payload = ItemStack.EMPTY;
        setChanged();
    }

    // ------------------------------------------------------------------
    // Provenance
    // ------------------------------------------------------------------

    @Override
    public GrabbyInstanceState grabbyState() {
        return grabbyState;
    }

    @Override
    public void setGrabbyState(GrabbyInstanceState state) {
        this.grabbyState = Objects.requireNonNull(state, "state");
        setChanged();
    }

    private void synchronise() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    // ------------------------------------------------------------------
    // Persistence
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        writePayload(tag, registries);
        grabbyState.write(tag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        readPayload(tag, registries);
        this.grabbyState = GrabbyInstanceState.read(tag);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = super.getUpdateTag(registries);
        // The client needs the stack to draw it, but not who put it there.
        writePayload(tag, registries);
        grabbyState.writeClient(tag);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag, HolderLookup.Provider registries) {
        super.handleUpdateTag(tag, registries);
        readPayload(tag, registries);
        this.grabbyState = GrabbyInstanceState.read(tag);
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void writePayload(CompoundTag tag, HolderLookup.Provider registries) {
        if (!payload.isEmpty()) {
            tag.put(TAG_PAYLOAD, payload.save(registries));
        }
    }

    private void readPayload(CompoundTag tag, HolderLookup.Provider registries) {
        this.payload = tag.contains(TAG_PAYLOAD)
                ? ItemStack.parse(registries, tag.getCompound(TAG_PAYLOAD)).orElse(ItemStack.EMPTY)
                : ItemStack.EMPTY;
    }
}
