package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.menu.ServiceNpcSpawnMenu;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnConfigureC2SPayload;
import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnResyncC2SPayload;
import com.seggellion.britannia_mod.network.payload.ServiceNpcSpawnStateS2CPayload;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ServiceNpcSpawnScreen extends AbstractContainerScreen<ServiceNpcSpawnMenu> {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private ServiceNpcSpawnStateS2CPayload state;
    private UUID selectedCityId;
    private String selectedTypeKey;
    private boolean selectedEnabled = true;
    private Button saveButton;
    private final Map<String, UUID> cityLabels = new LinkedHashMap<>();
    private final Map<String, String> typeLabels = new LinkedHashMap<>();

    public ServiceNpcSpawnScreen(ServiceNpcSpawnMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 360;
        imageHeight = 260;
        inventoryLabelY = 10_000;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos + 12;
        int y = topPos + 32;

        if (state != null) {
            buildCityDropdown(x, y);
            buildTypeDropdown(x, y + 32);
        }

        addRenderableWidget(Button.builder(
                Component.literal("Enabled: " + (selectedEnabled ? "Yes" : "No")),
                button -> {
                    selectedEnabled = !selectedEnabled;
                    button.setMessage(Component.literal("Enabled: " + (selectedEnabled ? "Yes" : "No")));
                }
        ).bounds(x, y + 64, 210, 20).build());

        saveButton = Button.builder(Component.literal("Save"), button -> save())
                .bounds(x, y + 96, 65, 20).build();
        updateSaveButton();
        addRenderableWidget(saveButton);
        addRenderableWidget(Button.builder(Component.literal("Refresh"), button -> refresh())
                .bounds(x + 72, y + 96, 65, 20).build());
        addRenderableWidget(Button.builder(Component.literal("Close"), button -> onClose())
                .bounds(x + 144, y + 96, 65, 20).build());
    }

    private void buildCityDropdown(int x, int y) {
        cityLabels.clear();
        for (ServiceNpcSpawnStateS2CPayload.CityOption option : state.cityOptions()) {
            String label = uniqueLabel(cityLabels, option.displayName(), option.publicId().toString());
            cityLabels.put(label, option.publicId());
        }
        String selectedLabel = labelForValue(cityLabels, selectedCityId);
        if (selectedLabel == null) {
            selectedLabel = selectedCityId == null ? "Select city" : "Unavailable (" + selectedCityId + ")";
        }
        StringDropdownWidget dropdown = new StringDropdownWidget(
                x,
                y,
                210,
                20,
                selectedLabel,
                new ArrayList<>(cityLabels.keySet()),
                label -> {
                    selectedCityId = cityLabels.get(label);
                    updateSaveButton();
                }
        );
        dropdown.active = state.cityRegistryAvailable();
        addRenderableWidget(dropdown);
    }

    private void buildTypeDropdown(int x, int y) {
        typeLabels.clear();
        for (ServiceNpcSpawnStateS2CPayload.ServiceTypeOption option : state.serviceTypeOptions()) {
            String label = uniqueLabel(typeLabels, option.displayName(), option.key());
            typeLabels.put(label, option.key());
        }
        String selectedLabel = labelForValue(typeLabels, selectedTypeKey);
        if (selectedLabel == null) {
            selectedLabel = selectedTypeKey == null ? "Select Service NPC type" : "Unavailable (" + selectedTypeKey + ")";
        }
        StringDropdownWidget dropdown = new StringDropdownWidget(
                x,
                y,
                210,
                20,
                selectedLabel,
                new ArrayList<>(typeLabels.keySet()),
                label -> {
                    selectedTypeKey = typeLabels.get(label);
                    updateSaveButton();
                }
        );
        dropdown.active = state.serviceTypeRegistryAvailable();
        addRenderableWidget(dropdown);
    }

    private void save() {
        if (!canSave()) return;
        NetworkHandler.sendToServer(new ServiceNpcSpawnConfigureC2SPayload(
                menu.containerId,
                menu.pos(),
                menu.spawnPointId(),
                state.configurationRevision(),
                selectedCityId,
                selectedTypeKey,
                selectedEnabled
        ));
    }

    private void refresh() {
        long expectedRevision = state == null ? 0L : state.configurationRevision();
        NetworkHandler.sendToServer(new ServiceNpcSpawnResyncC2SPayload(
                menu.containerId,
                menu.pos(),
                menu.spawnPointId(),
                expectedRevision
        ));
    }

    private boolean canSave() {
        if (state == null || !state.blockPresent()
                || !state.cityRegistryAvailable() || !state.serviceTypeRegistryAvailable()) return false;
        return selectedCityId != null
                && selectedTypeKey != null
                && cityLabels.containsValue(selectedCityId)
                && typeLabels.containsValue(selectedTypeKey);
    }

    private void updateSaveButton() {
        if (saveButton != null) saveButton.active = canSave();
    }

    public void acceptState(ServiceNpcSpawnStateS2CPayload payload) {
        if (payload.containerId() != menu.containerId
                || !payload.pos().equals(menu.pos())
                || !payload.spawnPointId().equals(menu.spawnPointId())) return;
        state = payload;
        selectedCityId = payload.cityPublicId();
        selectedTypeKey = payload.serviceNpcTypeKey();
        selectedEnabled = payload.enabled();
        rebuildWidgets();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xE0101010);
        graphics.renderOutline(leftPos, topPos, imageWidth, imageHeight, 0xFF808080);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 10, 0xFFFFFF, false);
        graphics.drawString(font, "City", 12, 22, 0xB0B0B0, false);
        graphics.drawString(font, "Service NPC type", 12, 54, 0xB0B0B0, false);
        if (state == null) {
            graphics.drawString(font, "Waiting for authoritative server state...", 12, 158, 0xFFCC66, false);
            return;
        }
        int x = 226;
        int y = 32;
        graphics.drawString(font, "Spawn Point", x, y, 0xB0B0B0, false);
        graphics.drawString(font, shortId(state.spawnPointId()), x, y + 10, 0xFFFFFF, false);
        graphics.drawString(font, "Status: " + displayStatus(), x, y + 28, statusColor(), false);
        graphics.drawString(font, "Config rev: " + state.configurationRevision(), x, y + 44, 0xFFFFFF, false);
        graphics.drawString(font, "Assigned: " + (state.assignedNpcDisplayName() == null
                ? "Unassigned" : state.assignedNpcDisplayName()), x, y + 60, 0xFFFFFF, false);
        graphics.drawString(font, "Assignment rev: " + state.assignmentRevision(), x, y + 76, 0xFFFFFF, false);
        graphics.drawString(font, "Last sync: " + (state.lastSuccessfulSyncEpochMillis() == null
                ? "Never" : TIME_FORMAT.format(Instant.ofEpochMilli(state.lastSuccessfulSyncEpochMillis()))),
                x, y + 92, 0xFFFFFF, false);
        if (state.assignedNpcPublicId() != null) {
            graphics.drawString(font, "NPC UUID: " + shortId(state.assignedNpcPublicId()), x, y + 108, 0xFFFFFF, false);
        }
        if (state.validationError() != com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError.NONE
                && state.validationError() != com.seggellion.britannia_mod.service.spawn.ServiceNpcSpawnValidationError.NO_CHANGE) {
            graphics.drawString(font, "Request: " + state.validationError().name(), 12, 158, 0xFF6666, false);
        }
        if (state.lastErrorCode() != null) {
            graphics.drawString(font, "Error: " + state.lastErrorCode(), 12, 174, 0xFF6666, false);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private String displayStatus() {
        if (!state.blockPresent()) return "Missing block";
        if (!state.cityRegistryAvailable() || !state.serviceTypeRegistryAvailable()) return "Registry unavailable";
        if (!state.citySelectionValid() && state.cityPublicId() != null) return "Invalid city";
        if (!state.serviceTypeSelectionValid() && state.serviceNpcTypeKey() != null) return "Invalid Service NPC type";
        if (!state.enabled()) return "Disabled / " + state.registrationState().name();
        return state.registrationState().name();
    }

    private int statusColor() {
        return displayStatus().contains("ERROR") || displayStatus().startsWith("Invalid")
                || displayStatus().startsWith("Missing") ? 0xFF6666 : 0xFFCC66;
    }

    private static String shortId(UUID id) {
        return id.toString().substring(0, 8) + "...";
    }

    private static <T> String labelForValue(Map<String, T> values, T target) {
        if (target == null) return null;
        return values.entrySet().stream()
                .filter(entry -> target.equals(entry.getValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse(null);
    }

    private static String uniqueLabel(Map<String, ?> values, String displayName, String stableId) {
        return values.containsKey(displayName) ? displayName + " [" + stableId.substring(0, Math.min(8, stableId.length())) + "]" : displayName;
    }
}
