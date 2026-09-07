package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonParser;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.bank.item.BankItemCodec;
import com.seggellion.britannia_mod.economy.*;
import com.seggellion.britannia_mod.farming.*;
import com.seggellion.britannia_mod.service.AcceptedCommodityPolicy;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.*;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.*;

@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class GameplayProduceCommodityGameTests {
    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    @GameTest(template = TEMPLATE)
    public static void everyCropHasAnExplicitPolicyAndExactHarvestIdentity(GameTestHelper h) throws Exception {
        var manifest = ProduceCommodityManifest.entries();
        var byCrop = new HashMap<String, ProduceCommodityManifest.Entry>();
        for (var entry : manifest) {
            var id = net.minecraft.resources.ResourceLocation.parse(entry.itemId());
            h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "unregistered produce " + id);
            for (String crop : entry.cropIds())
                h.assertTrue(byCrop.put(crop, entry) == null, "duplicate crop " + crop);
        }
        Set<String> excluded;
        try (var stream = GameplayProduceCommodityGameTests.class.getResourceAsStream("/economy/supported_produce.json")) {
            excluded = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("excluded_crop_ids").keySet();
        }
        var observed = new HashSet<String>();
        for (var crop : CropRegistry.all()) {
            observed.add(crop.id());
            var entry = byCrop.get(crop.id());
            h.assertTrue((entry != null) != excluded.contains(crop.id()), "unaccounted or ambiguous crop: " + crop.id());
            var stack = new ItemStack(crop.harvestItem().get());
            var mapped = CommodityMappings.forStack(stack);
            if (entry != null) {
                h.assertTrue(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().equals(entry.itemId()),
                        "harvest identity drift: " + crop.id());
                h.assertTrue(mapped.isPresent() && mapped.get().category().equals("produce"), "missing produce " + crop.id());
            } else h.assertTrue(mapped.isEmpty() || !mapped.get().category().equals("produce"), "excluded output accepted: " + crop.id());
            // Native carrot/potato are intentional produce-as-seed exceptions. Custom packets are excluded.
            if (crop.seedItem().get() != crop.harvestItem().get()) {
                var seed = CommodityMappings.forStack(new ItemStack(crop.seedItem().get()));
                h.assertTrue(seed.isEmpty() || !seed.get().category().equals("produce"), "custom seed priced as produce " + crop.id());
            }
        }
        var accounted = new HashSet<>(byCrop.keySet()); accounted.addAll(excluded);
        h.assertTrue(observed.equals(accounted), "stale manifest crop identity");
        h.succeed();
    }

    @GameTest(template = TEMPLATE)
    public static void harvestDefaultAndAdminComponentsRetainOneIdentityAndRefundExactly(GameTestHelper h) {
        var policy = new AcceptedCommodityPolicy(List.of(new AcceptedCommodityPolicy.Entry("produce", null, null)));
        var player = h.makeMockServerPlayerInLevel();
        for (var crop : CropRegistry.all()) {
            if (!Set.of("broccoli", "orange", "carrot", "apple").contains(crop.id())) continue;
            for (int variant = 0; variant < 3; variant++) {
                var stack = new ItemStack(crop.harvestItem().get(), 3);
                if (variant == 1) CropQualityCalculator.applyQuality(stack, crop, 83);
                if (variant == 2) {
                    var tag = new CompoundTag(); tag.putString("AdminProvenance", "M7"); tag.putInt("QualityScore", 100);
                    stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
                }
                var snapshot = stack.copy();
                var described = ServerEconomyService.describeSaleItem(stack);
                var mapping = CommodityMappings.forStack(stack).orElseThrow();
                h.assertTrue(policy.accepts(described.get("category").getAsString(),
                        described.get("subcategory").getAsString(), described.get("item_name").getAsString()), "components filtered " + crop.id());
                h.assertTrue(described.get("item_name").getAsString().equals(mapping.itemName()), "serializer identity changed");
                h.assertTrue(ItemStack.matches(snapshot, stack), "classification mutated stack");
                player.getInventory().clearContent();
                String key = "sale:m7-components:" + UUID.randomUUID();
                var store = TraderSaleReservationStore.get(h.getLevel());
                store.record(new TraderSaleReservationReceipt(key, player.getUUID(),
                        List.of(BankItemCodec.serialize(stack, h.getLevel().registryAccess())),
                        TraderSaleReservationReceipt.Status.DISPATCHED, System.currentTimeMillis()));
                h.assertTrue(TraderSaleReservationRecovery.refundStrandedReservations(h.getLevel(), player) == 1, "refund missing");
                h.assertTrue(ItemStack.matches(snapshot, player.getInventory().getItem(0)), "refund changed count/components");
                h.assertTrue(TraderSaleReservationRecovery.refundStrandedReservations(h.getLevel(), player) == 0, "refund replay minted items");
            }
        }
        for (var id : List.of("minecraft:wheat", "britannia_mod:broccoli_seeds", "britannia_mod:garlic",
                "britannia_mod:cotton", "britannia_mod:tobacco", "minecraft:bread")) {
            var stack = new ItemStack(BuiltInRegistries.ITEM.get(net.minecraft.resources.ResourceLocation.parse(id)));
            h.assertTrue(!stack.isEmpty(), "invalid negative fixture " + id);
            var row = ServerEconomyService.describeSaleItem(stack);
            h.assertTrue(!row.has("category") || !row.get("category").getAsString().equals("produce"), "excluded goods offered " + id);
        }
        h.succeed();
    }
}

