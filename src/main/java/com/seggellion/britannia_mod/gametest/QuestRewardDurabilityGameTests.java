package com.seggellion.britannia_mod.gametest;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemEligibility;
import com.seggellion.britannia_mod.quest.ClientQuestEntry;
import com.seggellion.britannia_mod.quest.QuestCleanupService;
import com.seggellion.britannia_mod.quest.QuestItemStamp;
import com.seggellion.britannia_mod.quest.QuestJournalRefresh;
import com.seggellion.britannia_mod.quest.QuestRewardService;
import com.seggellion.britannia_mod.quest.ServerQuestTable;
import com.seggellion.britannia_mod.quest.network.QuestModels;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Rowan farming questline M1 (discovery D1): rewards a quest hands out to KEEP must survive every
 * cleanup boundary the quest system has -- quit, journal refresh, completion, and the login pass
 * that deleted them before this milestone -- while the one legitimately temporary item (the ring
 * that must be cast into the fire) is still taken back when its quest ends.
 *
 * <p>These drive the real {@link QuestRewardService} and {@link QuestCleanupService} against a
 * real server player's inventory, armour and off hand, with Rails responses in the exact shape
 * {@code QuestProxyService} hands them over (parsed body plus raw JSON).
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class QuestRewardDurabilityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";
    private static final Gson GSON = new Gson();

    private static final long KIT_QUEST_ID = 4101L;
    private static final String KIT_QUEST_STATE_ID = "9101";
    private static final long RING_QUEST_ID = 4102L;
    private static final String RING_QUEST_STATE_ID = "9102";
    private static final long ESCORT_QUEST_ID = 4103L;
    private static final String ESCORT_QUEST_STATE_ID = "9103";

    /** Everything the questline pays out to keep: the kit on acceptance and coins on completion. */
    private static final Map<String, Integer> KIT = Map.of(
        "britannia_mod:britannia_shovel", 1,
        "britannia_mod:empty_bowl", 2,
        "minecraft:bucket", 1,
        "britannia_mod:watering_can", 1,
        "britannia_mod:carrot_seeds", 1,
        "britannia_mod:farming_hoe", 1,
        "britannia_mod:gold_coin", 5,
        "britannia_mod:silver_coin", 3,
        "britannia_mod:copper_coin", 7);

    private QuestRewardDurabilityGameTests() {
    }

    /**
     * The M1 gate: permanent items survive the complete existing cleanup lifecycle. Quest active
     * at login, quest completed then relog, quest quit, a journal refresh, and finally the login
     * pass with the quest gone from the journal -- the exact path that deleted every reward before.
     */
    @GameTest(template = TEMPLATE)
    public static void permanentRewardsSurviveEveryCleanupBoundary(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        ServerQuestTable.replaceFromBootstrap(player, List.of(kitQuest()));

        applyRails(player, kitGrant());

        check(KIT.equals(counts(player)), "the kit was not granted as authored: " + counts(player));
        check(stampedStacks(player) == 0, "a permanent reward must never carry the cleanup stamp");

        // Login while the quest is still active.
        QuestCleanupService.cleanupStaleLocalQuestState(player, List.of(kitQuest()));
        check(KIT.equals(counts(player)), "kit changed by the login pass with the quest active: " + counts(player));

        // Completion, then the next login: the journal no longer holds the quest.
        ServerQuestTable.removeAfterRailsCompletionSuccessByQuestId(player, KIT_QUEST_ID);
        QuestCleanupService.cleanupStaleLocalQuestState(player, ServerQuestTable.snapshot(player));
        check(KIT.equals(counts(player)), "kit changed by the login pass after completion: " + counts(player));

        // Quit.
        QuestCleanupService.cleanupAfterQuestQuit(player, kitQuest());
        check(KIT.equals(counts(player)), "kit changed by the quit pass: " + counts(player));

        // The login pass with an empty journal (the pre-M1 deletion path).
        QuestCleanupService.cleanupStaleLocalQuestState(player, List.of());
        check(KIT.equals(counts(player)), "kit changed by the login pass with an empty journal: " + counts(player));
        check(stampedStacks(player) == 0, "no permanent reward may have acquired a stamp");

        // A journal refresh that comes back empty.
        AtomicBoolean refreshed = new AtomicBoolean();
        QuestJournalRefresh.installFetcher((server, target) -> Optional.of(List.of()));
        try {
            QuestJournalRefresh.refresh(player, () -> refreshed.set(true));
            helper.succeedWhen(() -> {
                check(refreshed.get(), "the journal refresh has not completed yet");
                check(KIT.equals(counts(player)), "kit changed by the journal refresh: " + counts(player));
                check(stampedStacks(player) == 0, "no permanent reward may carry a stamp after the refresh");
                QuestJournalRefresh.resetFetcher();
            });
        } catch (RuntimeException error) {
            QuestJournalRefresh.resetFetcher();
            throw error;
        }
    }

    /** The heuristic path: a legacy response whose destination node says the ring must be destroyed. */
    @GameTest(template = TEMPLATE)
    public static void aTemporaryObjectiveItemIsTakenBackOnlyOnceItsQuestEnds(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        ServerQuestTable.replaceFromBootstrap(player, List.of(ringQuest()));

        applyRails(player, ringGrant(null));

        ItemStack ring = onlyStack(player);
        check(ring.getItem() == ItemRegistry.ONE_RING.get(), "expected the ring, got " + ring);
        CompoundTag stamp = QuestItemStamp.read(ring);
        check(QuestItemStamp.isTemporary(stamp), "the ring must carry the temporary stamp: " + stamp);
        check("ring_destroyed".equals(stamp.getString(QuestItemStamp.TRIGGER_KEY)),
            "the stamp must name the destroy objective, got " + stamp.getString(QuestItemStamp.TRIGGER_KEY));
        check("magic_ring".equals(stamp.getString(QuestItemStamp.ITEM)), "the stamp must carry the objective's item tag");
        check(player.getStringUUID().equals(stamp.getString(QuestItemStamp.OWNER_UUID)), "the stamp must name the owner by UUID");
        check(!stamp.contains(QuestItemStamp.OWNER_NAME), "the stamp must not carry the player's name");
        check(stamp.getLong(QuestItemStamp.QUEST_ID) == RING_QUEST_ID, "the stamp must carry the quest id");
        check(RING_QUEST_STATE_ID.equals(stamp.getString(QuestItemStamp.QUEST_STATE_ID)), "the stamp must carry the state id");
        check(stamp.getInt(QuestItemStamp.MIN_X) == 10 && stamp.getInt(QuestItemStamp.MAX_Y) == 70
                && stamp.getInt(QuestItemStamp.MAX_Z) == -10, "the stamp must carry the destroy volume");
        check(BankItemEligibility.isQuestBound(ring), "the bank must still refuse a temporary quest item");

        QuestCleanupService.cleanupStaleLocalQuestState(player, List.of(ringQuest()));
        check(count(player, "britannia_mod:one_ring") == 1, "the ring must survive a login while its quest is active");
        check(QuestItemStamp.isTemporary(onlyStack(player)), "the ring must stay stamped while its quest is active");

        QuestCleanupService.cleanupStaleLocalQuestState(player, List.of());
        check(count(player, "britannia_mod:one_ring") == 0, "the ring must be taken back once its quest left the journal");

        applyRails(player, ringGrant(null));
        check(count(player, "britannia_mod:one_ring") == 1, "precondition: the ring was granted again");
        QuestCleanupService.cleanupAfterQuestQuit(player, ringQuest());
        check(count(player, "britannia_mod:one_ring") == 0, "the ring must be taken back when its quest is quit");

        helper.succeed();
    }

    /** Rails' verdict wins over the heuristic: a delivery marked permanent is never stamped. */
    @GameTest(template = TEMPLATE)
    public static void aDeliveryMarkedPermanentIsNeverStampedEvenWhenAnObjectiveNamesIt(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        ServerQuestTable.replaceFromBootstrap(player, List.of(ringQuest()));

        applyRails(player, ringGrant(false));

        check(count(player, "britannia_mod:one_ring") == 1, "the ring was not granted");
        check(stampedStacks(player) == 0, "a delivery marked temporary=false must not be stamped");

        QuestCleanupService.cleanupStaleLocalQuestState(player, List.of());
        QuestCleanupService.cleanupAfterQuestQuit(player, ringQuest());
        check(count(player, "britannia_mod:one_ring") == 1, "a permanent delivery must survive every cleanup");

        applyRails(player, ringGrant(true));
        check(count(player, "britannia_mod:one_ring") == 2, "the second ring was not granted");
        check(stampedStacks(player) == 1, "a delivery marked temporary=true must be stamped");
        QuestCleanupService.cleanupStaleLocalQuestState(player, List.of());
        check(count(player, "britannia_mod:one_ring") == 1, "only the temporary ring may be taken back");
        check(stampedStacks(player) == 0, "the surviving ring is the permanent one");

        helper.succeed();
    }

    /**
     * Conservative compatibility: a stack stamped by the pre-M1 blanket stamp is a permanent reward.
     * The login pass strips the stamp and keeps the item, in the main inventory, the armour slots
     * and the off hand alike; only a legacy stamp that already named a destroy objective (the ring)
     * is temporary and taken back.
     */
    @GameTest(template = TEMPLATE)
    public static void legacyBlanketStampsAreStrippedAndKeptNeverDeleted(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Inventory inventory = player.getInventory();
        inventory.clearContent();

        ItemStack silver = legacyStamped(new ItemStack(ItemRegistry.SILVER_COIN.get(), 12), player, "silver_coin",
            ESCORT_QUEST_ID, ESCORT_QUEST_STATE_ID);
        ItemStack shovel = legacyStamped(new ItemStack(item("britannia_mod:britannia_shovel")), player, "britannia_shovel",
            KIT_QUEST_ID, KIT_QUEST_STATE_ID);
        ItemStack helmet = legacyStamped(new ItemStack(Items.LEATHER_HELMET), player, "minecraft:leather_helmet",
            KIT_QUEST_ID, KIT_QUEST_STATE_ID);
        ItemStack bucket = legacyStamped(new ItemStack(Items.BUCKET), player, "minecraft:bucket", 0L, "");
        ItemStack legacyRing = legacyStamped(new ItemStack(ItemRegistry.ONE_RING.get()), player, "magic_ring",
            RING_QUEST_ID, RING_QUEST_STATE_ID);
        CompoundTag ringStamp = QuestItemStamp.read(legacyRing);
        ringStamp.putString(QuestItemStamp.TRIGGER_KEY, "ring_destroyed"); // the pre-M1 destroy-objective shape
        legacyRing.set(DataComponents.CUSTOM_DATA, CustomData.of(ringStamp));
        ItemStack plainGold = new ItemStack(ItemRegistry.GOLD_COIN.get(), 4);
        ItemStack diamond = new ItemStack(Items.DIAMOND);
        CompoundTag unrelated = new CompoundTag();
        unrelated.putString("Filler", "keep");
        diamond.set(DataComponents.CUSTOM_DATA, CustomData.of(unrelated));

        inventory.items.set(0, silver);
        inventory.items.set(1, legacyRing);
        inventory.items.set(2, plainGold);
        inventory.items.set(3, diamond);
        inventory.items.set(4, bucket);
        inventory.armor.set(3, helmet);
        inventory.offhand.set(0, shovel);

        QuestCleanupService.cleanupStaleLocalQuestState(player, List.of());

        check(inventory.items.get(0) == silver && silver.getCount() == 12, "the legacy silver must be kept");
        check(!QuestItemStamp.isStamped(silver), "the legacy silver's stamp must be stripped");
        check(silver.get(DataComponents.CUSTOM_DATA) == null, "the stripped silver must merge with plain silver again");
        check(ItemStack.isSameItemSameComponents(silver, new ItemStack(ItemRegistry.SILVER_COIN.get())),
            "the stripped silver must be indistinguishable from plain silver");
        check(inventory.items.get(1).isEmpty(), "a legacy stamp that already named a destroy objective is temporary and taken back");
        check(inventory.items.get(2) == plainGold && plainGold.getCount() == 4, "an unstamped stack must be untouched");
        check(inventory.items.get(3) == diamond && "keep".equals(QuestItemStamp.read(diamond).getString("Filler")),
            "unrelated custom data must be untouched");
        check(inventory.items.get(4) == bucket && !QuestItemStamp.isStamped(bucket),
            "a legacy stamp naming no quest must be stripped, not deleted");
        check(inventory.armor.get(3) == helmet && !QuestItemStamp.isStamped(helmet), "the armour slot must be swept too");
        check(inventory.offhand.get(0) == shovel && !QuestItemStamp.isStamped(shovel), "the off hand must be swept too");

        helper.succeed();
    }

    /** Escort silver, granted before M1 and today, survives the relog after the escort is delivered. */
    @GameTest(template = TEMPLATE)
    public static void escortSilverSurvivesRelogAndQuit(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.getInventory().clearContent();
        ServerQuestTable.replaceFromBootstrap(player, List.of(escortQuest()));

        // What the pre-M1 escort turn-in left in the pack ...
        player.getInventory().items.set(0, legacyStamped(new ItemStack(ItemRegistry.SILVER_COIN.get(), 12), player,
            "silver_coin", ESCORT_QUEST_ID, ESCORT_QUEST_STATE_ID));
        // ... and what today's give_item turn-in grants: no stamp at all.
        applyRails(player, escortSilverGrant());
        check(count(player, "britannia_mod:silver_coin") == 20, "expected 20 silver, got " + counts(player));
        check(stampedStacks(player) == 1, "only the legacy stack may carry a stamp, got " + stampedStacks(player));

        QuestCleanupService.cleanupStaleLocalQuestState(player, List.of(escortQuest()));
        check(count(player, "britannia_mod:silver_coin") == 20, "silver changed by a login while escorting");

        ServerQuestTable.removeAfterRailsCompletionSuccessByQuestId(player, ESCORT_QUEST_ID);
        QuestCleanupService.cleanupStaleLocalQuestState(player, ServerQuestTable.snapshot(player));
        check(count(player, "britannia_mod:silver_coin") == 20, "escort silver was lost at the relog after delivery");
        check(stampedStacks(player) == 0, "the legacy escort stamp must be stripped at that relog");

        QuestCleanupService.cleanupAfterQuestQuit(player, escortQuest());
        check(count(player, "britannia_mod:silver_coin") == 20, "escort silver was lost at quit");

        helper.succeed();
    }

    // --- Rails responses, in the exact shape QuestProxyService hands over ----------------------

    private static void applyRails(ServerPlayer player, String body) {
        JsonObject root = JsonParser.parseString(body).getAsJsonObject();
        QuestModels.QuestResponse response = GSON.fromJson(root, QuestModels.QuestResponse.class);
        QuestRewardService.apply(player, response, root);
    }

    /** Stage-1 acceptance on today's Rails: no reward_delivery, a location objective on the destination. */
    private static String kitGrant() {
        return "{\"success\":true,\"quest_id\":" + KIT_QUEST_ID + ",\"quest_state_id\":\"" + KIT_QUEST_STATE_ID + "\","
            + "\"node\":{\"id\":1201,\"title\":\"Gather dung\",\"text\":\"Find a pile of dung.\",\"type\":\"decision\","
            + "\"metadata\":{\"location_trigger\":{\"trigger_key\":\"arrived_at_well\",\"min_x\":0,\"min_y\":0,\"min_z\":0,"
            + "\"max_x\":5,\"max_y\":5,\"max_z\":5}}},"
            + "\"choices\":[],\"granted_items\":["
            + "{\"id\":\"britannia_shovel\",\"count\":1},{\"id\":\"empty_bowl\",\"count\":2},"
            + "{\"id\":\"minecraft:bucket\",\"count\":1},{\"id\":\"watering_can\",\"count\":1},"
            + "{\"id\":\"carrot_seeds\",\"count\":1},{\"id\":\"farming_hoe\",\"count\":1},"
            + "{\"id\":\"gold_coin\",\"count\":5},{\"id\":\"silver_coin\",\"count\":3},{\"id\":\"copper_coin\",\"count\":7}],"
            + "\"client_actions\":[],\"completed\":false}";
    }

    /**
     * The ring: the destination node's destroy objective names it. With {@code temporary} null the
     * response is today's (heuristic); otherwise it carries a reward_delivery verdict. Since M3 a
     * reward_delivery is a durable delivery applied once per identity, so every call mints a
     * fresh delivery uuid: the same grant sent twice would otherwise be refused as a replay.
     */
    private static String ringGrant(Boolean temporary) {
        String delivery = temporary == null ? "" : ",\"reward_delivery\":{\"protocol_version\":1,"
            + "\"delivery_uuid\":\"" + java.util.UUID.randomUUID() + "\",\"quest_id\":" + RING_QUEST_ID + ","
            + "\"quest_state_id\":\"" + RING_QUEST_STATE_ID + "\",\"transition_key\":\"1757200000:1301:choice:accept\","
            + "\"items\":[{\"id\":\"britannia_mod:magic_ring\",\"count\":1,\"temporary\":" + temporary + "}],"
            + "\"state\":\"pending\"}";
        return "{\"success\":true,\"quest_id\":" + RING_QUEST_ID + ",\"quest_state_id\":\"" + RING_QUEST_STATE_ID + "\","
            + "\"node\":{\"id\":1301,\"title\":\"Cast it into the fire\",\"text\":\"Take the ring to the lava.\","
            + "\"type\":\"decision\",\"metadata\":{\"destroy_trigger\":{\"trigger_key\":\"ring_destroyed\","
            + "\"item_tag\":\"magic_ring\",\"min_x\":10,\"min_y\":60,\"min_z\":-20,\"max_x\":20,\"max_y\":70,\"max_z\":-10}}},"
            + "\"choices\":[],\"granted_items\":[{\"id\":\"magic_ring\",\"count\":1}]" + delivery + ","
            + "\"client_actions\":[],\"completed\":false}";
    }

    /** An escort delivered: the ending grants silver and nothing names it as an objective. */
    private static String escortSilverGrant() {
        return "{\"success\":true,\"quest_id\":" + ESCORT_QUEST_ID + ",\"quest_state_id\":\"" + ESCORT_QUEST_STATE_ID + "\","
            + "\"node\":{\"id\":1401,\"title\":\"Delivered\",\"text\":\"Thank you.\",\"type\":\"ending\",\"metadata\":{}},"
            + "\"choices\":[],\"granted_items\":[{\"id\":\"silver_coin\",\"count\":8}],"
            + "\"client_actions\":[],\"completed\":true}";
    }

    private static ClientQuestEntry kitQuest() {
        return new ClientQuestEntry(KIT_QUEST_STATE_ID, Long.toString(KIT_QUEST_ID), "rowan_farming_1",
            "Rowan", "From Soil to Supper (1 of 5): Dung", "", "", "accepted");
    }

    private static ClientQuestEntry ringQuest() {
        return new ClientQuestEntry(RING_QUEST_STATE_ID, Long.toString(RING_QUEST_ID), "cast_into_the_fire",
            "Mitexi", "Cast it into the fire", "", "", "accepted");
    }

    private static ClientQuestEntry escortQuest() {
        return new ClientQuestEntry(ESCORT_QUEST_STATE_ID, Long.toString(ESCORT_QUEST_ID), "escort_britain_to_vesper",
            "Mitexi", "Escort to Vesper", "", "", "accepted");
    }

    // --- inventory helpers ----------------------------------------------------------------------

    /** The pre-M1 blanket stamp, exactly as {@code QuestRewardService#stamp} wrote it. */
    private static ItemStack legacyStamped(ItemStack stack, ServerPlayer player, String itemId, long questId, String questStateId) {
        CompoundTag tag = new CompoundTag();
        tag.putString(QuestItemStamp.ITEM, itemId);
        tag.putString(QuestItemStamp.OWNER_UUID, player.getStringUUID());
        tag.putString(QuestItemStamp.OWNER_NAME, player.getGameProfile().getName());
        if (questId > 0) tag.putLong(QuestItemStamp.QUEST_ID, questId);
        if (!questStateId.isBlank()) tag.putString(QuestItemStamp.QUEST_STATE_ID, questStateId);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

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

    private static int stampedStacks(ServerPlayer player) {
        int stamped = 0;
        for (ItemStack stack : carried(player)) {
            if (!stack.isEmpty() && QuestItemStamp.isStamped(stack)) stamped++;
        }
        return stamped;
    }

    private static ItemStack onlyStack(ServerPlayer player) {
        ItemStack found = ItemStack.EMPTY;
        for (ItemStack stack : carried(player)) {
            if (stack.isEmpty()) continue;
            if (!found.isEmpty()) throw new GameTestAssertException("expected exactly one stack, found " + counts(player));
            found = stack;
        }
        if (found.isEmpty()) throw new GameTestAssertException("expected exactly one stack, found none");
        return found;
    }

    private static List<ItemStack> carried(ServerPlayer player) {
        Inventory inventory = player.getInventory();
        List<ItemStack> all = new java.util.ArrayList<>(inventory.items);
        all.addAll(inventory.armor);
        all.addAll(inventory.offhand);
        return all;
    }

    private static net.minecraft.world.item.Item item(String id) {
        return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
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
