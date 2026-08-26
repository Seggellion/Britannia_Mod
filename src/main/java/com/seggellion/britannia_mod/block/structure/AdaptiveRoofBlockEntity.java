package com.seggellion.britannia_mod.block.entity;

import com.seggellion.britannia_mod.block.AdaptiveRoofBlock;
import com.seggellion.britannia_mod.block.TopOnlySlabBlock;
import com.seggellion.britannia_mod.registry.BlockEntityRegistry;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

public class AdaptiveRoofBlockEntity extends BlockEntity {
    public static final ModelProperty<ResourceLocation> BOTTOM_TEXTURE_MODEL_PROPERTY =
            new ModelProperty<>(texture -> texture != null);

    private static final ResourceLocation AIR_TEXTURE =
            ResourceLocation.withDefaultNamespace("block/air");
    private static final String TAG_KEY = "BottomTexture";

    private ResourceLocation bottomTexture;

    public AdaptiveRoofBlockEntity(BlockPos pos, BlockState state) {
        super(BlockEntityRegistry.ADAPTIVE_ROOF.get(), pos, state);
    }

    public void setBottomTexture(@Nullable ResourceLocation texture) {
        bottomTexture = normalizeTexture(texture);
        boolean supports = bottomTexture != null;

        setChanged();
        requestModelDataUpdate();

        if (level == null) {
            return;
        }

        if (level instanceof ServerLevel server) {
            server.blockEntityChanged(worldPosition);
        }

        BlockState current = getBlockState();
        if (current.hasProperty(TopOnlySlabBlock.SUPPORTS_LANTERN)) {
            if (current.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN) != supports) {
                level.setBlock(
                        worldPosition,
                        current.setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, supports),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            } else {
                level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_CLIENTS);
            }
        } else if (current.hasProperty(AdaptiveRoofBlock.SUPPORTS_LANTERN)) {
            if (current.getValue(AdaptiveRoofBlock.SUPPORTS_LANTERN) != supports) {
                level.setBlock(
                        worldPosition,
                        current.setValue(AdaptiveRoofBlock.SUPPORTS_LANTERN, supports),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            } else {
                level.sendBlockUpdated(worldPosition, current, current, Block.UPDATE_CLIENTS);
            }
        }

        if (!level.isClientSide) {
            level.getChunkAt(worldPosition).setUnsaved(true);
        }
    }

    @Nullable
    public ResourceLocation getBottomTexture() {
        return bottomTexture;
    }

    @Override
    public ModelData getModelData() {
        return bottomTexture == null
                ? ModelData.EMPTY
                : ModelData.of(BOTTOM_TEXTURE_MODEL_PROPERTY, bottomTexture);
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) {
            return;
        }

        if (level.isClientSide) {
            requestModelDataUpdate();
            return;
        }

        // Repair legacy blocks whose support state remained set after their texture vanished.
        if (bottomTexture == null) {
            BlockState current = getBlockState();
            if (current.hasProperty(TopOnlySlabBlock.SUPPORTS_LANTERN)
                    && current.getValue(TopOnlySlabBlock.SUPPORTS_LANTERN)) {
                level.setBlock(
                        worldPosition,
                        current.setValue(TopOnlySlabBlock.SUPPORTS_LANTERN, false),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            } else if (current.hasProperty(AdaptiveRoofBlock.SUPPORTS_LANTERN)
                    && current.getValue(AdaptiveRoofBlock.SUPPORTS_LANTERN)) {
                level.setBlock(
                        worldPosition,
                        current.setValue(AdaptiveRoofBlock.SUPPORTS_LANTERN, false),
                        Block.UPDATE_CLIENTS | Block.UPDATE_NEIGHBORS);
            }
        }

        ((ServerLevel) level).blockEntityChanged(worldPosition);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        writeTexture(tag, bottomTexture, false);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = super.getUpdateTag(provider);
        // An explicit sentinel also makes clear packets non-empty, so the default remote
        // handler invokes loadAdditional instead of leaving stale model data on the client.
        writeTexture(tag, bottomTexture, true);
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        bottomTexture = readTexture(tag);

        if (level != null && level.isClientSide) {
            requestModelDataUpdate();
            level.sendBlockUpdated(
                    worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    static void writeTexture(
            CompoundTag tag,
            @Nullable ResourceLocation texture,
            boolean includeClearSentinel) {
        ResourceLocation normalized = normalizeTexture(texture);
        if (normalized != null) {
            tag.putString(TAG_KEY, normalized.toString());
        } else if (includeClearSentinel) {
            tag.putString(TAG_KEY, AIR_TEXTURE.toString());
        } else {
            tag.remove(TAG_KEY);
        }
    }

    @Nullable
    static ResourceLocation readTexture(CompoundTag tag) {
        return normalizeTexture(tag.contains(TAG_KEY)
                ? ResourceLocation.tryParse(tag.getString(TAG_KEY))
                : null);
    }

    @Nullable
    private static ResourceLocation normalizeTexture(@Nullable ResourceLocation texture) {
        return AIR_TEXTURE.equals(texture) ? null : texture;
    }
}
