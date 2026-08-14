package com.seggellion.britannia_mod.grabbyhands.testsupport;

import com.seggellion.britannia_mod.grabbyhands.GrabbyActor;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Scriptable player stand-in for the transaction tests. */
public final class FakeGrabbyActor implements GrabbyActor {
    private final UUID id = UUID.randomUUID();
    private final List<String> audioLog;
    private final List<ItemStack> inventory = new ArrayList<>();
    private final List<ItemStack> dropped = new ArrayList<>();

    private boolean canReach = true;
    private boolean creative;
    private int permissionLevel;
    private boolean insideForeignStructure;
    private boolean hasRoom = true;
    /** Simulates the inventory filling up between the room check and the insert. */
    private boolean insertionSucceeds = true;

    public FakeGrabbyActor(List<String> sharedAudioLog) {
        this.audioLog = sharedAudioLog;
    }

    public FakeGrabbyActor outOfReach() {
        this.canReach = false;
        return this;
    }

    public FakeGrabbyActor creative() {
        this.creative = true;
        return this;
    }

    public FakeGrabbyActor operator() {
        this.permissionLevel = 2;
        return this;
    }

    public FakeGrabbyActor insideSomebodyElsesHouse() {
        this.insideForeignStructure = true;
        return this;
    }

    public FakeGrabbyActor inventoryFull() {
        this.hasRoom = false;
        return this;
    }

    public FakeGrabbyActor insertionFailsUnexpectedly() {
        this.insertionSucceeds = false;
        return this;
    }

    public List<ItemStack> inventory() {
        return inventory;
    }

    public List<ItemStack> dropped() {
        return dropped;
    }

    @Override
    public UUID id() {
        return id;
    }

    @Override
    public boolean canReach(BlockPos pos) {
        return canReach;
    }

    @Override
    public boolean isCreative() {
        return creative;
    }

    @Override
    public int permissionLevel() {
        return permissionLevel;
    }

    @Override
    public boolean insideForeignStructure(BlockPos pos) {
        return insideForeignStructure;
    }

    @Override
    public boolean hasRoomFor(ItemStack stack) {
        return hasRoom && !stack.isEmpty();
    }

    @Override
    public synchronized boolean give(ItemStack stack) {
        if (!insertionSucceeds) {
            return false;
        }
        inventory.add(stack.copy());
        stack.setCount(0);
        return true;
    }

    @Override
    public void dropAtFeet(ItemStack stack) {
        dropped.add(stack.copy());
    }

    // ------------------------------------------------------------------
    // Placement
    // ------------------------------------------------------------------

    private FakeGrabbyWorld placementWorld;
    private BlockState placementState;
    private ItemStack placementPortable;
    private BlockPos placementTarget;
    private boolean blockRefusesPlacement;
    private boolean itemConsumed;

    /** Scripts what a successful native placement would produce. */
    public FakeGrabbyActor placing(FakeGrabbyWorld world, BlockPos target, BlockState state, ItemStack portableForm) {
        this.placementWorld = world;
        this.placementTarget = target.immutable();
        this.placementState = state;
        this.placementPortable = portableForm;
        return this;
    }

    /** Simulates canSurvive/isUnobstructed refusing - an obstructed or unsupported placement. */
    public FakeGrabbyActor blockRefusesPlacement() {
        this.blockRefusesPlacement = true;
        return this;
    }

    public FakeGrabbyActor noPlacementTarget() {
        this.placementTarget = null;
        return this;
    }

    /** Native placement consumes the source item only on success; tests assert that directly. */
    public boolean itemConsumed() {
        return itemConsumed;
    }

    @Override
    public Optional<BlockPos> resolvePlacementTarget(BlockHitResult hit) {
        return Optional.ofNullable(placementTarget);
    }

    @Override
    public Optional<BlockPos> placeMainHandItem(BlockHitResult hit) {
        if (blockRefusesPlacement || placementTarget == null || placementWorld == null) {
            return Optional.empty();
        }
        placementWorld.placeRaw(placementTarget, placementState, placementPortable);
        itemConsumed = true;
        return Optional.of(placementTarget);
    }

    private BlockState hostState;

    /** Scripts what placing the generic item host would produce. */
    public FakeGrabbyActor placingHost(FakeGrabbyWorld world, BlockPos target, BlockState hostState) {
        this.placementWorld = world;
        this.placementTarget = target.immutable();
        this.hostState = hostState;
        return this;
    }

    @Override
    public Optional<BlockPos> placeItemHost(BlockHitResult hit) {
        if (blockRefusesPlacement || placementTarget == null || placementWorld == null) {
            return Optional.empty();
        }
        placementWorld.placeRaw(placementTarget, hostState, ItemStack.EMPTY);
        itemConsumed = true;
        return Optional.of(placementTarget);
    }

    private int toolDamage;
    private boolean toolDamageable = true;

    public FakeGrabbyActor withIndestructibleTool() {
        this.toolDamageable = false;
        return this;
    }

    public int toolDamage() {
        return toolDamage;
    }

    @Override
    public boolean damageTool() {
        if (creative || !toolDamageable) {
            return false;
        }
        toolDamage++;
        return true;
    }

    @Override
    public void playPersonalSound(SoundEvent event) {
        synchronized (audioLog) {
            audioLog.add("PLAYER:" + event.getLocation());
        }
    }
}
