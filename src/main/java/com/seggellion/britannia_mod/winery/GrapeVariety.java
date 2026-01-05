package com.seggellion.britannia_mod.winery;

public record GrapeVariety(
    String id,
    String displayName,
    int optimalHydration,
    
    // Soil Chemistry
    float requiredNitrogen,
    float requiredPhosphorus,
    float requiredPotassium,
    float requiredOrganicMatter,
        
    // Altitude Requirements (Y-Level)
    String climate,
    int minAltitude,  // e.g., 60 (Sea Level)
    int maxAltitude,  // e.g., 120 (Mountainous)
    
    int baseColor,
    int difficulty,
    GrapeColor colorType
) {
    public String getFormattedName() {
        return displayName;
    }
}