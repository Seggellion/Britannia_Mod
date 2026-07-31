package com.seggellion.britannia_mod.farming;

/** Reasons that end the planted flower instance rather than resetting it. */
public enum FlowerRemovalReason {
    PERMANENT_UPROOT,
    AUTHORIZED_BREAK,
    ADMIN_COMMAND,
    ENVIRONMENTAL_DESTRUCTION
}
