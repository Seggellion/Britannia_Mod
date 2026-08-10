package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnEligibility;
import com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnRegistrationState;

import java.util.ArrayList;
import java.util.List;

/**
 * Guildmaster milestone 2: the taught-skill and city-economy readout on the Service NPC spawn
 * block, reduced to a flat list of already-formatted lines.
 *
 * <h2>Built once per payload, never per frame</h2>
 * {@code Screen.render} runs every frame. Formatting numbers, concatenating strings or streaming
 * collections there would allocate continuously to redraw data that only changes when a
 * {@link ServiceNpcSpawnStateS2CPayload} arrives — on open, save and refresh, which is a handful
 * of times in a session. So everything is computed here, once, when the payload lands; the screen
 * then walks an array-backed list and draws pre-built strings, allocating nothing.
 *
 * <p>That is also what makes it testable. {@code ServiceNpcSpawnScreen} is an
 * {@code AbstractContainerScreen} and Architecture Decision 0 means neither harness can construct
 * one — the same split {@code BankDialogueLayout} uses to get the bank screens' arithmetic under
 * JUnit. The performance answer and the testability answer happen to be the same answer.
 *
 * <p>Deliberately no {@code String.format}: it parses its pattern on every call and is easily the
 * most expensive way to render a number. Once-per-payload makes that affordable, but there is no
 * reason to pay it.
 */
public record ServiceNpcSpawnPresentation(List<Line> lines) {
    /** Matches the existing status colours on this screen. */
    public static final int COLOR_HEADING = 0xFFFFFF;
    public static final int COLOR_OK = 0x55FF55;
    public static final int COLOR_SHORTFALL = 0xFF5555;
    public static final int COLOR_UNKNOWN = 0xAAAAAA;
    public static final int COLOR_NOTE = 0xFFAA00;

    public record Line(String text, int color) {
    }

    public ServiceNpcSpawnPresentation {
        lines = List.copyOf(lines);
    }

    private static final ServiceNpcSpawnPresentation EMPTY =
            new ServiceNpcSpawnPresentation(List.of());

    public static ServiceNpcSpawnPresentation empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public static ServiceNpcSpawnPresentation of(ServiceNpcSpawnStateS2CPayload state) {
        if (state == null) return EMPTY;

        List<String> taught = state.taughtSkillLabels();
        List<ServiceNpcSpawnStateS2CPayload.SupplyLine> supplies = state.supplyRequirements();
        // A bank teller has neither, and must render exactly as it always did — no empty headings,
        // no blank rows, nothing new on screen at all.
        boolean unstaffed = isRegisteredButUnstaffed(state);
        if (taught.isEmpty() && supplies.isEmpty() && !unstaffed) return EMPTY;

        List<Line> lines = new ArrayList<>(taught.size() + supplies.size() + 3);

        if (!taught.isEmpty()) {
            lines.add(new Line("Teaches:", COLOR_HEADING));
            StringBuilder row = new StringBuilder(64);
            // Two per row: eleven skills (the Ranger guild) down a single column would run off the
            // panel, and one long comma-joined line would be truncated instead of wrapped.
            for (int index = 0; index < taught.size(); index++) {
                if (row.isEmpty()) {
                    row.append("  ");
                } else {
                    row.append(", ");
                }
                row.append(taught.get(index));
                if (index % 2 == 1 || index == taught.size() - 1) {
                    lines.add(new Line(row.toString(), COLOR_UNKNOWN));
                    row.setLength(0);
                }
            }
        }

        if (!supplies.isEmpty()) {
            lines.add(new Line("City economy:", COLOR_HEADING));
            for (ServiceNpcSpawnStateS2CPayload.SupplyLine supply : supplies) {
                lines.add(new Line(supplyText(supply), supplyColor(supply)));
            }
        }

        if (unstaffed) {
            lines.add(new Line(unstaffedReason(state), COLOR_NOTE));
        }

        return new ServiceNpcSpawnPresentation(lines);
    }

    /**
     * Registered with Rails but carrying no assigned NPC. That is the exact state an admin cannot
     * currently explain from the block: the configuration is correct and accepted, and nothing
     * spawned.
     */
    private static boolean isRegisteredButUnstaffed(ServiceNpcSpawnStateS2CPayload state) {
        return state.registrationState() == ServiceNpcSpawnRegistrationState.REGISTERED
                && state.assignedNpcPublicId() == null;
    }

    private static String unstaffedReason(ServiceNpcSpawnStateS2CPayload state) {
        return switch (state.eligibilityStatus()) {
            case BELOW_MINIMUM -> "No NPC: city economy is below the minimum above.";
            // Registered, economy fine, still nobody: staffing is applied by a Rails-side
            // reconciliation, not by this block, so the honest answer is "not yet" rather than
            // inventing a fault.
            case SATISFIED -> "No NPC yet: awaiting staffing reconciliation.";
            case UNKNOWN -> "No NPC: city supply figures unavailable to check.";
        };
    }

    private static int supplyColor(ServiceNpcSpawnStateS2CPayload.SupplyLine supply) {
        if (!supply.measured()) return COLOR_UNKNOWN;
        return supply.satisfied() ? COLOR_OK : COLOR_SHORTFALL;
    }

    private static String supplyText(ServiceNpcSpawnStateS2CPayload.SupplyLine supply) {
        StringBuilder text = new StringBuilder(32);
        text.append("  ").append(capitalize(supply.supply())).append(' ');
        if (supply.measured()) {
            appendAmount(text, supply.available());
        } else {
            text.append('?');
        }
        text.append(" / ");
        appendAmount(text, supply.required());
        return text.toString();
    }

    /**
     * One decimal only when there is one, so a treasury of 12 gold reads "12" rather than "12.0"
     * while 143.5 food keeps its precision. Hand-rolled rather than {@code String.format}, which
     * re-parses its pattern on every call.
     */
    static void appendAmount(StringBuilder out, double value) {
        long tenths = Math.round(value * 10.0D);
        long whole = tenths / 10L;
        long fraction = Math.abs(tenths % 10L);
        out.append(whole);
        if (fraction != 0L) out.append('.').append(fraction);
    }

    private static String capitalize(String value) {
        if (value == null || value.isEmpty()) return "";
        char first = value.charAt(0);
        if (Character.isUpperCase(first)) return value;
        return Character.toUpperCase(first) + value.substring(1);
    }
}
