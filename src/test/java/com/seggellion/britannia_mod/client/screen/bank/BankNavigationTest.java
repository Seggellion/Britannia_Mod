package com.seggellion.britannia_mod.client.screen.bank;

import com.seggellion.britannia_mod.network.payload.BankAccountOpenedS2CPayload;
import com.seggellion.britannia_mod.network.payload.BankTransferResultS2CPayload;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 4: the navigation rules, including the refresh cutover.
 *
 * <p>The screens themselves cannot be instantiated in JUnit (Architecture Decision 0), so what is
 * asserted here is every decision they delegate. The remaining half -- that the buttons are in the
 * right place and the parchment looks right -- is the owner visual gate's.
 */
class BankNavigationTest {

    private static final int TELLER = 42;

    @BeforeEach
    @AfterEach
    void resetSession() {
        ClientBankingSession.resetForTesting();
    }

    private static ClientBankingSession openSession(String city) {
        return ClientBankingSession.applyAccountOpened(new BankAccountOpenedS2CPayload(
                "Aldric", "male", TELLER, city, 250, 12.5, 3, 47, 92, List.of()
        ));
    }

    // ---------- The refresh cutover ----------

    @Test
    void opensTheMainScreenWhenBankingIsNotAlreadyOnScreen() {
        // The first bank.open of an interaction: nothing banking-related is mounted.
        assertEquals(BankNavigation.RefreshRoute.OPEN_MAIN, BankNavigation.refreshRoute(false));
    }

    @Test
    void leavesTheCurrentScreenMountedWhenBankingIsAlreadyOpen() {
        // The whole point of Milestone 2 and 4 together: a refresh must not rebuild the screen,
        // because rebuilding discards selection, scroll position and half-typed input -- and once
        // the Bank Box exists, would eject the player mid-drag.
        assertEquals(BankNavigation.RefreshRoute.KEEP_CURRENT, BankNavigation.refreshRoute(true));
    }

    @Test
    void theTwoRoutesAreTheOnlyOutcomes() {
        assertEquals(2, BankNavigation.RefreshRoute.values().length);
        assertNotEquals(BankNavigation.refreshRoute(true), BankNavigation.refreshRoute(false));
    }

    // ---------- Destination availability ----------

    @Test
    void everyDestinationOpensWhileTheSessionIsLive() {
        ClientBankingSession session = openSession("Britain");
        for (BankNavigation.Destination destination : BankNavigation.Destination.values()) {
            assertTrue(BankNavigation.canOpen(destination, session), destination.name());
        }
    }

    @Test
    void noDestinationOpensWithoutASession() {
        for (BankNavigation.Destination destination : BankNavigation.Destination.values()) {
            assertFalse(BankNavigation.canOpen(destination, null), destination.name());
        }
    }

    @Test
    void navigationStaysAvailableWhileAMutationIsPending() {
        ClientBankingSession session = openSession("Britain");
        session.beginPending(BankTransferResultS2CPayload.Operation.DEPOSIT);

        // Navigating is not mutating. The session outlives the screen and carries the lock with
        // it, so blocking navigation would only strand the player until the server answered.
        for (BankNavigation.Destination destination : BankNavigation.Destination.values()) {
            assertTrue(BankNavigation.canOpen(destination, session), destination.name());
        }
    }

    @Test
    void offersExactlyTheThreeDestinationsTheDesignRequires() {
        assertEquals(3, BankNavigation.Destination.values().length);
        Set<BankNavigation.Destination> destinations = Set.of(BankNavigation.Destination.values());
        assertTrue(destinations.contains(BankNavigation.Destination.BANK_BOX));
        assertTrue(destinations.contains(BankNavigation.Destination.BALANCE));
        assertTrue(destinations.contains(BankNavigation.Destination.CREATE_CHEQUE));
    }

    @Test
    void givesEachDestinationItsOwnLabel() {
        Set<String> keys = new HashSet<>();
        for (BankNavigation.Destination destination : BankNavigation.Destination.values()) {
            keys.add(destination.labelKey());
        }
        assertEquals(BankNavigation.Destination.values().length, keys.size());
    }

    // ---------- Greeting ----------

    @Test
    void namesTheCityWhenTheAccountHasOne() {
        assertEquals(
                "screen.britannia_mod.bank.main.greeting_city",
                BankNavigation.greetingKey("Britain")
        );
    }

    @Test
    void fallsBackToTheCitylessGreetingInGlobalMode() {
        assertEquals("screen.britannia_mod.bank.main.greeting", BankNavigation.greetingKey(null));
    }

    @Test
    void treatsABlankCityAsNoCityRatherThanGreetingAnEmptyPlace() {
        assertEquals("screen.britannia_mod.bank.main.greeting", BankNavigation.greetingKey(""));
        assertEquals("screen.britannia_mod.bank.main.greeting", BankNavigation.greetingKey("   "));
    }

    // ---------- Session identity across navigation ----------

    @Test
    void navigationPreservesBankerAndAccountContext() {
        ClientBankingSession session = openSession("Britain");

        // Navigation replaces screens but never touches the session, so every destination sees the
        // same teller, the same balances and the same items. This is what the four screens share.
        assertEquals(TELLER, ClientBankingSession.active().tellerEntityId());
        assertEquals("Aldric", ClientBankingSession.active().tellerName());
        assertEquals(session, ClientBankingSession.active());
    }
}
