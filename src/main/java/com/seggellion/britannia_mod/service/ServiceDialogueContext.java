package com.seggellion.britannia_mod.service;

public record ServiceDialogueContext(
        String npcName,
        String npcGender,
        String professionName,
        String cityName
) {
    public ServiceDialogueContext {
        npcName = valueOrEmpty(npcName);
        npcGender = valueOrEmpty(npcGender);
        professionName = valueOrEmpty(professionName);
        cityName = valueOrEmpty(cityName);
    }

    private static String valueOrEmpty(String value) {
        return value == null ? "" : value;
    }
}
