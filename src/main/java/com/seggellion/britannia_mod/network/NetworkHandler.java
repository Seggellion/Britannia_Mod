package com.seggellion.britannia_mod.network;

import com.seggellion.britannia_mod.ui.ManaOverlayScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import com.seggellion.britannia_mod.client.gui.HouseManagementScreen;
import com.seggellion.britannia_mod.network.ManaSyncPayload;
import com.seggellion.britannia_mod.network.RenameStorePayload;
import com.seggellion.britannia_mod.network.StoreSignScreenPayload;
import com.seggellion.britannia_mod.network.payload.TogglePrivacyPayload;
import com.seggellion.britannia_mod.network.payload.BuyItemsC2SPayload;
import com.seggellion.britannia_mod.network.payload.RequestCatalogC2SPayload;
import com.seggellion.britannia_mod.network.payload.TransactionSuccessS2CPayload;
import com.seggellion.britannia_mod.network.payload.TransactionFailedS2CPayload;
import com.seggellion.britannia_mod.network.payload.CloseScreenS2CPayload;
import com.seggellion.britannia_mod.network.HousePlacementPayload;
import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;
import com.seggellion.britannia_mod.network.HousePlacementHandler;
import com.seggellion.britannia_mod.structure.HousePrivacyHandler;
import com.seggellion.britannia_mod.block.entity.HouseLotBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;

import com.seggellion.britannia_mod.structure.HouseActionHandler;
import com.seggellion.britannia_mod.network.ClientboundOpenArchitectScreenPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.handling.IPayloadHandler;
import net.neoforged.fml.loading.FMLLoader;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;

import java.util.UUID;

public class NetworkHandler {

 @SubscribeEvent
public static void register(final RegisterPayloadHandlersEvent event) {
    final PayloadRegistrar registrar = event.registrar("1");

    /* ---------- packets that exist on BOTH sides or are SERVER-bound ---------- */

    registrar.playToServer(
        BuyItemsC2SPayload.TYPE, BuyItemsC2SPayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                BuyItemsC2SPayload.handle(payload, p);
            }
        }));

    registrar.playToServer(
        HousePlacementPayload.TYPE, HousePlacementPayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                HousePlacementHandler.handle(payload, p);
            }
        }));

    registrar.playToServer(
        SpellCastPayload.TYPE, SpellCastPayload.STREAM_CODEC,
        (data, ctx) -> handleSpellCastOnServer(data, ctx));

    registrar.playToServer(
        RenameStorePayload.TYPE, RenameStorePayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                RenameStorePayload.handle(payload, p);
            }
        }));

    registrar.playToServer(
        RenameHousePayload.TYPE, RenameHousePayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                RenameHouseHandler.handle(payload, p);
            }
        }));

    registrar.playToServer(
        UpdateSignStylePayload.TYPE, UpdateSignStylePayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                UpdateSignStyleHandler.handle(payload, p);
            }
        }));

    registrar.playToServer(
        HouseManagementActionPayload.TYPE, HouseManagementActionPayload.STREAM_CODEC,
        (data, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p
                    && data.action() == HouseManagementActionPayload.Action.REDEED) {
                HouseActionHandler.handleRedeed(p);
            }
        }));

    registrar.playToServer(
        RequestCatalogC2SPayload.TYPE, RequestCatalogC2SPayload.STREAM_CODEC,
        (payload, ctx) -> ctx.enqueueWork(() -> {
            if (ctx.player() instanceof ServerPlayer p) {
                RequestCatalogC2SPayload.handle(payload, p);
            }
        }));

registrar.playToServer(
    TogglePrivacyPayload.TYPE,
    TogglePrivacyPayload.STREAM_CODEC,
    (pkt, ctx) -> ctx.enqueueWork(() -> {

        if (!(ctx.player() instanceof ServerPlayer player)) return;
        ServerLevel level = player.serverLevel();

        BlockEntity be = level.getBlockEntity(pkt.pos());
        if (!(be instanceof HouseLotBlockEntity lot)) return;

        HousePrivacyHandler.handle(player, lot, pkt.makePrivate());
    }));


        // Mana sync
       /* ---------- client-bound packets ---------- */
// Mana sync
registrar.playToClient(
    ManaSyncPayload.TYPE,
    ManaSyncPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleManaSyncOnClient
        : (p, c) -> {});

// Architect screen
registrar.playToClient(
    ClientboundOpenArchitectScreenPayload.TYPE,
    ClientboundOpenArchitectScreenPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleOpenArchitectScreen
        : (p, c) -> {});

// Close current screen
registrar.playToClient(
    CloseScreenS2CPayload.TYPE,
    CloseScreenS2CPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? CloseScreenS2CPayload::handle
        : (p, c) -> {});

// Store-sign screen
registrar.playToClient(
    StoreSignScreenPayload.TYPE,
    StoreSignScreenPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleStoreSignScreenOnClient
        : (p, c) -> {});

// Transaction success / failure
registrar.playToClient(
    TransactionSuccessS2CPayload.TYPE,
    TransactionSuccessS2CPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? TransactionSuccessS2CPayload::handle
        : (p, c) -> {});

registrar.playToClient(
    TransactionFailedS2CPayload.TYPE,
    TransactionFailedS2CPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? TransactionFailedS2CPayload::handle
        : (p, c) -> {});

// House-management screen
registrar.playToClient(
    HouseManagementScreenPayload.TYPE,
    HouseManagementScreenPayload.STREAM_CODEC,
    FMLLoader.getDist().isClient()
        ? ClientNetworkHandler::handleHouseScreenOnClient
        : (p, c) -> {});

    
}


public static void sendToServer(SpellCastPayload payload) {
    if (FMLLoader.getDist().isClient()) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }
}

   public static void sendToServer(HousePlacementPayload payload) {
        if (FMLLoader.getDist().isClient()) {
            Minecraft.getInstance()
                     .getConnection()
                     .send(new ServerboundCustomPayloadPacket(payload));
        }
    }
    

public static void sendToServer(CustomPacketPayload payload) {
    if (FMLLoader.getDist().isClient()) {
        Minecraft.getInstance().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }
}



    // Method to send packets from server → client
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        player.connection.send(new ClientboundCustomPayloadPacket(payload));
    }
        // ✅ Handle spell casting on server
    public static void handleSpellCastOnServer(SpellCastPayload data, IPayloadContext context) {
        context.enqueueWork(() -> {
            Player playerEntity = context.player();
            if (playerEntity instanceof ServerPlayer player) {
                data.handleOnServer(player);
            }
        });
    }
}