package com.seggellion.britannia_mod.trader;

import java.util.List;

public record TraderAppearance(
        String outfitKey,
        List<String> allowedGenders,
        int hairIndex,
        int facialHairIndex,
        int shirtIndex,
        int chestIndex,
        int pantsIndex,
        int shoesIndex,
        int capeIndex
) {
    public static TraderAppearance outfit(String outfitKey) {
        return new TraderAppearance(outfitKey, List.of("male", "female"), 1, 1, 1, 1, 1, 1, 1);
    }

    public String safeOutfitKey() {
        return outfitKey == null ? "" : outfitKey;
    }

    public List<String> safeAllowedGenders() {
        return allowedGenders == null || allowedGenders.isEmpty()
                ? List.of("male", "female")
                : allowedGenders;
    }
}
