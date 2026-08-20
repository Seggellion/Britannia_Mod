package com.seggellion.britannia_mod.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.seggellion.britannia_mod.BritanniaMod;
import com.seggellion.britannia_mod.economy.EconomicVendorPurchaseService;
import com.seggellion.britannia_mod.economy.ServerCatalogService;
import com.seggellion.britannia_mod.entity.CitizenEntity;
import com.seggellion.britannia_mod.registry.EntityRegistry;
import com.seggellion.britannia_mod.registry.ItemRegistry;
import com.seggellion.britannia_mod.shop.Product;
import com.seggellion.britannia_mod.structure.HouseStyle;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Milestone 7: the Architect sells deeds through the ordinary economic vendor path.
 *
 * <p>The claim being tested is a negative one -- that no housing-specific vendor code was
 * needed. An Architect stamped by the assignment reconciler is an economic vendor by the same
 * three fields every other economic vendor is one by, its catalogue is the same catalogue, its
 * settlement is the same settlement, and a deed is just a product whose item id happens to be a
 * deed. So these tests exercise the generic machinery with the Architect's own data rather than
 * anything built for it.
 *
 * <p>Rails owns price, availability and recipes. Nothing here asserts what a villa costs; it
 * asserts that whatever Rails says survives the trip intact -- which for a house is a six- or
 * seven-figure number, in gold, and both of those are ways a price can quietly go wrong.
 */
@GameTestHolder(BritanniaMod.MODID)
@PrefixGameTestTemplate(false)
public final class ArchitectDeedVendorGameTests {

    private static final String TEMPLATE = "service_npc_spawn_test_empty";

    /** The Rails economic type. Named once, because the whole mechanism is this string. */
    private static final String ARCHITECT_VENDOR = "architect_vendor";

    /* ------------------------------------------------------------------ */
    /*  The deed roster                                                    */
    /* ------------------------------------------------------------------ */

    /**
     * Every deed Rails sells resolves to a real item here.
     *
     * <p>The roster is taken from {@link HouseStyle}, which is the same table the deed items are
     * registered from, so this cannot drift by someone forgetting to update a list. What it
     * catches is the id itself changing shape -- a structure stub renamed, a suffix altered --
     * because Rails names its products by that exact id and the catalogue silently drops a row
     * whose item does not resolve. A renamed deed would not error; the Architect would simply
     * stop selling that house.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void everyhousestylehasadeeditemrailscanname(GameTestHelper helper) {
        List<String> missing = new ArrayList<>();
        for (HouseStyle style : HouseStyle.values()) {
            String itemId = railsItemIdFor(style);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
            if (item == Items.AIR) missing.add(itemId);
        }
        if (!missing.isEmpty()) {
            throw new GameTestAssertException("Rails sells deeds no item here answers to: " + missing);
        }
        if (HouseStyle.values().length != 10) {
            throw new GameTestAssertException(
                    "the deed roster is " + HouseStyle.values().length + " styles, not the ten Rails "
                    + "seeded products for; one side has gained or lost a house");
        }
        helper.succeed();
    }

    /** The registry agrees with the factory that built it, for every style. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void thedeedregistryagreeswiththerailsidscheme(GameTestHelper helper) {
        for (HouseStyle style : HouseStyle.values()) {
            Item registered = ItemRegistry.deedFor(style);
            Item byRailsId = BuiltInRegistries.ITEM.get(ResourceLocation.parse(railsItemIdFor(style)));
            if (registered != byRailsId) {
                throw new GameTestAssertException(style + " resolves to " + registered
                        + " through the style table but to " + byRailsId + " through the Rails id");
            }
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Routing                                                           */
    /* ------------------------------------------------------------------ */

