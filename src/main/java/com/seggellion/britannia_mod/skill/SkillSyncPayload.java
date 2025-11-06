package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.skill.ClientSkillTable;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

public record SkillSyncPayload(Map<String, Float> skills) implements CustomPacketPayload {

    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("britannia", "skill_sync");

    public static final CustomPacketPayload.Type<SkillSyncPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public static final StreamCodec<FriendlyByteBuf, SkillSyncPayload> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.skills.size());
            payload.skills.forEach((key, value) -> {
                buf.writeUtf(key);
                buf.writeFloat(value);
            });
        },
        buf -> {
            int size = buf.readVarInt();
            Map<String, Float> skills = new HashMap<>();
            for (int i = 0; i < size; i++) {
                String key = buf.readUtf();
                float val = buf.readFloat();
                skills.put(key, val);
            }
            return new SkillSyncPayload(skills);
        }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillSyncPayload payload) {
        ClientSkillTable.overwrite(payload.skills());
    }
}
