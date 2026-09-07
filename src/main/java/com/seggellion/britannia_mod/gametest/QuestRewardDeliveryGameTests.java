package com.seggellion.britannia_mod.gametest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestJournalRefresh;
import com.seggellion.britannia_mod.quest.QuestRewardService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDelivery;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryClient;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryItem;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryLedger;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryLedgerEntry;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryLedgerStore;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryLocalState;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryParser;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryProtocol;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryReconciler;
import com.seggellion.britannia_mod.quest.delivery.QuestRewardDeliveryService;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;

/**
 * Rowan farming questline M3 (protocol section 1.8): the durable delivery workflow against a real
 * {@link ServerPlayer}, a real inventory, the real {@code SavedData} ledger on this server's
 * overworld storage, and the real vanilla player-file write. Rails is replaced by the
 * acknowledgement and pending-listing seams, so no test here touches the network.
 *
 * <p>Every delivery and every player here is a random identity: the gametest world persists
 * between runs, and a fixed uuid would meet its own earlier self in the ledger.
 *
 * <p><b>One batch per test.</b> The seams these tests install -- the acknowledger, the clock, the
 * pending fetcher, the journal fetcher -- are process-wide single slots, and the tests of one
 * batch are placed and ticked CONCURRENTLY. Sharing a batch therefore has one test's deliveries
 * recorded by another test's stand-in and one test's clock offset applied to another test's
 * backoff; batches, by contrast, run strictly one after another. This is the same rule the
 * repository learnt from its earlier process-wide fakes, applied per method because every method
 * here installs one.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestRewardDeliveryGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final Gson GSON = new Gson();
    private static final long QUEST_ID = 41L;
    private static final String QUEST_STATE_ID = "9001";
    private static final String TRANSITION_KEY = "1757200000:1201:choice:accept";
    private static final String SHOVEL = "britannia_mod:britannia_shovel";
    private static final String GOLD = "britannia_mod:gold_coin";

    private QuestRewardDeliveryGameTests() {
    }

    /** The same delivery from every direction it can arrive grants once and is acknowledged once. */
    @GameTest(template = TEMPLATE, batch = "delivery_theSameDeliveryFromEveryDirectionGrantsOnce")
    public static void theSameDeliveryFromEveryDirectionGrantsOnce(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        UUID uuid = UUID.randomUUID();
        QuestRewardDelivery delivery = delivery(uuid, SHOVEL, 1);
        try {
            // 1. The CHOOSE response itself (QuestProxyService path: parsed body plus raw JSON).
            applyTransition(player, transitionRoot(delivery, false), UUID.randomUUID().toString());
            check(count(player, SHOVEL) == 1, "the delivery was not granted: " + counts(player));
            check(state(player, uuid) == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "expected acknowledged, got " + state(player, uuid));
            check(PlayerDataStore.hasAppliedDelivery(player, uuid), "the player marker must name the delivery");
            check(acks.requests.size() == 1 && acks.requests.get(0).outcome() == QuestRewardDeliveryProtocol.Outcome.APPLIED,
                "expected one applied acknowledgement, got " + acks.requests);
            check(acks.requests.get(0).playerUuid().equals(player.getUUID()), "the acknowledgement names the player by uuid");

            // 2. A retried CHOOSE: Rails replays the same delivery under a fresh request uuid.
            applyTransition(player, transitionRoot(delivery, true), UUID.randomUUID().toString());
            check(count(player, SHOVEL) == 1, "a replayed response granted a second time: " + counts(player));

            // 3. A trigger result: the parsed response only, no raw JSON (QuestObjectiveWatcher path).
            QuestRewardService.apply(player, GSON.fromJson(transitionRoot(delivery, false), QuestModels.QuestResponse.class));
            check(count(player, SHOVEL) == 1, "the trigger path granted a second time: " + counts(player));

            // 4. A second Rowan / the pending listing / the bootstrap: the same identity again.
            check(QuestRewardDeliveryService.apply(player, delivery, QuestRewardDeliveryService.Source.PENDING_LISTING)
                == QuestRewardDeliveryService.ApplyOutcome.ALREADY_APPLIED, "the listing must be refused as already applied");
            QuestRewardDeliveryReconciler.onLogin(player, List.of(delivery));
            check(count(player, SHOVEL) == 1, "the bootstrap re-listing granted a second time: " + counts(player));
            check(acks.requests.size() == 1, "an acknowledged delivery must not be acknowledged again: " + acks.requests);
        } finally {
            cleanup(helper, player, uuid);
        }
        helper.succeed();
    }

    /** A fresh delivery through the trigger path (parsed response only) still goes through the ledger. */
    @GameTest(template = TEMPLATE, batch = "delivery_aTriggerResultAppliesItsDeliveryThroughTheLedger")
    public static void aTriggerResultAppliesItsDeliveryThroughTheLedger(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        UUID uuid = UUID.randomUUID();
        QuestRewardDelivery delivery = delivery(uuid, GOLD, 5);
        try {
            QuestModels.QuestResponse response = GSON.fromJson(transitionRoot(delivery, false), QuestModels.QuestResponse.class);
            check(response.reward_delivery != null, "precondition: Gson must carry reward_delivery on the parsed response");
            QuestRewardService.apply(player, response);
            check(count(player, GOLD) == 5, "the trigger path did not grant the delivery: " + counts(player));
            check(state(player, uuid) == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "expected acknowledged, got " + state(player, uuid));
            check(acks.requests.size() == 1, "expected one acknowledgement, got " + acks.requests);

            QuestRewardService.apply(player, response);
            check(count(player, GOLD) == 5, "the same trigger result applied twice granted twice");
        } finally {
            cleanup(helper, player, uuid);
        }
        helper.succeed();
    }

    /** Full inventory: queued, acknowledged as queued, nothing on the ground; delivered once space frees. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "delivery_aFullInventoryQueuesWithoutAGroundDropAndDeliversWhenSpaceFrees")
    public static void aFullInventoryQueuesWithoutAGroundDropAndDeliversWhenSpaceFrees(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        UUID uuid = UUID.randomUUID();
        QuestRewardDelivery delivery = new QuestRewardDelivery(uuid, QUEST_ID, QUEST_STATE_ID, TRANSITION_KEY,
            List.of(new QuestRewardDeliveryItem(SHOVEL, 1, false), new QuestRewardDeliveryItem(GOLD, 5, false)),
            "pending", "2026-09-06T21:10:00Z");
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.items.size(); slot++) inventory.items.set(slot, new ItemStack(Items.COBBLESTONE, 64));

        try {
            applyTransition(player, transitionRoot(delivery, false), UUID.randomUUID().toString());

            check(count(player, SHOVEL) == 0 && count(player, GOLD) == 0, "nothing may be inserted into a full pack: " + counts(player));
            check(count(player, "minecraft:cobblestone") == 64 * 36, "the pack must be untouched: " + counts(player));
            check(droppedNearby(helper, player) == 0, "a mandatory reward must never be dropped on the ground");
            QuestRewardDeliveryLedgerEntry queued = entry(player, uuid).orElseThrow(() -> new GameTestAssertException("no ledger row"));
            check(queued.localState() == QuestRewardDeliveryLocalState.QUEUED, "expected queued, got " + queued.localState());
            check(queued.items().equals(delivery.items()), "the queued row must keep the items");
            check("queued".equals(queued.acknowledgedOutcome()), "Rails must have been told queued, got '" + queued.acknowledgedOutcome() + "'");
            check(acks.requests.size() == 1 && acks.requests.get(0).outcome() == QuestRewardDeliveryProtocol.Outcome.QUEUED,
                "expected one queued acknowledgement, got " + acks.requests);
            check(!PlayerDataStore.hasAppliedDelivery(player, uuid), "no marker before the items exist");

            // The player makes room: the periodic pass notices the inventory change and delivers.
            inventory.items.set(0, ItemStack.EMPTY);
            inventory.items.set(1, ItemStack.EMPTY);
        } catch (RuntimeException error) {
            cleanup(helper, player, uuid);
            throw error;
        }

        helper.succeedWhen(() -> {
            try {
                check(count(player, SHOVEL) == 1 && count(player, GOLD) == 5, "waiting for the queued reward: " + counts(player));
                check(droppedNearby(helper, player) == 0, "delivery must never drop on the ground");
                check(state(player, uuid) == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "expected acknowledged, got " + state(player, uuid));
                check(PlayerDataStore.hasAppliedDelivery(player, uuid), "the marker must name the delivery once inserted");
                check(acks.requests.size() == 2 && acks.requests.get(1).outcome() == QuestRewardDeliveryProtocol.Outcome.APPLIED,
                    "expected the applied upgrade after queued, got " + acks.requests);
            } catch (GameTestAssertException pending) {
                throw pending;
            }
            cleanup(helper, player, uuid);
        });
    }

    /** Compatibility: a response with no reward_delivery takes the immediate path, with no ledger row. */
    @GameTest(template = TEMPLATE, batch = "delivery_aLegacyResponseStillGrantsImmediatelyWithoutALedgerRow")
    public static void aLegacyResponseStillGrantsImmediatelyWithoutALedgerRow(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        try {
            JsonObject root = GSON.fromJson("{\"success\":true,\"quest_id\":" + QUEST_ID + ",\"quest_state_id\":\"" + QUEST_STATE_ID + "\","
                + "\"node\":{\"id\":1201,\"title\":\"Dung Duty\",\"text\":\"Take this.\",\"type\":\"dialogue\",\"metadata\":{}},"
                + "\"choices\":[],\"granted_items\":[{\"id\":\"britannia_shovel\",\"count\":1},{\"id\":\"gold_coin\",\"count\":2}],"
                + "\"client_actions\":[],\"completed\":false,\"reward_delivery\":null}", JsonObject.class);
            applyTransition(player, root, UUID.randomUUID().toString());

            check(count(player, SHOVEL) == 1 && count(player, GOLD) == 2, "the legacy grant was not applied: " + counts(player));
            check(QuestRewardDeliveryLedger.store(player.server).entriesFor(player.getUUID()).isEmpty(), "legacy grants have no ledger row");
            check(acks.requests.isEmpty(), "legacy grants are never acknowledged");
            check(PlayerDataStore.appliedDeliveries(player).isEmpty(), "legacy grants leave no marker");
        } finally {
            cleanup(helper, player);
        }
        helper.succeed();
    }

    /** A malformed delivery grants nothing at all -- not even the legacy granted_items beside it. */
    @GameTest(template = TEMPLATE, batch = "delivery_aMalformedDeliveryMutatesNothing")
    public static void aMalformedDeliveryMutatesNothing(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        UUID uuid = UUID.randomUUID();
        try {
            JsonObject root = transitionRoot(delivery(uuid, SHOVEL, 1), false);
            root.getAsJsonObject("reward_delivery").getAsJsonArray("items").get(0).getAsJsonObject().addProperty("count", 0);
            applyTransition(player, root, UUID.randomUUID().toString());
            check(counts(player).isEmpty(), "a malformed delivery must mutate nothing, got " + counts(player));

            JsonObject badUuid = transitionRoot(delivery(uuid, SHOVEL, 1), false);
            badUuid.getAsJsonObject("reward_delivery").addProperty("delivery_uuid", "nope");
            applyTransition(player, badUuid, UUID.randomUUID().toString());

            JsonObject wrongVersion = transitionRoot(delivery(uuid, SHOVEL, 1), false);
            wrongVersion.getAsJsonObject("reward_delivery").addProperty("protocol_version", 9);
            applyTransition(player, wrongVersion, UUID.randomUUID().toString());

            JsonObject noItems = transitionRoot(delivery(uuid, SHOVEL, 1), false);
            noItems.getAsJsonObject("reward_delivery").add("items", new JsonArray());
            applyTransition(player, noItems, UUID.randomUUID().toString());

            check(counts(player).isEmpty(), "a malformed delivery must mutate nothing, got " + counts(player));
            check(droppedNearby(helper, player) == 0, "nothing may be dropped");
            check(entry(player, uuid).isEmpty(), "a malformed delivery must not reach the ledger");
            check(acks.requests.isEmpty(), "a malformed delivery is never acknowledged");
            check(PlayerDataStore.appliedDeliveries(player).isEmpty(), "no marker for a refused delivery");

            // Out of scope: a delivery the ledger holds for another player is refused for this one.
            ServerPlayer other = helper.makeMockServerPlayerInLevel();
            try {
                other.getInventory().clearContent();
                QuestRewardDelivery theirs = delivery(uuid, SHOVEL, 1);
                check(QuestRewardDeliveryService.apply(other, theirs, QuestRewardDeliveryService.Source.TRANSITION)
                    == QuestRewardDeliveryService.ApplyOutcome.APPLIED, "precondition: the other player's grant");
                check(QuestRewardDeliveryService.apply(player, theirs, QuestRewardDeliveryService.Source.PENDING_LISTING)
                    == QuestRewardDeliveryService.ApplyOutcome.REJECTED, "another player's delivery must be refused");
                check(counts(player).isEmpty(), "another player's delivery must not touch this pack: " + counts(player));
                check(count(other, SHOVEL) == 1, "the owner keeps exactly one");
            } finally {
                helper.getLevel().getServer().getPlayerList().remove(other);
            }
        } finally {
            cleanup(helper, player, uuid);
        }
        helper.succeed();
    }

    /** Crash after step 2 (or 3, before the player save): restart re-applies exactly once. */
    @GameTest(template = TEMPLATE, batch = "delivery_aRestartWithoutThePlayerMarkerGrantsExactlyOnce")
    public static void aRestartWithoutThePlayerMarkerGrantsExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        MinecraftServer server = player.server;
        UUID uuid = UUID.randomUUID();
        QuestRewardDelivery delivery = delivery(uuid, SHOVEL, 1);
        try {
            QuestRewardDeliveryLedgerEntry row = QuestRewardDeliveryLedgerEntry.pendingLocal(delivery, player.getUUID(), System.currentTimeMillis());
            check(QuestRewardDeliveryLedger.record(server, row) == QuestRewardDeliveryLedgerStore.RecordOutcome.CREATED, "precondition: pending_local recorded");
            simulateRestart(server);
            check(state(player, uuid) == QuestRewardDeliveryLocalState.PENDING_LOCAL, "the reloaded ledger must still say pending_local");
            check(!PlayerDataStore.hasAppliedDelivery(player, uuid), "precondition: no marker");

            QuestRewardDeliveryReconciler.reconcileNow(player);
            check(count(player, SHOVEL) == 1, "the lost insertion must be re-applied once: " + counts(player));
            check(state(player, uuid) == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "expected acknowledged, got " + state(player, uuid));
            check(PlayerDataStore.hasAppliedDelivery(player, uuid), "the marker must now name the delivery");
            check(acks.requests.size() == 1, "expected one acknowledgement, got " + acks.requests);

            QuestRewardDeliveryReconciler.reconcileNow(player);
            simulateRestart(server);
            QuestRewardDeliveryReconciler.onLogin(player, List.of(delivery));
            check(count(player, SHOVEL) == 1, "a second restart or relog granted again: " + counts(player));
            check(acks.requests.size() == 1, "an acknowledged row is not acknowledged again: " + acks.requests);
        } finally {
            cleanup(helper, player, uuid);
        }
        helper.succeed();
    }

    /** Crash after step 4, before step 5: the marker proves the items landed; repair, do not insert. */
    @GameTest(template = TEMPLATE, batch = "delivery_aRestartWithThePlayerMarkerRepairsTheLedgerWithoutInserting")
    public static void aRestartWithThePlayerMarkerRepairsTheLedgerWithoutInserting(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        MinecraftServer server = player.server;
        UUID uuid = UUID.randomUUID();
        QuestRewardDelivery delivery = delivery(uuid, SHOVEL, 1);
        try {
            // What the player file holds after step 4: the item and the marker, persisted together.
            player.getInventory().items.set(0, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(SHOVEL))));
            PlayerDataStore.markDeliveryApplied(player, uuid);
            // What the ledger holds: the flush of step 5 never happened.
            QuestRewardDeliveryLedger.record(server, QuestRewardDeliveryLedgerEntry.pendingLocal(delivery, player.getUUID(), System.currentTimeMillis()));
            simulateRestart(server);

            QuestRewardDeliveryReconciler.reconcileNow(player);
            check(count(player, SHOVEL) == 1, "the marker must prevent a second insertion: " + counts(player));
            QuestRewardDeliveryLedgerEntry repaired = entry(player, uuid).orElseThrow(() -> new GameTestAssertException("row lost"));
            check(repaired.localState() == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "expected repaired and acknowledged, got " + repaired.localState());
            check(repaired.appliedAtEpochMillis() > 0L, "the repair must stamp applied_at");
            check(acks.requests.size() == 1 && acks.requests.get(0).outcome() == QuestRewardDeliveryProtocol.Outcome.APPLIED,
                "the repaired row is acknowledged as applied, got " + acks.requests);

            // The ledger lost the row entirely (rolled back) but the player file kept the marker.
            UUID orphan = UUID.randomUUID();
            player.getInventory().items.set(1, new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(GOLD)), 3));
            PlayerDataStore.markDeliveryApplied(player, orphan);
            QuestRewardDeliveryReconciler.onLogin(player, List.of(delivery(orphan, GOLD, 3)));
            check(count(player, GOLD) == 3, "a marker without a ledger row must not insert again: " + counts(player));
            check(state(player, orphan) == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "the orphan is recorded as applied and acknowledged");
            check(acks.requests.size() == 2, "expected the orphan's acknowledgement, got " + acks.requests);
            QuestRewardDeliveryLedger.store(server).removeForTesting(orphan);
            QuestRewardDeliveryService.forgetScheduleForTesting(orphan);
        } finally {
            cleanup(helper, player, uuid);
        }
        helper.succeed();
    }

    /** Response timeout / lost acknowledgement: the grant stays single, the acknowledgement is retried on the schedule. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "delivery_anAcknowledgementFailureIsRetriedOnTheScheduleWhileTheGrantStaysSingle")
    public static void anAcknowledgementFailureIsRetriedOnTheScheduleWhileTheGrantStaysSingle(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        UUID uuid = UUID.randomUUID();
        QuestRewardDelivery delivery = delivery(uuid, SHOVEL, 1);
        long[] clockOffset = {0L};
        QuestRewardDeliveryService.installClock(() -> System.currentTimeMillis() + clockOffset[0]);
        try {
            acks.answer = request -> new QuestRewardDeliveryClient.Failure("overall_timeout");
            applyTransition(player, transitionRoot(delivery, false), UUID.randomUUID().toString());
            check(count(player, SHOVEL) == 1, "the grant must not wait for Rails: " + counts(player));
            check(state(player, uuid) == QuestRewardDeliveryLocalState.APPLIED, "expected applied (unacknowledged), got " + state(player, uuid));
            check(acks.requests.size() == 1, "expected one failed attempt, got " + acks.requests);

            // Relog: the bootstrap lists it again (Rails still pending) -- attempt at once, grant nothing.
            QuestRewardDeliveryReconciler.onLogin(player, List.of(delivery));
            check(count(player, SHOVEL) == 1, "a relog granted again: " + counts(player));
            check(acks.requests.size() == 2, "login retries the acknowledgement immediately, got " + acks.requests);

            // A lost response: Rails had recorded it; the retry answers duplicate:true.
            acks.answer = request -> accepted(request, true);
            QuestRewardDeliveryReconciler.reconcileNow(player);
            check(acks.requests.size() == 2, "the periodic schedule is not due yet, got " + acks.requests);
        } catch (RuntimeException error) {
            QuestRewardDeliveryService.resetClock();
            cleanup(helper, player, uuid);
            throw error;
        }

        // Two failures: 60 s then 120 s. Jump past both and let the periodic pass retry.
        clockOffset[0] = 200_000L;
        helper.succeedWhen(() -> {
            try {
                check(acks.requests.size() == 3, "waiting for the scheduled retry, got " + acks.requests.size());
                check(state(player, uuid) == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "expected acknowledged, got " + state(player, uuid));
                check(count(player, SHOVEL) == 1, "the grant must stay single: " + counts(player));
            } finally {
                if (acks.requests.size() == 3) {
                    QuestRewardDeliveryService.resetClock();
                    cleanup(helper, player, uuid);
                }
            }
        });
    }

    /** A terminal Rails answer closes the row with its reason and is never retried. */
    @GameTest(template = TEMPLATE, batch = "delivery_aTerminalRejectionClosesTheRowAndStopsRetrying")
    public static void aTerminalRejectionClosesTheRowAndStopsRetrying(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Acknowledgements acks = install();
        UUID uuid = UUID.randomUUID();
        QuestRewardDelivery delivery = delivery(uuid, SHOVEL, 1);
        long[] clockOffset = {0L};
        QuestRewardDeliveryService.installClock(() -> System.currentTimeMillis() + clockOffset[0]);
        try {
            acks.answer = request -> new QuestRewardDeliveryClient.TerminalRejection("conflicting_delivery_result");
            applyTransition(player, transitionRoot(delivery, false), UUID.randomUUID().toString());
            QuestRewardDeliveryLedgerEntry row = entry(player, uuid).orElseThrow(() -> new GameTestAssertException("no row"));
            check(row.localState() == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "a terminal answer closes the row, got " + row.localState());
            check("conflicting_delivery_result".equals(row.acknowledgementError()), "the row must carry the reason, got '" + row.acknowledgementError() + "'");
            check(count(player, SHOVEL) == 1, "the items already inserted stay: " + counts(player));

            clockOffset[0] = 1_000_000L;
            QuestRewardDeliveryReconciler.reconcileNow(player);
            QuestRewardDeliveryReconciler.onLogin(player, List.of(delivery));
            check(acks.requests.size() == 1, "a closed row is never acknowledged again, got " + acks.requests);
            check(count(player, SHOVEL) == 1, "a closed row never grants again: " + counts(player));

            // Rails does not know a queued delivery (shard reset): the owed items are dropped, never resurrected.
            UUID unknown = UUID.randomUUID();
            Inventory inventory = player.getInventory();
            for (int slot = 0; slot < inventory.items.size(); slot++) inventory.items.set(slot, new ItemStack(Items.COBBLESTONE, 64));
            acks.answer = request -> new QuestRewardDeliveryClient.TerminalRejection("delivery_not_found");
            check(QuestRewardDeliveryService.apply(player, delivery(unknown, GOLD, 4), QuestRewardDeliveryService.Source.BOOTSTRAP)
                == QuestRewardDeliveryService.ApplyOutcome.QUEUED, "precondition: queued against a full pack");
            QuestRewardDeliveryLedgerEntry closed = entry(player, unknown).orElseThrow(() -> new GameTestAssertException("no row"));
            check(closed.localState() == QuestRewardDeliveryLocalState.ACKNOWLEDGED && "delivery_not_found".equals(closed.acknowledgementError()),
                "a queued row Rails does not hold is closed, got " + closed);
            inventory.clearContent();
            QuestRewardDeliveryReconciler.reconcileNow(player);
            check(count(player, GOLD) == 0, "a delivery Rails destroyed must not be resurrected locally: " + counts(player));
            QuestRewardDeliveryLedger.store(player.server).removeForTesting(unknown);
            QuestRewardDeliveryService.forgetScheduleForTesting(unknown);
        } finally {
            QuestRewardDeliveryService.resetClock();
            cleanup(helper, player, uuid);
        }
        helper.succeed();
    }

    /** The marker travels through the vanilla player-file write, not just the in-memory player. */
    @GameTest(template = TEMPLATE, batch = "delivery_theMarkerReachesTheVanillaPlayerFile")
    public static void theMarkerReachesTheVanillaPlayerFile(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        install();
        UUID uuid = UUID.randomUUID();
        try {
            applyTransition(player, transitionRoot(delivery(uuid, SHOVEL, 1), false), UUID.randomUUID().toString());
            check(count(player, SHOVEL) == 1, "precondition: granted");

            Path file = player.server.getWorldPath(LevelResource.PLAYER_DATA_DIR).resolve(player.getStringUUID() + ".dat");
            check(Files.isRegularFile(file), "the player file must exist after the forced save: " + file);
            CompoundTag saved;
            try {
                saved = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap());
            } catch (java.io.IOException unreadable) {
                throw new GameTestAssertException("player file unreadable: " + unreadable);
            }
            CompoundTag mod = saved.getCompound("NeoForgeData").getCompound("britannia_player");
            boolean found = false;
            for (Tag tag : mod.getList(PlayerDataStore.APPLIED_DELIVERY_UUIDS, Tag.TAG_STRING)) {
                if (uuid.toString().equals(tag.getAsString())) found = true;
            }
            check(found, "the marker must be in the player file, keys=" + mod.getAllKeys());
            boolean shovelSaved = false;
            for (Tag tag : saved.getList("Inventory", Tag.TAG_COMPOUND)) {
                if (SHOVEL.equals(((CompoundTag) tag).getString("id"))) shovelSaved = true;
            }
            check(shovelSaved, "the inserted item must be in the same player file as the marker");
        } finally {
            cleanup(helper, player, uuid);
        }
        helper.succeed();
    }

    /** A journal refresh fetches Rails' pending listing on demand and applies it. */
    @GameTest(template = TEMPLATE, timeoutTicks = 200, batch = "delivery_aJournalRefreshFetchesThePendingListingAndAppliesIt")
    public static void aJournalRefreshFetchesThePendingListingAndAppliesIt(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        ServerQuestTable.forget(player.getUUID());
        QuestJournalRefresh.forget(player.getUUID());
        Acknowledgements acks = install();
        UUID uuid = UUID.randomUUID();
        QuestRewardDelivery delivery = delivery(uuid, SHOVEL, 1);
        AtomicBoolean refreshed = new AtomicBoolean();
        QuestJournalRefresh.installFetcher((server, target) -> Optional.of(List.of(
            new ClientQuestEntry(QUEST_STATE_ID, Long.toString(QUEST_ID), "rowan_farming_1", "Rowan", "Dung Duty", "", "", "accepted"))));
        QuestRewardDeliveryReconciler.installPendingFetcher((server, playerUuid) -> CompletableFuture.completedFuture(
            new QuestRewardDeliveryClient.PendingFetched(new QuestRewardDeliveryParser.PendingListing(playerUuid, List.of(delivery)))));
        try {
            QuestRewardDeliveryReconciler.clearPendingFetchIntervalForTesting(player.server, player.getUUID());
            QuestJournalRefresh.refresh(player, () -> refreshed.set(true));
        } catch (RuntimeException error) {
            QuestJournalRefresh.resetFetcher();
            cleanup(helper, player, uuid);
            throw error;
        }
        helper.succeedWhen(() -> {
            try {
                check(refreshed.get(), "waiting for the journal refresh");
                check(count(player, SHOVEL) == 1, "the listed delivery was not applied: " + counts(player));
                check(state(player, uuid) == QuestRewardDeliveryLocalState.ACKNOWLEDGED, "expected acknowledged, got " + state(player, uuid));
                check(acks.requests.size() == 1, "expected one acknowledgement, got " + acks.requests);
            } finally {
                if (count(player, SHOVEL) == 1 && acks.requests.size() == 1) {
                    QuestJournalRefresh.resetFetcher();
                    cleanup(helper, player, uuid);
                }
            }
        });
    }

    // --- fixtures ---------------------------------------------------------------------------------

    private static QuestRewardDelivery delivery(UUID uuid, String itemId, int count) {
        return new QuestRewardDelivery(uuid, QUEST_ID, QUEST_STATE_ID, TRANSITION_KEY,
            List.of(new QuestRewardDeliveryItem(itemId, count, false)), "pending", "2026-09-06T21:10:00Z");
    }

    /** The transition response in the shape of transition_response_with_delivery.json. */
    private static JsonObject transitionRoot(QuestRewardDelivery delivery, boolean replayed) {
        JsonObject root = delivery.asTransitionRoot();
        root.addProperty("success", true);
        root.addProperty("quest_state_id", delivery.questStateId());
        root.addProperty("quest_id", delivery.questId());
        JsonArray granted = new JsonArray();
        for (QuestRewardDeliveryItem item : delivery.items()) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", item.id());
            entry.addProperty("count", item.count());
            granted.add(entry);
        }
        root.add("granted_items", granted);
        root.add("client_actions", new JsonArray());
        JsonObject node = new JsonObject();
        node.addProperty("id", 1202);
        node.addProperty("title", "Dung Duty");
        node.addProperty("text", "Take this shovel.");
        node.addProperty("type", "dialogue");
        node.add("metadata", new JsonObject());
        root.add("node", node);
        root.add("choices", new JsonArray());
        root.addProperty("completed", false);
        if (replayed) root.addProperty("replayed", true);
        return root;
    }

    private static void applyTransition(ServerPlayer player, JsonObject root, String requestUuid) {
        QuestModels.QuestResponse response = GSON.fromJson(root, QuestModels.QuestResponse.class);
        QuestRewardService.apply(player, response, root, requestUuid);
    }

    private static ServerPlayer prepare(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        ServerQuestTable.replaceFromBootstrap(player, List.of(
            new ClientQuestEntry(QUEST_STATE_ID, Long.toString(QUEST_ID), "rowan_farming_1", "Rowan", "Dung Duty", "", "", "accepted")));
        return player;
    }

    private static Acknowledgements install() {
        Acknowledgements acks = new Acknowledgements();
        QuestRewardDeliveryService.installAcknowledger(acks);
        QuestRewardDeliveryReconciler.resetPendingFetcher();
        QuestRewardDeliveryService.resetClock();
        QuestRewardDeliveryService.resetPlayerSaver();
        return acks;
    }

    private static void cleanup(GameTestHelper helper, ServerPlayer player, UUID... uuids) {
        QuestRewardDeliveryService.resetAcknowledger();
        QuestRewardDeliveryService.resetClock();
        QuestRewardDeliveryService.resetPlayerSaver();
        QuestRewardDeliveryReconciler.resetPendingFetcher();
        MinecraftServer server = helper.getLevel().getServer();
        for (UUID uuid : uuids) {
            QuestRewardDeliveryLedger.store(server).removeForTesting(uuid);
            QuestRewardDeliveryService.forgetScheduleForTesting(uuid);
        }
        server.getPlayerList().remove(player);
    }

    /** Serialise the live ledger, reload it from those bytes, and make the reload the live store. */
    private static void simulateRestart(MinecraftServer server) {
        QuestRewardDeliveryLedgerStore live = QuestRewardDeliveryLedgerStore.get(server);
        CompoundTag bytes = live.save(new CompoundTag(), server.registryAccess());
        QuestRewardDeliveryLedgerStore reloaded = QuestRewardDeliveryLedgerStore.load(bytes, server.registryAccess());
        check(!reloaded.isReadOnlyFutureSchema(), "the reloaded ledger must be readable");
        server.overworld().getDataStorage().set(QuestRewardDeliveryLedgerStore.DATA_NAME, reloaded);
        QuestRewardDeliveryService.clear(server);
        QuestRewardDeliveryReconciler.clear(server);
    }

    private static Optional<QuestRewardDeliveryLedgerEntry> entry(ServerPlayer player, UUID uuid) {
        return QuestRewardDeliveryLedger.find(player.server, uuid);
    }

    private static QuestRewardDeliveryLocalState state(ServerPlayer player, UUID uuid) {
        return entry(player, uuid).map(QuestRewardDeliveryLedgerEntry::localState).orElse(null);
    }

    private static QuestRewardDeliveryClient.AcknowledgeResult accepted(QuestRewardDeliveryProtocol.AcknowledgementRequest request,
                                                                       boolean duplicate) {
        return new QuestRewardDeliveryClient.Acknowledged(new QuestRewardDeliveryProtocol.AcknowledgementResponse(
            1, request.deliveryUuid(), "acknowledged", request.outcome().wireName(), duplicate));
    }

    /** A recording acknowledger: every request captured, the answer scripted per test. */
    private static final class Acknowledgements implements QuestRewardDeliveryService.Acknowledger {
        final List<QuestRewardDeliveryProtocol.AcknowledgementRequest> requests = new CopyOnWriteArrayList<>();
        volatile Function<QuestRewardDeliveryProtocol.AcknowledgementRequest, QuestRewardDeliveryClient.AcknowledgeResult> answer =
            request -> accepted(request, false);

        @Override
        public CompletableFuture<QuestRewardDeliveryClient.AcknowledgeResult> acknowledge(
                MinecraftServer server, QuestRewardDeliveryProtocol.AcknowledgementRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(answer.apply(request));
        }
    }

    // --- inventory helpers ------------------------------------------------------------------------

    private static Map<String, Integer> counts(ServerPlayer player) {
        Map<String, Integer> counts = new TreeMap<>();
        for (ItemStack stack : carried(player)) {
            if (stack.isEmpty()) continue;
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static int count(ServerPlayer player, String itemId) {
        return counts(player).getOrDefault(itemId, 0);
    }

    private static List<ItemStack> carried(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> all = new ArrayList<>(inventory.items);
        all.addAll(inventory.armor);
        all.addAll(inventory.offhand);
        return all;
    }

    private static int droppedNearby(GameTestHelper helper, ServerPlayer player) {
        int dropped = 0;
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(player.blockPosition()).inflate(8.0D))) {
            String id = BuiltInRegistries.ITEM.getKey(item.getItem().getItem()).toString();
            if (SHOVEL.equals(id) || GOLD.equals(id)) dropped++;
        }
        return dropped;
    }

    /**
     * Throws {@link GameTestAssertException}, never anything else: inside a {@code succeedWhen}
     * callback only that type is swallowed and retried; anything else escapes into the server
     * tick loop and crashes the whole GameTest server.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
