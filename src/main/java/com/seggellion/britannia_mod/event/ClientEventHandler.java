package com.seggellion.britannia_mod.event;

import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.SpellCastPayload;
import com.seggellion.britannia_mod.magic.Spell;
import com.seggellion.britannia_mod.magic.SpellRegistry;
import com.seggellion.britannia_mod.item.AbstractHouseDeedItem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.Vec3i;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientPlayerChangeGameTypeEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import com.seggellion.britannia_mod.InvisibleInAdventureMode;
import com.seggellion.britannia_mod.item.QualityToolItem;
import com.seggellion.britannia_mod.item.TwoHandedAxeItem;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.client.renderer.item.ItemProperties;
import java.util.function.Predicate;


import net.minecraft.nbt.NbtIo;
import net.minecraft.core.registries.BuiltInRegistries;
import java.io.InputStream;
import net.minecraft.core.registries.Registries;
import java.io.IOException;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import com.seggellion.britannia_mod.network.ManaSyncPayload;
import com.seggellion.britannia_mod.network.ClientNetworkHandler;
import com.seggellion.britannia_mod.client.structure.StructureCache;
import com.seggellion.britannia_mod.network.NetworkHandler;
import com.seggellion.britannia_mod.network.HouseManagementScreenPayload;

import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import com.seggellion.britannia_mod.registry.BlockRegistry;
import com.seggellion.britannia_mod.block.entity.WineBarrelBlockEntity;
import com.seggellion.britannia_mod.client.screen.WineryScreen;
import net.minecraft.world.level.block.entity.BlockEntity;

import net.neoforged.neoforge.network.registration.PayloadRegistrar;


import net.minecraft.nbt.CompoundTag;


import java.util.List;

import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.util.function.Predicate;

public class ClientEventHandler {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static boolean wasAttackPressed = false;

    public static void register(IEventBus modEventBus) {
        modEventBus.addListener(ClientEventHandler::onClientSetup);
        NeoForge.EVENT_BUS.addListener(ClientEventHandler::onGameModeChange);
        NeoForge.EVENT_BUS.addListener(ClientEventHandler::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientEventHandler::onBlockRightClick);
    }

    public static void onClientSetup(FMLClientSetupEvent event) {
        LOGGER.info("Client setup event called. Registering client-side handlers.");
// Register the Item Property for the Grapes texture switching
        event.enqueueWork(() -> {
            net.minecraft.client.renderer.item.ItemProperties.register(
                com.seggellion.britannia_mod.registry.ItemRegistry.GRAPES.get(), 
                ResourceLocation.fromNamespaceAndPath("britannia_mod", "grape_type"), 
                (stack, level, entity, seed) -> {
                    // 1. Get the Variety ID from the Item NBT
                    String varietyId = com.seggellion.britannia_mod.item.GrapesItem.getVariety(stack);
                    
                    // 2. Get the Variety Object
                    com.seggellion.britannia_mod.winery.GrapeVariety variety = 
                        com.seggellion.britannia_mod.winery.GrapeVarietyManager.getVariety(varietyId);
                    
                    // 3. Determine if it should be Red (1.0) or Green (0.0)
                    return isRedGrape(variety.colorType()) ? 1.0f : 0.0f;
                });

// --- 2. NEW: Wine Bottle Label Logic ---
            // Define the property getter once to reuse for all 3 bottles
            net.minecraft.client.renderer.item.ClampedItemPropertyFunction labelProperty = (stack, level, entity, seed) -> {
                // Check if the item has wine data
                if (!stack.has(com.seggellion.britannia_mod.registry.DataComponentRegistry.WINE_DATA)) return 0.0f;
                
                // Get the color string from the data component
                String color = com.seggellion.britannia_mod.item.WineBottleBlockItem.getWineData(stack).labelColor();

                // Map string to float for the model predicate
return switch (color) {
    case "red"    -> 0.01f;
    case "green"  -> 0.02f;
    case "orange" -> 0.03f;
    case "yellow" -> 0.04f;
    case "blue"   -> 0.05f;
    case "gold"   -> 0.06f;
    case "pink"   -> 0.07f;
    case "brown"  -> 0.08f;
    case "white"  -> 0.09f;
    case "silver" -> 0.10f;
    case "black"  -> 0.11f;
    default       -> 0.00f; // NONE
};
            };

            // Register for Green Bottle
            net.minecraft.client.renderer.item.ItemProperties.register(
                com.seggellion.britannia_mod.registry.ItemRegistry.WINE_BOTTLE_GREEN.get(),
                ResourceLocation.fromNamespaceAndPath("britannia_mod", "label_variant"),
                labelProperty
            );

            // Register for Brown Bottle
            net.minecraft.client.renderer.item.ItemProperties.register(
                com.seggellion.britannia_mod.registry.ItemRegistry.WINE_BOTTLE_BROWN.get(),
                ResourceLocation.fromNamespaceAndPath("britannia_mod", "label_variant"),
                labelProperty
            );

            // Register for Blue Bottle
            net.minecraft.client.renderer.item.ItemProperties.register(
                com.seggellion.britannia_mod.registry.ItemRegistry.WINE_BOTTLE_BLUE.get(),
                ResourceLocation.fromNamespaceAndPath("britannia_mod", "label_variant"),
                labelProperty
            );

            // Register for Clear Bottle
            net.minecraft.client.renderer.item.ItemProperties.register(
                com.seggellion.britannia_mod.registry.ItemRegistry.WINE_BOTTLE_CLEAR.get(),
                ResourceLocation.fromNamespaceAndPath("britannia_mod", "label_variant"),
                labelProperty
            );

        });
            }



