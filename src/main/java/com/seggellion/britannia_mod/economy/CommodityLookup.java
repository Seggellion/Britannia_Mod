package com.seggellion.britannia_mod.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public final class CommodityLookup {
    private final List<CityCommodity> commodities;

    public CommodityLookup(Collection<CityCommodity> commodities) {
        this.commodities = List.copyOf(commodities);
    }

    public List<CityCommodity> byCategory(String category) {
        String normalizedCategory = CityCommodity.normalize(category);
        List<CityCommodity> out = new ArrayList<>();
        for (CityCommodity commodity : commodities) {
            if (commodity.category().equals(normalizedCategory)) {
                out.add(commodity);
            }
        }
        return out;
    }

    public Optional<CityCommodity> find(String category, String... aliases) {
        String normalizedCategory = CityCommodity.normalize(category);
        for (String alias : aliases) {
            String normalizedAlias = CityCommodity.normalize(alias);
            for (CityCommodity commodity : commodities) {
                if (!normalizedCategory.isBlank() && !commodity.category().equals(normalizedCategory)) continue;
                if (commodity.normalizedKey().equals(normalizedAlias)
                        || CityCommodity.normalize(commodity.itemName()).equals(normalizedAlias)) {
                    return Optional.of(commodity);
                }
            }
        }
        return Optional.empty();
    }
}
