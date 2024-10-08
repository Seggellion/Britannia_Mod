package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.magic.Spell;
import com.seggellion.britannia_mod.magic.SpellRegistry;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

public record SpellCastPayload(int targetEntityId) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SpellCastPayload> TYPE = new CustomPacketPayload.Type<>(
        ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "spell_cast")
    );

    public static final StreamCodec<FriendlyByteBuf, SpellCastPayload> STREAM_CODEC = StreamCodec.of(
        SpellCastPayload::encode,
        SpellCastPayload::decode
    );

    // Decoder method
    public static SpellCastPayload decode(FriendlyByteBuf buf) {
        int targetEntityId = buf.readInt();
        return new SpellCastPayload(targetEntityId);
    }

    // Encoder method
    public static void encode(FriendlyByteBuf buf, SpellCastPayload payload) {
        buf.writeInt(payload.targetEntityId());
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // Handler method to be called when the payload is received on the server
    public void handleOnServer(ServerPlayer player) {
        player.getServer().execute(() -> {
            Entity targetEntity = player.level().getEntity(targetEntityId);
            if (targetEntity instanceof LivingEntity target) {
                ItemStack itemStack = player.getMainHandItem();
                Spell spell = SpellRegistry.getSpell(itemStack);
                if (spell != null) {
                    if (!spell.castTarget(player, target)) {
                        player.sendSystemMessage(Component.literal("Failed to cast spell on target!"));
                    }
                }
            }
        });
    }
}