 @SubscribeEvent
public static void onClientTick(ClientTickEvent.Post event) {
    Minecraft mc = Minecraft.getInstance();
    if (mc.player == null || mc.level == null) {
        return;
    }

   ItemStack held = mc.player.getMainHandItem();
if (held.getItem() instanceof AbstractHouseDeedItem deed) {
    String structureName = deed.getHouseStyle().getStructureFile().replace(".nbt", ""); // ✅ uses HouseStyle

    if (StructureCache.get(structureName) == null) {
        LOGGER.info("🔍 Ghost structure '{}' not yet cached, loading...", structureName);
        loadGhostStructure(mc, structureName);  // dynamically load the correct structure
    }
}

    boolean isAttackPressed = mc.options.keyAttack.isDown();

    if (isAttackPressed && !wasAttackPressed) {
        handleLeftClick(mc);
    }

    wasAttackPressed = isAttackPressed;
}


    private static boolean isRedGrape(com.seggellion.britannia_mod.winery.GrapeColor color) {
        if (color == null) return false; // Safety check
        
        return switch (color) {
            case PURPLE, DARK_PURPLE, BLUE -> true;  // Maps to Red Texture
            default -> false; 
        };
    }

public static void onBlockRightClick(PlayerInteractEvent.RightClickBlock event) {
        // 1. Safety Checks
        if (!event.getLevel().isClientSide) return;
        
        // 2. Check if we clicked a Wine Barrel
        if (event.getLevel().getBlockState(event.getPos()).getBlock() == BlockRegistry.WINE_BARREL.get()) {
            
            ItemStack stack = event.getItemStack();
            // 3. Check if holding a bottle (Brown, Green, or Clear)
            boolean isBottle = stack.is(com.seggellion.britannia_mod.registry.ItemRegistry.WINE_BOTTLE_BROWN.get()) || 
                                stack.is(com.seggellion.britannia_mod.registry.ItemRegistry.WINE_BOTTLE_BLUE.get()) ||
                               stack.is(com.seggellion.britannia_mod.registry.ItemRegistry.WINE_BOTTLE_GREEN.get()) ||
                               stack.is(com.seggellion.britannia_mod.registry.ItemRegistry.WINE_BOTTLE_CLEAR.get());

            if (isBottle) {
                Level level = event.getLevel();
                BlockPos pos = event.getPos();
                BlockEntity be = level.getBlockEntity(pos);

                if (be instanceof WineBarrelBlockEntity barrel && barrel.isReady()) {
                    // 4. Open the Screen (Safe here because this class is Client-Only)
                    Minecraft.getInstance().setScreen(new WineryScreen(pos));
                    
                    // Swing hand visually so it looks responsive
                    event.getEntity().swing(event.getHand());
                }
            }
        }
    }

private static void handleLeftClick(Minecraft mc) {
        LocalPlayer player = mc.player;
        ItemStack itemStack = player.getMainHandItem();
        Spell spell = SpellRegistry.getSpell(itemStack);

        if (itemStack.getItem() instanceof com.seggellion.britannia_mod.item.QualityToolItem) {
            return; // Let vanilla handle the left click (mining)
        }

        if (spell != null) {
            // Perform an entity ray trace
            double reachDistance = 20.0D; // Set the reach distance to 20 blocks
            Vec3 eyePosition = player.getEyePosition(1.0F);
            Vec3 lookVector = player.getLookAngle();
            Vec3 endVec = eyePosition.add(lookVector.scale(reachDistance));

            AABB boundingBox = player.getBoundingBox().expandTowards(lookVector.scale(reachDistance)).inflate(1.0D, 1.0D, 1.0D);
            Predicate<Entity> predicate = entity -> !entity.isSpectator() && entity.isPickable() && entity != player;

            EntityHitResult entityHitResult = ProjectileUtil.getEntityHitResult(mc.level, player, eyePosition, endVec, boundingBox, predicate);

            if (entityHitResult != null && entityHitResult.getEntity() instanceof LivingEntity target) {
                // Log the target's name and distance
                double distanceToTarget = player.distanceTo(target);
                LOGGER.info("Client: Target found: {}, Distance: {}", target.getName().getString(), distanceToTarget);

                // Create and send the payload to the server
                SpellCastPayload payload = new SpellCastPayload(target.getId());
                NetworkHandler.sendToServer(payload);
            } else if (entityHitResult == null) {
                LOGGER.info("Client: No target hit, entityHitResult is null.");
                player.sendSystemMessage(Component.literal("No target in crosshairs, spell fizzles!"));
            } else {
                LOGGER.info("Client: No valid target found for spell.");
                player.sendSystemMessage(Component.literal("No valid target for spell!"));
            }
            // Cancel default attack action to prevent physical damage
            mc.gameMode.stopDestroyBlock();
        } else {
            LOGGER.info("Client: No spell detected on item.");
        }
    }

