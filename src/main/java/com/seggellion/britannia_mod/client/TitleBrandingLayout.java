package com.seggellion.britannia_mod.client;

/** Stable layout contract for the title-screen branding added on top of vanilla rendering. */
public final class TitleBrandingLayout {
    public static final String SUBTITLE = "Version 18";

    private static final int VANILLA_LOGO_TOP = 30;
    private static final int VANILLA_LOGO_HEIGHT = 44;
    private static final int SUBTITLE_GAP = 2;

    private TitleBrandingLayout() {
    }

    public static int subtitleCenterX(int screenWidth) {
        return screenWidth / 2;
    }

    public static int subtitleY() {
        return VANILLA_LOGO_TOP + VANILLA_LOGO_HEIGHT + SUBTITLE_GAP;
    }
}
