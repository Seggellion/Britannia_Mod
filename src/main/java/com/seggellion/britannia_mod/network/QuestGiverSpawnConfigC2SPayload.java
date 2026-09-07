package com.seggellion.britannia_mod.network.payload;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import com.seggellion.britannia_mod.block.entity.QuestGiverSpawnBlockEntity;

import java.util.Optional;
import java.util.Set;

public record QuestGiverSpawnConfigC2SPayload(
        BlockPos pos,
        String npcName,
        String cityName,
        String customApiId,
        String gender,
        int spawnRadius // NEW: Added radius to payload
) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath("britannia_mod", "quest_giver_spawn_config");

    public static final Type<QuestGiverSpawnConfigC2SPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, QuestGiverSpawnConfigC2SPayload> STREAM_CODEC =
            new StreamCodec<>() {
                @Override
                public QuestGiverSpawnConfigC2SPayload decode(FriendlyByteBuf buf) {
                    BlockPos pos = BlockPos.STREAM_CODEC.decode(buf);
                    String npcName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String cityName = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String customApiId = ByteBufCodecs.STRING_UTF8.decode(buf);
                    String gender = ByteBufCodecs.STRING_UTF8.decode(buf); 
                    int spawnRadius = ByteBufCodecs.INT.decode(buf); // NEW: Decode radius
                    return new QuestGiverSpawnConfigC2SPayload(pos, npcName, cityName, customApiId, gender, spawnRadius);
                }

                @Override
                public void encode(FriendlyByteBuf buf, QuestGiverSpawnConfigC2SPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.npcName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.customApiId);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.gender); 
                    ByteBufCodecs.INT.encode(buf, payload.spawnRadius); // NEW: Encode radius
                }
            };

    // --- Validation (Rowan farming questline M1, discovery D3) --------------------------------
    // The wire format above is unchanged; these only say whether a decoded payload may be applied.

    /** Longest city name or Rails api id the server accepts; the screen's boxes are narrower. */
    public static final int MAX_TEXT_LENGTH = 64;
    /** 0 keeps the NPC at its post (the block entity accepted it before M1); -1 would lift the leash. */
    public static final int MIN_SPAWN_RADIUS = 0;
    public static final int MAX_SPAWN_RADIUS = 64;
    /** Exactly what the screen sends; the spawner and the model select textures on these strings. */
    public static final Set<String> GENDERS = Set.of("male", "female");
    public static final String GENERIC_COMBAT_ARCHETYPE = "Generic Combat";

    /**
     * Empty when the payload may be applied; otherwise the first rule it breaks. The archetype must
     * be one the spawner supports, the city is required, every text is bounded and free of control
     * characters, a Generic Combat NPC must name its Rails api id, the gender must be one the model
     * knows, and the radius must be a leash the spawner can enforce.
     */
    public Optional<String> shapeViolation() {
        if (pos == null) return Optional.of("pos_missing");
        if (npcName == null || npcName.isBlank()) return Optional.of("archetype_blank");
        if (!QuestGiverSpawnBlockEntity.supportsArchetype(npcName)) return Optional.of("archetype_unsupported");
        if (cityName == null || cityName.isBlank()) return Optional.of("city_blank");
        if (cityName.length() > MAX_TEXT_LENGTH) return Optional.of("city_too_long");
        if (hasControlCharacters(cityName)) return Optional.of("city_control_characters");
        if (customApiId == null) return Optional.of("custom_api_id_missing");
        if (customApiId.length() > MAX_TEXT_LENGTH) return Optional.of("custom_api_id_too_long");
        if (hasControlCharacters(customApiId)) return Optional.of("custom_api_id_control_characters");
        if (GENERIC_COMBAT_ARCHETYPE.equals(npcName) && customApiId.isBlank()) return Optional.of("custom_api_id_blank");
        if (gender == null || !GENDERS.contains(gender)) return Optional.of("gender_invalid");
        if (spawnRadius < MIN_SPAWN_RADIUS || spawnRadius > MAX_SPAWN_RADIUS) return Optional.of("spawn_radius_out_of_range");
        return Optional.empty();
    }

    public boolean isValidShape() {
        return shapeViolation().isEmpty();
    }

    private static boolean hasControlCharacters(String value) {
        return value.chars().anyMatch(Character::isISOControl);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}