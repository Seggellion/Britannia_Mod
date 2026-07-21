package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.dye.preview.DyePreviewDisplayData;
import com.seggellion.britannia_mod.dye.preview.DyePreviewViewModel;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;
import com.seggellion.britannia_mod.network.payload.dye.C2SCancelDyePreviewPayload;
import com.seggellion.britannia_mod.network.payload.dye.C2SConfirmDyeApplicationPayload;
import com.seggellion.britannia_mod.network.payload.dye.S2CDyeApplicationResultPayload;
import com.seggellion.britannia_mod.network.payload.dye.S2COpenDyePreviewPayload;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

/** Text, static item icon, and authoritative swatches only; layered heraldry begins in Milestone 9. */
@OnlyIn(Dist.CLIENT)
public final class DyePreviewScreen extends Screen {
    private final DyePreviewViewModel model;
    private Button applyButton;
    private boolean terminalResponse;
    private boolean cancelSent;

    public DyePreviewScreen(S2COpenDyePreviewPayload payload) {
        super(Component.translatable("screen.britannia_mod.dye_preview.title"));
        model = new DyePreviewViewModel(
                payload.sessionId(), payload.displayData(), payload.lifetimeMillis(), System::currentTimeMillis);
    }

    @Override
    protected void init() {
        int center = width / 2;
        int bottom = height / 2 + 102;
        addRenderableWidget(Button.builder(Component.translatable("screen.britannia_mod.dye_preview.cancel"),
                button -> cancelAndClose()).bounds(center - 104, bottom, 98, 20).build());
        applyButton = addRenderableWidget(Button.builder(
                Component.translatable("screen.britannia_mod.dye_preview.apply"), button -> apply())
                .bounds(center + 6, bottom, 98, 20).build());
        applyButton.active = model.applyEnabled();
    }

    private void apply() {
        if (!model.beginConfirmation()) {
            return;
        }
        applyButton.active = false;
        ClientNetworkHandler.sendToServer(new C2SConfirmDyeApplicationPayload(model.sessionId()));
    }

    private void cancelAndClose() {
        if (!cancelSent && !terminalResponse && !model.confirmationInFlight()) {
            cancelSent = true;
            ClientNetworkHandler.sendToServer(new C2SCancelDyePreviewPayload(model.sessionId()));
        }
        Minecraft.getInstance().setScreen(null);
    }

    public void handleResult(S2CDyeApplicationResultPayload payload) {
        if (!model.sessionId().equals(payload.sessionId())) {
            return;
        }
        terminalResponse = true;
        model.markServerInvalid();
        if (applyButton != null) {
            applyButton.active = false;
        }
        if (payload.closeScreen()) {
            Minecraft.getInstance().setScreen(null);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (applyButton != null) {
            applyButton.active = model.applyEnabled();
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        DyePreviewDisplayData data = model.displayData();
        int left = width / 2 - 145;
        int top = height / 2 - 112;
        graphics.drawCenteredString(font, title, width / 2, top, 0xFFFFFF);
        graphics.renderItem(new ItemStack(BannerItemRegistry.BANNER.get()), left + 8, top + 19);
        drawLine(graphics, "screen.britannia_mod.dye_preview.banner", data.bannerNameKey(), left + 36, top + 20);
        drawLine(graphics, "screen.britannia_mod.dye_preview.material", data.materialNameKey(), left + 36, top + 34);
        drawLine(graphics, "screen.britannia_mod.dye_preview.mount", data.mountNameKey(), left + 36, top + 48);
        drawLine(graphics, "screen.britannia_mod.dye_preview.current_colour",
                data.currentColourNameKey(), left, top + 70);
        data.currentPigmentNameKey().ifPresent(key -> drawLine(graphics,
                "screen.britannia_mod.dye_preview.current_pigment", key, left, top + 84));
        drawLine(graphics, "screen.britannia_mod.dye_preview.tub_pigment",
                data.tubPigmentNameKey(), left, top + 100);
        drawLine(graphics, "screen.britannia_mod.dye_preview.new_colour",
                data.newColourNameKey(), left, top + 114);
        Component match = data.matchType() == com.seggellion.britannia_mod.dye.service.MatchType.NEAREST_COLOUR
                ? Component.translatable(model.matchLabelKey(), Component.translatable(data.materialNameKey()))
                : Component.translatable(model.matchLabelKey());
        graphics.drawString(font, match, left, top + 130, 0xE0E0E0);

        int swatchY = top + 150;
        graphics.drawString(font, Component.translatable("screen.britannia_mod.dye_preview.swatch_current"),
                left, swatchY - 12, 0xC0C0C0);
        graphics.fill(left, swatchY, left + 64, swatchY + 24, 0xFF000000 | data.currentSrgb());
        graphics.drawCenteredString(font, Component.literal("→"), width / 2, swatchY + 8, 0xFFFFFF);
        graphics.drawString(font, Component.translatable("screen.britannia_mod.dye_preview.swatch_new"),
                left + 220, swatchY - 12, 0xC0C0C0);
        graphics.fill(left + 220, swatchY, left + 284, swatchY + 24, 0xFF000000 | data.newSrgb());
        if (data.placeholder()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.britannia_mod.dye_preview.placeholder_warning"),
                    width / 2, top + 181, 0xFFE080);
        }
        if (data.provisionalDimensions()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.britannia_mod.dye_preview.provisional_warning"),
                    width / 2, top + 193, 0xA0A0A0);
        }
        if (model.expired()) {
            graphics.drawCenteredString(font,
                    Component.translatable("screen.britannia_mod.dye_preview.expired"),
                    width / 2, top + 207, 0xFF6060);
        }
    }

    private void drawLine(GuiGraphics graphics, String labelKey, String valueKey, int x, int y) {
        graphics.drawString(font, Component.translatable(labelKey, Component.translatable(valueKey)), x, y, 0xE0E0E0);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE
                || (minecraft != null && minecraft.options.keyInventory.matches(keyCode, scanCode))) {
            cancelAndClose();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void onClose() {
        cancelAndClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
