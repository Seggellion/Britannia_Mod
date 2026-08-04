package com.seggellion.britannia_mod.component;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.UUID;

/**
 * The physical bank cheque item's data component -- Milestone 11 NeoForge Slice 1. Carries
 * exactly what Codex Prompt 11's own invariant allows: {@code chequeId} (the real, Rails-issued
 * {@code BankCheque.public_id} -- the one thing that actually identifies the Rails value
 * instrument) plus {@code displayAmount}/{@code issuerText}, which exist purely for the
 * player's own tooltip/UI and are NEVER read to decide what redemption is worth ("The check's
 * displayed amount cannot alter value" -- Codex Prompt 11's own non-negotiable constraint).
 *
 * <p><b>Milestone 8c (ADR-027): {@code displayAmount} is a count of coins of {@code currencyKey}</b>,
 * matching exactly what Rails' {@code BankCheque.amount} now stores. It used to be a copper value,
 * which rendered correctly only for gold -- a 500-silver cheque read as "5 gold", because the
 * tooltip divided by {@code COPPER_PER_GOLD}. The two fields are meaningful only together, and
 * there is no longer any conversion between what was written, what is stored, and what redemption
 * pays back.
 *
 * <p>No redemption code in this slice ever reads {@code chequeId} back out to authorize
 * anything -- that is explicitly a later slice's job. This component exists only so the item
 * has stable, round-trippable identity and a legible tooltip.
 */
public record BankChequeData(UUID chequeId, long displayAmount, String issuerText, String currencyKey) {

    /**
     * What a cheque issued before Milestone 8b was funded from. Every cheque was gold-only then,
     * so this is the true answer for an existing item rather than a guess -- which is why the
     * codec can default to it rather than having to migrate anything.
     */
    public static final String DEFAULT_CURRENCY_KEY = "gold";

    public static final BankChequeData EMPTY =
            new BankChequeData(new UUID(0L, 0L), 0L, "", DEFAULT_CURRENCY_KEY);

    /**
     * {@code currencyKey} is optional on read. A cheque already sitting in a player's inventory or
     * an existing world save has no such field, and inventing a migration for a value that is
     * knowably "gold" would be work for its own sake.
     *
     * <p>It is presentation only -- it tints the item and names the denomination in the tooltip.
     * Redemption is still decided entirely by {@code chequeId} against Rails, so a modified
     * component can change what a cheque <em>looks</em> like and never what it is worth.
     */
    public static final Codec<BankChequeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("chequeId").forGetter(BankChequeData::chequeId),
            Codec.LONG.fieldOf("displayAmount").forGetter(BankChequeData::displayAmount),
            Codec.STRING.fieldOf("issuerText").forGetter(BankChequeData::issuerText),
            Codec.STRING.optionalFieldOf("currencyKey", DEFAULT_CURRENCY_KEY).forGetter(BankChequeData::currencyKey)
    ).apply(instance, BankChequeData::new));

    public static final StreamCodec<ByteBuf, BankChequeData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BankChequeData::chequeId,
            ByteBufCodecs.VAR_LONG, BankChequeData::displayAmount,
            ByteBufCodecs.STRING_UTF8, BankChequeData::issuerText,
            ByteBufCodecs.STRING_UTF8, BankChequeData::currencyKey,
            BankChequeData::new
    );

    public BankChequeData {
        if (currencyKey == null || currencyKey.isBlank()) currencyKey = DEFAULT_CURRENCY_KEY;
    }
}
