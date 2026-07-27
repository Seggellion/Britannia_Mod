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
 * <p>{@code displayAmount} is stored in copper (this program's canonical unit -- ADR-018/019,
 * {@link com.seggellion.britannia_mod.economy.CoinConversion}), matching exactly what Rails'
 * {@code BankCheque.amount} itself stores, not a display-converted gold figure -- conversion to
 * a human-readable "X gold" string happens only at render time (see {@code BankChequeItem}),
 * so a future redemption slice never has to guess which unit an already-issued cheque's
 * component was written in.
 *
 * <p>No redemption code in this slice ever reads {@code chequeId} back out to authorize
 * anything -- that is explicitly a later slice's job. This component exists only so the item
 * has stable, round-trippable identity and a legible tooltip.
 */
public record BankChequeData(UUID chequeId, long displayAmount, String issuerText) {
    public static final BankChequeData EMPTY = new BankChequeData(new UUID(0L, 0L), 0L, "");

    public static final Codec<BankChequeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("chequeId").forGetter(BankChequeData::chequeId),
            Codec.LONG.fieldOf("displayAmount").forGetter(BankChequeData::displayAmount),
            Codec.STRING.fieldOf("issuerText").forGetter(BankChequeData::issuerText)
    ).apply(instance, BankChequeData::new));

    public static final StreamCodec<ByteBuf, BankChequeData> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, BankChequeData::chequeId,
            ByteBufCodecs.VAR_LONG, BankChequeData::displayAmount,
            ByteBufCodecs.STRING_UTF8, BankChequeData::issuerText,
            BankChequeData::new
    );
}
