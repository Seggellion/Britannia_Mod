package com.seggellion.britannia_mod.client.screen.guild;

import com.seggellion.britannia_mod.client.screen.DialoguePresentation;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.GuildTrainingOpenS2CPayload;
import com.seggellion.britannia_mod.network.payload.GuildTrainingRequestC2SPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * The Guildmaster training screen. Guildmaster milestone 6.
 *
 * <p>A plain client {@link Screen}, matching {@code BankMainScreen} rather than inventing a
 * container menu — there is no inventory to bind and nothing to slot.
 *
 * <h2>It decides nothing</h2>
 * Every figure was computed on the server and arrives ready to draw; pressing a button sends a
 * slug and an entity id and nothing else. The server recomputes the entire quote before a coin
 * moves, so a stale screen cannot mislead a player into a price they will actually be charged.
 *
 * <p>Closes on purchase. The result arrives as chat, and the numbers on screen are stale the
 * instant the purchase commits — leaving it open would show a price that is no longer real.
 */
public final class GuildmasterTrainingScreen extends Screen {
    /** Guarded by GuildTranslationKeysTest, like every bank screen key. */
    public static final String KEY_CLOSE = "screen.britannia_mod.guild.close";

    private static final int ROW_HEIGHT = 24;
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 20;

    private final GuildTrainingOpenS2CPayload offer;

    public GuildmasterTrainingScreen(GuildTrainingOpenS2CPayload offer) {
        super(Component.literal(offer.guildDisplayName()));
        this.offer = offer;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - BUTTON_WIDTH) / 2;
        int top = 60;

        for (int index = 0; index < offer.offers().size(); index++) {
            GuildTrainingOpenS2CPayload.Offer row = offer.offers().get(index);
            Button button = Button.builder(
                    GuildTrainingOfferLabel.of(row),
                    ignored -> train(row.slug()))
                .bounds(x, top + index * ROW_HEIGHT, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build();
            // Disabled rather than hidden: a player needs to see that a skill is taught here and
            // that they have already maxed it, or that they simply cannot afford it today.
            button.active = row.purchasable();
            addRenderableWidget(button);
        }

        addRenderableWidget(Button.builder(Component.translatable(KEY_CLOSE), ignored -> onClose())
                .bounds(x, top + offer.offers().size() * ROW_HEIGHT + 10, BUTTON_WIDTH, BUTTON_HEIGHT)
                .build());
    }

    private void train(String skillSlug) {
        NetworkHandler.sendToServer(new GuildTrainingRequestC2SPayload(offer.entityId(), skillSlug));
        onClose();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        DialoguePresentation.renderPaperBackground(graphics, width, height);
        graphics.drawCenteredString(font, DialoguePresentation.text(offer.npcName()), width / 2, 24, 0xFFFFFF);
        graphics.drawCenteredString(font,
                DialoguePresentation.text(offer.guildDisplayName()), width / 2, 38, 0xC0C0C0);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
