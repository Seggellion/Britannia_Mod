package com.seggellion.britannia_mod.service;

import java.util.Objects;

public final class SafeDialogueInterpolator {
    private SafeDialogueInterpolator() {
    }

    public static String interpolate(String template, ServiceDialogueContext context) {
        Objects.requireNonNull(template, "template");
        Objects.requireNonNull(context, "context");

        String result = template
                .replace("%{city_name}", context.cityName())
                .replace("%{npc_name}", context.npcName())
                .replace("%{profession_name}", context.professionName());
        if (result.indexOf('%') >= 0) {
            throw new IllegalArgumentException("Unsupported or malformed dialogue interpolation");
        }
        return result;
    }
}
