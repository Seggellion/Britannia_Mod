package com.seggellion.britannia_mod.network.payload.housing;

import com.seggellion.britannia_mod.BritanniaMod;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Tells one client that the server has lent it, or taken back, the right to build.
 *
 * <h2>Why this packet has to exist</h2>
 *
 * <p>{@code SurvivalZoneHandler} grants a house owner {@code Abilities.mayBuild} while they stand
 * inside their own house, and that is genuinely all Adventure mode's server-side gates read. But
 * {@code ClientboundPlayerAbilitiesPacket} carries only {@code invulnerable}, {@code flying},
 * {@code mayfly} and {@code instabuild} — <em>not</em> {@code mayBuild}. The client's own copy of
 * that flag is written once, by {@code GameType.updatePlayerAbilities}, whenever the game mode
 * changes, and for Adventure that writes {@code false} and nothing ever writes it again.
 *
 * <p>So the owner's lent right was invisible to their own client, and
 * {@code MultiPlayerGameMode.startDestroyBlock} refused the swing locally: the break packet was
 * never sent, and the server never learned an owner had tried to break anything in their own
 * house. Placing worked, because the client does not gate {@code useItemOn} at all — which is
 * exactly the reported symptom, "I can place blocks in my house, I cannot break them".
 *
 * <p>This carries one boolean and nothing else. It is not authority: the server still decides
 * every break through {@code HouseBuildRights} and {@code StructureProtectionHandler}, and a
 * client that lied about this flag would simply have its break refused server-side as it is today.
 * All the flag does is stop the client refusing on the server's behalf, with stale information.
 */
public record S2CHouseBuildRightsPayload(boolean granted) implements CustomPacketPayload {

    public static final ResourceLocation TYPE_ID =
            ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "house_build_rights");
    public static final Type<S2CHouseBuildRightsPayload> TYPE = new Type<>(TYPE_ID);

    public static final StreamCodec<FriendlyByteBuf, S2CHouseBuildRightsPayload> STREAM_CODEC =
            StreamCodec.of(
                    (buffer, payload) -> buffer.writeBoolean(payload.granted),
                    buffer -> new S2CHouseBuildRightsPayload(buffer.readBoolean()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
