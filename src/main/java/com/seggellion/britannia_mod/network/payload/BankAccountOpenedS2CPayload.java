package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.service.banking.BankItemSummary;
import com.seggellion.britannia_mod.service.banking.BankingOpenAccount;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Carries a successful {@code banking/open} result to the client so it can open the real
 * (read-only) Bank Screen, replacing Slice A's chat-message placeholder. Built server-side
 * from data already in hand at result time ({@link ServiceNpcEntity#getPersonalName()} for
 * the teller and {@link BootstrapCityRegistryCache} to resolve {@code cityPublicId} to a
 * display name, mirroring exactly how {@code ServiceNpcSpawnStateS2CPayload} resolves city
 * display names server-side for its own screen) — never a raw UUID sent for display.
 * {@code cityDisplayName} is {@code null} whenever the account has no city (global mode);
 * the client must render nothing for city in that case, not an empty label.
 */
public record BankAccountOpenedS2CPayload(
        String tellerName,
        int entityId,
        @Nullable String cityDisplayName,
        int weightLimit,
        double currentWeight,
        int goldBalance,
        int silverBalance,
        int copperBalance,
        List<BankItemSummary> bankItems
) implements CustomPacketPayload {
    private static final int MAX_NAME_BYTES = 128;
    private static final String FALLBACK_TELLER_NAME = "the teller";
    /** Mirrors {@link BankingOpenResponseParser}'s own defensive bound on the same list. */
    private static final int MAX_BANK_ITEMS = 10_000;

    public BankAccountOpenedS2CPayload {
        bankItems = List.copyOf(bankItems);
    }

    public static final Type<BankAccountOpenedS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_account_opened")
    );
    public static final StreamCodec<FriendlyByteBuf, BankAccountOpenedS2CPayload> STREAM_CODEC =
            StreamCodec.of(BankAccountOpenedS2CPayload::encode, BankAccountOpenedS2CPayload::decode);

    public static void send(
            ServerPlayer player, ServiceNpcEntity teller, BankingOpenAccount account, List<BankItemSummary> bankItems
    ) {
        PacketDistributor.sendToPlayer(player, create(teller, account, bankItems));
    }

    public static BankAccountOpenedS2CPayload create(
            ServiceNpcEntity teller, BankingOpenAccount account, List<BankItemSummary> bankItems
    ) {
        Objects.requireNonNull(teller, "teller");
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(bankItems, "bankItems");
        return new BankAccountOpenedS2CPayload(
                resolveTellerName(teller),
                teller.getId(),
                resolveCityDisplayName(account.cityPublicId()),
                account.weightLimit(),
                account.currentWeight(),
                account.goldBalance(),
                account.silverBalance(),
                account.copperBalance(),
                bankItems
        );
    }

    private static String resolveTellerName(ServiceNpcEntity teller) {
        String personal = teller.getPersonalName();
        if (personal == null || personal.isBlank()) return FALLBACK_TELLER_NAME;
        return isBounded(personal) ? personal : FALLBACK_TELLER_NAME;
    }

    @Nullable
    private static String resolveCityDisplayName(@Nullable UUID cityPublicId) {
        if (cityPublicId == null) return null;
        BootstrapCityDefinition city = BootstrapCityRegistryCache.snapshot().find(cityPublicId);
        if (city == null) return null;
        return isBounded(city.displayName()) ? city.displayName() : null;
    }

    private static boolean isBounded(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length <= MAX_NAME_BYTES;
    }

    private static void encode(FriendlyByteBuf buffer, BankAccountOpenedS2CPayload payload) {
        ServiceNpcSpawnPayloadCodec.writeUtf(buffer, payload.tellerName, MAX_NAME_BYTES);
        ByteBufCodecs.VAR_INT.encode(buffer, payload.entityId);
        buffer.writeBoolean(payload.cityDisplayName != null);
        if (payload.cityDisplayName != null) {
            ServiceNpcSpawnPayloadCodec.writeUtf(buffer, payload.cityDisplayName, MAX_NAME_BYTES);
        }
        buffer.writeVarInt(payload.weightLimit);
        buffer.writeDouble(payload.currentWeight);
        buffer.writeVarInt(payload.goldBalance);
        buffer.writeVarInt(payload.silverBalance);
        buffer.writeVarInt(payload.copperBalance);

        if (payload.bankItems.size() > MAX_BANK_ITEMS) throw new IllegalArgumentException("Too many bank items");
        buffer.writeVarInt(payload.bankItems.size());
        for (BankItemSummary item : payload.bankItems) {
            buffer.writeUUID(item.publicId());
            buffer.writeDouble(item.weight());
        }
    }

    private static BankAccountOpenedS2CPayload decode(FriendlyByteBuf buffer) {
        String tellerName = ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_NAME_BYTES);
        int entityId = ByteBufCodecs.VAR_INT.decode(buffer);
        String cityDisplayName = buffer.readBoolean() ? ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_NAME_BYTES) : null;
        int weightLimit = buffer.readVarInt();
        double currentWeight = buffer.readDouble();
        int goldBalance = buffer.readVarInt();
        int silverBalance = buffer.readVarInt();
        int copperBalance = buffer.readVarInt();

        int bankItemCount = buffer.readVarInt();
        if (bankItemCount < 0 || bankItemCount > MAX_BANK_ITEMS) throw new IllegalArgumentException("Invalid bank item count");
        List<BankItemSummary> bankItems = new ArrayList<>(bankItemCount);
        for (int i = 0; i < bankItemCount; i++) {
            UUID publicId = buffer.readUUID();
            double weight = buffer.readDouble();
            bankItems.add(new BankItemSummary(publicId, weight));
        }

        return new BankAccountOpenedS2CPayload(
                tellerName, entityId, cityDisplayName, weightLimit, currentWeight, goldBalance, silverBalance, copperBalance,
                bankItems
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
