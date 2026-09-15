package com.seggellion.britannia_mod.gametest;

import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.block.*;
import com.seggellion.britannia_mod.block.entity.*;
import com.seggellion.britannia_mod.farming.*;
import com.seggellion.britannia_mod.item.WateringCanItem;
import com.seggellion.britannia_mod.registry.*;
import com.seggellion.britannia_mod.skill.SkillManager;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.*;
import net.minecraft.server.level.*;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayPlantingPresentationGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final BlockPos P = new BlockPos(3, 2, 3);

    @GameTest(template = TEMPLATE, timeoutTicks = 200)
    public static void everySpeciesAndHandCommitsOneSeedSoundMessageAndPaidPractice(GameTestHelper h) throws Exception {
        var level = h.getLevel(); var pos = h.absolutePos(P); var rig = join(level, "PlantingMessages");
        var rival = ManagedResourceTestPlayers.survival(level, "PlantingRival");
        record Species(String id, Item seed, float skill, CropDefinition crop) {}
        var all = new ArrayList<Species>();
        for (var crop : CropRegistry.all()) all.add(new Species(crop.id(), crop.seedItem().get(), crop.minimumFarmingSkill(), crop));
        for (var flower : FlowerRegistry.initial().definitions().values()) all.add(new Species(flower.id().getPath(), BuiltInRegistries.ITEM.get(flower.seedItemId()), flower.minimumFarmingSkill(), null));
        var sounds = new AtomicInteger();
        java.util.function.Consumer<PlayLevelSoundEvent.AtPosition> listener = e -> {
            if (e.getLevel() == level && e.getPosition().distanceToSqr(Vec3.atCenterOf(pos)) < 1
                    && e.getSound() != null && e.getSound().value() == SoundEvents.CROP_PLANTED) sounds.incrementAndGet();
        };
        NeoForge.EVENT_BUS.addListener(listener); int paidPractices = 0;
        try {
            for (var species : all) for (var hand : InteractionHand.values()) {
                level.setBlock(pos.above(), Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                level.setBlock(pos, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
                if (species.crop() != null && (species.crop().supportRequirement() == CropSupportRequirement.TRELLIS
                        || species.crop().supportRequirement() == CropSupportRequirement.LATTICE))
                    level.setBlock(pos.above(), BlockRegistry.TRELLIS_BLOCK.get().defaultBlockState(), 3);
                var original = level.getBlockState(pos);
                rig.player().getInventory().clearContent(); var seed = new ItemStack(species.seed());
                rig.player().setItemInHand(hand, seed);
                SkillManager.applyConfirmedValue(rig.player(), "farming", species.skill());
                SkillManager.applyConfirmedValue(rival, "farming", 100);
                rig.messages(); int before = sounds.get();
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                rig.player().gameMode.useItemOn(rig.player(), level, seed, hand, hit);
                h.assertTrue(seed.isEmpty(), "planting did not consume exactly one " + species.id() + " in " + hand);
                var status = FarmingPlotStatus.at(level, pos).orElseThrow();
                h.assertTrue(status.species().equals(species.id()) && status.stage() == FarmingPlotStatus.Stage.GERMINATING, "committed invisible crop not identified: " + species.id());
                // Repeat with another seed through stale state and the other hand, then another player.
                var again = new ItemStack(species.seed(), 2); rig.player().setItemInHand(hand, again);
                var other = hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
                rig.player().gameMode.useItemOn(rig.player(), level, again, hand, hit);
                rig.player().gameMode.useItemOn(rig.player(), level, rig.player().getItemInHand(other), other, hit);
                if (species.crop() != null && !species.id().equals("grapes")) FarmingBlock.tryPlantSeed(level, pos, original, rig.player(), again, false, "stale_test");
                else if (species.id().equals("grapes")) FarmingBlock.tryPlantGrapes(level, pos, original, rig.player(), again);
                else FlowerPlantingService.tryPlant(level, pos, original, rig.player(), again);
                var rivalSeed = new ItemStack(species.seed(), 2); rival.setItemInHand(InteractionHand.MAIN_HAND, rivalSeed);
                rival.gameMode.useItemOn(rival, level, rivalSeed, InteractionHand.MAIN_HAND, hit);
                h.assertTrue(again.getCount() == 2 && rivalSeed.getCount() == 2, "repeat or rival spent seed: " + species.id());
                var messages = rig.messages();
                h.assertTrue(messages.size() == 1, "success message count " + messages.size() + " for " + species.id());
                var translated = (TranslatableContents) messages.getFirst().getContents();
                h.assertTrue(translated.getArgs()[0] instanceof Component name
                        && name.getContents() instanceof TranslatableContents n && n.getKey().equals("crop.britannia_mod." + species.id()), "wrong planted species name");
                h.assertTrue(sounds.get() == before + 1, "plant sound repeated: " + species.id());
                if (species.skill() < 100) paidPractices++;
                h.assertTrue(practiceAttempts(rig.player()) == paidPractices, "plant practice count changed for " + species.id());
            }
        } finally { NeoForge.EVENT_BUS.unregister(listener); level.getServer().getPlayerList().remove(rig.player()); level.getServer().getPlayerList().remove(rival); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void rejectedTreePlacementStaleRootAndReentrantPlantingProduceNoSuccess(GameTestHelper h) {
        var level = h.getLevel(); var rig = join(level, "PlantingRefusal"); var crop = CropRegistry.byId("orange").orElseThrow();
        var pos = new BlockPos(h.absolutePos(P).getX(), level.getMaxBuildHeight() - 1, h.absolutePos(P).getZ());
        try {
            level.setBlock(pos, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
            var soil = (FarmingBlockEntity) level.getBlockEntity(pos); var state = level.getBlockState(pos);
            var seeds = new ItemStack(crop.seedItem().get(), 2); rig.player().setItemInHand(InteractionHand.MAIN_HAND, seeds);
            SkillManager.applyConfirmedValue(rig.player(), "farming", 100); rig.messages();
            h.assertTrue(!FarmingPlantingTransaction.plant(level, pos, state, soil, rig.player(), seeds, crop, "", FruitTreeRegistry.byIdOrDefault(crop.id())), "out-of-height tree committed");
            h.assertTrue(level.getBlockState(pos).equals(state) && !soil.hasCrop() && seeds.getCount() == 2 && rig.messages().isEmpty(), "failed placement acknowledged or charged");
            boolean nested = FarmingPlantingTransaction.locked(level, pos, () -> FarmingPlantingTransaction.plant(level, pos, state, soil, rig.player(), seeds, crop, "", null), true);
            h.assertTrue(!nested, "plant transaction reentered");
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); level.setBlock(pos, state, 3);
            h.assertTrue(!FarmingPlantingTransaction.plant(level, pos, state, soil, rig.player(), seeds, crop, "", null), "stale soil identity committed");
            h.assertTrue(rig.messages().isEmpty() && seeds.getCount() == 2, "refusal emitted success or consumed seed");
        } finally { level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3); level.getServer().getPlayerList().remove(rig.player()); }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void synchronizedPlotStatusTracksEmptyGrowthPartsRemovalAndReload(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(P);
        for (String id : List.of("carrot", "corn", "banana", "grapes", "tomato", "hops")) {
            for (int dy = 3; dy >= 0; dy--) level.setBlock(pos.above(dy), Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(pos, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
            var soil = (FarmingBlockEntity) level.getBlockEntity(pos); var crop = CropRegistry.byId(id).orElseThrow();
            h.assertTrue(!soil.hasSynchronizedPlotStatus(), "new BE pretends a packet arrived");
            h.assertTrue(FarmingPlotStatus.at(level, pos, true).orElseThrow().stage() == FarmingPlotStatus.Stage.LOADING, "client default empty shown before packet");
            h.assertTrue(FarmingPlotStatus.at(level, pos).orElseThrow().stage() == FarmingPlotStatus.Stage.EMPTY, "authoritative empty status");
            if (crop.supportRequirement() == CropSupportRequirement.TRELLIS || crop.supportRequirement() == CropSupportRequirement.LATTICE) level.setBlock(pos.above(), BlockRegistry.TRELLIS_BLOCK.get().defaultBlockState(), 3);
            for (int age : new int[]{0, 2, crop.maxGrowthAge()}) {
                soil.plantMigratedCrop(crop, "", age);
                var expected = age == 0 ? FarmingPlotStatus.Stage.GERMINATING : age == crop.maxGrowthAge() ? FarmingPlotStatus.Stage.READY : FarmingPlotStatus.Stage.GROWING;
                h.assertTrue(FarmingPlotStatus.at(level, pos).orElseThrow().stage() == expected, "wrong stage for " + id);
                var packet = soil.getUpdateTag(level.registryAccess());
                soil.handleUpdateTag(packet, level.registryAccess());
                h.assertTrue(FarmingPlotStatus.at(level, pos, true).orElseThrow().stage() == expected, "packet did not make current stage authoritative");
                h.assertTrue(!packet.contains("OwnerUUID"), "HUD packet leaked private owner");
                var replica = new FarmingBlockEntity(pos, level.getBlockState(pos));
                replica.handleUpdateTag(packet, level.registryAccess());
                h.assertTrue(replica.hasSynchronizedPlotStatus() && replica.getPlantedCropId().equals(id) && replica.getGrowthStage() == age, "packet/reconnect loses crop");
                var saved = soil.saveWithoutMetadata(level.registryAccess()); replica.loadWithComponents(saved, level.registryAccess());
                h.assertTrue(!replica.hasSynchronizedPlotStatus(), "disk data reused as received client packet");
                if (!level.getBlockState(pos.above()).isAir()) h.assertTrue(FarmingPlotStatus.at(level, pos.above()).orElseThrow().species().equals(id), "part did not resolve to " + id);
            }
            soil.clearCrop(); level.setBlock(pos, level.getBlockState(pos).setValue(FarmingBlock.HAS_SEEDS, false), 3);
            h.assertTrue(FarmingPlotStatus.at(level, pos).orElseThrow().stage() == FarmingPlotStatus.Stage.EMPTY, "cleared occupancy cached");
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
            h.assertTrue(FarmingPlotStatus.at(level, pos).isEmpty(), "removed plot still displayed");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wateringCanPredicateChangesOnActualRefillAndDispenseInEitherHand(GameTestHelper h) {
        var level = h.getLevel(); var pos = h.absolutePos(P); var player = ManagedResourceTestPlayers.survival(level, "CanStates");
        try {
            for (var hand : InteractionHand.values()) {
                var can = new ItemStack(ItemRegistry.WATERING_CAN.get()); player.setItemInHand(hand, can);
                WateringCanItem.setWaterCharges(can, 0); h.assertTrue(WateringCanItem.fullModelState(can) == 0, "empty can full");
                level.setBlock(pos, Blocks.WATER.defaultBlockState(), 3);
                var hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
                can.getItem().useOn(new UseOnContext(player, hand, hit));
                h.assertTrue(WateringCanItem.getWaterCharges(can) == 12 && WateringCanItem.fullModelState(can) == 1, "refill not full");
                level.setBlock(pos, BlockRegistry.FARMING_BLOCK.get().defaultBlockState(), 3);
                var soil = (FarmingBlockEntity) level.getBlockEntity(pos); soil.setHydration(0);
                can.getItem().useOn(new UseOnContext(player, hand, hit));
                h.assertTrue(WateringCanItem.getWaterCharges(can) == 11 && WateringCanItem.fullModelState(can) == 0 && soil.getHydration() == 1, "dispense did not update partial state");
                var restored = ItemStack.parse(level.registryAccess(), can.save(level.registryAccess())).orElseThrow();
                h.assertTrue(WateringCanItem.fullModelState(restored) == 0 && WateringCanItem.getWaterCharges(restored) == 11, "can save/reload lost predicate");
            }
        } finally { level.getServer().getPlayerList().remove(player); }
        h.succeed();
    }

    private static int practiceAttempts(ServerPlayer player) throws Exception {
        var field = FarmingSkill.class.getDeclaredField("DATA"); field.setAccessible(true);
        var data = ((Map<?, ?>) field.get(null)).get(player.getUUID()); if (data == null) return 0;
        var count = data.getClass().getDeclaredField("consecutiveActions"); count.setAccessible(true);
        return count.getInt(data) + 1;
    }

    private record Rig(ServerPlayer player, EmbeddedChannel channel) {
        List<Component> messages() {
            channel.flushOutbound(); var result = new ArrayList<Component>(); Object message;
            while ((message = channel.outboundMessages().poll()) != null) collect(message, result);
            return result;
        }
        private static void collect(Object packet, List<Component> into) {
            if (packet instanceof ClientboundBundlePacket bundle) for (var p : bundle.subPackets()) collect(p, into);
            else if (packet instanceof ClientboundSystemChatPacket chat && chat.overlay()
                    && chat.content().getContents() instanceof TranslatableContents text
                    && text.getKey().equals("message.britannia_mod.seed_planted")) into.add(chat.content());
        }
    }
    private static Rig join(ServerLevel level, String name) {
        var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), name), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND); var channel = new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie); player.setGameMode(GameType.SURVIVAL);
        return new Rig(player, channel);
    }
}