    @SubscribeEvent
    public static void registerClientPackets(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");


    }

    @SubscribeEvent
    public static void onGameModeChange(ClientPlayerChangeGameTypeEvent event) {
        // LOGGER.info("Game mode change detected! event: {} ", event);

        Player player = Minecraft.getInstance().player;
        Level level = Minecraft.getInstance().level;

        if (player != null && level != null) {
            
            // --- FIX START ---
            // Check if the player is holding a tool that requires breaking blocks.
            // If so, we SKIP the block update loop. This prevents mining progress from 
            // resetting to 0 when the game mode switches.
            ItemStack heldItem = player.getMainHandItem();
            if (heldItem.getItem() instanceof QualityToolItem || 
                heldItem.getItem() instanceof TwoHandedAxeItem) {
                return; 
            }
            // --- FIX END ---

            BlockPos pos = player.blockPosition();

            // Iterate over a small area around the player to ensure nearby blocks are updated
            int range = 10; 
            for (int x = -range; x <= range; x++) {
                for (int y = -range; y <= range; y++) {
                    for (int z = -range; z <= range; z++) {
                        BlockPos checkPos = pos.offset(x, y, z);
                        BlockState blockState = level.getBlockState(checkPos);
                        Block block = blockState.getBlock();

                        if (block instanceof InvisibleInAdventureMode) {
                            // LOGGER.info("Forcing block update at {}", checkPos);
                            level.sendBlockUpdated(checkPos, blockState, blockState, 3);
                        }
                    }
                }
            }
        }
    }

private static void loadGhostStructure(Minecraft mc, String structureName) {
    LOGGER.info("🔍 Attempting to manually load ghost structure '{}' from resource stream...", structureName);

    ResourceLocation resource = ResourceLocation.fromNamespaceAndPath("britannia_mod", "structures/" + structureName + ".nbt");

    try (InputStream stream = mc.getResourceManager().getResourceOrThrow(resource).open()) {
        CompoundTag tag = NbtIo.readCompressed(stream, NbtAccounter.unlimitedHeap());
        RegistryAccess registryAccess = mc.level.registryAccess(); // client-side

        StructureTemplate template = new StructureTemplate();
        template.load(registryAccess.lookupOrThrow(Registries.BLOCK), tag); // ✅ Load the structure

        // Save it to cache with the *right* structure name
        StructureCache.put(structureName, template);

        if (template.getSize().equals(Vec3i.ZERO)) {
            LOGGER.warn("⚠️ Loaded structure '{}' has size Vec3i.ZERO (likely empty)", structureName);
        } else {
            LOGGER.info("✅ Structure '{}' loaded with size: {}", structureName, template.getSize());
        }

    } catch (IOException e) {
        LOGGER.error("❌ Failed to load structure '{}'", structureName, e);
    }
}

}
