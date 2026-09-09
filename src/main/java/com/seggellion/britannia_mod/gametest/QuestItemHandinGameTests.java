package com.seggellion.britannia_mod.gametest;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.transfer.BankTransferPlayerDurability;
import com.seggellion.britannia_mod.event.PlayerDataCloneHandler;
import com.seggellion.britannia_mod.player.PlayerDataStore;
import com.seggellion.britannia_mod.quest.QuestProxyService;
import com.seggellion.britannia_mod.quest.handin.QuestHandinLedger;
import com.seggellion.britannia_mod.quest.handin.QuestHandinLedgerEntry;
import com.seggellion.britannia_mod.quest.handin.QuestHandinLedgerStore;
import com.seggellion.britannia_mod.quest.handin.QuestHandinLocalState;
import com.seggellion.britannia_mod.quest.handin.QuestHandinRemoval;
import com.seggellion.britannia_mod.quest.handin.QuestItemHandinClient;
import com.seggellion.britannia_mod.quest.handin.QuestItemHandinProtocol;
import com.seggellion.britannia_mod.quest.handin.QuestItemHandinReconciler;
import com.seggellion.britannia_mod.quest.handin.QuestItemHandinService;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

/**
 * Strict item hand-ins (protocol section 1.5) against a real {@link ServerPlayer}, a real
 * inventory, the real {@code SavedData} ledger on this server's overworld storage, the real player
 * persistent data and the real vanilla player-file write. Rails is replaced by the confirmer seam,
 * so nothing here touches the network.
 *
 * <p>Every transaction and every player is a random identity: the gametest world persists between
 * runs, and a fixed uuid would meet its own earlier self in the ledger.
 *
 * <p><b>One batch per test.</b> The confirmer is a process-wide single slot and the tests of one
 * batch are placed and ticked CONCURRENTLY, so sharing a batch has one test's confirmations
 * answered by another test's script. Batches run strictly one after another. This is the rule the
 * repository learnt from its earlier process-wide fakes.
 *
 * <p>Every assertion goes through {@link #check}, which throws {@link GameTestAssertException} and
 * nothing else: any other exception escaping a sequence callback kills the whole GameTest server
 * rather than failing one test.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestItemHandinGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final Gson GSON = new Gson();

    private static final String DUNG = "britannia_mod:dung";
    private static final String DIRT = "britannia_mod:dirt";
    private static final String SHOVEL = "britannia_mod:britannia_shovel";
    private static final String EMPTY_BOWL = "britannia_mod:empty_bowl";
    private static final String CARROTS = "britannia_mod:carrots";
    private static final String CARROT_SEEDS = "britannia_mod:carrot_seeds";
    private static final String BUCKET = "minecraft:bucket";
    private static final String WATER_BUCKET = "minecraft:water_bucket";

    private QuestItemHandinGameTests() {}

    // --- the rule -------------------------------------------------------------------------

    /** Exactly the dung, and the shovel that granted it stays. */
    @GameTest(template = TEMPLATE, batch = "handin_takesExactlyTheDungAndKeepsTheShovel")
    public static void takesExactlyTheDungAndKeepsTheShovel(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 3);
            give(player, SHOVEL, 1);
            give(player, EMPTY_BOWL, 2);
            give(player, BUCKET, 1);

            begin(player, demand(handin, literal(DUNG, 1)));

            check(count(player, DUNG) == 2, "exactly one dung comes off the stack: " + counts(player));
            check(count(player, SHOVEL) == 1, "the shovel the quest granted must stay: " + counts(player));
            check(count(player, EMPTY_BOWL) == 2, "the bowls must stay: " + counts(player));
            check(count(player, BUCKET) == 1, "the bucket must stay: " + counts(player));
            check(rails.requests.size() == 1, "one confirmation, got " + rails.requests.size());
            QuestItemHandinProtocol.ConfirmationRequest sent = rails.requests.get(0);
            check(sent.removed(), "the confirmation must report the removal");
            check(sent.removedItems().size() == 1, "one proof entry per requirement");
            check(sent.removedItems().get(0).itemId().equals(DUNG), "the proof names what was taken");
            check(sent.removedItems().get(0).count() == 1, "and how much");
            check(sent.removedItems().get(0).requirementIndex() == 0, "joined to its requirement");
            check(state(player, handin) == QuestHandinLocalState.CONSUMED,
                    "expected consumed, got " + state(player, handin));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** The dirt goes; the shovel, the bowls and the bucket the earlier stages granted do not. */
    @GameTest(template = TEMPLATE, batch = "handin_takesTheDirtAndKeepsTheKit")
    public static void takesTheDirtAndKeepsTheKit(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DIRT, 1);
            give(player, SHOVEL, 1);
            give(player, EMPTY_BOWL, 2);
            give(player, BUCKET, 1);

            begin(player, demand(handin, literal(DIRT, 1)));

            check(count(player, DIRT) == 0, "the dirt is handed over: " + counts(player));
            check(count(player, SHOVEL) == 1 && count(player, EMPTY_BOWL) == 2 && count(player, BUCKET) == 1,
                    "nothing else may be taken: " + counts(player));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A filled bucket is handed over whole -- no empty one comes back, because nothing is returned. */
    @GameTest(template = TEMPLATE, batch = "handin_takesTheFilledBucketAndReturnsNothing")
    public static void takesTheFilledBucketAndReturnsNothing(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, WATER_BUCKET, 1);

            begin(player, demand(handin, literal(WATER_BUCKET, 1)));

            check(count(player, WATER_BUCKET) == 0, "the water bucket is handed over: " + counts(player));
            check(count(player, BUCKET) == 0,
                    "an empty bucket must NOT come back: a hand-in returns nothing, and there is no "
                            + "authorable give-back in the contract at all. " + counts(player));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /**
     * The crop is resolved through {@code CropRegistry}, and it is the produce rather than the seed.
     *
     * <p>The mistake this guards is the exact one the resolver shape exists to prevent: the player
     * is carrying both, and a lookup that reached for the seed would find one.
     */
    @GameTest(template = TEMPLATE, batch = "handin_resolvesTheCropToItsProduceAndNeverItsSeed")
    public static void resolvesTheCropToItsProduceAndNeverItsSeed(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, CARROTS, 4);
            give(player, CARROT_SEEDS, 8);

            begin(player, demand(handin, resolver("carrot", 1)));

            check(count(player, CARROTS) == 3, "one produce is taken: " + counts(player));
            check(count(player, CARROT_SEEDS) == 8, "the seed is never the produce: " + counts(player));
            QuestHandinRemoval proof = rails.requests.get(0).removedItems().get(0);
            check(proof.itemId().equals(CARROTS), "the proof names the produce, got " + proof.itemId());
            check(proof.answersResolver(), "a resolver requirement is answered as one");
            check("carrot".equals(proof.flagValue()), "echoing the flag pinned at prepare time");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A crop this build has never heard of takes nothing and closes nothing permanently. */
    @GameTest(template = TEMPLATE, batch = "handin_refusesAnUnknownCropWithoutTakingAnything")
    public static void refusesAnUnknownCropWithoutTakingAnything(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, CARROTS, 4);

            String body = begin(player, demand(handin, resolver("not_a_crop", 1)));

            check(count(player, CARROTS) == 4, "nothing is taken for a demand this build cannot read");
            check(rails.requests.isEmpty(), "and Rails is told nothing at all");
            check(body.contains("unknown_crop"), "the player is told why: " + body);
            check(state(player, handin) == QuestHandinLocalState.ABANDONED,
                    "expected abandoned, got " + state(player, handin));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    // --- all or nothing -------------------------------------------------------------------

    /** Short by one unit: nothing is taken, Rails is not told, and the shortfall is exact. */
    @GameTest(template = TEMPLATE, batch = "handin_shortByOneLosesNothing")
    public static void shortByOneLosesNothing(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 1);
            give(player, DIRT, 2);

            String body = begin(player, demand(handin, literal(DUNG, 1), literal(DIRT, 3)));

            check(count(player, DUNG) == 1 && count(player, DIRT) == 2,
                    "a player short by one loses nothing at all: " + counts(player));
            check(rails.requests.isEmpty(), "nothing was removed, so nothing is reported");
            JsonObject handinBlock = JsonParser.parseString(body).getAsJsonObject()
                    .getAsJsonObject("handin");
            check("items_missing".equals(handinBlock.get("state").getAsString()), "state: " + body);
            JsonArray missing = handinBlock.getAsJsonArray("missing");
            check(missing.size() == 1, "only the shortfall is reported: " + body);
            check(missing.get(0).getAsJsonObject().get("count").getAsInt() == 1,
                    "one dirt short, not three: " + body);
            check(state(player, handin) == QuestHandinLocalState.PREPARED,
                    "the transaction stays claimable so the player can come back with the items");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** The off hand counts. The ender chest does not. */
    @GameTest(template = TEMPLATE, batch = "handin_readsTheOffHandAndNotTheEnderChest")
    public static void readsTheOffHandAndNotTheEnderChest(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        try {
            player.getInventory().offhand.set(0, stack(DUNG, 1));
            player.getEnderChestInventory().setItem(0, stack(DIRT, 8));

            begin(player, demand(first, literal(DUNG, 1)));
            check(player.getInventory().offhand.get(0).isEmpty(),
                    "the off hand is carried inventory and is read: " + counts(player));

            String body = begin(player, demand(second, literal(DIRT, 1)));
            check(player.getEnderChestInventory().getItem(0).getCount() == 8,
                    "the ender chest is never searched, and never emptied");
            check(body.contains("items_missing"),
                    "a hand-in the player can only satisfy from an ender chest is a shortfall: " + body);
        } finally {
            player.getEnderChestInventory().clearContent();
            cleanup(helper, player, first, second);
        }
        helper.succeed();
    }

    /** Two requirements naming one item: both are answered, each with its own proof entry. */
    @GameTest(template = TEMPLATE, batch = "handin_answersRepeatedRequirementsSeparately")
    public static void answersRepeatedRequirementsSeparately(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 5);

            begin(player, demand(handin, literal(DUNG, 1), literal(DUNG, 2)));

            check(count(player, DUNG) == 2, "three come off in total: " + counts(player));
            List<QuestHandinRemoval> proof = rails.requests.get(0).removedItems();
            check(proof.size() == 2, "one entry per requirement, never merged: " + proof.size());
            check(proof.get(0).requirementIndex() == 0 && proof.get(0).count() == 1, "first requirement");
            check(proof.get(1).requirementIndex() == 1 && proof.get(1).count() == 2, "second requirement");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    // --- the durable boundary --------------------------------------------------------------

    /** The proof is in both durable places before Rails is told anything. */
    @GameTest(template = TEMPLATE, batch = "handin_theProofIsDurableBeforeRailsIsTold")
    public static void theProofIsDurableBeforeRailsIsTold(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        AtomicReference<String> seen = new AtomicReference<>("");
        try {
            give(player, DUNG, 1);
            rails.answer = request -> {
                // Read at the moment of the call: both copies must already exist.
                QuestHandinLocalState state = state(player, handin);
                boolean marked = PlayerDataStore.handinRemoval(player, handin).isPresent();
                seen.set(state + "/marker=" + marked);
                return answered(consumed(handin));
            };

            begin(player, demand(handin, literal(DUNG, 1)));

            check(seen.get().equals("CONFIRMING/marker=true"),
                    "the ledger and the player's own file must both hold the proof before the "
                            + "confirmation goes out, got " + seen.get());
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /**
     * A crash between the mutation and the ledger flush: the player's file has the removal and its
     * proof, the ledger only has the intent. The marker is the authority and the row is finished.
     */
    @GameTest(template = TEMPLATE, batch = "handin_aCrashAfterTheMutationIsRepairedFromThePlayerFile")
    public static void aCrashAfterTheMutationIsRepairedFromThePlayerFile(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            List<QuestHandinRemoval> proof = List.of(QuestHandinRemoval.literal(0, DUNG, 1));
            // Exactly what a crash after step 5 and before step 6 leaves behind.
            QuestHandinLedger.record(player.server, QuestHandinLedgerEntry
                    .prepared(handin, player.getUUID(), "41", "9001", "hand_over", UUID.randomUUID(), 1L)
                    .withRemovalIntent(proof, 1L));
            PlayerDataStore.markHandinRemoved(player, new PlayerDataStore.HandinRemovalMarker(
                    handin, UUID.randomUUID(), 1L, proof));

            QuestItemHandinReconciler.onLogin(player);

            check(rails.requests.size() == 1,
                    "the recovered transaction must be confirmed, got " + rails.requests.size());
            check(rails.requests.get(0).removedItems().get(0).itemId().equals(DUNG),
                    "with the marker's own proof");
            check(state(player, handin) == QuestHandinLocalState.CONSUMED,
                    "expected consumed, got " + state(player, handin));
            check(PlayerDataStore.handinRemoval(player, handin).isEmpty(),
                    "and the marker is dropped once the row is durably settled");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** An intent with no marker means the mutation never reached disk: nothing was taken. */
    @GameTest(template = TEMPLATE, batch = "handin_anIntentWithNoMarkerIsUnwound")
    public static void anIntentWithNoMarkerIsUnwound(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            QuestHandinLedger.record(player.server, QuestHandinLedgerEntry
                    .prepared(handin, player.getUUID(), "41", "9001", "hand_over", UUID.randomUUID(), 1L)
                    .withRemovalIntent(List.of(QuestHandinRemoval.literal(0, DUNG, 1)), 1L));

            QuestItemHandinReconciler.onLogin(player);

            check(state(player, handin) == QuestHandinLocalState.PREPARED,
                    "an intent whose mutation did not persist owes nothing, got " + state(player, handin));
            check(rails.requests.isEmpty(), "and Rails must not be told a removal happened");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /**
     * The dangerous disagreement: the ledger says the items are gone and the player's own file does
     * not. Confirming would buy a completion or a refund for items the player still holds.
     */
    @GameTest(template = TEMPLATE, batch = "handin_aLedgerAheadOfThePlayerFileIsStranded")
    public static void aLedgerAheadOfThePlayerFileIsStranded(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 1);
            QuestHandinLedger.record(player.server, QuestHandinLedgerEntry
                    .prepared(handin, player.getUUID(), "41", "9001", "hand_over", UUID.randomUUID(), 1L)
                    .withRemovalIntent(List.of(QuestHandinRemoval.literal(0, DUNG, 1)), 1L)
                    .withRemovedLocally(2L));

            QuestItemHandinReconciler.onLogin(player);

            check(state(player, handin) == QuestHandinLocalState.STRANDED,
                    "expected stranded, got " + state(player, handin));
            check(rails.requests.isEmpty(),
                    "a contradiction is never confirmed: the player keeps the items and nobody is paid");
            check(count(player, DUNG) == 1, "and the items are still theirs: " + counts(player));
            check(QuestHandinLedger.find(player.server, handin).orElseThrow().proof().size() == 1,
                    "the record is preserved for an operator, not deleted");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A marker whose ledger row is gone is self-sufficient: the transaction still finishes. */
    @GameTest(template = TEMPLATE, batch = "handin_anOrphanMarkerIsAdoptedAndConfirmed")
    public static void anOrphanMarkerIsAdoptedAndConfirmed(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            PlayerDataStore.markHandinRemoved(player, new PlayerDataStore.HandinRemovalMarker(
                    handin, UUID.randomUUID(), 1L, List.of(QuestHandinRemoval.literal(0, DUNG, 1))));

            QuestItemHandinReconciler.onLogin(player);

            check(rails.requests.size() == 1, "the marker alone is enough to confirm, got " + rails.requests.size());
            check(rails.requests.get(0).handinUuid().equals(handin), "for the transaction it names");
            check(state(player, handin) == QuestHandinLocalState.CONSUMED,
                    "expected consumed, got " + state(player, handin));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** Dying must not destroy the evidence that the player gave something up. */
    @GameTest(template = TEMPLATE, batch = "handin_theMarkerSurvivesARespawn")
    public static void theMarkerSurvivesARespawn(GameTestHelper helper) {
        ServerPlayer original = prepare(helper);
        ServerPlayer respawned = helper.makeMockServerPlayerInLevel();
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            PlayerDataStore.markHandinRemoved(original, new PlayerDataStore.HandinRemovalMarker(
                    handin, UUID.randomUUID(), 1L, List.of(QuestHandinRemoval.literal(0, DUNG, 1))));
            check(respawned.getPersistentData().getCompound(PlayerDataStore.PERSISTENT_KEY).isEmpty(),
                    "precondition: a fresh player carries none of this compound");

            // What ServerPlayer#restoreFrom does for a respawn, driven directly: it copies only the
            // PlayerPersisted sub-tag, so without the clone handler this compound is simply lost.
            PlayerDataCloneHandler.copy(original, respawned, true);

            check(PlayerDataStore.handinRemoval(respawned, handin).isPresent(),
                    "the removal marker must survive dying: losing it strands the transaction, and "
                            + "the player is left with no item, no quest and no refund");
            check(PlayerDataStore.handinRemoval(respawned, handin).orElseThrow().proof().get(0)
                    .itemId().equals(DUNG), "with its proof intact");
        } finally {
            cleanup(helper, original, handin);
            helper.getLevel().getServer().getPlayerList().remove(respawned);
        }
        helper.succeed();
    }

    // --- what Rails answers ----------------------------------------------------------------

    /** A refund is never minted here: the items come back as an ordinary reward delivery. */
    @GameTest(template = TEMPLATE, batch = "handin_aRefundIsNeverMintedLocally")
    public static void aRefundIsNeverMintedLocally(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 1);
            rails.answer = request -> answered(cancelledRefunded(handin));

            String body = begin(player, demand(handin, literal(DUNG, 1)));

            check(count(player, DUNG) == 0,
                    "the refund arrives through the delivery ledger, never by putting it back here: "
                            + counts(player));
            check(state(player, handin) == QuestHandinLocalState.REFUNDED,
                    "expected refunded, got " + state(player, handin));
            check(body.contains("\"state\":\"refunded\""), "and the player is told: " + body);
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** Evidence refused: no second removal, and the row is preserved rather than closed. */
    @GameTest(template = TEMPLATE, batch = "handin_evidenceRejectedNeverTakesASecondItem")
    public static void evidenceRejectedNeverTakesASecondItem(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 3);
            rails.answer = request -> answered(refusal("evidence_rejected", handin, "evidence_mismatch"));

            begin(player, demand(handin, literal(DUNG, 1)));
            check(count(player, DUNG) == 2, "one removal, refused: " + counts(player));

            // The sweep must not answer a refusal by trying again forever, or by taking more.
            QuestItemHandinReconciler.onLogin(player);
            check(count(player, DUNG) == 2, "a refusal never causes a second removal: " + counts(player));
            check(state(player, handin) == QuestHandinLocalState.STRANDED,
                    "expected stranded, got " + state(player, handin));
            check(rails.requests.size() == 1,
                    "and the identical body is not resent forever, got " + rails.requests.size());
            check(QuestHandinLedger.find(player.server, handin).orElseThrow().proof().size() == 1,
                    "the proof is kept for an operator");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A transaction Rails does not hold: items gone, neither ending proved, record preserved. */
    @GameTest(template = TEMPLATE, batch = "handin_anUnknownTransactionIsStrandedNotClosed")
    public static void anUnknownTransactionIsStrandedNotClosed(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 1);
            rails.answer = request -> answered(refusal("rejected", null, ""));

            begin(player, demand(handin, literal(DUNG, 1)));

            check(state(player, handin) == QuestHandinLocalState.STRANDED,
                    "expected stranded, got " + state(player, handin));
            check(!state(player, handin).settled(), "a stranded row is never closed as finished");
            check(QuestItemHandinReconciler.outstanding(player.server).stream()
                            .anyMatch(entry -> entry.handinUuid().equals(handin)),
                    "and it is reported at every boot until an operator deals with it");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A transport failure keeps the proof, and the next sweep finishes the transaction. */
    @GameTest(template = TEMPLATE, batch = "handin_aTimeoutIsFinishedByTheNextSweep")
    public static void aTimeoutIsFinishedByTheNextSweep(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 1);
            rails.answer = request -> new QuestItemHandinClient.Failure("overall_timeout");

            begin(player, demand(handin, literal(DUNG, 1)));
            check(count(player, DUNG) == 0, "the items are gone: " + counts(player));
            check(state(player, handin).confirmationOutstanding(),
                    "and the row still owes a confirmation, got " + state(player, handin));

            rails.answer = request -> answered(consumed(handin));
            QuestItemHandinReconciler.onLogin(player);

            check(rails.requests.size() == 2, "the sweep retries, got " + rails.requests.size());
            check(textOf(rails.requests.get(0)).equals(textOf(rails.requests.get(1))),
                    "and it resends byte-identical evidence rather than recomputing it");
            check(state(player, handin) == QuestHandinLocalState.CONSUMED,
                    "expected consumed, got " + state(player, handin));
            check(count(player, DUNG) == 0, "and never takes a second item: " + counts(player));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** The completion runs once, however many times Rails answers. */
    @GameTest(template = TEMPLATE, batch = "handin_theCompletionIsAppliedExactlyOnce")
    public static void theCompletionIsAppliedExactlyOnce(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        AtomicInteger applied = new AtomicInteger();
        try {
            give(player, DUNG, 2);
            QuestItemHandinService.begin(player, demand(handin, literal(DUNG, 1)),
                    (target, response) -> {
                        applied.incrementAndGet();
                        return GSON.toJson(response);
                    },
                    (status, body) -> { });
            check(applied.get() == 1, "the completion applies once, got " + applied.get());

            // Rails replaying its stored completion, exactly as a lost response would produce.
            rails.answer = request -> answered(duplicate(handin));
            QuestItemHandinService.begin(player, demand(handin, literal(DUNG, 1)),
                    (target, response) -> {
                        applied.incrementAndGet();
                        return GSON.toJson(response);
                    },
                    (status, body) -> { });

            check(applied.get() == 1, "a duplicate answer must not apply it again, got " + applied.get());
            check(count(player, DUNG) == 1, "and must not take a second item: " + counts(player));
            check(rails.requests.size() == 1,
                    "the second claim never reaches Rails at all -- it stops at the ledger, which is "
                            + "why the duplicate path below has to be driven directly");

            // And the duplicate path itself, which a second claim can never reach because the row is
            // already settled. This is what Rails answers when a confirmation was applied and only
            // its response was lost.
            QuestHandinLedgerEntry settled = QuestHandinLedger.find(player.server, handin).orElseThrow();
            check(settled.localState() == QuestHandinLocalState.CONSUMED, "precondition");
            QuestItemHandinService.apply(player, settled, duplicate(handin), new JsonObject(),
                    (target, response) -> {
                        applied.incrementAndGet();
                        return GSON.toJson(response);
                    });

            check(applied.get() == 1,
                    "an answer arriving for a row this server already finished replays rather than "
                            + "re-applies, got " + applied.get());
            check(count(player, DUNG) == 1, "and still takes nothing: " + counts(player));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A second claim on a transaction already under way takes nothing more. */
    @GameTest(template = TEMPLATE, batch = "handin_aSecondClaimTakesNothingMore")
    public static void aSecondClaimTakesNothingMore(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 4);
            rails.answer = request -> new QuestItemHandinClient.Failure("overall_timeout");

            begin(player, demand(handin, literal(DUNG, 1)));
            begin(player, demand(handin, literal(DUNG, 1)));
            begin(player, demand(handin, literal(DUNG, 1)));

            check(count(player, DUNG) == 3,
                    "the same transaction takes at most one item however often it is claimed: "
                            + counts(player));
            check(rails.requests.size() == 1,
                    "and a claim on a transaction already under way sends nothing, got "
                            + rails.requests.size());
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /**
     * A transaction closed for a reason that has passed can be claimed again.
     *
     * <p>Rails keys a hand-in on the transition, so every later claim hands back the same
     * transaction id. A local row that refused to reopen would make the quest permanently
     * unfinishable while insisting the transaction was still in flight.
     */
    @GameTest(template = TEMPLATE, batch = "handin_anAbandonedTransactionCanBeClaimedAgain")
    public static void anAbandonedTransactionCanBeClaimedAgain(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            QuestHandinLedger.record(player.server, QuestHandinLedgerEntry
                    .prepared(handin, player.getUUID(), "41", "9001", "hand_over", UUID.randomUUID(), 1L));
            QuestHandinLedger.transition(player.server, handin,
                    entry -> entry.withSettled(QuestHandinLocalState.ABANDONED, 2L));
            give(player, DUNG, 1);

            begin(player, demand(handin, literal(DUNG, 1)));

            check(count(player, DUNG) == 0, "the reopened claim works: " + counts(player));
            check(state(player, handin) == QuestHandinLocalState.CONSUMED,
                    "expected consumed, got " + state(player, handin));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A quest with no hand-in metadata removes nothing and mints no transaction. */
    @GameTest(template = TEMPLATE, batch = "handin_aQuestWithNoHandinRemovesNothing")
    public static void aQuestWithNoHandinRemovesNothing(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        try {
            give(player, DUNG, 2);
            give(player, SHOVEL, 1);
            JsonObject ordinary = JsonParser.parseString(
                    "{\"success\": true, \"completed\": true, \"quest_id\": 41, \"quest_state_id\": \"9001\"}")
                    .getAsJsonObject();

            check(!QuestItemHandinService.isHandinRequired(ordinary),
                    "an ordinary transition answer is not a hand-in demand, so the proxy never routes "
                            + "it here at all -- which is how every quest in the game keeps behaving "
                            + "exactly as it did");
            int before = QuestHandinLedgerStore.get(player.server).entriesFor(player.getUUID()).size();

            // And if something ever did route one here, it still takes nothing. Belt as well as
            // braces, because this is the compatibility promise the release order depends on.
            AtomicReference<String> body = new AtomicReference<>("");
            QuestItemHandinService.begin(player, ordinary, (target, response) -> GSON.toJson(response),
                    (status, answer) -> body.set(answer));

            check(counts(player).equals(Map.of(DUNG, 2, SHOVEL, 1)),
                    "nothing is removed for a response with no hand-in: " + counts(player));
            check(QuestHandinLedgerStore.get(player.server).entriesFor(player.getUUID()).size() == before,
                    "and no transaction is minted for it");
            check(rails.requests.isEmpty(), "and Rails is told nothing");
            check(body.get().contains("demand_unreadable"), "it is refused, not guessed at: " + body.get());
        } finally {
            cleanup(helper, player);
        }
        helper.succeed();
    }

    /** The ledger survives a restart with its proof, and finishes afterwards. */
    @GameTest(template = TEMPLATE, batch = "handin_theLedgerSurvivesARestart")
    public static void theLedgerSurvivesARestart(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 1);
            rails.answer = request -> new QuestItemHandinClient.Failure("overall_timeout");
            begin(player, demand(handin, literal(DUNG, 1)));

            simulateRestart(player.server);

            QuestHandinLedgerEntry reloaded = QuestHandinLedger.find(player.server, handin).orElse(null);
            check(reloaded != null, "the transaction must survive the restart");
            check(reloaded.proof().size() == 1 && reloaded.proof().get(0).itemId().equals(DUNG),
                    "with the exact proof of what was taken");

            rails.answer = request -> answered(consumed(handin));
            QuestItemHandinReconciler.onLogin(player);
            check(state(player, handin) == QuestHandinLocalState.CONSUMED,
                    "and it is finished after the restart, got " + state(player, handin));
            check(count(player, DUNG) == 0, "without taking anything more: " + counts(player));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /**
     * The forced player save reports a failure, so the removal is rolled back.
     *
     * <p>This is the boundary the whole ordering exists for: a removal this server cannot prove
     * reached the player's own file must not be reported to Rails, because the ledger would then be
     * ahead of the player file and confirming it would buy a completed quest for items the player
     * still holds. Rolling back is the only safe answer, and the player must end up with exactly
     * what they started with.
     */
    @GameTest(template = TEMPLATE, batch = "handin_aFailedPlayerSaveRollsTheRemovalBack")
    public static void aFailedPlayerSaveRollsTheRemovalBack(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 1);
            give(player, DIRT, 4);
            BankTransferPlayerDurability.useSaveDelegateForTesting(target -> {
                throw new IllegalStateException("the player file could not be written");
            });

            String body = begin(player, demand(handin, literal(DUNG, 1), literal(DIRT, 2)));

            check(count(player, DUNG) == 1, "the whole removal is put back: " + counts(player));
            check(count(player, DIRT) == 4, "including a stack that was only shrunk: " + counts(player));
            check(PlayerDataStore.handinRemoval(player, handin).isEmpty(),
                    "and the marker with it, so nothing claims a removal that did not happen");
            check(rails.requests.isEmpty(),
                    "a removal this server cannot prove is durable is never reported");
            check(state(player, handin) == QuestHandinLocalState.PREPARED,
                    "the transaction owes nothing and can simply be claimed again, got "
                            + state(player, handin));
            check(body.contains("removal_not_durable"), "and the player is told: " + body);
        } finally {
            BankTransferPlayerDurability.resetSaveDelegateForTesting();
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    // --- reconciliation ----------------------------------------------------------------------

    /**
     * A stranded transaction Rails did in fact record.
     *
     * <p>Confirmation cannot resolve this one -- that is what stranded it -- so the read-only
     * reconciliation endpoint is the only way to learn the answer, and learning it turns an
     * operator's problem back into a finished quest.
     */
    @GameTest(template = TEMPLATE, batch = "handin_aStrandedTransactionIsResolvedByAskingRails")
    public static void aStrandedTransactionIsResolvedByAskingRails(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        AtomicInteger applied = new AtomicInteger();
        try {
            QuestItemHandinReconciler.useCompletionApplier((target, response) -> {
                applied.incrementAndGet();
                return GSON.toJson(response);
            });
            // A real strand keeps its marker: only settling drops one, and stranding is not
            // settling. A stranded row WITHOUT its marker is the different, unvouched case that
            // anUnvouchedStrandIsNeverResolvedByAnInquiry covers, and it must not resolve.
            QuestHandinLedger.record(player.server, stranded(player, handin));
            PlayerDataStore.markHandinRemoved(player, new PlayerDataStore.HandinRemovalMarker(
                    handin, UUID.randomUUID(), 1L, List.of(QuestHandinRemoval.literal(0, DUNG, 1))));
            JsonObject completion = new JsonObject();
            completion.addProperty("success", true);
            completion.addProperty("completed", true);
            rails.inquiryAnswer = asked -> List.of(new QuestItemHandinProtocol.ReconcileEntry(
                    handin, QuestItemHandinProtocol.ReconcileOutcome.CONSUMED, List.of(), "", completion));

            QuestItemHandinReconciler.onLogin(player);

            check(rails.inquiries.contains(handin), "a stranded row is asked about");
            check(applied.get() == 1, "and its lost completion is applied once, got " + applied.get());
            check(state(player, handin) == QuestHandinLocalState.CONSUMED,
                    "expected consumed, got " + state(player, handin));
            check(rails.requests.isEmpty(), "reconciliation never confirms and never removes");
        } finally {
            // The production applier, not the class default: this slot is process-wide and every
            // later batch on this server would otherwise reconcile with a no-op.
            QuestItemHandinReconciler.useCompletionApplier(QuestProxyService.handinCompletionApplier());
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /**
     * Rails has given up on a transaction this server removed for, and does not know it removed.
     *
     * <p>Reconciliation cannot pay the compensation -- only the shard knows it took something -- so
     * the row is put back where the confirmation sweep will send the stored proof, which is what
     * publishes the refund.
     */
    @GameTest(template = TEMPLATE, batch = "handin_aCancelledTransactionIsRequeuedSoItsProofCanBuyTheRefund")
    public static void aCancelledTransactionIsRequeuedSoItsProofCanBuyTheRefund(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            List<QuestHandinRemoval> proof = List.of(QuestHandinRemoval.literal(0, DUNG, 1));
            QuestHandinLedger.record(player.server, stranded(player, handin));
            PlayerDataStore.markHandinRemoved(player, new PlayerDataStore.HandinRemovalMarker(
                    handin, UUID.randomUUID(), 1L, proof));
            rails.inquiryAnswer = asked -> List.of(new QuestItemHandinProtocol.ReconcileEntry(
                    handin, QuestItemHandinProtocol.ReconcileOutcome.CANCELLED, List.of(), "abandoned", null));
            rails.answer = request -> answered(cancelledRefunded(handin));

            QuestItemHandinReconciler.onLogin(player);
            check(state(player, handin) == QuestHandinLocalState.REMOVED_LOCAL,
                    "the row is put back where the sweep will confirm it, got " + state(player, handin));

            QuestItemHandinReconciler.onJournalRefreshed(player);

            check(rails.requests.size() == 1,
                    "the stored proof is sent, which is what buys the refund, got " + rails.requests.size());
            check(rails.requests.get(0).removed(), "reporting the removal it really made");
            check(state(player, handin) == QuestHandinLocalState.REFUNDED,
                    "expected refunded, got " + state(player, handin));
            check(count(player, DUNG) == 0, "and nothing is put back locally: " + counts(player));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A transaction Rails has never heard of, with items gone, stays exactly where it is. */
    @GameTest(template = TEMPLATE, batch = "handin_anUnknownAnswerLeavesARemovedTransactionStranded")
    public static void anUnknownAnswerLeavesARemovedTransactionStranded(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            QuestHandinLedger.record(player.server, stranded(player, handin));
            rails.inquiryAnswer = asked -> List.of(new QuestItemHandinProtocol.ReconcileEntry(
                    handin, QuestItemHandinProtocol.ReconcileOutcome.UNKNOWN, List.of(), "", null));

            QuestItemHandinReconciler.onLogin(player);

            check(state(player, handin) == QuestHandinLocalState.STRANDED,
                    "a removal Rails cannot account for is never closed, got " + state(player, handin));
            check(QuestHandinLedger.find(player.server, handin).orElseThrow().proof().size() == 1,
                    "and its proof is kept");
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** An answer about somebody else's transaction is not an answer about this player's. */
    @GameTest(template = TEMPLATE, batch = "handin_anAnswerAboutAnotherPlayersTransactionIsIgnored")
    public static void anAnswerAboutAnotherPlayersTransactionIsIgnored(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        ServerPlayer other = helper.makeMockServerPlayerInLevel();
        Confirmations rails = install();
        UUID mine = UUID.randomUUID();
        UUID theirs = UUID.randomUUID();
        AtomicInteger applied = new AtomicInteger();
        try {
            QuestItemHandinReconciler.useCompletionApplier((target, response) -> {
                applied.incrementAndGet();
                return "";
            });
            QuestHandinLedger.record(player.server, stranded(player, mine));
            PlayerDataStore.markHandinRemoved(player, new PlayerDataStore.HandinRemovalMarker(
                    mine, UUID.randomUUID(), 1L, List.of(QuestHandinRemoval.literal(0, DUNG, 1))));
            QuestHandinLedger.record(other.server, stranded(other, theirs));

            JsonObject completion = new JsonObject();
            completion.addProperty("success", true);
            // Rails answers about a transaction this sweep never asked about, belonging to someone
            // else. A transaction id is enough to find any row on the server, so without the two
            // filters this would run their completion on this player and close their row.
            rails.inquiryAnswer = asked -> List.of(new QuestItemHandinProtocol.ReconcileEntry(
                    theirs, QuestItemHandinProtocol.ReconcileOutcome.CONSUMED, List.of(), "", completion));

            QuestItemHandinReconciler.onLogin(player);

            check(applied.get() == 0, "another player's completion is never applied here");
            check(state(other, theirs) == QuestHandinLocalState.STRANDED,
                    "and their row is untouched, got " + state(other, theirs));
        } finally {
            QuestItemHandinReconciler.useCompletionApplier(QuestProxyService.handinCompletionApplier());
            cleanup(helper, player, mine, theirs);
            helper.getLevel().getServer().getPlayerList().remove(other);
        }
        helper.succeed();
    }

    /**
     * A refusal of the evidence is not a transaction waiting for a refund.
     *
     * <p>Requeueing it would undo the strand and spin the same removal through
     * confirm-refuse-strand once a minute forever.
     */
    @GameTest(template = TEMPLATE, batch = "handin_anEvidenceRefusalIsNotUndoneByAReconciliation")
    public static void anEvidenceRefusalIsNotUndoneByAReconciliation(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 2);
            rails.answer = request -> answered(refusal("evidence_rejected", handin, "evidence_mismatch"));
            begin(player, demand(handin, literal(DUNG, 1)));
            check(state(player, handin) == QuestHandinLocalState.STRANDED, "precondition");

            rails.inquiryAnswer = asked -> List.of(new QuestItemHandinProtocol.ReconcileEntry(
                    handin, QuestItemHandinProtocol.ReconcileOutcome.CANCELLED, List.of(), "abandoned", null));
            QuestItemHandinReconciler.onLogin(player);

            check(state(player, handin) == QuestHandinLocalState.STRANDED,
                    "it stays stranded, got " + state(player, handin));
            check(rails.requests.size() == 1,
                    "and the identical body is not sent again, got " + rails.requests.size());
            check(count(player, DUNG) == 1, "and nothing more is taken: " + counts(player));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /**
     * A row stranded because the player's own file did not record the removal is never resolved by
     * an inquiry -- the player may still be holding the goods, and completing the quest or taking a
     * refund for them would hand them both.
     */
    @GameTest(template = TEMPLATE, batch = "handin_anUnvouchedStrandIsNeverResolvedByAnInquiry")
    public static void anUnvouchedStrandIsNeverResolvedByAnInquiry(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        AtomicInteger applied = new AtomicInteger();
        try {
            QuestItemHandinReconciler.useCompletionApplier((target, response) -> {
                applied.incrementAndGet();
                return "";
            });
            give(player, DUNG, 1);
            // Stranded with no marker: exactly what a silently failed player save leaves behind, and
            // the player still has the dung.
            QuestHandinLedger.record(player.server, stranded(player, handin));
            JsonObject completion = new JsonObject();
            completion.addProperty("success", true);
            rails.inquiryAnswer = asked -> List.of(new QuestItemHandinProtocol.ReconcileEntry(
                    handin, QuestItemHandinProtocol.ReconcileOutcome.CONSUMED, List.of(), "", completion));

            QuestItemHandinReconciler.onLogin(player);

            check(applied.get() == 0,
                    "a quest is not completed for items the player is still holding");
            check(state(player, handin) == QuestHandinLocalState.STRANDED,
                    "and the row stays where an operator can see it, got " + state(player, handin));
            check(count(player, DUNG) == 1, "and the dung is still theirs: " + counts(player));
        } finally {
            QuestItemHandinReconciler.useCompletionApplier(QuestProxyService.handinCompletionApplier());
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    /** A sweep landing while a live confirmation is in flight sends no second body. */
    @GameTest(template = TEMPLATE, batch = "handin_aSweepDuringALiveConfirmationSendsNoSecondBody")
    public static void aSweepDuringALiveConfirmationSendsNoSecondBody(GameTestHelper helper) {
        ServerPlayer player = prepare(helper);
        Confirmations rails = install();
        UUID handin = UUID.randomUUID();
        try {
            give(player, DUNG, 1);
            // The sweep runs re-entrantly, from inside the confirmation the dialogue started: the
            // row is CONFIRMING with its marker present, which is exactly the shape the reconciler
            // would otherwise send a second, byte-identical confirmation for.
            rails.answer = request -> {
                QuestItemHandinReconciler.onLogin(player);
                return answered(consumed(handin));
            };

            begin(player, demand(handin, literal(DUNG, 1)));

            check(rails.requests.size() == 1,
                    "one transaction, one confirmation, got " + rails.requests.size());
            check(state(player, handin) == QuestHandinLocalState.CONSUMED,
                    "expected consumed, got " + state(player, handin));
        } finally {
            cleanup(helper, player, handin);
        }
        helper.succeed();
    }

    // --- scaffolding -----------------------------------------------------------------------

    /**
     * Drives one hand-in to completion and answers with the body the screen would have received.
     *
     * <p>Synchronous by construction, and worth knowing why. The scripted confirmer answers with an
     * already-completed future, so its {@code whenComplete} runs on this thread; and
     * {@code BlockableEventLoop#execute} runs a task inline rather than queueing it when the caller
     * is already the server thread, which a GameTest is. So the whole transaction -- removal,
     * confirmation, settlement -- has finished by the time this returns, and nothing below races it.
     */
    private static String begin(ServerPlayer player, JsonObject demand) {
        AtomicReference<String> body = new AtomicReference<>("");
        QuestItemHandinService.begin(player, demand,
                (target, response) -> GSON.toJson(response),
                (status, answer) -> body.set(answer));
        return body.get();
    }

    /** A transaction whose items are gone and whose last answer proved neither ending. */
    private static QuestHandinLedgerEntry stranded(ServerPlayer player, UUID handinUuid) {
        return QuestHandinLedgerEntry
                .prepared(handinUuid, player.getUUID(), "41", "9001", "hand_over", UUID.randomUUID(), 1L)
                .withRemovalIntent(List.of(QuestHandinRemoval.literal(0, DUNG, 1)), 1L)
                .withRemovedLocally(2L)
                .withStranded("handin_rejected", 3L);
    }

    private static ServerPlayer prepare(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        return player;
    }

    private static Confirmations install() {
        Confirmations rails = new Confirmations();
        QuestItemHandinService.installConfirmer(rails);
        // Stubbed too, and silent by default: the sweep asks Rails what happened to anything it
        // cannot resolve, and a live client here would reach for real credentials.
        QuestItemHandinReconciler.installInquiry(rails);
        return rails;
    }

    private static void cleanup(GameTestHelper helper, ServerPlayer player, UUID... handins) {
        QuestItemHandinService.resetConfirmer();
        QuestItemHandinReconciler.resetInquiry();
        MinecraftServer server = helper.getLevel().getServer();
        for (UUID handin : handins) {
            QuestHandinLedgerStore.get(server).removeForTesting(handin);
            PlayerDataStore.forgetHandinRemoval(player, handin);
            QuestItemHandinReconciler.release(server, handin);
        }
        QuestItemHandinReconciler.clear(server);
        server.getPlayerList().remove(player);
    }

    /** Serialise the live ledger, reload it from those bytes, and make the reload the live store. */
    private static void simulateRestart(MinecraftServer server) {
        QuestHandinLedgerStore live = QuestHandinLedgerStore.get(server);
        CompoundTag bytes = live.save(new CompoundTag(), server.registryAccess());
        QuestHandinLedgerStore reloaded = QuestHandinLedgerStore.load(bytes, server.registryAccess());
        check(!reloaded.isReadOnlyFutureSchema(), "the reloaded ledger must be readable");
        server.overworld().getDataStorage().set(QuestHandinLedgerStore.DATA_NAME, reloaded);
        QuestItemHandinReconciler.clear(server);
    }

    // --- the demands Rails would have sent ---------------------------------------------------

    private static JsonObject demand(UUID handinUuid, JsonObject... requires) {
        JsonObject handin = new JsonObject();
        handin.addProperty("handin_uuid", handinUuid.toString());
        handin.addProperty("choice", "hand_over");
        handin.addProperty("missing_message", "Rowan needs what he asked for.");
        JsonArray array = new JsonArray();
        for (JsonObject requirement : requires) array.add(requirement);
        handin.add("requires", array);

        JsonObject root = new JsonObject();
        root.addProperty("success", true);
        root.addProperty("result", QuestItemHandinProtocol.RESULT_HANDIN_REQUIRED);
        root.addProperty("completed", false);
        root.addProperty("quest_id", 41);
        root.addProperty("quest_state_id", "9001");
        root.add(QuestItemHandinProtocol.HANDIN_KEY, handin);
        return root;
    }

    private static JsonObject literal(String itemId, int count) {
        JsonObject requirement = new JsonObject();
        requirement.addProperty("item", itemId);
        requirement.addProperty("count", count);
        return requirement;
    }

    private static JsonObject resolver(String cropId, int count) {
        JsonObject requirement = new JsonObject();
        requirement.addProperty("resolver", "awarded_crop_harvest_item");
        requirement.addProperty("flag", "awarded_crop");
        requirement.addProperty("flag_value", cropId);
        requirement.addProperty("count", count);
        return requirement;
    }

    // --- the answers Rails would have given --------------------------------------------------

    private static QuestItemHandinClient.ConfirmResult answered(QuestItemHandinProtocol.ConfirmationResponse response) {
        return new QuestItemHandinClient.Answered(response);
    }

    private static QuestItemHandinProtocol.ConfirmationResponse consumed(UUID handinUuid) {
        return completion(QuestItemHandinProtocol.Result.CONSUMED, handinUuid);
    }

    private static QuestItemHandinProtocol.ConfirmationResponse duplicate(UUID handinUuid) {
        return completion(QuestItemHandinProtocol.Result.DUPLICATE, handinUuid);
    }

    private static QuestItemHandinProtocol.ConfirmationResponse completion(
            QuestItemHandinProtocol.Result result, UUID handinUuid) {
        JsonObject response = new JsonObject();
        response.addProperty("success", true);
        response.addProperty("completed", true);
        response.addProperty("quest_id", 41);
        response.addProperty("quest_state_id", "9001");
        return new QuestItemHandinProtocol.ConfirmationResponse(result, handinUuid, "consumed", "",
                List.of(), List.of(), response, null);
    }

    private static QuestItemHandinProtocol.ConfirmationResponse cancelledRefunded(UUID handinUuid) {
        return new QuestItemHandinProtocol.ConfirmationResponse(
                QuestItemHandinProtocol.Result.CANCELLED_REFUNDED, handinUuid, "cancelled", "abandoned",
                List.of(), List.of(), null, UUID.randomUUID());
    }

    private static QuestItemHandinProtocol.ConfirmationResponse refusal(String wire, UUID handinUuid,
                                                                        String reason) {
        return new QuestItemHandinProtocol.ConfirmationResponse(
                QuestItemHandinProtocol.Result.fromWireName(wire), handinUuid, "pending", reason,
                List.of(), List.of(), null, null);
    }

    /**
     * A recording stand-in for Rails: every request captured, the answers scripted per test.
     *
     * <p>Both halves, because the sweep uses both. The inquiry answers with nothing by default --
     * an empty listing changes no row -- so a test that does not care about reconciliation is not
     * quietly driven by it.
     */
    private static final class Confirmations
            implements QuestItemHandinService.Confirmer, QuestItemHandinReconciler.Inquiry {
        final List<QuestItemHandinProtocol.ConfirmationRequest> requests = new CopyOnWriteArrayList<>();
        final List<UUID> inquiries = new CopyOnWriteArrayList<>();
        volatile Function<QuestItemHandinProtocol.ConfirmationRequest, QuestItemHandinClient.ConfirmResult> answer =
                request -> answered(consumed(request.handinUuid()));
        volatile Function<List<UUID>, List<QuestItemHandinProtocol.ReconcileEntry>> inquiryAnswer =
                asked -> List.of();

        @Override
        public CompletableFuture<QuestItemHandinClient.ConfirmResult> confirm(
                MinecraftServer server, QuestItemHandinProtocol.ConfirmationRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(answer.apply(request));
        }

        @Override
        public CompletableFuture<QuestItemHandinClient.ReconcileResult> reconcile(
                MinecraftServer server, UUID playerUuid, List<UUID> handinUuids) {
            inquiries.addAll(handinUuids);
            return CompletableFuture.completedFuture(new QuestItemHandinClient.Reconciled(
                    new QuestItemHandinProtocol.ReconcileResponse(inquiryAnswer.apply(handinUuids))));
        }
    }

    // --- inventory helpers --------------------------------------------------------------------

    private static void give(ServerPlayer player, String itemId, int count) {
        player.getInventory().add(stack(itemId, count));
    }

    private static ItemStack stack(String itemId, int count) {
        return new ItemStack(BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.parse(itemId)), count);
    }

    private static Map<String, Integer> counts(ServerPlayer player) {
        Map<String, Integer> counts = new TreeMap<>();
        Inventory inventory = player.getInventory();
        List<ItemStack> all = new ArrayList<>(inventory.items);
        all.addAll(inventory.armor);
        all.addAll(inventory.offhand);
        for (ItemStack stack : all) {
            if (stack.isEmpty() || stack.getItem() == Items.AIR) continue;
            counts.merge(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(), stack.getCount(), Integer::sum);
        }
        return counts;
    }

    private static int count(ServerPlayer player, String itemId) {
        return counts(player).getOrDefault(itemId, 0);
    }

    private static QuestHandinLocalState state(ServerPlayer player, UUID handinUuid) {
        Optional<QuestHandinLedgerEntry> entry = QuestHandinLedger.find(player.server, handinUuid);
        return entry.map(QuestHandinLedgerEntry::localState).orElse(null);
    }

    private static String textOf(QuestItemHandinProtocol.ConfirmationRequest request) {
        return new String(QuestItemHandinProtocol.encodeConfirmation(request),
                java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Throws {@link GameTestAssertException}, never anything else: only that type is swallowed and
     * retried inside a sequence callback; anything else escapes into the server tick loop and
     * crashes the whole GameTest server.
     */
    private static void check(boolean condition, String message) {
        if (!condition) throw new GameTestAssertException(message);
    }
}
