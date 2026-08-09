package com.seggellion.britannia_mod.bannerdyeing.registry;

/** The six reloadable definition domains owned by the banner and dyeing feature. */
public enum RegistryDomain {
    BANNER_DEFINITION("banner_definitions"),
    FABRIC_MATERIAL("fabric_materials"),
    PIGMENT("pigments"),
    MATERIAL_PALETTE("material_palettes"),
    MOUNT("banner_mounts"),
    PLACEMENT_PROFILE("placement_profiles");

    private final String folder;

    RegistryDomain(String folder) {
        this.folder = folder;
    }

    public String folder() {
        return folder;
    }
}
