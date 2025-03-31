package com.seggellion.britannia_mod.util;

public class ToolQualityUtils {
    public static int getModelDataForMaterialKey(String registryKey) {
        return switch (registryKey) {
            case "pickaxe_gold", "viking_sword_gold"     -> 1001;
            case "pickaxe_iron", "viking_sword_iron"     -> 1002;
            case "pickaxe_valorite", "viking_sword_valorite" -> 1003;
            default -> 0;
        };
    }
}