    /**
     * A stamped Architect is an economic vendor by the same test every other one passes.
     *
     * <p>All three fields matter and the reconciler sets all three together. Two of them route
     * the catalogue; the third is also required before settlement will touch it, which is why an
     * Architect that had only been given a type key would show a shop and then refuse to sell.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void astampedarchitectisaneconomicvendor(GameTestHelper helper) {
        CitizenEntity architect = spawnArchitect(helper);

        if (EconomicVendorPurchaseService.isEconomicVendor(architect)) {
            throw new GameTestAssertException(
                    "an unstamped Architect already counts as an economic vendor, so the stamp is "
                    + "not what decides it");
        }

        architect.setWorldNpcPublicId(UUID.randomUUID());
        architect.setEconomicNpcTypeKey(ARCHITECT_VENDOR);
        architect.setEconomicCityPublicId(UUID.randomUUID());

        if (!EconomicVendorPurchaseService.isEconomicVendor(architect)) {
            throw new GameTestAssertException(
                    "a fully stamped Architect is not recognised as an economic vendor, so its "
                    + "purchases would fall through to the legacy merchant path and fail");
        }
        if (!ARCHITECT_VENDOR.equals(architect.getEconomicNpcTypeKey())) {
            throw new GameTestAssertException("the type key did not survive being stamped");
        }
        helper.succeed();
    }

    /**
     * A partly stamped Architect is not an economic vendor.
     *
     * <p>Worth pinning separately: the catalogue routes on two fields and settlement on three, so
     * a missing world-NPC id is exactly the shape of bug that shows a full shop and then refuses
     * every purchase in it.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void ahalfstampedarchitectisnotaneconomicvendor(GameTestHelper helper) {
        CitizenEntity architect = spawnArchitect(helper);
        architect.setEconomicNpcTypeKey(ARCHITECT_VENDOR);
        architect.setEconomicCityPublicId(UUID.randomUUID());

        if (EconomicVendorPurchaseService.isEconomicVendor(architect)) {
            throw new GameTestAssertException(
                    "an Architect with no world-NPC id was accepted for settlement");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  The catalogue Rails sends                                          */
    /* ------------------------------------------------------------------ */

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anunavailabledeedisnotofferedforsale(GameTestHelper helper) {
        JsonObject response = catalogue(
                row("britannia_mod:two_story_villa_deed", true, 136_500.0, "gold", 3),
                unavailableRow("britannia_mod:stone_keep_deed", "insufficient_commodity_supply"));

        List<Product> products = ServerCatalogService.parseEconomicCatalogRows(response);

        if (products.size() != 1) {
            throw new GameTestAssertException(
                    "expected one purchasable deed, got " + describe(products));
        }
        if (!products.get(0).itemId().equals("britannia_mod:two_story_villa_deed")) {
            throw new GameTestAssertException("the wrong deed survived: " + describe(products));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void deedsareindependentlyavailable(GameTestHelper helper) {
        JsonObject response = catalogue(
                row("britannia_mod:wooden_house_deed", true, 43_800.0, "gold", 1),
                unavailableRow("britannia_mod:large_patio_deed", "insufficient_commodity_supply"),
                row("britannia_mod:castle_deed", true, 1_022_800.0, "gold", 1),
                unavailableRow("britannia_mod:stone_keep_deed", "insufficient_commodity_supply"));

        List<Product> products = ServerCatalogService.parseEconomicCatalogRows(response);
        if (products.size() != 2) {
            throw new GameTestAssertException("expected two of four deeds, got " + describe(products));
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void afullyunavailablecataloguesellsnothingandthrowsnothing(GameTestHelper helper) {
        JsonObject allRefused = catalogue(
                unavailableRow("britannia_mod:two_story_villa_deed", "insufficient_commodity_supply"),
                unavailableRow("britannia_mod:castle_deed", "insufficient_commodity_supply"));

        if (!ServerCatalogService.parseEconomicCatalogRows(allRefused).isEmpty()) {
            throw new GameTestAssertException("a catalogue Rails refused entirely still offered rows");
        }
        if (!ServerCatalogService.parseEconomicCatalogRows(catalogue()).isEmpty()) {
            throw new GameTestAssertException("an empty catalogue produced rows");
        }
        if (!ServerCatalogService.parseEconomicCatalogRows(new JsonObject()).isEmpty()) {
            throw new GameTestAssertException("a response with no rows array was not handled");
        }
        helper.succeed();
    }

    /** A deed id nothing here answers to is dropped rather than shown as a missing item. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void anunknownitemidisdroppedratherthanshown(GameTestHelper helper) {
        JsonObject response = catalogue(
                row("britannia_mod:no_such_deed", true, 1.0, "gold", 1),
                row("britannia_mod:castle_deed", true, 1_022_800.0, "gold", 1));

        List<Product> products = ServerCatalogService.parseEconomicCatalogRows(response);
        if (products.size() != 1 || !products.get(0).itemId().equals("britannia_mod:castle_deed")) {
            throw new GameTestAssertException("unknown ids were not filtered: " + describe(products));
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Gold, and large numbers                                            */
    /* ------------------------------------------------------------------ */

    /**
     * Prices survive the trip exactly, denomination included.
     *
     * <p>Rails sends {@code unit_price} as a JSON float. A house is the first product expensive
     * enough for that to matter: the castle at 1,022,800 is seven digits, and a denomination
     * dropped anywhere on the way is read as copper, which would price a villa deed at 136,500
     * copper instead of gold.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void goldpricessurviveexactlyincludingsevenfigures(GameTestHelper helper) {
        record Expected(String itemId, int price) {}
        List<Expected> expected = List.of(
                new Expected("britannia_mod:wooden_house_deed", 43_800),
                new Expected("britannia_mod:two_story_villa_deed", 136_500),
                new Expected("britannia_mod:large_patio_deed", 152_800),
                new Expected("britannia_mod:stone_keep_deed", 665_200),
                new Expected("britannia_mod:castle_deed", 1_022_800));

        JsonArray rows = new JsonArray();
        for (Expected e : expected) rows.add(rowObject(e.itemId(), true, e.price(), "gold", 1));
        JsonObject response = new JsonObject();
        response.add("rows", rows);

        List<Product> products = ServerCatalogService.parseEconomicCatalogRows(response);
        if (products.size() != expected.size()) {
            throw new GameTestAssertException("lost rows: " + describe(products));
        }
        for (int index = 0; index < expected.size(); index++) {
            Product product = products.get(index);
            Expected want = expected.get(index);
            if (product.price() != want.price()) {
                throw new GameTestAssertException(want.itemId() + " arrived at " + product.price()
                        + " instead of " + want.price());
            }
            if (!"gold".equals(product.currency())) {
                throw new GameTestAssertException(want.itemId() + " arrived denominated in "
                        + product.currency() + " rather than gold");
            }
        }
        helper.succeed();
    }

    /** A row with no denomination falls back to copper -- pinned so the fallback stays visible. */
    @GameTest(template = TEMPLATE, timeoutTicks = 40)
    public static void amissingdenominationfallsbacktocopper(GameTestHelper helper) {
        JsonObject bare = rowObject("britannia_mod:castle_deed", true, 1_022_800, null, 1);
        JsonArray rows = new JsonArray();
        rows.add(bare);
        JsonObject response = new JsonObject();
        response.add("rows", rows);

        List<Product> products = ServerCatalogService.parseEconomicCatalogRows(response);
        if (products.size() != 1 || !"copper".equals(products.get(0).currency())) {
            throw new GameTestAssertException(
                    "the copper fallback changed. Rails always sends a denomination for economic "
                    + "rows, so this only fires if the wire contract has moved: " + describe(products));
        }
        helper.succeed();
    }

    /**
     * A gold-priced deed is paid for in gold and in nothing else.
     *
     * <p>The settlement rule is the generic one -- no automatic conversion between denominations
     * -- but a house is the first thing whose price makes the distinction expensive.
     */
    @GameTest(template = TEMPLATE, timeoutTicks = 60)
    public static void agolddeedisboughtwithgoldandnotwithsilver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer buyer = FakePlayerFactory.get(
                level, new GameProfile(UUID.randomUUID(), "m7-buyer"));
        buyer.getInventory().clearContent();

        buyer.getInventory().add(new ItemStack(ItemRegistry.SILVER_COIN.get(), 64));
        if (EconomicVendorPurchaseService.reserveExactDenomination(buyer, "gold", 10)) {
            throw new GameTestAssertException("silver paid for a gold-priced deed");
        }

        buyer.getInventory().add(new ItemStack(ItemRegistry.GOLD_COIN.get(), 64));
        if (!EconomicVendorPurchaseService.reserveExactDenomination(buyer, "gold", 10)) {
            throw new GameTestAssertException("gold could not pay a gold price");
        }
        if (EconomicVendorPurchaseService.countDenomination(buyer, "silver") != 64) {
            throw new GameTestAssertException("the silver was touched to fund a gold purchase");
        }
        helper.succeed();
    }

    /* ------------------------------------------------------------------ */
    /*  Fixtures                                                           */
    /* ------------------------------------------------------------------ */

    /** The id Rails names a deed product by: britannia_mod:&lt;structure stub&gt;_deed. */
    private static String railsItemIdFor(HouseStyle style) {
        return "britannia_mod:" + style.getStructureFile().replace(".nbt", "") + "_deed";
    }

    private static CitizenEntity spawnArchitect(GameTestHelper helper) {
        var architect = EntityRegistry.ARCHITECT_ENTITY.get().create(helper.getLevel());
        if (architect == null) throw new GameTestAssertException("the Architect entity would not spawn");
        return architect;
    }

    private static JsonObject catalogue(JsonObject... rows) {
        JsonArray array = new JsonArray();
        for (JsonObject row : rows) array.add(row);
        JsonObject response = new JsonObject();
        response.addProperty("catalog_revision", "m7-test");
        response.add("rows", array);
        return response;
    }

    private static JsonObject row(String itemId, boolean available, double price,
                                  String denomination, int units) {
        return rowObject(itemId, available, price, denomination, units);
    }

    /** An unavailable row, shaped the way Rails sends one -- reason and all. */
    private static JsonObject unavailableRow(String itemId, String reason) {
        JsonObject row = new JsonObject();
        row.addProperty("item_id", itemId);
        row.addProperty("display_name", itemId);
        row.addProperty("available", false);
        row.add("unit_price", JsonParser.parseString("null"));
        row.addProperty("denomination", "gold");
        row.addProperty("available_units", 0);
        JsonArray reasons = new JsonArray();
        JsonObject entry = new JsonObject();
        entry.addProperty("code", reason);
        reasons.add(entry);
        row.add("reasons", reasons);
        return row;
    }

    private static JsonObject rowObject(String itemId, boolean available, double price,
                                        String denomination, int units) {
        JsonObject row = new JsonObject();
        row.addProperty("item_id", itemId);
        row.addProperty("display_name", itemId);
        row.addProperty("available", available);
        row.addProperty("unit_price", price);
        if (denomination != null) row.addProperty("denomination", denomination);
        row.addProperty("available_units", units);
        row.add("reasons", new JsonArray());
        return row;
    }

    private static String describe(List<Product> products) {
        StringBuilder text = new StringBuilder("[");
        for (Product product : products) {
            text.append(product.itemId()).append('@').append(product.price())
                .append(' ').append(product.currency()).append(' ');
        }
        return text.append(']').toString();
    }
}
