package com.seggellion.britannia_mod.city;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class CityManager extends SavedData {
    private static final String DATA_NAME = "britannia_city_manager";
    private final Map<String, City> cities = new HashMap<>();

    private static final Logger LOGGER = LogManager.getLogger();

    public CityManager() {
    }

    // Corrected load method signature
    public static CityManager load(CompoundTag tag, HolderLookup.Provider provider) {
        CityManager manager = new CityManager();
        ListTag citiesList = tag.getList("Cities", Tag.TAG_COMPOUND);
        for (int i = 0; i < citiesList.size(); i++) {
            CompoundTag cityTag = citiesList.getCompound(i);
            City city = City.load(cityTag);
            city.getInventory().setManager(manager);
            manager.cities.put(city.getName(), city);
        }
        return manager;
    }

    // Corrected save method signature
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag citiesList = new ListTag();
        for (City city : cities.values()) {
            citiesList.add(city.save());
        }
        tag.put("Cities", citiesList);
        return tag;
    }

    public static CityManager get(ServerLevel level) {
        SavedData.Factory<CityManager> factory = new SavedData.Factory<>(
            CityManager::new,   // Supplier<CityManager> constructor
            CityManager::load   // BiFunction<CompoundTag, HolderLookup.Provider, CityManager> deserializer
        );
        return level.getDataStorage().computeIfAbsent(factory, DATA_NAME);
    }

    public City getCity(String name) {
        return cities.get(name);
    }

    public void addCity(String name) {
        if (name == null || name.trim().isEmpty()) {
            return;
        }
        if (!cities.containsKey(name)) {
            City city = new City(name);
            city.getInventory().setManager(this); 
            cities.put(name, city);
            this.setDirty();
        }
    }

    public Map<String, City> getCities() {
        return cities;
    }
}
