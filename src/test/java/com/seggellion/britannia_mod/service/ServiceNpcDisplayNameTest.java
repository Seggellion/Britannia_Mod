package com.seggellion.britannia_mod.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Title formatting, asserted here rather than through the entity: Architecture Decision 0 means
 * neither test harness can construct a {@code ServiceNpcEntity}, so the composition rule lives in
 * a plain class and the entity only calls it.
 */
class ServiceNpcDisplayNameTest {
    @Test
    void combinesAPersonalNameWithARoleTitle() {
        assertEquals("Marcus the Warrior Guildmaster",
                ServiceNpcDisplayName.combine("Marcus", "Warrior Guildmaster"));
    }

    @Test
    void aNpcWithNoRoleTitleKeepsThePersonalNameAlone() {
        // This is every non-Guildmaster Service NPC, including every existing bank teller.
        assertEquals("Aldric", ServiceNpcDisplayName.combine("Aldric", null));
        assertEquals("Aldric", ServiceNpcDisplayName.combine("Aldric", ""));
        assertEquals("Aldric", ServiceNpcDisplayName.combine("Aldric", "   "));
    }

    @Test
    void aBlankPersonalNameNeverProducesADanglingConnector() {
        assertEquals("Warrior Guildmaster", ServiceNpcDisplayName.combine(null, "Warrior Guildmaster"));
        assertEquals("Warrior Guildmaster", ServiceNpcDisplayName.combine("", "Warrior Guildmaster"));
        assertEquals("Warrior Guildmaster", ServiceNpcDisplayName.combine("  ", "Warrior Guildmaster"));
    }

    @Test
    void bothBlankProducesAnEmptyString() {
        assertEquals("", ServiceNpcDisplayName.combine(null, null));
        assertEquals("", ServiceNpcDisplayName.combine("  ", "  "));
    }

    @Test
    void surroundingWhitespaceIsTrimmedFromBothHalves() {
        assertEquals("Marcus the Warrior Guildmaster",
                ServiceNpcDisplayName.combine("  Marcus  ", "  Warrior Guildmaster  "));
    }
}
