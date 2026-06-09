package com.seggellion.britannia_mod.npc;

import java.util.Locale;

public final class TraderRoleHandlers {
    private TraderRoleHandlers() {
    }

    public static NpcRoleHandler create(NpcType npcType, String role, String city) {
        if (npcType == NpcType.MERCHANT) {
            return new MerchantRoleHandler(role, city);
        }

        String lowerRole = role == null ? "" : role.toLowerCase(Locale.ROOT);
        if (lowerRole.contains("salvage")) {
            return new SalvageTraderRoleHandler(role, city);
        }
        if (lowerRole.contains("alcohol") || lowerRole.contains("wine") || lowerRole.contains("vintner")) {
            return new AlcoholTraderRoleHandler(role, city);
        }
        return new TraderRoleHandler(role, city);
    }
}
