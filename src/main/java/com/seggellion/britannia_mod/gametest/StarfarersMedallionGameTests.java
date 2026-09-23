package com.seggellion.britannia_mod.gametest;

import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.item.StarfarersMedallionItem;
import com.seggellion.britannia_mod.sync.BlessedItemInventorySync;
import com.seggellion.britannia_mod.sync.BlessedItemSyncAPI;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceiptStatus;
import com.seggellion.britannia_mod.blessed.delivery.BlessedDeliveryReceipts;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.bank.item.BankItemDecodeResult;
import com.seggellion.britannia_mod.block.entity.DisplayCaseBlockEntity;
import com.seggellion.britannia_mod.registry.BlockRegistry;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemExpireEvent;

import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.List;

/**
 * Starfarer M5 gate: the medallion resolves and is usable in a running game.
 *
 * <p>The contract test proves the class and its assets in plain JUnit. This proves the
 * thing the gate actually asks for -- that a real server, having loaded the mod,
 * resolves {@code britannia_mod:starfarers_medallion} from the live registry and can put
 * one in a real player's hands.
 *
 * <p>Deliberately no delivery here. Nothing in M5 fetches from Rails, mints an
 * instance_uuid or writes a receipt; the medallion is stamped by hand below exactly as
 * BlessedItemInventorySync would stamp it, purely to show the item carries that identity
 * like any other blessed item.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class StarfarersMedallionGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    @GameTest(template = TEMPLATE)
    public static void theMedallionResolvesFromTheLiveRegistry(GameTestHelper helper) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(
                BritanniaMod.MODID, "starfarers_medallion");

        Item resolved = BuiltInRegistries.ITEM.get(id);

        check(resolved == ItemRegistry.STARFARERS_MEDALLION.get(),
                "the live registry resolves the same item the mod registered");
        check(BuiltInRegistries.ITEM.getKey(resolved).equals(id),
                "and it round trips back to britannia_mod:starfarers_medallion");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void aPlayerCanHoldOneAndItDoesNotStack(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Item medallion = ItemRegistry.STARFARERS_MEDALLION.get();

        player.getInventory().add(new ItemStack(medallion));
        player.getInventory().add(new ItemStack(medallion));

        long slots = player.getInventory().items.stream()
                .filter(stack -> stack.is(medallion))
                .count();
        int total = player.getInventory().items.stream()
                .filter(stack -> stack.is(medallion))
                .mapToInt(ItemStack::getCount)
                .sum();

        check(total == 2, "both medallions are present, found " + total);
        check(slots == 2,
                "two medallions must occupy two slots rather than merging into one -- a "
                        + "merged stack is how a duplicate hides. Found " + slots + " slot(s).");
        helper.succeed();
    }

    /**
     * The blessed stamp travels on the medallion like any other blessed item. Written by
     * hand here, not by the sync, because M5 delivers nothing.
     */
    @GameTest(template = TEMPLATE)
    public static void itCarriesBlessedIdentityLikeAnyOtherBlessedItem(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();

        ItemStack stack = new ItemStack(ItemRegistry.STARFARERS_MEDALLION.get());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", player.getStringUUID());
        tag.putString("deed_id", "entitlement-uuid");
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        player.getInventory().add(stack);

        ItemStack held = player.getInventory().items.stream()
                .filter(candidate -> candidate.is(ItemRegistry.STARFARERS_MEDALLION.get()))
                .findFirst()
                .orElseThrow();
        CustomData data = held.get(DataComponents.CUSTOM_DATA);

        check(data != null, "the medallion kept its blessed data");
        check(data.copyTag().getBoolean("blessed"), "blessed flag survived");
        check(player.getStringUUID().equals(data.copyTag().getString("owner")), "owner survived");
        check("entitlement-uuid".equals(data.copyTag().getString("deed_id")), "deed_id survived");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void ownerRightClickEquipsOneAndUnequipPreservesItsIdentity(GameTestHelper helper) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(
                helper.getLevel(), "medallion-owner");
        StarfarersMedallionItem medallion =
                (StarfarersMedallionItem) ItemRegistry.STARFARERS_MEDALLION.get();
        UUID instance = UUID.randomUUID();
        ItemStack original = stamped(player.getUUID(), instance);
        player.setItemInHand(InteractionHand.MAIN_HAND, original);

        check(medallion.canEquip(original, EquipmentSlot.CHEST, player),
                "the stamped owner can place the medallion in CHEST");
        check(player.inventoryMenu.getSlot(6).mayPlace(original),
                "the owner's armor slot accepts the medallion");

        InteractionResultHolder<ItemStack> result = medallion.use(
                player.level(), player, InteractionHand.MAIN_HAND);
        player.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
        ItemStack worn = player.getItemBySlot(EquipmentSlot.CHEST);

        check(result.getResult().consumesAction(), "owner right-click equips successfully");
        check(player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "the hand source clears when CHEST was empty");
        check(worn.is(medallion) && worn.getCount() == 1,
                "CHEST receives exactly one medallion");
        check(countCarried(player, medallion) == 1, "equipping makes no duplicate");
        checkIdentity(worn, player.getUUID(), instance);
        check(diagnosticSees(player, instance), "the active-item diagnostic sees worn armor");

        ItemStack removed = player.inventoryMenu.quickMoveStack(player, 6);
        check(removed.is(medallion), "shift-click removes the medallion from CHEST");
        check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "CHEST clears on unequip");
        ItemStack carried = player.getInventory().items.stream()
                .filter(stack -> stack.is(medallion)).findFirst().orElse(ItemStack.EMPTY);
        checkIdentity(carried, player.getUUID(), instance);
        check(countCarried(player, medallion) == 1, "unequipping makes no duplicate");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void nonOwnerCannotEquipByRightClickDragOrShiftClick(GameTestHelper helper) {
        ServerPlayer player = ManagedResourceTestPlayers.survival(
                helper.getLevel(), "medallion-stranger");
        StarfarersMedallionItem medallion =
                (StarfarersMedallionItem) ItemRegistry.STARFARERS_MEDALLION.get();
        UUID instance = UUID.randomUUID();
        ItemStack foreign = stamped(UUID.randomUUID(), instance);
        player.setItemInHand(InteractionHand.MAIN_HAND, foreign);

        check(!medallion.canEquip(foreign, EquipmentSlot.CHEST, player),
                "a non-owner cannot use the CHEST equip hook");
        check(!player.inventoryMenu.getSlot(6).mayPlace(foreign),
                "armor-slot drag refuses a non-owner");
        InteractionResultHolder<ItemStack> result = medallion.use(
                player.level(), player, InteractionHand.MAIN_HAND);
        check(result.getResult() == InteractionResult.FAIL, "non-owner right-click is refused");
        check(player.getItemInHand(InteractionHand.MAIN_HAND).is(foreign.getItem()),
                "refusal leaves the source in hand");
        check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "CHEST stays empty");

        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.getInventory().setItem(9, foreign);
        player.inventoryMenu.quickMoveStack(player, 9);
        check(player.getItemBySlot(EquipmentSlot.CHEST).isEmpty(),
                "shift-click cannot bypass the armor slot gate");
        check(countCarried(player, medallion) == 1, "refusal makes no duplicate or loss");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wornActiveMaterializationCannotDeliverAgain(GameTestHelper helper) {
        ServerPlayer owner = ManagedResourceTestPlayers.survival(helper.getLevel(), "worn-active-owner");
        owner.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        BlessedItemSyncAPI.BlessedRow pending = row(instance, "pending");
        BlessedItemInventorySync.apply(owner, List.of(pending));

        int slot = firstMedallionSlot(owner);
        check(slot >= 0, "pending materialization delivered a medallion");
        ItemStack delivered = owner.getInventory().removeItem(slot, 1);
        checkIdentity(delivered, owner.getUUID(), instance);
        wear(owner, delivered);
        check(countCarried(owner, ItemRegistry.STARFARERS_MEDALLION.get()) == 1,
                "equipping the delivered instance keeps exactly one copy");
        check(BlessedDeliveryReceipts.find(owner.serverLevel(), instance)
                        .orElseThrow().status() == BlessedDeliveryReceiptStatus.DELIVERED,
                "the delivery receipt remains DELIVERED while worn");

        BlessedItemInventorySync.apply(owner, List.of(pending));
        BlessedItemInventorySync.apply(owner, List.of(row(instance, "active")));
        check(countCarried(owner, ItemRegistry.STARFARERS_MEDALLION.get()) == 1,
                "neither pending replay nor ACTIVE creates another medallion");
        checkIdentity(owner.getItemBySlot(EquipmentSlot.CHEST), owner.getUUID(), instance);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void armorDiagnosticHasANegativeControl(GameTestHelper helper) {
        ServerPlayer owner = ManagedResourceTestPlayers.survival(helper.getLevel(), "worn-diagnostic-owner");
        owner.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        ItemStack worn = wear(owner, stamped(owner.getUUID(), instance));

        check(diagnosticSees(owner, instance), "diagnostic sees the armor-worn instance");
        owner.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);
        check(!diagnosticSees(owner, instance),
                "negative control: removing the only copy makes diagnostic visibility false");
        owner.setItemSlot(EquipmentSlot.CHEST, worn);
        check(diagnosticSees(owner, instance), "restoring CHEST restores diagnostic visibility");
        check(countCarried(owner, ItemRegistry.STARFARERS_MEDALLION.get()) == 1,
                "the diagnostic test makes no duplicate");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void chestOriginDropIsRescuedToItsStampedOwner(GameTestHelper helper) {
        ServerPlayer owner = ManagedResourceTestPlayers.survival(helper.getLevel(), "worn-rescue-owner");
        owner.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        ItemStack worn = wear(owner, stamped(owner.getUUID(), instance));
        owner.setItemSlot(EquipmentSlot.CHEST, ItemStack.EMPTY);

        ItemEntity dropped = owner.drop(worn, false);
        check(dropped != null && !dropped.isRemoved(), "CHEST-origin item became a world drop");
        checkIdentity(dropped.getItem(), owner.getUUID(), instance);
        NeoForge.EVENT_BUS.post(new ItemExpireEvent(dropped));

        check(dropped.isRemoved(), "rescue consumed the dropped entity");
        check(countCarried(owner, ItemRegistry.STARFARERS_MEDALLION.get()) == 1,
                "exactly one medallion returns to its owner");
        checkIdentity(carriedMedallion(owner), owner.getUUID(), instance);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void deathDropsTheWornMedallionWithItsIdentity(GameTestHelper helper) {
        ServerPlayer owner = ManagedResourceTestPlayers.survival(helper.getLevel(), "worn-death-owner");
        owner.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        wear(owner, stamped(owner.getUUID(), instance));

        owner.die(owner.damageSources().generic());
        List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                owner.getBoundingBox().inflate(5.0D), entity ->
                        entity.getItem().is(ItemRegistry.STARFARERS_MEDALLION.get()));
        check(drops.size() == 1, "death creates exactly one worn medallion drop, found " + drops.size());
        checkIdentity(drops.get(0).getItem(), owner.getUUID(), instance);
        drops.get(0).discard();
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wornMedallionSurvivesTheBankCodec(GameTestHelper helper) {
        ServerPlayer owner = ManagedResourceTestPlayers.survival(helper.getLevel(), "worn-bank-owner");
        owner.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        wear(owner, stamped(owner.getUUID(), instance));
        ItemStack removed = owner.inventoryMenu.quickMoveStack(owner, 6);
        check(owner.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "bank source is unequipped");
        checkIdentity(removed, owner.getUUID(), instance);

        byte[] payload = BankItemCodec.serialize(removed, helper.getLevel().registryAccess());
        BankItemDecodeResult decoded = BankItemCodec.deserialize(
                payload, helper.getLevel().registryAccess());
        check(decoded instanceof BankItemDecodeResult.Success, "bank codec decodes the medallion");
        ItemStack restored = ((BankItemDecodeResult.Success) decoded).stack();
        check(restored.is(ItemRegistry.STARFARERS_MEDALLION.get()), "bank restores the same item type");
        checkIdentity(restored, owner.getUUID(), instance);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void wornMedallionSurvivesDisplayCaseSaveAndRetrieve(GameTestHelper helper) {
        ServerPlayer owner = ManagedResourceTestPlayers.survival(helper.getLevel(), "worn-case-owner");
        owner.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        wear(owner, stamped(owner.getUUID(), instance));
        ItemStack removed = owner.inventoryMenu.quickMoveStack(owner, 6);
        check(owner.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "case source is unequipped");

        BlockPos pos = helper.absolutePos(new BlockPos(1, 2, 1));
        var state = BlockRegistry.DISPLAY_CASE.get().defaultBlockState();
        DisplayCaseBlockEntity source = new DisplayCaseBlockEntity(pos, state);
        check(source.storeOne(removed), "the generic case stores the worn medallion");
        DisplayCaseBlockEntity reloaded = new DisplayCaseBlockEntity(pos, state);
        reloaded.loadWithComponents(source.saveWithoutMetadata(helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess());
        ItemStack retrieved = reloaded.takeDisplayedItem();

        check(retrieved.is(ItemRegistry.STARFARERS_MEDALLION.get()),
                "the case retrieves the same medallion type");
        checkIdentity(retrieved, owner.getUUID(), instance);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void foreignHolderCannotWearAndRescueStillBelongsToOwner(GameTestHelper helper) {
        ServerPlayer owner = ManagedResourceTestPlayers.survival(helper.getLevel(), "worn-cross-owner");
        ServerPlayer holder = ManagedResourceTestPlayers.survival(helper.getLevel(), "worn-cross-holder");
        owner.getInventory().clearContent();
        holder.getInventory().clearContent();
        UUID instance = UUID.randomUUID();
        ItemStack foreign = stamped(owner.getUUID(), instance);
        holder.setItemInHand(InteractionHand.MAIN_HAND, foreign);
        StarfarersMedallionItem medallion = (StarfarersMedallionItem) foreign.getItem();

        check(!medallion.canEquip(foreign, EquipmentSlot.CHEST, holder),
                "B cannot wear A's medallion");
        check(medallion.use(holder.level(), holder, InteractionHand.MAIN_HAND)
                        .getResult() == InteractionResult.FAIL,
                "B's right-click equip is denied");
        check(holder.getItemBySlot(EquipmentSlot.CHEST).isEmpty(), "B's CHEST stays empty");
        checkIdentity(holder.getItemInHand(InteractionHand.MAIN_HAND), owner.getUUID(), instance);

        holder.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        ItemEntity dropped = holder.drop(foreign, false);
        check(dropped != null, "B can drop the foreign item without changing its owner stamp");
        NeoForge.EVENT_BUS.post(new ItemExpireEvent(dropped));
        check(dropped.isRemoved(), "rescue consumes B's drop");
        check(countCarried(holder, medallion) == 0, "B receives no rescued copy");
        check(countCarried(owner, medallion) == 1, "rescue responsibility remains A's");
        checkIdentity(carriedMedallion(owner), owner.getUUID(), instance);
        helper.succeed();
    }

    private static BlessedItemSyncAPI.BlessedRow row(UUID instance, String state) {
        return new BlessedItemSyncAPI.BlessedRow("britannia_mod:starfarers_medallion",
                "wearable-deed", false, instance.toString(), state);
    }

    private static ItemStack wear(ServerPlayer owner, ItemStack stack) {
        owner.setItemInHand(InteractionHand.MAIN_HAND, stack);
        StarfarersMedallionItem medallion = (StarfarersMedallionItem) stack.getItem();
        InteractionResultHolder<ItemStack> result = medallion.use(
                owner.level(), owner, InteractionHand.MAIN_HAND);
        owner.setItemInHand(InteractionHand.MAIN_HAND, result.getObject());
        check(result.getResult().consumesAction(), "owner equips through the real item path");
        check(owner.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "equip clears the hand");
        return owner.getItemBySlot(EquipmentSlot.CHEST);
    }

    private static int firstMedallionSlot(ServerPlayer player) {
        for (int i = 0; i < player.getInventory().items.size(); i++) {
            if (player.getInventory().items.get(i).is(ItemRegistry.STARFARERS_MEDALLION.get())) return i;
        }
        return -1;
    }

    private static ItemStack carriedMedallion(ServerPlayer player) {
        return player.getInventory().items.stream()
                .filter(stack -> stack.is(ItemRegistry.STARFARERS_MEDALLION.get()))
                .findFirst().orElse(ItemStack.EMPTY);
    }

    private static ItemStack stamped(UUID owner, UUID instance) {
        ItemStack stack = new ItemStack(ItemRegistry.STARFARERS_MEDALLION.get());
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("blessed", true);
        tag.putString("owner", owner.toString());
        tag.putString("deed_id", "wearable-deed");
        tag.putString("instance_uuid", instance.toString());
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        return stack;
    }

    private static void checkIdentity(ItemStack stack, UUID owner, UUID instance) {
        check(!stack.isEmpty(), "the medallion remains present");
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        check(data != null, "the blessed custom data survives");
        CompoundTag tag = data.copyTag();
        check(tag.getBoolean("blessed"), "blessed=true survives");
        check(owner.toString().equals(tag.getString("owner")), "owner survives");
        check("wearable-deed".equals(tag.getString("deed_id")), "deed_id survives");
        check(instance.toString().equals(tag.getString("instance_uuid")), "instance_uuid survives");
    }

    private static long countCarried(ServerPlayer player, Item medallion) {
        return java.util.stream.Stream.of(player.getInventory().items,
                        player.getInventory().offhand, player.getInventory().armor)
                .flatMap(java.util.List::stream)
                .filter(stack -> stack.is(medallion))
                .mapToInt(ItemStack::getCount).sum();
    }

    private static boolean diagnosticSees(ServerPlayer player, UUID instance) {
        try {
            Method diagnostic = BlessedItemInventorySync.class.getDeclaredMethod(
                    "visibleInCarriedInventory", ServerPlayer.class, UUID.class);
            diagnostic.setAccessible(true);
            return (boolean) diagnostic.invoke(null, player, instance);
        } catch (ReflectiveOperationException failure) {
            throw new GameTestAssertException("Could not inspect blessed visibility diagnostic: "
                    + failure);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
