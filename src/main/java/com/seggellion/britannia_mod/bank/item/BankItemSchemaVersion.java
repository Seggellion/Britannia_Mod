package com.seggellion.britannia_mod.bank.item;

/**
 * Explicit schema version for the {@link BankItemCodec} durable payload format.
 *
 * There is no prior canonical bank-item payload anywhere in this codebase (Milestone 1/8
 * recon confirmed the only existing versioned envelope precedent is the Rails-side wire
 * protocol, e.g. {@code ServiceNpcSpawnProtocol.VERSION}) -- version 1 here is the first
 * definition, not a migration from something else.
 */
public final class BankItemSchemaVersion {
    public static final int CURRENT = 1;

    private static final int MIN_SUPPORTED = 1;
    private static final int MAX_SUPPORTED = 1;

    private BankItemSchemaVersion() {
    }

    public static boolean isSupported(int version) {
        return version >= MIN_SUPPORTED && version <= MAX_SUPPORTED;
    }
}
