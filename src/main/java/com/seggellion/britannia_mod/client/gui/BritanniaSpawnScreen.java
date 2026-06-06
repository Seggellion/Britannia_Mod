package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.BritanniaSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.BritanniaSpawnConfigC2SPayload;
import com.seggellion.britannia_mod.spawner.BritanniaSpawnableEntities;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class BritanniaSpawnScreen extends Screen {

    private final BlockPos pos;

    // initial values from server
    private ResourceLocation currentId;
    private int radius, minTicks, maxTicks, maxEntities;
    private boolean nightOnly;

    // widgets
    private EditBox monsterSearchBox;
    private EditBox radiusBox, minTicksBox, maxTicksBox, maxEntitiesBox;
    private Checkbox nightOnlyBox;
    private final List<ResourceLocation> activeEntities;
    private String currentSuggestion = "";
    private final List<ResourceLocation> availableEntities = new ArrayList<>();
    // The closest match based on what the user is typing
    private ResourceLocation currentBestMatch;

    public BritanniaSpawnScreen(BlockPos pos, ResourceLocation entityId,
                              int radius, int minTicks, int maxTicks,
                              boolean nightOnly, int maxEntities,
                              List<ResourceLocation> activeEntities) {
        super(Component.literal("Britannia Spawner"));
        this.pos = pos;
        this.currentId = entityId;
        this.radius = radius;
        this.minTicks = minTicks;
        this.maxTicks = maxTicks;
        this.nightOnly = nightOnly;
        this.maxEntities = maxEntities;
        this.activeEntities = new ArrayList<>(activeEntities);
    }

    @Override
    protected void init() {
        availableEntities.clear();
        availableEntities.addAll(BritanniaSpawnableEntities.allowedIds());

        currentBestMatch = currentId;

        int cx = this.width / 2;
        int cy = this.height / 2;

        monsterSearchBox = new EditBox(this.font, cx - 110, cy - 50, 220, 20, Component.literal("Entity Search"));
        monsterSearchBox.setMaxLength(100);
        monsterSearchBox.setValue(initialSearchValue(currentId));
        
        monsterSearchBox.setResponder(this::updateSearchSuggestion);
        // Trigger the initial suggestion text
        updateSearchSuggestion(monsterSearchBox.getValue());
        addRenderableWidget(monsterSearchBox);

        // input fields
        radiusBox   = new EditBox(this.font, cx - 110, cy - 20, 60, 20, Component.literal("Radius"));
        minTicksBox = new EditBox(this.font, cx - 40,  cy - 20, 60, 20, Component.literal("Min T"));
        maxTicksBox = new EditBox(this.font, cx + 30,  cy - 20, 60, 20, Component.literal("Max T"));
        maxEntitiesBox = new EditBox(this.font, cx - 110, cy + 10, 60, 20, Component.literal("Max Entities"));

        radiusBox.setValue(Integer.toString(radius));
        minTicksBox.setValue(Integer.toString(minTicks));
        maxTicksBox.setValue(Integer.toString(maxTicks));
        maxEntitiesBox.setValue(Integer.toString(maxEntities));

        addRenderableWidget(radiusBox);
        addRenderableWidget(minTicksBox);
        addRenderableWidget(maxTicksBox);
        addRenderableWidget(maxEntitiesBox);

        nightOnlyBox = Checkbox.builder(Component.literal("Night only"), this.font)
                .pos(cx + 30, cy + 10)
                .selected(nightOnly)
                .build();
        addRenderableWidget(nightOnlyBox);

        addRenderableWidget(Button.builder(Component.literal("Save"), b -> saveAndClose())
                .bounds(cx - 110, cy + 40, 80, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), b -> onClose())
                .bounds(cx + 30, cy + 40, 80, 20).build());
    }

    // Intercept TAB key to autocomplete the text box
@Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_TAB && monsterSearchBox.isFocused()) {
            if (!currentSuggestion.isEmpty() && !currentSuggestion.startsWith(" (")) {
                monsterSearchBox.setValue(monsterSearchBox.getValue() + currentSuggestion);
                currentSuggestion = "";
                monsterSearchBox.setSuggestion("");
                return true; // Consume the keypress
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

private void updateSearchSuggestion(String text) {
        String typed = text.toLowerCase(Locale.ROOT);
        
        List<ResourceLocation> matches = availableEntities.stream()
                .filter(rl -> {
                    String label = BritanniaSpawnableEntities.displayName(rl).toLowerCase(Locale.ROOT);
                    return rl.getPath().contains(typed) || rl.toString().contains(typed) || label.contains(typed);
                })
                .sorted((a, b) -> {
                    String pathA = a.getPath();
                    String pathB = b.getPath();
                    String labelA = BritanniaSpawnableEntities.displayName(a).toLowerCase(Locale.ROOT);
                    String labelB = BritanniaSpawnableEntities.displayName(b).toLowerCase(Locale.ROOT);
                    
                    // 1. Exact matches get absolute highest priority
                    if (pathA.equals(typed) || labelA.equals(typed)) return -1;
                    if (pathB.equals(typed) || labelB.equals(typed)) return 1;
                    
                    // 2. Prefix matches ("starts with") get second priority
                    boolean aStarts = pathA.startsWith(typed) || labelA.startsWith(typed);
                    boolean bStarts = pathB.startsWith(typed) || labelB.startsWith(typed);
                    if (aStarts && !bStarts) return -1;
                    if (!aStarts && bStarts) return 1;
                    
                    // 3. If both are equal up to this point, prioritize the shorter string
                    return Integer.compare(pathA.length(), pathB.length());
                })
                .toList();

        if (matches.isEmpty() || typed.isEmpty()) {
            currentSuggestion = "";
            monsterSearchBox.setSuggestion("");
            currentBestMatch = null;
        } else {
            currentBestMatch = matches.get(0);
            String matchPath = currentBestMatch.getPath();
            
            // If they are typing the exact start of the word, show the rest as phantom text
            if (matchPath.startsWith(typed)) {
                currentSuggestion = matchPath.substring(typed.length());
                monsterSearchBox.setSuggestion(currentSuggestion);
            } else {
                // If it matches somewhere in the middle, just show the match in parentheses
                currentSuggestion = " (" + matchPath + ")";
                monsterSearchBox.setSuggestion(currentSuggestion);
            }
        }
    }

    private void saveAndClose() {
        try {
            int r  = Math.max(1, Integer.parseInt(radiusBox.getValue().trim()));
            int mn = Math.max(1, Integer.parseInt(minTicksBox.getValue().trim()));
            int mx = Math.max(mn, Integer.parseInt(maxTicksBox.getValue().trim()));
            int me = Math.max(0, Integer.parseInt(maxEntitiesBox.getValue().trim()));
            
            // Fallback to the current ID if they typed complete gibberish
            ResourceLocation idToSave = currentBestMatch != null ? currentBestMatch : currentId;

            // send to server with the new config payload
            NetworkHandler.sendToServer(new BritanniaSpawnConfigC2SPayload(
                    pos, idToSave, r, mn, mx, nightOnlyBox.selected(), me
            ));
        } catch (Exception ignored) {
        } finally {
            onClose();
        }
    }

    private String initialSearchValue(ResourceLocation id) {
        return "minecraft".equals(id.getNamespace()) ? BritanniaSpawnableEntities.displayName(id) : id.getPath();
    }

    @Override
    public void render(GuiGraphics gg, int mouseX, int mouseY, float pt) {
        this.renderBackground(gg, mouseX, mouseY, pt);

        // draw widgets first (buttons, edit boxes, etc.)
        super.render(gg, mouseX, mouseY, pt);

        int cx = this.width / 2;
        int cy = this.height / 2;

        // title
        gg.drawCenteredString(this.font, this.title, this.width / 2, cy - 100, 0xFFFFFF);

        // labels
        gg.drawString(this.font, "Entity", cx - 110, cy - 62, 0xA0A0A0);
        gg.drawString(this.font, "Radius", cx - 110, cy - 32, 0xFFFFFF);
        gg.drawString(this.font, "Min Ticks", cx - 40, cy - 32, 0xFFFFFF);
        gg.drawString(this.font, "Max Ticks", cx + 30, cy - 32, 0xFFFFFF);
        gg.drawString(this.font, "Max Entities", cx - 110, cy - 2, 0xFFFFFF);
        gg.drawString(this.font, "Night Only", cx + 30, cy - 2, 0xFFFFFF);

        gg.drawString(this.font, "Active Spawns:", cx - 110, cy + 70, 0xFFFFFF);

        // Warning if typing doesn't match anything
        if (currentBestMatch == null && !monsterSearchBox.getValue().isEmpty()) {
            gg.drawString(this.font, "Invalid ID! Will not save.", cx + 5, cy + 45, 0xFF5555);
        }

        int y = cy + 85;
        for (int i = 0; i < activeEntities.size(); i++) {
            String name = BritanniaSpawnableEntities.displayName(activeEntities.get(i));
            gg.drawString(this.font, "- " + name, cx - 110, y + i * 10, 0xA0A0A0);
        }
    }

    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
