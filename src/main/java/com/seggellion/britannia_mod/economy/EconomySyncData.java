package com.seggellion.britannia_mod.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.LinkedHashSet;
import java.util.Set;

public class EconomySyncData extends SavedData {
    private static final String DATA_NAME = "britannia_economy_sync";
    private static final int MAX_APPLIED_KEYS = 1024;

    private final LinkedHashSet<String> appliedTransactionKeys = new LinkedHashSet<>();

    public static EconomySyncData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(EconomySyncData::new, EconomySyncData::load),
                DATA_NAME
        );
    }

    public static EconomySyncData load(CompoundTag tag, HolderLookup.Provider provider) {
        EconomySyncData data = new EconomySyncData();
        ListTag keys = tag.getList("AppliedTransactionKeys", Tag.TAG_STRING);
        for (int i = 0; i < keys.size(); i++) {
            data.appliedTransactionKeys.add(keys.getString(i));
        }
        data.trim();
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag keys = new ListTag();
        for (String key : appliedTransactionKeys) {
            keys.add(StringTag.valueOf(key));
        }
        tag.put("AppliedTransactionKeys", keys);
        return tag;
    }

    public boolean markApplied(String key) {
        if (key == null || key.isBlank()) return false;
        if (appliedTransactionKeys.contains(key)) return false;
        appliedTransactionKeys.add(key);
        trim();
        setDirty();
        return true;
    }

    public boolean hasApplied(String key) {
        return key != null && appliedTransactionKeys.contains(key);
    }

    public Set<String> appliedTransactionKeys() {
        return Set.copyOf(appliedTransactionKeys);
    }

    private void trim() {
        while (appliedTransactionKeys.size() > MAX_APPLIED_KEYS) {
            String first = appliedTransactionKeys.iterator().next();
            appliedTransactionKeys.remove(first);
        }
    }
}
