package com.seggellion.britannia_mod.dye.preview;

import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import com.seggellion.britannia_mod.network.payload.dye.S2CDyeApplicationResultPayload;
import com.seggellion.britannia_mod.network.payload.dye.S2COpenDyePreviewPayload;
import com.seggellion.britannia_mod.banner.renderdata.BannerPreviewRenderState;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.DataComponentRegistry;
import com.seggellion.britannia_mod.registry.DyeItemRegistry;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** The sole live-server bridge for preview creation, confirmation, cancellation, and feedback. */
public final class DyePreviewRuntime {
    private static final DyePreviewSessionService SESSIONS = new DyePreviewSessionService();
    private static final DyePreviewValidationService PREVIEWS = new DyePreviewValidationService(new DyeResolver());
    private static final DyeApplicationService APPLICATIONS = new DyeApplicationService(new DyeResolver());

    private DyePreviewRuntime() {
    }

    public static DyePreviewFailure openPreview(ServerPlayer player) {
        ItemStack mainHand = player.getMainHandItem();
        ItemStack offHand = player.getOffhandItem();
        BannerItem bannerItem = BannerItemRegistry.BANNER.get();
        RegistrySnapshot snapshot = BannerDataRegistries.current();
        DyePreviewPlan plan = PREVIEWS.plan(
                mainHand, DyeItemRegistry.DYE_TUB.get(), offHand, bannerItem,
                snapshot, BannerDataRegistries.isAvailable(), DataComponentRegistry.DYE_TUB_STATE.get());
        if (!plan.successful()) {
            player.displayClientMessage(Component.translatable(DyePreviewMessages.previewFailure(plan.failure())), true);
            return plan.failure();
        }
        Optional<DyePreviewSession> created = SESSIONS.create(player.getUUID(), mainHand, offHand, plan, snapshot);
        if (created.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    DyePreviewMessages.previewFailure(DyePreviewFailure.SESSION_CREATION_FAILURE)), true);
            return DyePreviewFailure.SESSION_CREATION_FAILURE;
        }
        DyePreviewSession session = created.orElseThrow();
        BannerPreviewRenderState currentRender = BannerPreviewRenderState.from(session.bannerState());
        BannerPreviewRenderState proposedRender = new BannerPreviewRenderState(
                session.bannerState().bannerDefinitionId(), session.bannerState().materialId(),
                session.resolvedResult().resolvedColourId(), session.bannerState().mountId());
        player.connection.send(new ClientboundCustomPayloadPacket(new S2COpenDyePreviewPayload(
                session.sessionId(), session.displayData(), currentRender, proposedRender,
                SESSIONS.lifetimeMillis())));
        return DyePreviewFailure.NONE;
    }

    public static DyeApplicationResultCode confirm(ServerPlayer player, UUID sessionId) {
        DyePreviewSessionService.SessionClaim claim = SESSIONS.claimForConfirmation(player.getUUID(), sessionId);
        DyeApplicationResultCode result;
        if (claim.session().isEmpty()) {
            result = claim.result();
        } else {
            BannerItem bannerItem = BannerItemRegistry.BANNER.get();
            result = APPLICATIONS.apply(
                    claim.session().orElseThrow(), player.getItemInHand(InteractionHand.MAIN_HAND),
                    player.getOffhandItem(), DyeItemRegistry.DYE_TUB.get(), bannerItem,
                    BannerDataRegistries.current(), BannerDataRegistries.isAvailable(),
                    DataComponentRegistry.DYE_TUB_STATE.get());
        }
        if (result != DyeApplicationResultCode.CANCELLED) {
            player.displayClientMessage(Component.translatable(DyePreviewMessages.applicationResult(result)), true);
        }
        if (result.successfulApplication()) {
            emitSuccess(player);
        }
        player.connection.send(new ClientboundCustomPayloadPacket(
                new S2CDyeApplicationResultPayload(sessionId, result, true)));
        return result;
    }

    public static DyeApplicationResultCode cancel(ServerPlayer player, UUID sessionId) {
        DyeApplicationResultCode result = SESSIONS.cancel(player.getUUID(), sessionId);
        player.connection.send(new ClientboundCustomPayloadPacket(
                new S2CDyeApplicationResultPayload(sessionId, result, true)));
        return result;
    }

    public static void invalidatePlayer(UUID playerId) {
        SESSIONS.invalidatePlayer(playerId);
    }

    public static void clear() {
        SESSIONS.clear();
    }

    static DyePreviewSessionService sessions() {
        return SESSIONS;
    }

    private static void emitSuccess(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        level.playSound(null, player.blockPosition(), SoundEvents.DYE_USE, SoundSource.PLAYERS, 0.7F, 1.0F);
        level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                player.getX(), player.getY() + 0.8D, player.getZ(),
                6, 0.2D, 0.25D, 0.2D, 0.01D);
    }
}
