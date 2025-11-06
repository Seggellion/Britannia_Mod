package com.seggellion.britannia_mod.client;

import com.seggellion.britannia_mod.client.gui.SkillTableScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;
import com.mojang.blaze3d.platform.InputConstants;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

public class Keybinds {
        private static final Logger LOGGER = LogUtils.getLogger();


    public static final KeyMapping OPEN_SKILL_SCREEN = new KeyMapping(
        "key.britannia_mod.open_skills",
        InputConstants.Type.KEYSYM,
        GLFW.GLFW_KEY_O,
        "key.categories.ui"
    );

    // Register the keybinding itself (mod event bus only!)
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(OPEN_SKILL_SCREEN);
    }

    // Register the actual key handling (common NeoForge bus)
    public static void registerInputHandler() {
        NeoForge.EVENT_BUS.addListener(Keybinds::onKeyPress);
    }

    private static void onKeyPress(InputEvent.Key event) {
        if (OPEN_SKILL_SCREEN.consumeClick()) {
            LOGGER.info("buttonpress occurred");
            Minecraft.getInstance().setScreen(new SkillTableScreen());
        }
    }
}
