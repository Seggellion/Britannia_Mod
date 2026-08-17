package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The quote-to-payout pricing contract, pinned against the Rails authority
 * ({@code Economy::BuybackValuation}).
 *
 * <h2>What the fields mean</h2>
 * <ul>
 *   <li>{@code quantity} — item count.</li>
 *   <li>{@code weight} — Rails' {@code delta}: the magnitude in the commodity's CANONICAL unit.
 *       Total stack weight for a weight-canonical commodity (fish, wood, ore, stone, meat, grain,
 *       metal); literally equal to {@code quantity} for a count-canonical one (alcohol, fur,
 *       leather). The field name says weight, the contract says canonical delta.</li>
 *   <li>{@code unit_price} — {@code current_price × form multiplier}, per canonical unit. Per
 *       STONE for weight-canonical goods, per ITEM for count-canonical ones.</li>
 *   <li>{@code line_total} — {@code unit_price × delta}, in CANONICAL VALUE UNITS (copper), NOT
 *       in coins of the row's denomination.</li>
 * </ul>
 *
 * <p>That last point is the one worth a test: coins are
 * {@code round(Σ line_total / base)} with base copper=1, silver=100, gold=10 000. Reading
 * {@code line_total} as coins is correct only by accident for copper-tier goods and is a
 * hundredfold overpayment for the silver-tier ones (metal/salvage, alcohol/wine).
 */
class EconomicBuybackPricingContractTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<ItemStack> oneSource() {
        return List.of(new ItemStack(Items.COD));
    }

    /** The mod's copper ratios must equal Rails' {@code Currency::CANONICAL_BASE_VALUES}. */
    @Test
    void denominationBasesMatchTheRailsCanonicalBaseValues() {
        assertEquals(1, EconomicBuybackCatalogService.denominationBaseCopper("copper"));
        assertEquals(100, EconomicBuybackCatalogService.denominationBaseCopper("silver"));
        assertEquals(10_000, EconomicBuybackCatalogService.denominationBaseCopper("gold"));
    }

    /**
     * Weight-priced: one cod, 4.5 stones, unit_price 4.5/stone → line_total 20.25 copper.
     * Rails would pay round(20.25 / 1) = 20 copper for it alone.
     */
    @Test
    void aWeightPricedRowKeepsItsExactCopperValue() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "reasons":[],"form":"raw","denomination":"copper","unit_price":4.5,
                      "quantity":1,"weight":4.5,"line_total":20.25}],
             "payout":{"available":true,"denomination":"copper","amount":20,"reasons":[]}}
            """, oneSource());

        Product cod = quote.products().get(0);
        assertEquals(2025L, cod.lineValueCopper(), "value is carried in hundredths of a copper");
        assertEquals(1, cod.quotedQuantity());
        assertEquals(20, cod.price(), "display price rounds to whole copper");
        assertEquals(2025L, cod.valueCopperFor(1));
    }

    /**
     * The 100x bug. A finished/processed row pays in SILVER, so its line_total of 250 copper is
     * 2.5 silver — not 250 silver. Reading line_total as coins overpays by the base.
     */
    @Test
    void aSilverTierRowIsNotPricedAsThoughLineTotalWereCoins() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"copper_ring","display_name":"copper ring",
                      "available":true,"reasons":[],"form":"finished","denomination":"silver",
                      "unit_price":250,"quantity":1,"weight":1,"line_total":250}],
             "payout":{"available":true,"denomination":"silver","amount":3,"reasons":[]}}
            """, oneSource());

        Product ring = quote.products().get(0);
        assertEquals(25_000L, ring.lineValueCopper());
        assertEquals("silver", ring.currency());
        // 250 copper / 100 = 2.5 silver, displayed as 3 -- emphatically not 250.
        assertEquals(3, ring.price());
        assertNotEquals(250, ring.price());
    }

    /**
     * Count-priced: Rails sets delta = quantity, so line_total = unit_price × quantity and the
     * per-item value divides exactly.
     */
    @Test
    void aCountPricedRowDividesExactlyAcrossItsItems() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"leather","display_name":"leather",
                      "available":true,"reasons":[],"form":"raw","denomination":"copper",
                      "unit_price":6,"quantity":4,"weight":4,"line_total":24}],
             "payout":{"available":true,"denomination":"copper","amount":24,"reasons":[]}}
            """, oneSource());

        Product leather = quote.products().get(0);
        assertEquals(2400L, leather.lineValueCopper());
        assertEquals(4, leather.quotedQuantity());
        assertEquals(6, leather.price());
        // A partial sale of the stack is priced by the same rule, with no drift at any count.
        assertEquals(600L, leather.valueCopperFor(1));
        assertEquals(1200L, leather.valueCopperFor(2));
        assertEquals(1800L, leather.valueCopperFor(3));
        assertEquals(2400L, leather.valueCopperFor(4));
    }

    /**
     * A partial stack of a weight-priced good. Four identically-weighted fish (they could not
     * share a stack otherwise) at 13.5 copper total: selling two is worth exactly half.
     */
    @Test
    void aPartialWeightPricedStackIsPricedProRata() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "reasons":[],"form":"raw","denomination":"copper","unit_price":4.5,
                      "quantity":4,"weight":3.0,"line_total":13.5}],
             "payout":{"available":true,"denomination":"copper","amount":14,"reasons":[]}}
            """, oneSource());

        Product cod = quote.products().get(0);
        assertEquals(1350L, cod.lineValueCopper());
        assertEquals(675L, cod.valueCopperFor(2));
        assertEquals(1350L, cod.valueCopperFor(4));
        // 3.375 copper each: the row can only display 3, which is exactly why the cart totals
        // from valueCopperFor and not from the displayed price.
        assertEquals(3, cod.price());
        assertEquals(1350L, cod.valueCopperFor(4));
        assertTrue(cod.price() * 4 < 14, "rounding per item under-reports the true 14 copper");
    }

    /**
     * The cross-repository parity pin, against figures produced by running the real
     * {@code Economy::BuybackValuation} (cod at current_price 4.5/stone, 2026-08-17).
     *
     * <p>Three cod of 0.6, 1.4 and 4.9 stones quote at 2.7, 6.3 and 22.05 copper; Rails settles
     * the basket at round(31.05) = 31 copper. The cart must show 31 — and would have shown 9
     * under the old code, which merged all three into one row at the first fish's rounded price.
     */
    @Test
    void theCartTotalMatchesWhatRailsSettlesForThreeDifferentlyWeightedFish() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "denomination":"copper","unit_price":4.5,"quantity":1,"weight":0.6,"line_total":2.7},
                     {"index":1,"commodity_key":"cod","display_name":"cod","available":true,
                      "denomination":"copper","unit_price":4.5,"quantity":1,"weight":1.4,"line_total":6.3},
                     {"index":2,"commodity_key":"cod","display_name":"cod","available":true,
                      "denomination":"copper","unit_price":4.5,"quantity":1,"weight":4.9,"line_total":22.05}],
             "payout":{"available":true,"denomination":"copper","amount":31,"reasons":[]}}
            """, List.of(new ItemStack(Items.COD), new ItemStack(Items.COD), new ItemStack(Items.COD)));

        assertEquals(3, quote.products().size(), "three weights are three offers, never one");
        long cart = quote.products().stream().mapToLong(p -> p.valueCopperFor(1)).sum();
        assertEquals(3105L, cart);
        assertEquals(31, EconomicBuybackCatalogService.coinsFor(cart, "copper"),
                "must equal the payout amount Rails computed for the same basket");
    }

    /**
     * Partial stack, same source: 4 cod totalling 6.0 stones quote at 27 copper; keeping 2 leaves
     * 3.0 stones, which Rails re-quotes at 13.5 and settles at 14.
     */
    @Test
    void aPartialSaleDisplaysWhatTheRequoteWillPay() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "denomination":"copper","unit_price":4.5,"quantity":4,"weight":6.0,"line_total":27.0}],
             "payout":{"available":true,"denomination":"copper","amount":27,"reasons":[]}}
            """, oneSource());

        Product cod = quote.products().get(0);
        assertEquals(27, EconomicBuybackCatalogService.coinsFor(cod.valueCopperFor(4), "copper"));
        assertEquals(14, EconomicBuybackCatalogService.coinsFor(cod.valueCopperFor(2), "copper"),
                "half of 27 is 13.5, and both sides round half up to 14");
        assertEquals(7, EconomicBuybackCatalogService.coinsFor(cod.valueCopperFor(1), "copper"));
    }

    /** Scenario C from the same run: a ring worth 375 copper pays 4 silver, not 375. */
    @Test
    void aSilverRowConvertsToCoinsAtTheSilverBase() {
        assertEquals(4, EconomicBuybackCatalogService.coinsFor(37_500L, "silver"));
        assertEquals(375, EconomicBuybackCatalogService.coinsFor(37_500L, "copper"),
                "the same 375 copper of value is 375 coins at the copper base and 4 at the silver "
                        + "base -- reading line_total as coins is what got this wrong");
        assertEquals(31, EconomicBuybackCatalogService.coinsFor(3105L, "copper"));
    }

    @Test
    void anUnpricedRowNeverClaimsToBeServerPriced() {
        Product legacy = new Product("britannia_mod:cod", "cod", 5, "copper", new ItemStack(Items.COD));
        assertEquals(0L, legacy.lineValueCopper());
        assertEquals(0, legacy.quotedQuantity());
        assertTrue(!legacy.isServerPriced());
        assertEquals(0L, legacy.valueCopperFor(3), "an unpriced row must contribute no exact value");
    }

    /**
     * Two stacks of the same fish at different weights are different offers. They must not
     * compare equal, or the cart -- keyed by Product -- folds one into the other and the player
     * sells a heavy fish at a light one's price.
     */
    @Test
    void rowsOfEqualDisplayPriceButUnequalValueStayDistinct() {
        Product light = new Product("britannia_mod:cod", "cod", 4, "copper",
                new ItemStack(Items.COD), null, 420L, 1);
        Product heavy = new Product("britannia_mod:cod", "cod", 4, "copper",
                new ItemStack(Items.COD), null, 440L, 1);
        assertNotEquals(light, heavy);
        assertNotEquals(light.hashCode(), heavy.hashCode());

        Product same = new Product("britannia_mod:cod", "cod", 4, "copper",
                new ItemStack(Items.COD), null, 420L, 1);
        assertEquals(light, same, "economically identical rows must still merge");
    }

    /** Zero and negative values must fail closed rather than mis-price. */
    @Test
    void zeroAndInvalidValuesDoNotProduceAPricedRow() {
        var negative = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","available":true,"quantity":1,
                      "denomination":"copper","line_total":-5}]}
            """, oneSource());
        assertTrue(negative.products().isEmpty());

        var zeroValue = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "quantity":1,"denomination":"copper","line_total":0}]}
            """, oneSource());
        // A genuinely worthless row is still a row; Rails rejects the basket with
        // value_below_denomination_minimum rather than the mod inventing a floor price.
        assertEquals(0, zeroValue.products().get(0).price());
        assertEquals(0L, zeroValue.products().get(0).lineValueCopper());
    }
}
