package com.seggellion.britannia_mod.network.payload;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.BlockPos;

public record ItemBurnedS2CPayload(ItemStack item, BlockPos pos) implements CustomPacketPayload {
    public static final Type<ItemBurnedS2CPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("britannia_mod", "item_burned"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemBurnedS2CPayload> CODEC = StreamCodec.composite(
        ItemStack.STREAM_CODEC, ItemBurnedS2CPayload::item,
        BlockPos.STREAM_CODEC, ItemBurnedS2CPayload::pos,
        ItemBurnedS2CPayload::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}