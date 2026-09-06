package com.seggellion.britannia_mod.blessed.delivery;

/**
 * The complete, allowlisted set of causes that may move a materialization to destroyed.
 *
 * <p>Deliberately short. M7 is rescue-first, and rescue succeeded on every path it covers --
 * despawn, lava, fire, the void and the Trash Barrel all leave the item in the world -- so none
 * of them appears here. A cause only earns a place once there is an implemented path that can
 * assert the instance positively no longer exists.
 *
 * <p>The reason is a local concern: Rails' M4 result contract takes
 * {@code protocol_version, instance_uuid, minecraft_uuid, status} and nothing else, so this never
 * goes on the wire. It exists so the log says WHY an entitlement ended, which is the question an
 * operator asks first.
 */
public enum BlessedDestructionReason {
    /**
     * An audited operator recovery action: a human, with a reason recorded, has determined the
     * item is genuinely gone by a route the server cannot observe. The only cause M7 implements,
     * and the one M9 will drive.
     */
    OPERATOR("operator");

    private final String wireReason;

    BlessedDestructionReason(String wireReason) {
        this.wireReason = wireReason;
    }

    public String wireReason() {
        return wireReason;
    }
}
