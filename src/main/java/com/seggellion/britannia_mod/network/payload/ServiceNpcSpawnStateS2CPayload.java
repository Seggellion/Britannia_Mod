package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.ServiceNpcSpawnBlockEntity;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.menu.ServiceNpcSpawnMenu;
import com.seggellion.britannia_mod.service.ServiceNpcRegistryCache;
import com.seggellion.britannia_mod.service.ServiceNpcTypeDefinition;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnEligibility;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRegistrationState;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import com.seggellion.britannia_mod.skill.SkillManager;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public record ServiceNpcSpawnStateS2CPayload(
        int containerId,
        BlockPos pos,
        UUID spawnPointId,
        boolean blockPresent,
        ServiceNpcSpawnValidationError validationError,
        boolean cityRegistryAvailable,
        boolean serviceTypeRegistryAvailable,
        List<CityOption> cityOptions,
        List<ServiceTypeOption> serviceTypeOptions,
        @Nullable UUID cityPublicId,
        @Nullable String serviceNpcTypeKey,
        boolean citySelectionValid,
        boolean serviceTypeSelectionValid,
        boolean enabled,
        long configurationRevision,
        ServiceNpcSpawnRegistrationState registrationState,
        @Nullable String lastErrorCode,
        @Nullable UUID assignedNpcPublicId,
        @Nullable String assignedNpcDisplayName,
        long assignmentRevision,
        @Nullable Long lastSuccessfulSyncEpochMillis,
        List<String> taughtSkillLabels,
        ServiceNpcSpawnEligibility.Status eligibilityStatus,
        List<SupplyLine> supplyRequirements
) implements CustomPacketPayload {
    public record CityOption(UUID publicId, String displayName) {}
    public record ServiceTypeOption(String key, String displayName) {}

    /**
     * Guildmaster milestone 2. One of the selected type's configured supply minimums and how the
     * selected city measures against it.
     *
     * <p>{@code measured} is false when the bootstrap carries no figure for that supply, which is
     * what makes the whole readout {@link ServiceNpcSpawnEligibility.Status#UNKNOWN}. It is
     * deliberately distinguishable from a real zero, so the screen never reports a shortfall it
     * cannot actually see.
     */
    public record SupplyLine(String supply, double required, double available, boolean measured) {
        public boolean satisfied() {
            return measured && available >= required;
        }
    }

    /** Bounded like every other collection on this payload. */
    private static final int MAX_TAUGHT_SKILL_LABELS = 64;
    private static final int MAX_SUPPLY_LINES = 32;

    public static final Type<ServiceNpcSpawnStateS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "service_npc_spawn_state")
    );
    public static final StreamCodec<FriendlyByteBuf, ServiceNpcSpawnStateS2CPayload> STREAM_CODEC =
            StreamCodec.of(ServiceNpcSpawnStateS2CPayload::encode, ServiceNpcSpawnStateS2CPayload::decode);

    public ServiceNpcSpawnStateS2CPayload {
        cityOptions = List.copyOf(cityOptions);
        serviceTypeOptions = List.copyOf(serviceTypeOptions);
        taughtSkillLabels = List.copyOf(taughtSkillLabels);
        supplyRequirements = List.copyOf(supplyRequirements);
    }

    public static void send(
            ServerPlayer player,
            ServiceNpcSpawnMenu menu,
            ServiceNpcSpawnValidationError validationError
    ) {
        PacketDistributor.sendToPlayer(player, create(player, menu, validationError));
    }

    public static ServiceNpcSpawnStateS2CPayload create(
            ServerPlayer player,
            ServiceNpcSpawnMenu menu,
            ServiceNpcSpawnValidationError validationError
    ) {
        ServiceNpcSpawnBlockEntity blockEntity = player.serverLevel().getBlockEntity(menu.pos())
                instanceof ServiceNpcSpawnBlockEntity found ? found : null;

        var citySnapshot = BootstrapCityRegistryCache.snapshot();
        boolean cityAvailable = citySnapshot.available()
                && !citySnapshot.cities().isEmpty()
                && citySnapshot.cities().size() <= ServiceNpcSpawnPayloadCodec.MAX_CITY_OPTIONS;
        List<CityOption> cities = new ArrayList<>();
        if (cityAvailable) {
            for (BootstrapCityDefinition city : citySnapshot.cities().values()) {
                if (!isBounded(city.displayName(), ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES)) {
                    cityAvailable = false;
                    cities.clear();
                    break;
                }
                cities.add(new CityOption(city.publicId(), city.displayName()));
            }
        }

        var typeSnapshot = ServiceNpcRegistryCache.snapshot();
        List<ServiceTypeOption> types = typeSnapshot.serviceNpcTypes().values().stream()
                .filter(ServiceNpcTypeDefinition::active)
                .filter(ServiceNpcTypeDefinition::spawnable)
                .sorted(Comparator.comparing(ServiceNpcTypeDefinition::displayName)
                        .thenComparing(ServiceNpcTypeDefinition::key))
                .map(type -> new ServiceTypeOption(type.key(), type.displayName()))
                .toList();
        boolean typeAvailable = !types.isEmpty();
        if (types.size() > ServiceNpcSpawnPayloadCodec.MAX_TYPE_OPTIONS
                || types.stream().anyMatch(type -> !isBounded(type.key(), ServiceNpcSpawnPayloadCodec.MAX_TYPE_KEY_BYTES)
                || !isBounded(type.displayName(), ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES))) {
            typeAvailable = false;
            types = List.of();
        }

        if (blockEntity == null || blockEntity.getSpawnPointId() == null) {
            return new ServiceNpcSpawnStateS2CPayload(
                    menu.containerId,
                    menu.pos(),
                    menu.spawnPointId(),
                    false,
                    validationError,
                    cityAvailable,
                    typeAvailable,
                    cities,
                    types,
                    null,
                    null,
                    false,
                    false,
                    true,
                    0L,
                    ServiceNpcSpawnRegistrationState.ERROR,
                    "missing_block_entity",
                    null,
                    null,
                    0L,
                    null,
                    List.of(),
                    ServiceNpcSpawnEligibility.Status.SATISFIED,
                    List.of()
            );
        }

        UUID cityId = blockEntity.getCityPublicId();
        String typeKey = blockEntity.getServiceNpcTypeKey();
        ServiceNpcTypeDefinition storedType = typeKey == null ? null : typeSnapshot.serviceNpcTypes().get(typeKey);
        // Evaluated against the SAVED selection, not whatever the admin is currently browsing in
        // the dropdown: this payload is only rebuilt on open/save/refresh, and reporting a city's
        // economy against an unsaved selection would show a verdict for a configuration Rails has
        // never been told about.
        ServiceNpcSpawnEligibility.Result eligibility = ServiceNpcSpawnEligibility.evaluate(
                storedType,
                cityId == null ? null : citySnapshot.find(cityId)
        );
        String networkTypeKey = boundedOrFallback(
                typeKey,
                ServiceNpcSpawnPayloadCodec.MAX_TYPE_KEY_BYTES,
                "<oversized>"
        );
        String networkErrorCode = boundedOrFallback(
                blockEntity.getLastErrorCode(),
                ServiceNpcSpawnPayloadCodec.MAX_ERROR_BYTES,
                "stored_error_code_too_long"
        );
        String networkAssignedDisplayName = boundedOrFallback(
                blockEntity.getAssignedNpcDisplayName(),
                ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES,
                "<oversized display name>"
        );
        return new ServiceNpcSpawnStateS2CPayload(
                menu.containerId,
                menu.pos(),
                blockEntity.getSpawnPointId(),
                true,
                validationError,
                cityAvailable,
                typeAvailable,
                cities,
                types,
                cityId,
                networkTypeKey,
                cityAvailable && cityId != null && citySnapshot.cities().containsKey(cityId),
                typeAvailable && storedType != null && storedType.active() && storedType.spawnable(),
                blockEntity.isEnabled(),
                blockEntity.getConfigurationRevision(),
                blockEntity.getRegistrationState(),
                networkErrorCode,
                blockEntity.getAssignedNpcPublicId(),
                networkAssignedDisplayName,
                blockEntity.getAssignmentRevision(),
                blockEntity.getLastSuccessfulSyncEpochMillis(),
                taughtSkillLabelsFor(storedType),
                eligibility.status(),
                supplyLinesFor(eligibility)
        );
    }

    /**
     * The taught skills of the <em>saved</em> type, resolved to display names server-side.
     *
     * <p>Resolved here rather than on the client because the client has no skill definitions at
     * all -- {@code ClientSkillTable} holds only the viewing player's own values, keyed by slug,
     * with no names. {@link SkillManager#displayNameForSlug} reads the Rails-published definition
     * and falls back to a prettified slug, so a skill the server has not loaded a definition for
     * still reads as "Animal Lore" rather than "animal-lore".
     */
    private static List<String> taughtSkillLabelsFor(@Nullable ServiceNpcTypeDefinition type) {
        if (type == null) return List.of();
        return type.taughtSkillSlugs().stream()
                .limit(MAX_TAUGHT_SKILL_LABELS)
                .map(SkillManager::displayNameForSlug)
                .filter(label -> isBounded(label, ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES))
                .toList();
    }

    private static List<SupplyLine> supplyLinesFor(ServiceNpcSpawnEligibility.Result eligibility) {
        return eligibility.requirements().stream()
                .limit(MAX_SUPPLY_LINES)
                .filter(requirement -> isBounded(requirement.supply(), ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES))
                .map(requirement -> new SupplyLine(
                        requirement.supply(),
                        requirement.required(),
                        requirement.available() == null ? 0.0D : requirement.available(),
                        requirement.available() != null
                ))
                .toList();
    }

    private static void encode(FriendlyByteBuf buffer, ServiceNpcSpawnStateS2CPayload payload) {
        buffer.writeVarInt(payload.containerId);
        buffer.writeBlockPos(payload.pos);
        buffer.writeUUID(payload.spawnPointId);
        buffer.writeBoolean(payload.blockPresent);
        buffer.writeVarInt(payload.validationError.ordinal());
        buffer.writeBoolean(payload.cityRegistryAvailable);
        buffer.writeBoolean(payload.serviceTypeRegistryAvailable);
        buffer.writeVarInt(payload.cityOptions.size());
        payload.cityOptions.forEach(option -> {
            buffer.writeUUID(option.publicId());
            ServiceNpcSpawnPayloadCodec.writeUtf(buffer, option.displayName(), ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES);
        });
        buffer.writeVarInt(payload.serviceTypeOptions.size());
        payload.serviceTypeOptions.forEach(option -> {
            ServiceNpcSpawnPayloadCodec.writeUtf(buffer, option.key(), ServiceNpcSpawnPayloadCodec.MAX_TYPE_KEY_BYTES);
            ServiceNpcSpawnPayloadCodec.writeUtf(buffer, option.displayName(), ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES);
        });
        writeNullableUuid(buffer, payload.cityPublicId);
        writeNullableString(buffer, payload.serviceNpcTypeKey, ServiceNpcSpawnPayloadCodec.MAX_TYPE_KEY_BYTES);
        buffer.writeBoolean(payload.citySelectionValid);
        buffer.writeBoolean(payload.serviceTypeSelectionValid);
        buffer.writeBoolean(payload.enabled);
        buffer.writeLong(payload.configurationRevision);
        buffer.writeVarInt(payload.registrationState.ordinal());
        writeNullableString(buffer, payload.lastErrorCode, ServiceNpcSpawnPayloadCodec.MAX_ERROR_BYTES);
        writeNullableUuid(buffer, payload.assignedNpcPublicId);
        writeNullableString(buffer, payload.assignedNpcDisplayName, ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES);
        buffer.writeLong(payload.assignmentRevision);
        buffer.writeBoolean(payload.lastSuccessfulSyncEpochMillis != null);
        if (payload.lastSuccessfulSyncEpochMillis != null) buffer.writeLong(payload.lastSuccessfulSyncEpochMillis);
        buffer.writeVarInt(payload.taughtSkillLabels.size());
        payload.taughtSkillLabels.forEach(label ->
                ServiceNpcSpawnPayloadCodec.writeUtf(buffer, label, ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES));
        buffer.writeVarInt(payload.eligibilityStatus.ordinal());
        buffer.writeVarInt(payload.supplyRequirements.size());
        payload.supplyRequirements.forEach(line -> {
            ServiceNpcSpawnPayloadCodec.writeUtf(buffer, line.supply(), ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES);
            buffer.writeDouble(line.required());
            buffer.writeDouble(line.available());
            buffer.writeBoolean(line.measured());
        });
    }

    private static ServiceNpcSpawnStateS2CPayload decode(FriendlyByteBuf buffer) {
        ServiceNpcSpawnPayloadCodec.requireReadableLimit(buffer, ServiceNpcSpawnPayloadCodec.MAX_STATE_BYTES);
        int containerId = buffer.readVarInt();
        BlockPos pos = buffer.readBlockPos();
        UUID spawnPointId = buffer.readUUID();
        boolean blockPresent = buffer.readBoolean();
        ServiceNpcSpawnValidationError error = readEnum(buffer, ServiceNpcSpawnValidationError.values());
        boolean cityAvailable = buffer.readBoolean();
        boolean typeAvailable = buffer.readBoolean();

        int cityCount = readCount(buffer, ServiceNpcSpawnPayloadCodec.MAX_CITY_OPTIONS);
        List<CityOption> cities = new ArrayList<>(cityCount);
        for (int index = 0; index < cityCount; index++) {
            cities.add(new CityOption(
                    buffer.readUUID(),
                    ServiceNpcSpawnPayloadCodec.readUtf(buffer, ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES)
            ));
        }
        int typeCount = readCount(buffer, ServiceNpcSpawnPayloadCodec.MAX_TYPE_OPTIONS);
        List<ServiceTypeOption> types = new ArrayList<>(typeCount);
        for (int index = 0; index < typeCount; index++) {
            types.add(new ServiceTypeOption(
                    ServiceNpcSpawnPayloadCodec.readUtf(buffer, ServiceNpcSpawnPayloadCodec.MAX_TYPE_KEY_BYTES),
                    ServiceNpcSpawnPayloadCodec.readUtf(buffer, ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES)
            ));
        }

        return new ServiceNpcSpawnStateS2CPayload(
                containerId,
                pos,
                spawnPointId,
                blockPresent,
                error,
                cityAvailable,
                typeAvailable,
                cities,
                types,
                readNullableUuid(buffer),
                readNullableString(buffer, ServiceNpcSpawnPayloadCodec.MAX_TYPE_KEY_BYTES),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readLong(),
                readEnum(buffer, ServiceNpcSpawnRegistrationState.values()),
                readNullableString(buffer, ServiceNpcSpawnPayloadCodec.MAX_ERROR_BYTES),
                readNullableUuid(buffer),
                readNullableString(buffer, ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES),
                buffer.readLong(),
                buffer.readBoolean() ? buffer.readLong() : null,
                // Java evaluates arguments left to right, so these three read after every inline
                // read above and stay in wire order. They are method calls rather than locals for
                // exactly that reason: locals declared before this return would read too early.
                readTaughtSkillLabels(buffer),
                readEnum(buffer, ServiceNpcSpawnEligibility.Status.values()),
                readSupplyLines(buffer)
        );
    }

    private static List<String> readTaughtSkillLabels(FriendlyByteBuf buffer) {
        int count = readCount(buffer, MAX_TAUGHT_SKILL_LABELS);
        List<String> labels = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            labels.add(ServiceNpcSpawnPayloadCodec.readUtf(buffer, ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES));
        }
        return labels;
    }

    private static List<SupplyLine> readSupplyLines(FriendlyByteBuf buffer) {
        int count = readCount(buffer, MAX_SUPPLY_LINES);
        List<SupplyLine> lines = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            lines.add(new SupplyLine(
                    ServiceNpcSpawnPayloadCodec.readUtf(buffer, ServiceNpcSpawnPayloadCodec.MAX_LABEL_BYTES),
                    buffer.readDouble(),
                    buffer.readDouble(),
                    buffer.readBoolean()
            ));
        }
        return lines;
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) throw new IllegalArgumentException("Invalid option count " + count);
        return count;
    }

    private static <E> E readEnum(FriendlyByteBuf buffer, E[] values) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) throw new IllegalArgumentException("Invalid enum ordinal " + ordinal);
        return values[ordinal];
    }

    private static void writeNullableUuid(FriendlyByteBuf buffer, @Nullable UUID value) {
        buffer.writeBoolean(value != null);
        if (value != null) buffer.writeUUID(value);
    }

    @Nullable
    private static UUID readNullableUuid(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? buffer.readUUID() : null;
    }

    private static void writeNullableString(FriendlyByteBuf buffer, @Nullable String value, int maxBytes) {
        buffer.writeBoolean(value != null);
        if (value != null) ServiceNpcSpawnPayloadCodec.writeUtf(buffer, value, maxBytes);
    }

    @Nullable
    private static String readNullableString(FriendlyByteBuf buffer, int maxBytes) {
        return buffer.readBoolean() ? ServiceNpcSpawnPayloadCodec.readUtf(buffer, maxBytes) : null;
    }

    private static boolean isBounded(String value, int maxBytes) {
        return value != null && value.getBytes(StandardCharsets.UTF_8).length <= maxBytes;
    }

    @Nullable
    private static String boundedOrFallback(@Nullable String value, int maxBytes, String fallback) {
        if (value == null) return null;
        return isBounded(value, maxBytes) ? value : fallback;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
