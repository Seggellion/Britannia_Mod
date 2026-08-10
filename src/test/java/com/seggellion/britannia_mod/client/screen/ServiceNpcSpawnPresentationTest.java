package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload;
import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload.SupplyLine;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnEligibility;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRegistrationState;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The spawn-block readout. Asserted here rather than through the screen because
 * {@code ServiceNpcSpawnScreen} is an {@code AbstractContainerScreen} and Architecture Decision 0
 * means neither harness can construct one — the same split {@code BankDialogueLayout} uses.
 */
class ServiceNpcSpawnPresentationTest {
    private static String allText(ServiceNpcSpawnPresentation presentation) {
        StringBuilder joined = new StringBuilder();
        presentation.lines().forEach(line -> joined.append(line.text()).append('\n'));
        return joined.toString();
    }

    private static ServiceNpcSpawnPresentation of(
            List<String> taught,
            ServiceNpcSpawnEligibility.Status status,
            List<SupplyLine> supplies,
            ServiceNpcSpawnRegistrationState registrationState,
            UUID assignedNpc
    ) {
        return ServiceNpcSpawnPresentation.of(new ServiceNpcSpawnStateS2CPayload(
                1, BlockPos.ZERO, UUID.randomUUID(), true, ServiceNpcSpawnValidationError.NONE,
                true, true, List.of(), List.of(), UUID.randomUUID(), "warrior_guildmaster",
                true, true, true, 1L, registrationState, null, assignedNpc, null, 1L, null,
                taught, status, supplies
        ));
    }

    // ---------- A bank teller must look exactly as it always did ----------

    @Test
    void aTypeWithNoTaughtSkillsAndNoRequirementsRendersNothingAtAll() {
        ServiceNpcSpawnPresentation presentation = of(
                List.of(), ServiceNpcSpawnEligibility.Status.SATISFIED, List.of(),
                ServiceNpcSpawnRegistrationState.REGISTERED, UUID.randomUUID());

        assertTrue(presentation.isEmpty(), "a staffed bank teller must add no rows to the screen");
    }

    // ---------- Taught skills ----------

    @Test
    void taughtSkillsAreListedTwoPerRowUnderAHeading() {
        ServiceNpcSpawnPresentation presentation = of(
                List.of("Arms Lore", "Fencing", "Mace Fighting"),
                ServiceNpcSpawnEligibility.Status.SATISFIED, List.of(),
                ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION, null);

        List<ServiceNpcSpawnPresentation.Line> lines = presentation.lines();
        assertEquals("Teaches:", lines.get(0).text());
        assertEquals("  Arms Lore, Fencing", lines.get(1).text());
        assertEquals("  Mace Fighting", lines.get(2).text(), "an odd final skill gets its own row");
    }

    @Test
    void anElevenSkillGuildDoesNotProduceElevenRows() {
        // The Ranger guild. One row per skill would run off the panel.
        ServiceNpcSpawnPresentation presentation = of(
                List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K"),
                ServiceNpcSpawnEligibility.Status.SATISFIED, List.of(),
                ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION, null);

        assertEquals(7, presentation.lines().size(), "1 heading + 6 rows for 11 skills");
    }

    // ---------- Supply lines ----------

    @Test
    void aShortfallIsRedAndAMetRequirementIsGreen() {
        ServiceNpcSpawnPresentation presentation = of(
                List.of(), ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM,
                List.of(new SupplyLine("food", 200.0, 143.5, true),
                        new SupplyLine("gold", 1.0, 12.0, true)),
                ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION, null);

        List<ServiceNpcSpawnPresentation.Line> lines = presentation.lines();
        assertEquals("City economy:", lines.get(0).text());
        assertEquals("  Food 143.5 / 200", lines.get(1).text());
        assertEquals(ServiceNpcSpawnPresentation.COLOR_SHORTFALL, lines.get(1).color());
        assertEquals("  Gold 12 / 1", lines.get(2).text(), "a whole number keeps no decimal");
        assertEquals(ServiceNpcSpawnPresentation.COLOR_OK, lines.get(2).color());
    }

