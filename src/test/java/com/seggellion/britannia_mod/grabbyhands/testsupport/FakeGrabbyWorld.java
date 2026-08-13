package com.seggellion.britannia_mod.grabbyhands.testsupport;

import com.seggellion.britannia_mod.grabbyhands.GrabbyInstanceState;
import com.seggellion.britannia_mod.grabbyhands.GrabbyTransportRefusal;
import com.seggellion.britannia_mod.grabbyhands.GrabbyWorld;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/** In-memory world backing the transaction tests. */
public final class FakeGrabbyWorld implements GrabbyWorld {
    private final Object identity = new Object();
    private final Map<BlockPos, BlockState> blocks = new HashMap<>();
    private final Map<BlockPos, GrabbyInstanceState> provenance = new HashMap<>();
    private final Map<BlockPos, ItemStack> portable = new HashMap<>();
    private final List<String> audioLog;

    private long gameTime = 100L;
    private boolean removalSucceeds = true;
    private int removeCalls;
    private Consumer<BlockPos> beforeCapture = pos -> {
    };

    public FakeGrabbyWorld(List<String> sharedAudioLog) {
        this.audioLog = sharedAudioLog;
    }

    public FakeGrabbyWorld place(BlockPos pos, BlockState state, GrabbyInstanceState instanceState, ItemStack portableForm) {
        blocks.put(pos.immutable(), state);
        provenance.put(pos.immutable(), instanceState);
        portable.put(pos.immutable(), portableForm);
        return this;
    }

    public FakeGrabbyWorld removalFails() {
        this.removalSucceeds = false;
        return this;
    }

    /** Hook used to simulate a competing transaction arriving mid-flight, deterministically. */
    public FakeGrabbyWorld onBeforeCapture(Consumer<BlockPos> hook) {
        this.beforeCapture = hook;
        return this;
    }

    /**
     * What a native placement leaves behind: the block exists but carries no Grabby provenance yet.
     * Stamping it is the transaction's job, exactly as in the live path.
     */
    public void placeRaw(BlockPos pos, BlockState state, ItemStack portableForm) {
        blocks.put(pos.immutable(), state);
        provenance.put(pos.immutable(), GrabbyInstanceState.worldPlaced());
        portable.put(pos.immutable(), portableForm);
    }

    public boolean occupied(BlockPos pos) {
        return blocks.containsKey(pos);
    }

    public int removeCalls() {
        return removeCalls;
    }

    @Override
    public Object levelIdentity() {
        return identity;
    }

    @Override
    public BlockState blockState(BlockPos pos) {
        return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
    }

    @Override
    public GrabbyInstanceState grabbyState(BlockPos pos) {
        return provenance.getOrDefault(pos, GrabbyInstanceState.worldPlaced());
    }

    private boolean provenanceStampingWorks = true;

    /** Simulates a block that cannot carry provenance, so the fail-closed path can be exercised. */
    public FakeGrabbyWorld provenanceStampingFails() {
        this.provenanceStampingWorks = false;
        return this;
    }

    @Override
    public boolean setGrabbyState(BlockPos pos, GrabbyInstanceState state) {
        if (!provenanceStampingWorks || !blocks.containsKey(pos)) {
            return false;
        }
        provenance.put(pos.immutable(), state);
        return true;
    }

    @Override
    public long gameTime() {
        return gameTime;
    }

    @Override
    public ItemStack capturePortableStack(BlockPos pos) {
        beforeCapture.accept(pos);
        ItemStack stack = portable.get(pos);
        return stack == null ? ItemStack.EMPTY : stack.copy();
    }

    private final Map<BlockPos, ItemStack> payloads = new HashMap<>();
    private boolean payloadAttachWorks = true;
    private int detachCalls;

    public FakeGrabbyWorld payloadAttachFails() {
        this.payloadAttachWorks = false;
        return this;
    }

    public ItemStack payloadAt(BlockPos pos) {
        return payloads.getOrDefault(pos, ItemStack.EMPTY);
    }

    public int detachCalls() {
        return detachCalls;
    }

    @Override
    public boolean attachPayload(BlockPos pos, ItemStack payload) {
        if (!payloadAttachWorks || !blocks.containsKey(pos)) {
            return false;
        }
        payloads.put(pos.immutable(), payload.copy());
        portable.put(pos.immutable(), payload.copy());
        return true;
    }

    private GrabbyTransportRefusal refusal;
    private int restoreCalls;

    /** Simulates an object refusing transport for its own reasons - open viewers, nesting, and so on. */
    public FakeGrabbyWorld refusingTransport(GrabbyTransportRefusal refusal) {
        this.refusal = refusal;
        return this;
    }

    public int restoreCalls() {
        return restoreCalls;
    }

    @Override
    public Optional<GrabbyTransportRefusal> transportRefusal(BlockPos pos) {
        return Optional.ofNullable(blocks.containsKey(pos) ? refusal : null);
    }

    @Override
    public boolean detachPayloadBeforeRemoval(BlockPos pos) {
        detachCalls++;
        return payloads.remove(pos) != null;
    }

    @Override
    public void restoreAfterFailedRemoval(BlockPos pos, ItemStack captured) {
        restoreCalls++;
        if (blocks.containsKey(pos)) {
            payloads.put(pos.immutable(), captured.copy());
        }
    }

    @Override
    public synchronized boolean removeObject(BlockPos pos) {
        removeCalls++;
        if (!removalSucceeds || !blocks.containsKey(pos)) {
            return false;
        }
        blocks.remove(pos);
        provenance.remove(pos);
        portable.remove(pos);
        return true;
    }

    private int destructionEffects;
    private int objectPayloadDetaches;
    private int occupiedSlots;

    /** Pretends the object here is a container holding this many occupied slots. */
    public FakeGrabbyWorld holdingOccupiedSlots(int occupied) {
        this.occupiedSlots = occupied;
        return this;
    }

    public int destructionEffects() {
        return destructionEffects;
    }

    /** How many times a payload that IS the object was consumed, as opposed to contents spilling. */
    public int objectPayloadDetaches() {
        return objectPayloadDetaches;
    }

    private boolean secured;

    /** Marks the object here as locked, so destruction must not bypass its security. */
    public FakeGrabbyWorld secured() {
        this.secured = true;
        return this;
    }

    @Override
    public boolean securedAgainstDestruction(BlockPos pos) {
        return secured && blocks.containsKey(pos);
    }

    @Override
    public int occupiedSlotCount(BlockPos pos) {
        return blocks.containsKey(pos) ? occupiedSlots : 0;
    }

    @Override
    public boolean detachObjectPayloadForDestruction(BlockPos pos) {
        // Only a host's payload is the object itself. Container contents stay attached so the block's
        // own removal path spills them, which the fake models by leaving the payload map alone.
        if (!hostPayload) {
            return false;
        }
        objectPayloadDetaches++;
        return payloads.remove(pos) != null;
    }

    @Override
    public void playDestructionEffect(BlockPos pos, BlockState state) {
        destructionEffects++;
        synchronized (audioLog) {
            audioLog.add("DESTROY:" + state.getBlock());
        }
    }

    private boolean hostPayload;

    /** Marks the object at this position as a placed-item host rather than a container. */
    public FakeGrabbyWorld asItemHost() {
        this.hostPayload = true;
        return this;
    }

    @Override
    public void playWorldSound(BlockPos pos, SoundEvent event) {
        synchronized (audioLog) {
            audioLog.add("WORLD:" + event.getLocation());
        }
    }

    public static List<String> newAudioLog() {
        return new ArrayList<>();
    }
}
