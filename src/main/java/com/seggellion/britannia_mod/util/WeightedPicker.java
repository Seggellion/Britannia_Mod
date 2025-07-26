package com.seggellion.britannia_mod.util;

import java.util.List;
import java.util.Random;

public final class WeightedPicker {
    private static final Random RNG = new Random();

    public static <T extends HasWeight> T pick(List<T> list) {
        if (list.isEmpty()) return null;

        int total = 0;
        for (T t : list) total += Math.max(0, t.weight());

        if (total <= 0) return null;

        int roll = RNG.nextInt(total);
        int cum  = 0;

        for (T t : list) {
            cum += Math.max(0, t.weight());
            if (roll < cum) return t;
        }
        return list.get(list.size() - 1); // fallback
    }

    public interface HasWeight {
        int weight();
    }
}
