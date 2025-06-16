package com.seggellion.britannia_mod.city;

import com.seggellion.britannia_mod.inventory.CityInventory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

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

    public void removeNpcsForBlock(BlockPos blockPos) {
        inventory.removeNpcsForBlock(blockPos); // Delegate to CityInventory
    }

    public void associateNpcWithBlock(BlockPos blockPos, Entity npc) {
        inventory.associateNpcWithBlock(blockPos, npc); // Delegate to CityInventory
    }


    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("CityName", name);

        CompoundTag inventoryTag = inventory.save(); // Call save() without arguments
        tag.put("Inventory", inventoryTag);

        return tag;
    }

    public static City load(CompoundTag tag) {
        String name = tag.getString("CityName");
        City city = new City(name);

        CompoundTag inventoryTag = tag.getCompound("Inventory");
        city.getInventory().load(inventoryTag);

        return city;
    }
}
