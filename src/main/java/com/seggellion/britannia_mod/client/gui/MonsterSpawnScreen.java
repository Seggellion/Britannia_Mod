package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.MonsterSpawnScreenS2CPayload;
import com.seggellion.britannia_mod.network.payload.MonsterSpawnConfigC2SPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

import java.util.ArrayList;
import java.util.List;

public class MonsterSpawnScreen extends Screen {

    private final BlockPos pos;

    // initial values from server
    private ResourceLocation currentId;
    private int radius, minTicks, maxTicks, maxEntities;
    private boolean nightOnly;

    // widgets
    private EditBox radiusBox, minTicksBox, maxTicksBox, maxEntitiesBox;
    private Checkbox nightOnlyBox;
    private Button monsterButton;
    private final List<ResourceLocation> activeEntities;

    // list of monster ids + current index
    private List<ResourceLocation> monsterIds = new ArrayList<>();
    private int idx = 0;

    public MonsterSpawnScreen(BlockPos pos, ResourceLocation entityId,
                              int radius, int minTicks, int maxTicks,
                              boolean nightOnly, int maxEntities,
                          List<ResourceLocation> activeEntities) {
        super(Component.literal("Monster Spawner"));
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
        // gather monsters (filter: only britannia_mod namespace + MONSTER category)
        for (EntityType<?> t : BuiltInRegistries.ENTITY_TYPE) {
            if (t.getCategory() == MobCategory.MONSTER) {
                ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(t);
                if ("britannia_mod".equals(key.getNamespace())) {
                    monsterIds.add(key);
                }
            }
        }
        if (monsterIds.isEmpty()) {
            // fallback: include at least one
            monsterIds.add(BuiltInRegistries.ENTITY_TYPE.getKey(EntityType.ZOMBIE));
        }

        idx = Math.max(0, monsterIds.indexOf(currentId));
        if (idx < 0) idx = 0;

        int cx = this.width / 2;
        int cy = this.height / 2;

        // monster "dropdown" (cycles ids)
        monsterButton = Button.builder(Component.literal(monsterIds.get(idx).toString()), b -> {
            idx = (idx + 1) % monsterIds.size();
            b.setMessage(Component.literal(monsterIds.get(idx).toString()));
        }).bounds(cx - 110, cy - 50, 220, 20).build();
        addRenderableWidget(monsterButton);

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

private void saveAndClose() {
    try {
        int r  = Math.max(1, Integer.parseInt(radiusBox.getValue().trim()));
        int mn = Math.max(1, Integer.parseInt(minTicksBox.getValue().trim()));
        int mx = Math.max(mn, Integer.parseInt(maxTicksBox.getValue().trim()));
        int me = Math.max(0, Integer.parseInt(maxEntitiesBox.getValue().trim()));
        ResourceLocation id = monsterIds.get(idx);

        // send to server with the new config payload
        NetworkHandler.sendToServer(new MonsterSpawnConfigC2SPayload(
                pos, id, r, mn, mx, nightOnlyBox.selected(), me
        ));
    } catch (Exception ignored) {
    } finally {
        onClose();
    }
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
    gg.drawString(this.font, "Monster", cx - 110, cy - 62, 0xFFFFFF);
    gg.drawString(this.font, "Radius", cx - 110, cy - 32, 0xFFFFFF);
    gg.drawString(this.font, "Min Ticks", cx - 40, cy - 32, 0xFFFFFF);
    gg.drawString(this.font, "Max Ticks", cx + 30, cy - 32, 0xFFFFFF);
    gg.drawString(this.font, "Max Entities", cx - 110, cy - 2, 0xFFFFFF);
    gg.drawString(this.font, "Night Only", cx + 30, cy - 2, 0xFFFFFF);

    gg.drawString(this.font, "Active Spawns:", cx - 110, cy + 70, 0xFFFFFF);

    int y = cy + 85;
    for (int i = 0; i < activeEntities.size(); i++) {
        String name = activeEntities.get(i).toString();
        gg.drawString(this.font, "- " + name, cx - 110, y + i * 10, 0xA0A0A0);
    }

}




    @Override public boolean isPauseScreen() { return false; }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(null);
    }
}
