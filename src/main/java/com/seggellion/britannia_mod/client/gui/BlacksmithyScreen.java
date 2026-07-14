package com.seggellion.britannia_mod.client.screen;

import com.seggellion.britannia_mod.item.BlackSmithsHammerItem;
import com.seggellion.britannia_mod.item.BlacksmithItemData;
import com.seggellion.britannia_mod.item.UOMetalToolMaterial;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.payload.CraftBlacksmithItemC2SPayload;
import com.seggellion.britannia_mod.registry.BlacksmithItemRegistry;
import com.seggellion.britannia_mod.registry.WeaponProfile;
import com.seggellion.britannia_mod.registry.WeaponRegistry;
import com.seggellion.britannia_mod.skill.BlacksmithCrafting;
import com.seggellion.britannia_mod.skill.SkillManager;
import com.seggellion.britannia_mod.skill.crafting.ArmorProfileRegistry;
import com.seggellion.britannia_mod.skill.crafting.CraftableDef;
import com.seggellion.britannia_mod.skill.crafting.CraftableRegistry;
import com.seggellion.britannia_mod.skill.crafting.IngredientRequirement;
import com.seggellion.britannia_mod.skill.crafting.ResistanceProfile;
import com.seggellion.britannia_mod.skill.crafting.ShieldProfileRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/** Compact five-workflow Blacksmithing screen using the existing screen/payload architecture. */
public class BlacksmithyScreen extends Screen {
    public enum Workflow { REPAIR, SMELT, SHIELDS, ARMOR, WEAPONS }
    public static final List<Workflow> WORKFLOWS = List.of(Workflow.REPAIR, Workflow.SMELT,
            Workflow.SHIELDS, Workflow.ARMOR, Workflow.WEAPONS);

    private final String openingMaterial;
    private final Set<String> learnedRecipes;
    private final String playerRace;
    private final String playerGender;
    private final String sessionToken;
    private final List<CraftableDef> visibleRecipes = new ArrayList<>();
    private final List<Button> workflowButtons = new ArrayList<>();
    private Workflow workflow = Workflow.WEAPONS;
    private CraftableDef selectedRecipe;
    private int carouselOffset;
    private int selectedIndex = -1;
    private int lastSignature;
    private int refreshTicker;
    private Button actionButton;
    private Button cancelButton;
    private Button leftButton;
    private Button rightButton;
    private boolean confirmingSmelt;
    private String confirmationToken = "";

    public BlacksmithyScreen(String material) {
        this(material, List.of(), "human", "female", "");
    }

    public BlacksmithyScreen(String material, List<String> learnedRecipes, String race, String gender, String sessionToken) {
        super(Component.translatable("screen.britannia_mod.blacksmithing.title"));
        this.openingMaterial = material == null ? "" : material;
        this.learnedRecipes = Set.copyOf(learnedRecipes);
        this.playerRace = race == null ? "human" : race;
        this.playerGender = gender == null ? "female" : gender;
        this.sessionToken = sessionToken == null ? "" : sessionToken;
    }

