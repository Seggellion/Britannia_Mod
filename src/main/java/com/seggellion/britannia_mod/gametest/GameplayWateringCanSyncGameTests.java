package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.entity.FarmingBlockEntity;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.Arrays;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual server inventory packets and entity-data codecs; does not certify artwork or GPU rendering. */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayWateringCanSyncGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    @GameTest(template = TEMPLATE)
    public static void chargesSurviveSlotPacketsSwappingDroppingPickupAndReconnectData(GameTestHelper h) {
        var rig = join(h.getLevel(), "CanWireStates");
        try {
            var player = rig.player;
            for (int charges : new int[]{0, 1, 11, 12, -1}) {
                player.getInventory().clearContent();
                player.getInventory().selected = 0;
                var can = new ItemStack(ItemRegistry.WATERING_CAN.get());
                can.set(DataComponents.CUSTOM_NAME, Component.literal("Can wire specimen"));
                player.setItemInHand(InteractionHand.MAIN_HAND, can);
                rig.sync(); // Baseline full stack, followed by an in-place component change.
                if (charges >= 0) WateringCanItem.setWaterCharges(can, charges);
                int expected = charges < 0 ? 12 : charges;
                rig.sync();
                assertCan(h, rig.slots[36], expected, "mainhand slot packet");

                player.connection.handlePlayerAction(new ServerboundPlayerActionPacket(
                        ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ZERO, Direction.DOWN));
                rig.sync();
                h.assertTrue(rig.slots[36].isEmpty(), "swap left can in mainhand replica");
                assertCan(h, rig.slots[45], expected, "offhand swap packet");
                player.inventoryMenu.clicked(45, 0, ClickType.PICKUP, player);
                player.inventoryMenu.clicked(9, 0, ClickType.PICKUP, player);
                rig.sync();
                h.assertTrue(rig.slots[45].isEmpty(), "move left can in offhand replica");
                assertCan(h, rig.slots[9], expected, "inventory move packet");

                var drop = player.drop(player.getInventory().removeItem(9, 1), false, true);
                h.assertTrue(drop != null, "drop failed");
                rig.sync();
                h.assertTrue(rig.slots[9].isEmpty(), "drop left inventory replica occupied");
                var data = roundTrip(h.getLevel(), ClientboundSetEntityDataPacket.STREAM_CODEC,
                        new ClientboundSetEntityDataPacket(drop.getId(), drop.getEntityData().getNonDefaultValues()));
                var droppedReplica = new ItemEntity(h.getLevel(), 0, 0, 0, ItemStack.EMPTY);
                droppedReplica.getEntityData().assignValues(data.packedItems());
                assertCan(h, droppedReplica.getItem(), expected, "dropped entity-data packet");
                drop.setNoPickUpDelay(); drop.playerTouch(player);
                h.assertTrue(drop.isRemoved(), "pickup did not consume dropped entity");
                rig.sync();
                assertCan(h, rig.slots[36], expected, "pickup slot packet");

                var savedInventory = player.getInventory().save(new ListTag());
                var reconnect = join(h.getLevel(), "CanWireReload");
                try {
                    reconnect.player.getInventory().load(savedInventory);
                    reconnect.player.inventoryMenu.sendAllDataToRemote();
                    reconnect.sync();
                    assertCan(h, reconnect.slots[36], expected, "saved inventory and reconnect content packet");
                } finally { reconnect.close(); }
            }
        } finally { rig.close(); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void actualRefillAndDispenseSendChangedComponentsInBothHands(GameTestHelper h) {
        var rig = join(h.getLevel(), "CanWireUse");
        var pos = h.absolutePos(new BlockPos(2, 2, 2));
        try {
            for (var hand : InteractionHand.values()) {
                rig.player.getInventory().clearContent(); rig.player.getInventory().selected = 0;
                var can = new ItemStack(ItemRegistry.WATERING_CAN.get());
                can.set(DataComponents.CUSTOM_NAME, Component.literal("Can wire specimen"));
                WateringCanItem.setWaterCharges(can, 0);
                rig.player.setItemInHand(hand, can); rig.sync();
                int slot = hand == InteractionHand.MAIN_HAND ? 36 : 45;
                assertCan(h, rig.slots[slot], 0, "before refill");
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                h.getLevel().setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                can.getItem().useOn(new UseOnContext(rig.player, hand, hit)); rig.sync();
                assertCan(h, rig.slots[slot], 12, "actual refill packet");
                h.getLevel().setBlock(pos, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
                var soil = (FarmingBlockEntity) h.getLevel().getBlockEntity(pos);
                for (int expected = 11; expected >= 0; expected--) {
                    soil.setHydration(0);
                    can.getItem().useOn(new UseOnContext(rig.player, hand, hit)); rig.sync();
                    assertCan(h, rig.slots[slot], expected, "actual dispense packet");
                    h.assertTrue(soil.getHydration() == 1, "dispense changed one-charge water use");
                }
                soil.setHydration(0);
                can.getItem().useOn(new UseOnContext(rig.player, hand, hit)); rig.sync();
                assertCan(h, rig.slots[slot], 0, "empty use");
                h.assertTrue(soil.getHydration() == 0, "empty can dispensed water");
            }
        } finally { rig.close(); }
        h.succeed();
    }

    private static void assertCan(GameTestHelper h, ItemStack stack, int expected, String stage) {
        h.assertTrue(stack.is(ItemRegistry.WATERING_CAN.get()) && stack.getCount() == 1, stage + " lost item/count");
        h.assertTrue(WateringCanItem.getWaterCharges(stack) == expected, stage + " lost charges " + expected);
        h.assertTrue(WateringCanItem.fullModelState(stack) == (expected == 12 ? 1f : 0f), stage + " wrong predicate");
        h.assertTrue(Component.literal("Can wire specimen").equals(stack.get(DataComponents.CUSTOM_NAME)), stage + " lost name");
    }

    private static <T> T roundTrip(ServerLevel level, StreamCodec<? super RegistryFriendlyByteBuf, T> codec, T packet) {
        var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
        try { codec.encode(buffer, packet); return codec.decode(buffer); }
        finally { buffer.release(); }
    }

    private static Rig join(ServerLevel level, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(GameType.SURVIVAL);
        return new Rig(player, channel);
    }

    private static final class Rig {
        final ServerPlayer player;
        final EmbeddedChannel channel;
        final ItemStack[] slots = new ItemStack[46];
        Rig(ServerPlayer player, EmbeddedChannel channel) {
            this.player = player; this.channel = channel; Arrays.fill(slots, ItemStack.EMPTY);
        }
        void sync() {
            // This is the vanilla per-player tick synchronization path, not a mod-forced full resend.
            player.containerMenu.broadcastChanges(); channel.flushOutbound();
            Object packet;
            while ((packet = channel.outboundMessages().poll()) != null) receive(packet);
        }
        void receive(Object packet) {
            if (packet instanceof ClientboundBundlePacket bundle) {
                for (var nested : bundle.subPackets()) receive(nested);
            } else if (packet instanceof ClientboundContainerSetSlotPacket slot && slot.getContainerId() == 0) {
                var decoded = roundTrip(player.serverLevel(), ClientboundContainerSetSlotPacket.STREAM_CODEC, slot);
                slots[decoded.getSlot()] = decoded.getItem();
            } else if (packet instanceof ClientboundContainerSetContentPacket content && content.getContainerId() == 0) {
                var decoded = roundTrip(player.serverLevel(), ClientboundContainerSetContentPacket.STREAM_CODEC, content);
                for (int i = 0; i < decoded.getItems().size(); i++) slots[i] = decoded.getItems().get(i);
            }
        }
        void close() { player.server.getPlayerList().remove(player); channel.finishAndReleaseAll(); }
    }
}
