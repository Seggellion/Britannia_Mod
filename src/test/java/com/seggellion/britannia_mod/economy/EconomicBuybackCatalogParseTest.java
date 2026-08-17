package com.seggellion.britannia_mod.economy;

import com.seggellion.britannia_mod.network.ClientboundOpenNpcScreenPayload.Notice;
import com.seggellion.britannia_mod.shop.Product;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a buyback quote off the wire.
 *
 * <p>Rows are positional, so the mapping from a row back to the stack that produced it is by
 * index and nothing else -- a row that cannot be placed must be dropped rather than guessed at,
 * or a player would be shown a price against the wrong item.
 */
class EconomicBuybackCatalogParseTest {

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<ItemStack> sources() {
        return List.of(new ItemStack(Items.COD), new ItemStack(Items.SALMON));
    }

    @Test
    void anAvailableRowIsPricedPerUnitFromItsLineTotal() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "reasons":[],"denomination":"copper","unit_price":4.5,"quantity":3,
                      "weight":4.5,"line_total":20}],
             "payout":{"available":true,"denomination":"copper","amount":20,"reasons":[]}}
            """, sources());

        assertEquals(1, quote.products().size());
        Product product = quote.products().get(0);
        assertEquals("minecraft:cod", product.itemId());
        assertEquals("cod", product.name());
        assertEquals("copper", product.currency());
        // The screen multiplies a per-unit price by the quantity the player picks, so the row
        // total is divided down rather than unit_price being used directly: unit_price is per
        // unit of WEIGHT for weight-valued goods and would badly misprice a heavy fish.
        assertEquals(7, product.price());
        assertTrue(quote.notices().isEmpty());
    }

    @Test
    void aCommodityTheCityDoesNotTradeIsDroppedWithoutComment() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":false,
                      "reasons":["commodity_not_found"]}],
             "payout":{"available":false,"denomination":"copper","amount":0,"reasons":[]}}
            """, sources());

        assertTrue(quote.products().isEmpty());
        // Saying "this city does not trade cod" for every item the trader was never going to
        // want would bury the reasons that are actually actionable; the blanket line covers it.
        assertTrue(quote.notices().isEmpty());
    }

    @Test
    void actionableRowRefusalsAreNamedWithTheirCommodity() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":false,
                      "reasons":["commodity_not_buyable"]},
                     {"index":1,"commodity_key":"lava_fish","display_name":"lava fish",
                      "available":false,"reasons":["commodity_stock_cap_exceeded"]}],
             "payout":{"available":false,"denomination":"copper","amount":0,"reasons":[]}}
            """, sources());

        assertTrue(quote.products().isEmpty());
        assertEquals(List.of(new Notice(Notice.COMMODITY_NOT_BUYABLE, "cod"),
                             new Notice(Notice.COMMODITY_STOCK_CAP_EXCEEDED, "lava fish")),
                     quote.notices());
    }

    @Test
    void basketRefusalsCarryThePayoutDenomination() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "reasons":[],"denomination":"copper","quantity":2,"line_total":10}],
             "payout":{"available":false,"denomination":"copper","amount":0,
                       "reasons":["treasury_insufficient","treasury_denomination_unavailable"]}}
            """, sources());

        // The row still prices -- the city simply cannot pay for it right now, which is a
        // different thing from the trader not wanting it, and the player is told which.
        assertEquals(1, quote.products().size());
        assertEquals(List.of(new Notice(Notice.TREASURY_INSUFFICIENT, "copper"),
                             new Notice(Notice.TREASURY_DENOMINATION_UNAVAILABLE, "copper")),
                     quote.notices());
    }

    /**
     * A partly-accepted offer must show the accepted rows AND explain the refused ones. Before
     * refusal codes existed this collapsed to a blanket "not interested", which is how a trader
     * that could not buy anything at all looked identical to one that simply had a full store.
     */
    @Test
    void aPartlyAcceptedOfferKeepsItsPricedRowsAndExplainsTheRest() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "denomination":"copper","quantity":1,"weight":1.0,"line_total":4.5},
                     {"index":1,"commodity_key":"lava_fish","display_name":"lava fish",
                      "available":false,"reasons":["commodity_stock_cap_exceeded"]}],
             "payout":{"available":true,"denomination":"copper","amount":5,"reasons":[]}}
            """, sources());

        assertEquals(1, quote.products().size(), "the sellable row must survive the refusal");
        assertEquals("cod", quote.products().get(0).name());
        assertEquals(List.of(new Notice(Notice.COMMODITY_STOCK_CAP_EXCEEDED, "lava fish")),
                     quote.notices());
    }

    @Test
    void rowsThatCannotBePlacedAgainstAStackAreDropped() {
        var quote = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":9,"commodity_key":"cod","available":true,"quantity":1,"line_total":5},
                     {"index":-1,"commodity_key":"cod","available":true,"quantity":1,"line_total":5},
                     {"commodity_key":"cod","available":true,"quantity":1,"line_total":5}],
             "payout":{"available":true,"denomination":"copper","amount":5,"reasons":[]}}
            """, sources());

        assertTrue(quote.products().isEmpty());
        assertTrue(quote.notices().isEmpty());
    }

    @Test
    void nonsenseAndMissingSectionsYieldAnEmptyQuoteRatherThanThrowing() {
        assertTrue(EconomicBuybackCatalogService.parse("[]", sources()).products().isEmpty());
        assertTrue(EconomicBuybackCatalogService.parse("{}", sources()).notices().isEmpty());

        var zeroQuantity = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","available":true,"quantity":0,"line_total":5}]}
            """, sources());
        assertTrue(zeroQuantity.products().isEmpty());

        var unknownDenomination = EconomicBuybackCatalogService.parse("""
            {"rows":[{"index":0,"commodity_key":"cod","display_name":"cod","available":true,
                      "denomination":"platinum","quantity":1,"line_total":5}]}
            """, sources());
        assertEquals("copper", unknownDenomination.products().get(0).currency());
    }
}
