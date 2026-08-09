package com.seggellion.britannia_mod.farming;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

/** Durable plant identity owned by a house farm plot, independent of growth state. */
public record HouseFarmPlotAssignment(
        State state,
        Optional<Kind> kind,
        Optional<ResourceLocation> plantId
) {
    private static final String STATE_KEY = "State";
    private static final String KIND_KEY = "Kind";
    private static final String PLANT_ID_KEY = "PlantId";

    public HouseFarmPlotAssignment {
        Objects.requireNonNull(state, "House farm plot assignment state is required");
        kind = Objects.requireNonNull(kind, "House farm plot plant kind optional is required");
        plantId = Objects.requireNonNull(plantId, "House farm plot plant ID optional is required");
        boolean assigned = state == State.ASSIGNED;
        if (assigned != kind.isPresent() || assigned != plantId.isPresent()) {
            throw new IllegalArgumentException("Only ASSIGNED house farm plots may contain a plant kind and ID");
        }
    }

    public static HouseFarmPlotAssignment uninitialized() {
        return new HouseFarmPlotAssignment(State.UNINITIALIZED, Optional.empty(), Optional.empty());
    }

    public static HouseFarmPlotAssignment cleared() {
        return new HouseFarmPlotAssignment(State.CLEARED, Optional.empty(), Optional.empty());
    }

    public static HouseFarmPlotAssignment assigned(Kind kind, ResourceLocation plantId) {
        return new HouseFarmPlotAssignment(
                State.ASSIGNED,
                Optional.of(Objects.requireNonNull(kind, "House farm plot plant kind is required")),
                Optional.of(Objects.requireNonNull(plantId, "House farm plot plant ID is required"))
        );
    }

    public boolean isAssignedTo(Kind expectedKind, ResourceLocation expectedId) {
        return state == State.ASSIGNED
                && kind.filter(expectedKind::equals).isPresent()
                && plantId.filter(expectedId::equals).isPresent();
    }

    public CompoundTag toTag() {
        CompoundTag tag = new CompoundTag();
        tag.putString(STATE_KEY, state.name());
        kind.ifPresent(value -> tag.putString(KIND_KEY, value.name()));
        plantId.ifPresent(value -> tag.putString(PLANT_ID_KEY, value.toString()));
        return tag;
    }

    public static HouseFarmPlotAssignment fromTag(CompoundTag tag) {
        if (tag == null || !tag.contains(STATE_KEY)) {
            return uninitialized();
        }
        try {
            State loadedState = State.valueOf(tag.getString(STATE_KEY));
            if (loadedState != State.ASSIGNED) {
                return loadedState == State.CLEARED ? cleared() : uninitialized();
            }
            if (!tag.contains(KIND_KEY) || !tag.contains(PLANT_ID_KEY)) {
                return cleared();
            }
            Kind loadedKind = Kind.valueOf(tag.getString(KIND_KEY));
            ResourceLocation loadedId = ResourceLocation.tryParse(tag.getString(PLANT_ID_KEY));
            return loadedId == null ? cleared() : assigned(loadedKind, loadedId);
        } catch (IllegalArgumentException exception) {
            return cleared();
        }
    }

    public enum State {
        UNINITIALIZED,
        ASSIGNED,
        CLEARED
    }

    public enum Kind {
        CROP,
        FLOWER
    }
}
