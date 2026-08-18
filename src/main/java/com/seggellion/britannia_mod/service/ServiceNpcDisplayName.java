package com.seggellion.britannia_mod.service;

import javax.annotation.Nullable;

/**
 * How a Service NPC's nameplate text is composed. Pure string arithmetic, deliberately outside
 * {@code ServiceNpcEntity}: Architecture Decision 0 (see {@code BankDialogueLayout}'s class
 * docs) established that neither test harness in this project can instantiate an entity or a
 * screen, so anything worth asserting lives in a class that can be constructed in JUnit.
 *
 * <h2>The "the" convention</h2>
 * {@code personalName + " the " + roleTitle} is not invented here — it is the format
 * {@code TownPersonEntity}, {@code AlcoholTraderEntity} and {@code AbstractTraderEntity} all
 * already build inline in their own {@code updateDisplayName()} overrides. This centralizes it
 * for the Guildmaster rather than adding a fourth copy.
 *
 * <h2>Which Service NPCs get it</h2>
 * All of them, as of the owner request that widened it. {@code ServiceNpcEntity} once inherited
 * {@code CitizenEntity.updateDisplayName()} — personal name alone, so a bank teller read
 * {@code "Aldric"} and only Guildmasters produced a role title. It now renders
 * {@code "Aldric the Bank Teller"} for any service type that publishes a display name, which
 * makes Service NPCs consistent with the three families above rather than the exception to them.
 *
 * <p>{@link #combine} is still only reached for a role title that is actually present, so a type
 * that is unknown to the registry, inactive, or nameless falls back to the personal name alone
 * rather than inventing a label.
 */
public final class ServiceNpcDisplayName {
    private static final String LINK = " the ";

    private ServiceNpcDisplayName() {
    }

    /**
     * The nameplate text for a Service NPC.
     *
     * @param personalName the random personal name from the Rails World NPC record
     * @param roleTitle    the role, or {@code null}/blank for an NPC with no published role
     * @return {@code "Marcus the Warrior Guildmaster"}, or just {@code "Marcus"} when there is no
     *         role title — never a dangling {@code " the "}
     */
    public static String combine(@Nullable String personalName, @Nullable String roleTitle) {
        String name = personalName == null ? "" : personalName.trim();
        String role = roleTitle == null ? "" : roleTitle.trim();
        if (role.isEmpty()) return name;
        // A blank personal name would otherwise render as " the Warrior Guildmaster". The role
        // alone is the more useful of the two halves, so it stands on its own.
        if (name.isEmpty()) return role;
        return name + LINK + role;
    }
}
