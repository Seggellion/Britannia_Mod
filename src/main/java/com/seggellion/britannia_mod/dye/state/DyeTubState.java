package com.seggellion.britannia_mod.dye.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.seggellion.britannia_mod.bannerdyeing.api.DataCodecs;
import com.seggellion.britannia_mod.bannerdyeing.BannerDyeingConstants;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import io.netty.buffer.ByteBuf;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** An absent remaining-use count is the sole canonical representation of unlimited uses. */
public record DyeTubState(int schemaVersion, Optional<PigmentId> pigmentId, Optional<Integer> remainingUses) {
    private static final Codec<Decoded> RAW_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataCodecs.CURRENT_SCHEMA_VERSION.fieldOf("schema_version").forGetter(Decoded::schemaVersion),
            PigmentId.CODEC.optionalFieldOf("pigment_id").forGetter(Decoded::pigmentId),
            Codec.intRange(0, Integer.MAX_VALUE).optionalFieldOf("remaining_uses").forGetter(Decoded::remainingUses)
    ).apply(instance, Decoded::new));
    public static final Codec<DyeTubState> CODEC = RAW_CODEC.flatXmap(DyeTubState::decode,
            state -> DataResult.success(new Decoded(state.schemaVersion, state.pigmentId, state.remainingUses)));
    public static final StreamCodec<ByteBuf, DyeTubState> STREAM_CODEC = StreamCodec.ofMember(
            DyeTubState::encode, DyeTubState::decode);

    public DyeTubState {
        DataCodecs.requireCurrentSchema(schemaVersion);
        pigmentId = Objects.requireNonNull(pigmentId, "pigmentId");
        remainingUses = Objects.requireNonNull(remainingUses, "remainingUses");
        remainingUses.ifPresent(uses -> {
            if (uses < 0) {
                throw new IllegalArgumentException("remainingUses must not be negative");
            }
        });
        if (pigmentId.isEmpty() && remainingUses.isPresent()) {
            throw new IllegalArgumentException("remainingUses requires a loaded pigment");
        }
    }

    public static DyeTubState empty() {
        return new DyeTubState(BannerDyeingConstants.CURRENT_SCHEMA_VERSION, Optional.empty(), Optional.empty());
    }

    public static DyeTubState loadedUnlimited(PigmentId pigmentId) {
        return new DyeTubState(
                BannerDyeingConstants.CURRENT_SCHEMA_VERSION,
                Optional.of(Objects.requireNonNull(pigmentId, "pigmentId")),
                Optional.empty());
    }

    private static DataResult<DyeTubState> decode(Decoded decoded) {
        if (decoded.pigmentId.isEmpty() && decoded.remainingUses.isPresent()) {
            return DataResult.error(() -> "remaining_uses requires pigment_id");
        }
        return DataResult.success(new DyeTubState(decoded.schemaVersion, decoded.pigmentId, decoded.remainingUses));
    }

    private void encode(ByteBuf buffer) {
        ByteBufCodecs.VAR_INT.encode(buffer, schemaVersion);
        buffer.writeBoolean(pigmentId.isPresent());
        pigmentId.ifPresent(pigment -> PigmentId.STREAM_CODEC.encode(buffer, pigment));
        buffer.writeBoolean(remainingUses.isPresent());
        remainingUses.ifPresent(uses -> ByteBufCodecs.VAR_INT.encode(buffer, uses));
    }

    private static DyeTubState decode(ByteBuf buffer) {
        int schemaVersion = ByteBufCodecs.VAR_INT.decode(buffer);
        Optional<PigmentId> pigmentId = buffer.readBoolean()
                ? Optional.of(PigmentId.STREAM_CODEC.decode(buffer))
                : Optional.empty();
        Optional<Integer> remainingUses = buffer.readBoolean()
                ? Optional.of(ByteBufCodecs.VAR_INT.decode(buffer))
                : Optional.empty();
        return new DyeTubState(schemaVersion, pigmentId, remainingUses);
    }

    private record Decoded(int schemaVersion, Optional<PigmentId> pigmentId, Optional<Integer> remainingUses) {
    }
}
