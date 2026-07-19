package com.seggellion.britannia_mod.network.payload;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.city.BootstrapCityDefinition;
import com.seggellion.britannia_mod.city.BootstrapCityRegistryCache;
import com.seggellion.britannia_mod.entity.ServiceNpcEntity;
import com.seggellion.britannia_mod.service.banking.BankingOpenAccount;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;
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
        @Nullable String cityDisplayName,
        int weightLimit,
        double currentWeight,
        int goldBalance,
        int silverBalance,
        int copperBalance
) implements CustomPacketPayload {
    private static final int MAX_NAME_BYTES = 128;
    private static final String FALLBACK_TELLER_NAME = "the teller";

    public static final Type<BankAccountOpenedS2CPayload> TYPE = new Type<>(
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "bank_account_opened")
    );
    public static final StreamCodec<FriendlyByteBuf, BankAccountOpenedS2CPayload> STREAM_CODEC =
            StreamCodec.of(BankAccountOpenedS2CPayload::encode, BankAccountOpenedS2CPayload::decode);

    public static void send(ServerPlayer player, ServiceNpcEntity teller, BankingOpenAccount account) {
        PacketDistributor.sendToPlayer(player, create(teller, account));
    }

    public static BankAccountOpenedS2CPayload create(ServiceNpcEntity teller, BankingOpenAccount account) {
        Objects.requireNonNull(teller, "teller");
        Objects.requireNonNull(account, "account");
        return new BankAccountOpenedS2CPayload(
                resolveTellerName(teller),
                resolveCityDisplayName(account.cityPublicId()),
                account.weightLimit(),
                account.currentWeight(),
                account.goldBalance(),
                account.silverBalance(),
                account.copperBalance()
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
        buffer.writeBoolean(payload.cityDisplayName != null);
        if (payload.cityDisplayName != null) {
            ServiceNpcSpawnPayloadCodec.writeUtf(buffer, payload.cityDisplayName, MAX_NAME_BYTES);
        }
        buffer.writeVarInt(payload.weightLimit);
        buffer.writeDouble(payload.currentWeight);
        buffer.writeVarInt(payload.goldBalance);
        buffer.writeVarInt(payload.silverBalance);
        buffer.writeVarInt(payload.copperBalance);
    }

    private static BankAccountOpenedS2CPayload decode(FriendlyByteBuf buffer) {
        String tellerName = ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_NAME_BYTES);
        String cityDisplayName = buffer.readBoolean() ? ServiceNpcSpawnPayloadCodec.readUtf(buffer, MAX_NAME_BYTES) : null;
        int weightLimit = buffer.readVarInt();
        double currentWeight = buffer.readDouble();
        int goldBalance = buffer.readVarInt();
        int silverBalance = buffer.readVarInt();
        int copperBalance = buffer.readVarInt();
        return new BankAccountOpenedS2CPayload(
                tellerName, cityDisplayName, weightLimit, currentWeight, goldBalance, silverBalance, copperBalance
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