    @Override
    protected void init() {
        super.init();
        workflowButtons.clear();
        int left = panelLeft();
        int top = panelTop();
        int buttonWidth = 44;
        int gap = 7;
        int total = WORKFLOWS.size() * buttonWidth + (WORKFLOWS.size() - 1) * gap;
        int start = left + (panelWidth() - total) / 2;
        for (int i = 0; i < WORKFLOWS.size(); i++) {
            Workflow value = WORKFLOWS.get(i);
            Button button = Button.builder(Component.empty(), ignored -> selectWorkflow(value))
                    .bounds(start + i * (buttonWidth + gap), top + 12, buttonWidth, 38).build();
            button.setTooltip(Tooltip.create(Component.translatable(workflowKey(value))));
            workflowButtons.add(addRenderableWidget(button));
        }

        leftButton = addRenderableWidget(Button.builder(Component.literal("◀"), ignored -> moveCarousel(-1))
                .bounds(left + 10, top + 66, 20, 32).build());
        rightButton = addRenderableWidget(Button.builder(Component.literal("▶"), ignored -> moveCarousel(1))
                .bounds(left + panelWidth() - 30, top + 66, 20, 32).build());
        actionButton = addRenderableWidget(Button.builder(actionLabel(), ignored -> performAction())
                .bounds(left + panelWidth() - 94, top + panelHeight() - 28, 84, 20).build());
        cancelButton = addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), ignored -> cancelSmelt())
                .bounds(left + panelWidth() - 182, top + panelHeight() - 28, 84, 20).build());
        cancelButton.visible = false;

        Player player = Minecraft.getInstance().player;
        if (player != null && BlacksmithItemData.isSupportedEquipment(player.getMainHandItem())) {
            workflow = player.getMainHandItem().isDamaged() ? Workflow.REPAIR : Workflow.SMELT;
        }
        refreshVisible(true);
    }

    private void selectWorkflow(Workflow selected) {
        if (workflow == selected) return;
        workflow = selected;
        carouselOffset = 0;
        selectedIndex = -1;
        selectedRecipe = null;
        cancelSmelt();
        refreshVisible(true);
    }

    @Override
    public void tick() {
        super.tick();
        if (++refreshTicker >= 5) {
            refreshTicker = 0;
            refreshVisible(false);
        }
    }

    private void refreshVisible(boolean force) {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        int signature = stateSignature(player);
        if (!force && signature == lastSignature) return;
        lastSignature = signature;
        String preserve = selectedRecipe == null ? null : selectedRecipe.id();
        visibleRecipes.clear();
        if (isCraftWorkflow()) {
            for (CraftableDef definition : CraftableRegistry.getAll()) {
                if (belongsToWorkflow(definition) && canCurrentlyCraft(player, definition)) visibleRecipes.add(definition);
            }
        }
        selectedIndex = -1;
        if (preserve != null) {
            for (int i = 0; i < visibleRecipes.size(); i++) if (visibleRecipes.get(i).id().equals(preserve)) selectedIndex = i;
        }
        if (selectedIndex < 0 && !visibleRecipes.isEmpty()) selectedIndex = 0;
        selectedRecipe = selectedIndex < 0 ? null : visibleRecipes.get(selectedIndex);
        int maxOffset = Math.max(0, visibleRecipes.size() - visibleSlotCount());
        carouselOffset = Math.min(carouselOffset, maxOffset);
        if (confirmingSmelt && !confirmationToken.equals(BlacksmithItemData.identityToken(player.getMainHandItem()))) cancelSmelt();
        updateButtons(player);
    }

    private boolean canCurrentlyCraft(Player player, CraftableDef definition) {
        if (!(player.getMainHandItem().getItem() instanceof BlackSmithsHammerItem) || !hasNearbyAnvil(player)) return false;
        UOMetalToolMaterial material = UOMetalToolMaterial.getMaterialByIngot(player.getOffhandItem().getItem());
        if (material == null) return false;
        for (var skill : definition.skillRequirements()) {
            if (SkillManager.getSkill(player.getUUID(), skill.skillKey()) < skill.minValue()) return false;
        }
        if (definition.requiresLearnedRecipe() && !learnedRecipes.contains(definition.learnedRecipeKey())) return false;
        if (definition.raceRestriction() != null && !definition.raceRestriction().equalsIgnoreCase(playerRace)) return false;
        if (definition.genderRestriction() != null && !definition.genderRestriction().equalsIgnoreCase(playerGender)) return false;
        for (IngredientRequirement ingredient : definition.ingredients()) {
            int available = ingredient.selectableMetal() ? player.getOffhandItem().getCount()
                    : inventoryCount(player, BlacksmithItemRegistry.ingredientItem(ingredient.materialKey()));
            if (available < ingredient.amount()) return false;
        }
        return true;
    }

    private boolean belongsToWorkflow(CraftableDef definition) {
        return switch (workflow) {
            case SHIELDS -> definition.category().equalsIgnoreCase("Shields");
            case ARMOR -> definition.category().equalsIgnoreCase("Armor")
                    || definition.category().equalsIgnoreCase("Helmets")
                    || definition.equipmentType().equals("armor");
            case WEAPONS -> CraftableDef.isWeaponCategory(definition.category());
            default -> false;
        };
    }

    private void updateButtons(Player player) {
        boolean hasTarget = BlacksmithItemData.isSupportedEquipment(player.getMainHandItem());
        if (workflow == Workflow.REPAIR) actionButton.active = hasTarget && player.getMainHandItem().isDamaged()
                && matchingRepairMaterial(player);
        else if (workflow == Workflow.SMELT) actionButton.active = hasTarget && smeltRecovery(player) > 0;
        else actionButton.active = selectedRecipe != null;
        actionButton.setMessage(actionLabel());
        cancelButton.visible = confirmingSmelt;
        leftButton.active = isCraftWorkflow() && carouselOffset > 0;
        rightButton.active = isCraftWorkflow() && carouselOffset + visibleSlotCount() < visibleRecipes.size();
    }

    private boolean matchingRepairMaterial(Player player) {
        String recipeId = BlacksmithItemData.recipeId(player.getMainHandItem());
        CraftableDef def = recipeId == null ? null : CraftableRegistry.get(recipeId);
        String material = BlacksmithItemData.materialId(player.getMainHandItem());
        UOMetalToolMaterial stored = material == null ? null : UOMetalToolMaterial.getMaterialByName(material.replace('_', ' '));
        int primary = def == null ? 0 : BlacksmithCrafting.primaryMaterialCount(def);
        return stored != null && primary > 0 && player.getOffhandItem().getItem() == stored.getIngotSupplier().get()
                && player.getOffhandItem().getCount() >= BlacksmithCrafting.repairCost(primary);
    }

    private int smeltRecovery(Player player) {
        String recipeId = BlacksmithItemData.recipeId(player.getMainHandItem());
        CraftableDef def = recipeId == null ? null : CraftableRegistry.get(recipeId);
        return def == null ? 0 : BlacksmithCrafting.smeltRecovery(BlacksmithCrafting.primaryMaterialCount(def));
    }

    private void performAction() {
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (isCraftWorkflow() && selectedRecipe != null) {
            NetworkHandler.sendToServer(new CraftBlacksmithItemC2SPayload(selectedRecipe.id(), sessionToken));
            onClose();
        } else if (workflow == Workflow.REPAIR) {
            NetworkHandler.sendToServer(CraftBlacksmithItemC2SPayload.repair(
                    BlacksmithItemData.identityToken(player.getMainHandItem()), sessionToken));
            onClose();
        } else if (workflow == Workflow.SMELT) {
            if (!confirmingSmelt) {
                confirmingSmelt = true;
                confirmationToken = BlacksmithItemData.identityToken(player.getMainHandItem());
                updateButtons(player);
            } else {
                NetworkHandler.sendToServer(CraftBlacksmithItemC2SPayload.smelt(confirmationToken, sessionToken));
                onClose();
            }
        }
    }

    private void cancelSmelt() {
        confirmingSmelt = false;
        confirmationToken = "";
        if (actionButton != null) actionButton.setMessage(actionLabel());
        if (cancelButton != null) cancelButton.visible = false;
    }

    private Component actionLabel() {
        return Component.translatable(switch (workflow) {
            case REPAIR -> "screen.britannia_mod.blacksmithing.repair";
            case SMELT -> confirmingSmelt ? "screen.britannia_mod.blacksmithing.smelt_item" : "screen.britannia_mod.blacksmithing.smelt";
            default -> "screen.britannia_mod.blacksmithing.craft";
        });
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        int left = panelLeft(), top = panelTop(), right = left + panelWidth(), bottom = top + panelHeight();
        graphics.fill(left, top, right, bottom, 0xEE211B17);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, 0xEE4A3828);
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(font, title, width / 2, top - 11, 0xFFE4C890);
        renderWorkflowIcons(graphics, mouseX, mouseY);
        renderCarousel(graphics, mouseX, mouseY);
        renderDetails(graphics);
    }

    private void renderWorkflowIcons(GuiGraphics graphics, int mouseX, int mouseY) {
        for (int i = 0; i < workflowButtons.size(); i++) {
            Button button = workflowButtons.get(i);
            if (WORKFLOWS.get(i) == workflow) {
                graphics.renderOutline(button.getX() - 1, button.getY() - 1, button.getWidth() + 2, button.getHeight() + 2, 0xFFFFD866);
            }
            graphics.renderItem(workflowIcon(WORKFLOWS.get(i)), button.getX() + 14, button.getY() + 10);
        }
    }

    private void renderCarousel(GuiGraphics graphics, int mouseX, int mouseY) {
        int x = panelLeft() + 36, y = panelTop() + 65, width = panelWidth() - 72;
        graphics.fill(x, y, x + width, y + 34, 0xAA17120F);
        graphics.enableScissor(x, y, x + width, y + 34);
        if (isCraftWorkflow()) {
            int end = Math.min(visibleRecipes.size(), carouselOffset + visibleSlotCount());
            for (int i = carouselOffset; i < end; i++) {
                int slotX = x + (i - carouselOffset) * 34;
                boolean selected = i == selectedIndex;
                graphics.fill(slotX + 2, y + 2, slotX + 32, y + 32, selected ? 0xFF8A6B32 : 0xFF33271D);
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(visibleRecipes.get(i).resultItem()));
                graphics.renderItem(stack, slotX + 9, y + 9);
                if (visibleRecipes.get(i).outputCount() > 1) graphics.renderItemDecorations(font, stack, slotX + 9, y + 9,
                        Integer.toString(visibleRecipes.get(i).outputCount()));
                if (hovered(mouseX, mouseY, slotX + 2, y + 2, 30, 30))
                    graphics.renderTooltip(font, Component.literal(visibleRecipes.get(i).displayName()), mouseX, mouseY);
            }
        } else {
            Player player = Minecraft.getInstance().player;
            if (player != null && BlacksmithItemData.isSupportedEquipment(player.getMainHandItem())) {
                graphics.fill(x + 2, y + 2, x + 32, y + 32, 0xFF8A6B32);
                graphics.renderItem(player.getMainHandItem(), x + 9, y + 9);
                if (hovered(mouseX, mouseY, x + 2, y + 2, 30, 30))
                    graphics.renderTooltip(font, player.getMainHandItem(), mouseX, mouseY);
            }
        }
        graphics.disableScissor();
    }

    private void renderDetails(GuiGraphics graphics) {
        int x = panelLeft() + 12, y = panelTop() + 108;
        Player player = Minecraft.getInstance().player;
        if (player == null) return;
        if (confirmingSmelt) {
            graphics.drawWordWrap(font, Component.translatable("screen.britannia_mod.blacksmithing.confirm_smelt", smeltRecovery(player)),
                    x, y, panelWidth() - 24, 0xFFFF7777);
            return;
        }
        if (isCraftWorkflow()) {
            if (selectedRecipe == null) {
                Component empty = UOMetalToolMaterial.getMaterialByIngot(player.getOffhandItem().getItem()) == null
                        ? Component.translatable("screen.britannia_mod.blacksmithing.empty_material")
                        : Component.translatable("screen.britannia_mod.blacksmithing.empty_craftable");
                graphics.drawWordWrap(font, empty, x, y, panelWidth() - 24, 0xFFDDC9A4);
                return;
            }
            renderRecipeDetails(graphics, selectedRecipe, x, y, player);
        } else {
            renderTargetDetails(graphics, x, y, player);
        }
    }

    private void renderRecipeDetails(GuiGraphics graphics, CraftableDef def, int x, int y, Player player) {
        graphics.drawString(font, def.displayName(), x, y, 0xFFFFD866); y += 11;
        int primary = BlacksmithCrafting.primaryMaterialCount(def);
        graphics.drawString(font, Component.translatable("screen.britannia_mod.blacksmithing.material_line",
                materialName(player), primary), x, y, 0xFFE5E5E5); y += 10;
        graphics.drawString(font, Component.translatable("screen.britannia_mod.blacksmithing.skill_line",
                def.minimumBlacksmithy(), successPercent(player, def)), x, y, 0xFF8CE6E6); y += 10;
        graphics.drawString(font, Component.translatable("screen.britannia_mod.blacksmithing.weight_line", def.baseWeight()), x, y, 0xFFCCCCCC);
        int sx = x + panelWidth() / 2;
        WeaponProfile weapon = WeaponRegistry.getProfile(def.weaponProfileId());
        if (weapon != null) {
            graphics.drawString(font, "Damage " + weapon.minimumDamage() + "-" + weapon.maximumDamage()
                    + "  Speed " + weapon.speed(), sx, y - 20, 0xFFE5E5E5);
            graphics.drawString(font, (weapon.twoHanded() ? "Two-handed" : "One-handed")
                    + "  Strength " + weapon.minimumStrength(), sx, y - 10, 0xFFE5E5E5);
        }
        ResistanceProfile armor = ArmorProfileRegistry.get(def.armorProfileId());
        if (armor != null) graphics.drawString(font, resistanceText(armor), sx, y - 20, 0xFFE5E5E5);
        var shield = ShieldProfileRegistry.get(def.shieldProfileId());
        if (shield != null) {
            graphics.drawString(font, resistanceText(shield.bonus()), sx, y - 20, 0xFFE5E5E5);
            graphics.drawString(font, "Strength " + shield.minimumStrength() + "  Durability "
                    + shield.minimumDurability() + "-" + shield.maximumDurability(), sx, y - 10, 0xFFE5E5E5);
        }
    }

    private void renderTargetDetails(GuiGraphics graphics, int x, int y, Player player) {
        ItemStack target = player.getMainHandItem();
        if (!BlacksmithItemData.isSupportedEquipment(target)) {
            graphics.drawWordWrap(font, Component.translatable(workflow == Workflow.REPAIR
                    ? "screen.britannia_mod.blacksmithing.empty_repair" : "screen.britannia_mod.blacksmithing.empty_smelt"),
                    x, y, panelWidth() - 24, 0xFFDDC9A4);
            return;
        }
        String recipeId = BlacksmithItemData.recipeId(target);
        CraftableDef def = recipeId == null ? null : CraftableRegistry.get(recipeId);
        int original = def == null ? 0 : BlacksmithCrafting.primaryMaterialCount(def);
        graphics.drawString(font, target.getHoverName(), x, y, 0xFFFFD866); y += 11;
        graphics.drawString(font, "Material: " + Objects.toString(BlacksmithItemData.materialId(target), "Unknown"), x, y, 0xFFE5E5E5); y += 10;
        if (workflow == Workflow.REPAIR) {
            graphics.drawString(font, "Durability: " + (target.getMaxDamage() - target.getDamageValue()) + "/" + target.getMaxDamage(), x, y, 0xFFE5E5E5); y += 10;
            graphics.drawString(font, "Repair cost: " + BlacksmithCrafting.repairCost(original) + " matching ingots", x, y,
                    matchingRepairMaterial(player) ? 0xFF77DD77 : 0xFFFF7777);
        } else {
            graphics.drawString(font, "Original material: " + original + "  Recoverable: " + BlacksmithCrafting.smeltRecovery(original), x, y, 0xFFE5E5E5); y += 10;
            graphics.drawString(font, Component.translatable("screen.britannia_mod.blacksmithing.destroy_warning"), x, y, 0xFFFF7777);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isCraftWorkflow()) {
            int x = panelLeft() + 36, y = panelTop() + 65;
            int end = Math.min(visibleRecipes.size(), carouselOffset + visibleSlotCount());
            for (int i = carouselOffset; i < end; i++) {
                int slotX = x + (i - carouselOffset) * 34;
                if (hovered(mouseX, mouseY, slotX + 2, y + 2, 30, 30)) {
                    selectedIndex = i;
                    selectedRecipe = visibleRecipes.get(i);
                    updateButtons(Minecraft.getInstance().player);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (isCraftWorkflow() && hovered(mouseX, mouseY, panelLeft() + 30, panelTop() + 60, panelWidth() - 60, 44)) {
            moveCarousel(scrollY < 0 || scrollX > 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 263) { selectRelative(-1); return true; }
        if (keyCode == 262) { selectRelative(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void selectRelative(int direction) {
        if (!isCraftWorkflow() || visibleRecipes.isEmpty()) return;
        selectedIndex = Math.max(0, Math.min(visibleRecipes.size() - 1, selectedIndex + direction));
        selectedRecipe = visibleRecipes.get(selectedIndex);
        if (selectedIndex < carouselOffset) carouselOffset = selectedIndex;
        if (selectedIndex >= carouselOffset + visibleSlotCount()) carouselOffset = selectedIndex - visibleSlotCount() + 1;
    }

    private void moveCarousel(int direction) {
        int max = Math.max(0, visibleRecipes.size() - visibleSlotCount());
        carouselOffset = Math.max(0, Math.min(max, carouselOffset + direction));
        Player player = Minecraft.getInstance().player;
        if (player != null) updateButtons(player);
    }

    private int stateSignature(Player player) {
        int inventory = 1;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            inventory = 31 * inventory + Objects.hash(BuiltInRegistries.ITEM.getKey(stack.getItem()), stack.getCount(), stack.getDamageValue());
        }
        return Objects.hash(workflow, inventory, BlacksmithItemData.identityToken(player.getMainHandItem()),
                SkillManager.getSkill(player.getUUID(), "blacksmithy"), SkillManager.getSkill(player.getUUID(), "tailoring"),
                SkillManager.getSkill(player.getUUID(), "carpentry"), SkillManager.getSkill(player.getUUID(), "magery"));
    }

    private static int inventoryCount(Player player, Item item) {
        if (item == Items.AIR) return 0;
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) total += stack.getCount();
        }
        return total;
    }

    private static boolean hasNearbyAnvil(Player player) {
        BlockPos center = player.blockPosition();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-4, -2, -4), center.offset(4, 2, 4))) {
            var state = player.level().getBlockState(pos);
            if (state.is(Blocks.ANVIL) || state.is(Blocks.CHIPPED_ANVIL) || state.is(Blocks.DAMAGED_ANVIL)) return true;
        }
        return false;
    }

    private ItemStack workflowIcon(Workflow value) {
        return switch (value) {
            case REPAIR -> new ItemStack(Items.ANVIL);
            case SMELT -> new ItemStack(Items.BLAST_FURNACE);
            case SHIELDS -> catalogueIcon("bronze_shield", Items.SHIELD);
            case ARMOR -> catalogueIcon("platemail_tunic", Items.IRON_CHESTPLATE);
            case WEAPONS -> catalogueIcon("viking_sword", Items.IRON_SWORD);
        };
    }

    private ItemStack catalogueIcon(String recipe, Item fallback) {
        CraftableDef definition = CraftableRegistry.get(recipe);
        Item item = definition == null ? fallback : BuiltInRegistries.ITEM.get(definition.resultItem());
        return new ItemStack(item == Items.AIR ? fallback : item);
    }

    private String materialName(Player player) {
        UOMetalToolMaterial material = UOMetalToolMaterial.getMaterialByIngot(player.getOffhandItem().getItem());
        return material == null ? openingMaterial : material.getMetalName();
    }

    private static int successPercent(Player player, CraftableDef def) {
        float skill = SkillManager.getSkill(player.getUUID(), "blacksmithy");
        return (int) Math.round(Math.max(5, Math.min(100, 50 + skill - def.minimumBlacksmithy())));
    }

    private static String resistanceText(ResistanceProfile profile) {
        return "P " + profile.physical() + " F " + profile.fire() + " I " + profile.ice()
                + " Po " + profile.poison() + " M " + profile.magic();
    }

    private static String workflowKey(Workflow workflow) {
        return "screen.britannia_mod.blacksmithing.category." + workflow.name().toLowerCase(Locale.ROOT);
    }

    private boolean isCraftWorkflow() { return workflow == Workflow.SHIELDS || workflow == Workflow.ARMOR || workflow == Workflow.WEAPONS; }
    private int panelWidth() { return Math.min(420, width - 20); }
    private int panelHeight() { return Math.min(218, height - 28); }
    private int panelLeft() { return (width - panelWidth()) / 2; }
    private int panelTop() { return Math.max(20, (height - panelHeight()) / 2); }
    private int visibleSlotCount() { return Math.max(4, (panelWidth() - 72) / 34); }
    private static boolean hovered(double mx, double my, int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }

    @Override public boolean isPauseScreen() { return false; }
}
