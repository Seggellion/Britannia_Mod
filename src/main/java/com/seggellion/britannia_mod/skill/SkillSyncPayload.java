package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.skill.ClientSkillTable;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;

/** Versioned server-to-client skill snapshot used for UI only. */
public record SkillSyncPayload(
        int wireVersion,
        SkillManager.SkillDataState state,
        long revision,
        boolean identificationBypass,
        Map<String, Float> skills
) implements CustomPacketPayload {
    public static final int WIRE_VERSION = 1;
    private static final int MAX_SKILLS = 512;
    public static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("britannia", "skill_sync");
    public static final CustomPacketPayload.Type<SkillSyncPayload> TYPE = new CustomPacketPayload.Type<>(ID);

    public SkillSyncPayload {
        if (wireVersion != WIRE_VERSION) {
            throw new IllegalArgumentException("Unsupported skill sync version " + wireVersion);
        }
        if (revision < 0L) {
            throw new IllegalArgumentException("Skill sync revision must be non-negative");
        }
        skills = Map.copyOf(skills);
    }

    public static SkillSyncPayload create(
            SkillManager.SkillDataState state,
            long revision,
            boolean identificationBypass,
            Map<String, Float> skills
    ) {
        return new SkillSyncPayload(WIRE_VERSION, state, revision, identificationBypass, skills);
    }

    public static final StreamCodec<FriendlyByteBuf, SkillSyncPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.wireVersion);
                buf.writeEnum(payload.state);
                buf.writeVarLong(payload.revision);
                buf.writeBoolean(payload.identificationBypass);
                buf.writeVarInt(payload.skills.size());
                payload.skills.forEach((key, value) -> {
                    buf.writeUtf(key);
                    buf.writeFloat(value);
                });
            },
            buf -> {
                int version = buf.readVarInt();
                if (version != WIRE_VERSION) {
                    throw new IllegalArgumentException("Unsupported skill sync version " + version);
                }
                SkillManager.SkillDataState state = buf.readEnum(SkillManager.SkillDataState.class);
                long revision = buf.readVarLong();
                boolean bypass = buf.readBoolean();
                int size = buf.readVarInt();
                if (size < 0 || size > MAX_SKILLS) {
                    throw new IllegalArgumentException("Invalid skill sync entry count " + size);
                }
                Map<String, Float> skills = new HashMap<>();
                for (int i = 0; i < size; i++) {
                    skills.put(buf.readUtf(), buf.readFloat());
                }
                return new SkillSyncPayload(version, state, revision, bypass, skills);
            }
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(SkillSyncPayload payload) {
        ClientSkillTable.applyAuthoritativeSync(
                payload.state(), payload.skills(), payload.revision(), payload.identificationBypass()
        );
    }
}
