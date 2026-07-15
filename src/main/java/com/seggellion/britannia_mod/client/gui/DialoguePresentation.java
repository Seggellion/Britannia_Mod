package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.client.render.PortraitDownloader;
import com.seggellion.britannia_mod.dialogue.DialogueLayout;
import com.seggellion.britannia_mod.dialogue.DialogueOptionViewModel;
import com.seggellion.britannia_mod.dialogue.DialogueViewModel;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;

import java.util.function.Consumer;

public final class DialoguePresentation {
    public static final ResourceLocation PAPER_BACKGROUND = ResourceLocation.fromNamespaceAndPath(
            "britannia_mod",
            "textures/screens/dialogue_screen.png"
    );
    public static final ResourceLocation FONT_UO_CLASSIC = ResourceLocation.fromNamespaceAndPath(
            "britannia_mod",
            "uo_classic"
    );
    public static final Style UO_STYLE = Style.EMPTY.withFont(FONT_UO_CLASSIC);
    public static final int TEXT_COLOR = 0xFF111111;
    public static final int PORTRAIT_TEXTURE_SIZE = 108;
    public static final int PORTRAIT_CROP_MARGIN = 10;
    public static final int PORTRAIT_VISIBLE_SIZE =
            PORTRAIT_TEXTURE_SIZE - (PORTRAIT_CROP_MARGIN * 2);
    public static final int TEXT_RENDER_X =
            DialogueLayout.PORTRAIT_X + PORTRAIT_VISIBLE_SIZE + 25;

    private DialoguePresentation() {
    }

    public static Component text(String value) {
        return Component.literal(value == null ? "" : value).withStyle(UO_STYLE);
    }

    public static Button optionButton(
            DialogueOptionViewModel option,
            int index,
            DialogueLayout layout,
            Consumer<String> onSelected
    ) {
        Button button = Button.builder(text(option.label()), ignored -> onSelected.accept(option.id()))
                .bounds(
                        layout.buttonStartX(),
                        layout.buttonStartY() + (index * (20 + DialogueLayout.BUTTON_GAP)),
                        layout.buttonWidth(),
                        20
                )
                .build();
        button.active = !option.locked();
        return button;
    }

    public static void renderDialogue(
            GuiGraphics graphics,
            Font font,
            DialogueViewModel view,
            DialogueLayout layout,
            Component bodyComponent,
            String fallbackTitle
    ) {
        if (!view.npcName().isEmpty()) {
            ResourceLocation portrait = PortraitDownloader.getPortrait(view.npcName(), view.npcGender());
            graphics.blit(
                    portrait,
                    DialogueLayout.PORTRAIT_X,
                    layout.portraitY(),
                    PORTRAIT_CROP_MARGIN,
                    PORTRAIT_CROP_MARGIN,
                    PORTRAIT_VISIBLE_SIZE,
                    PORTRAIT_VISIBLE_SIZE,
                    PORTRAIT_TEXTURE_SIZE,
                    PORTRAIT_TEXTURE_SIZE
            );
            Component name = text(view.npcName());
            int nameX = DialogueLayout.PORTRAIT_X
                    + (PORTRAIT_VISIBLE_SIZE / 2)
                    - (font.width(name) / 2);
            int nameY = layout.portraitY() + PORTRAIT_VISIBLE_SIZE + 3;
            graphics.drawString(font, name, nameX, nameY, TEXT_COLOR, false);

            if (!view.professionLabel().isBlank()) {
                Component profession = text(view.professionLabel());
                int professionX = DialogueLayout.PORTRAIT_X
                        + (PORTRAIT_VISIBLE_SIZE / 2)
                        - (font.width(profession) / 2);
                graphics.drawString(
                        font,
                        profession,
                        professionX,
                        nameY + font.lineHeight + 1,
                        TEXT_COLOR,
                        false
                );
            }

            graphics.drawWordWrap(
                    font,
                    bodyComponent,
                    TEXT_RENDER_X,
                    layout.textY(),
                    layout.maxTextWidth(),
                    TEXT_COLOR
            );
        } else {
            Component title = text(view.title().isBlank() ? fallbackTitle : view.title());
            graphics.drawString(
                    font,
                    title,
                    DialogueLayout.PORTRAIT_X,
                    layout.portraitY(),
                    TEXT_COLOR,
                    false
            );
            graphics.drawWordWrap(
                    font,
                    bodyComponent,
                    DialogueLayout.PORTRAIT_X,
                    layout.textY() + 15,
                    layout.maxTextWidth() + PORTRAIT_VISIBLE_SIZE,
                    TEXT_COLOR
            );
        }
    }

    public static void renderPaperBackground(
            GuiGraphics graphics,
            int width,
            int height
    ) {
        graphics.fill(0, 0, width, height, 0xCC000000);
        graphics.blit(
                PAPER_BACKGROUND,
                0,
                0,
                0,
                0,
                width,
                DialogueLayout.TOP_SECTION_HEIGHT,
                width,
                DialogueLayout.TOP_SECTION_HEIGHT
        );
    }
}
