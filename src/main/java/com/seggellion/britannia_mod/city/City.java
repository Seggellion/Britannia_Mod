package com.seggellion.britannia_mod.city;

import com.seggellion.britannia_mod.inventory.CityInventory;
import net.minecraft.nbt.CompoundTag;

public class City {
    private final String name;
    private final CityInventory inventory;

    public City(String name) {
        this.name = name;
        this.inventory = new CityInventory(name); // Pass cityName to the constructor
    }

    public String getName() {
        return name;
    }

    public CityInventory getInventory() {
        return inventory;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", name);

        CompoundTag inventoryTag = inventory.save(); // Call save() without arguments
        tag.put("Inventory", inventoryTag);

        return tag;
    }

    public static City load(CompoundTag tag) {
        String name = tag.getString("Name");
        City city = new City(name);

        CompoundTag inventoryTag = tag.getCompound("Inventory");
        city.getInventory().load(inventoryTag);

        return city;
    }
}
