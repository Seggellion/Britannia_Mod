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

/**
 * A quest-giver spawner's configuration, as the screen's Save sends it.
 *
 * <h2>Wire format (Rowan farming questline M6)</h2>
 * The six fields M1 shipped are encoded exactly as they were. {@code directions} is appended after
 * them, and only when it has something in it, so:
 * <ul>
 *   <li>a client that never sends a hint produces the byte-for-byte pre-M6 packet, and the decoder
 *       — which reads the trailing string only if bytes remain — hands the handler an empty hint;
 *   <li>a spawner's stored hint survives such a save, because an empty hint means "leave it alone"
 *       in {@code QuestGiverSpawnBlockEntity.applyConfig};
 *   <li>and a hint that is too long or carries control characters is refused by
 *       {@link #shapeViolation()} on the server, whatever the client believed it was sending.
 * </ul>
 */
public record QuestGiverSpawnConfigC2SPayload(
        BlockPos pos,
        String npcName,
        String cityName,
        String customApiId,
        String gender,
        int spawnRadius, // NEW: Added radius to payload
        String directions
) implements CustomPacketPayload {

    /** Never null, so every rule below — and the codec — can treat "no hint" as one value. */
    public QuestGiverSpawnConfigC2SPayload {
        directions = directions == null ? "" : directions;
    }

    /** The pre-M6 shape: a configuration that carries no hint and therefore changes none. */
    public QuestGiverSpawnConfigC2SPayload(BlockPos pos, String npcName, String cityName,
                                           String customApiId, String gender, int spawnRadius) {
        this(pos, npcName, cityName, customApiId, gender, spawnRadius, "");
    }

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
                    // Trailing and optional: a pre-M6 client stops here, and its packet is complete.
                    String directions = buf.isReadable() ? ByteBufCodecs.STRING_UTF8.decode(buf) : "";
                    return new QuestGiverSpawnConfigC2SPayload(pos, npcName, cityName, customApiId, gender,
                            spawnRadius, directions);
                }

                @Override
                public void encode(FriendlyByteBuf buf, QuestGiverSpawnConfigC2SPayload payload) {
                    BlockPos.STREAM_CODEC.encode(buf, payload.pos);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.npcName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.cityName);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.customApiId);
                    ByteBufCodecs.STRING_UTF8.encode(buf, payload.gender);
                    ByteBufCodecs.INT.encode(buf, payload.spawnRadius); // NEW: Encode radius
                    if (!payload.directions.isEmpty()) {
                        ByteBufCodecs.STRING_UTF8.encode(buf, payload.directions);
                    }
                }
            };

    // --- Validation (Rowan farming questline M1, discovery D3) --------------------------------
    // These say whether a decoded payload may be applied; the codec above accepts anything.

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
     * knows, the radius must be a leash the spawner can enforce, and the optional directions hint
     * must be bounded and printable -- it is stored, echoed and logged, so a hint carrying newlines
     * or terminal escapes is refused rather than quietly edited.
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
        if (directions.length() > QuestGiverSpawnBlockEntity.MAX_DIRECTIONS_LENGTH) return Optional.of("directions_too_long");
        if (hasControlCharacters(directions)) return Optional.of("directions_control_characters");
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