package com.seggellion.britannia_mod.service.guild;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Guildmaster milestone 3: the fixed-point rendering used in the offer lines.
 *
 * <p>Separated from the offer line itself because that needs a {@code ServerPlayer}, which neither
 * harness can build. This is the half that carries the tenths discipline — the whole feature does
 * its arithmetic in integer tenths precisely so no {@code float} formatting ever reaches a price,
 * and it is worth pinning that a 400-tenth ceiling reads as "40.0" rather than "39.99999".
 */
class GuildmasterOfferFormatTest {
    @Test
    void rendersWholeValues() {
        assertEquals("0.0", GuildmasterProxyService.formatTenths(0));
        assertEquals("1.0", GuildmasterProxyService.formatTenths(10));
        assertEquals("40.0", GuildmasterProxyService.formatTenths(400));
        assertEquals("100.0", GuildmasterProxyService.formatTenths(1000));
    }

    @Test
    void rendersTenths() {
        assertEquals("0.1", GuildmasterProxyService.formatTenths(1));
        assertEquals("12.7", GuildmasterProxyService.formatTenths(127));
        assertEquals("39.9", GuildmasterProxyService.formatTenths(399));
    }

    @Test
    void theTrainingCeilingIsFourHundredTenths() {
        // 1 gold buys 0.1 skill, so the ceiling in tenths is also the price of 0.0 -> 40.0 in gold.
        // The design's headline figure: 400 gold.
        assertEquals(400, GuildmasterProxyService.GUILDMASTER_MAX_TENTHS);
        assertEquals("40.0", GuildmasterProxyService.formatTenths(GuildmasterProxyService.GUILDMASTER_MAX_TENTHS));
    }
}