    @Test
    void anUnmeasuredSupplyShowsAQuestionMarkRatherThanAFalseZero() {
        // Reporting "0 / 5" for a figure the bootstrap never sent would be a lie that reads as a
        // shortfall.
        ServiceNpcSpawnPresentation presentation = of(
                List.of(), ServiceNpcSpawnEligibility.Status.UNKNOWN,
                List.of(new SupplyLine("reagents", 5.0, 0.0, false)),
                ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION, null);

        assertEquals("  Reagents ? / 5", presentation.lines().get(1).text());
        assertEquals(ServiceNpcSpawnPresentation.COLOR_UNKNOWN, presentation.lines().get(1).color());
    }

    @Test
    void amountsRenderOneDecimalOnlyWhenThereIsOne() {
        StringBuilder out = new StringBuilder();
        ServiceNpcSpawnPresentation.appendAmount(out, 200.0);
        out.append('|');
        ServiceNpcSpawnPresentation.appendAmount(out, 143.5);
        out.append('|');
        ServiceNpcSpawnPresentation.appendAmount(out, 0.0);
        assertEquals("200|143.5|0", out.toString());
    }

    // ---------- The registered-but-unstaffed explanation ----------

    @Test
    void aRegisteredBlockWithNoNpcExplainsTheEconomy() {
        ServiceNpcSpawnPresentation presentation = of(
                List.of("Swordsmanship"), ServiceNpcSpawnEligibility.Status.BELOW_MINIMUM,
                List.of(new SupplyLine("food", 200.0, 143.5, true)),
                ServiceNpcSpawnRegistrationState.REGISTERED, null);

        assertTrue(allText(presentation).contains("city economy is below the minimum"),
                "this is the whole point of the milestone: the block must say why");
    }

    @Test
    void aRegisteredBlockWithAHealthyEconomyBlamesReconciliationRatherThanInventingAFault() {
        // Staffing is applied by a Rails-side reconciliation, not by this block. "Not yet" is the
        // honest answer.
        ServiceNpcSpawnPresentation presentation = of(
                List.of("Swordsmanship"), ServiceNpcSpawnEligibility.Status.SATISFIED,
                List.of(new SupplyLine("gold", 1.0, 12.0, true)),
                ServiceNpcSpawnRegistrationState.REGISTERED, null);

        assertTrue(allText(presentation).contains("awaiting staffing reconciliation"));
    }

    @Test
    void aStaffedBlockOffersNoExplanationBecauseThereIsNothingToExplain() {
        ServiceNpcSpawnPresentation presentation = of(
                List.of("Swordsmanship"), ServiceNpcSpawnEligibility.Status.SATISFIED,
                List.of(new SupplyLine("gold", 1.0, 12.0, true)),
                ServiceNpcSpawnRegistrationState.REGISTERED, UUID.randomUUID());

        assertFalse(allText(presentation).contains("No NPC"));
    }

    @Test
    void anUnregisteredBlockIsNotAccusedOfBeingUnstaffed() {
        // Nothing has been sent to Rails yet, so "no NPC" is not news.
        ServiceNpcSpawnPresentation presentation = of(
                List.of("Swordsmanship"), ServiceNpcSpawnEligibility.Status.SATISFIED,
                List.of(new SupplyLine("gold", 1.0, 12.0, true)),
                ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION, null);

        assertFalse(allText(presentation).contains("No NPC"));
    }

    // ---------- Rebuilt once, then immutable ----------

    @Test
    void theResultIsImmutableSoTheRenderLoopCannotBeMutatedMidFrame() {
        ServiceNpcSpawnPresentation presentation = of(
                List.of("Swordsmanship"), ServiceNpcSpawnEligibility.Status.SATISFIED, List.of(),
                ServiceNpcSpawnRegistrationState.PENDING_REGISTRATION, null);

        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> presentation.lines().clear());
    }
}
