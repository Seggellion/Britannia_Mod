package com.seggellion.britannia_mod.bank.item;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

/**
 * Version of the <em>Rails wire envelope</em> a deposit is sent as -- deliberately distinct from
 * {@link BankItemSchemaVersion}, which versions the opaque NBT payload format itself.
 *
 * <p>These were one constant until Milestone 17, and conflating them is actively dangerous.
 * {@link BankItemCodec#serialize} writes {@link BankItemSchemaVersion#CURRENT} <em>inside</em> the
 * compressed payload, and {@link BankItemCodec#deserialize} refuses to decode a payload whose
 * inner version it does not support. If adding identity fields to the wire envelope had been done
 * by bumping that constant, every newly-deposited payload would carry inner version 2, and any
 * client still on an older build would get {@code UnsupportedSchemaVersion} when it tried to
 * withdraw one -- an item permanently stuck in the vault, on a shard whose operator upgrades on
 * their own schedule. Mixed client versions are permanent; older clients must keep working
 * indefinitely.
 *
 * <p>The payload format genuinely did not change in Milestone 17. Item identity travels as its own
 * keys <em>beside</em> the payload in the JSON envelope, never inside it -- the payload stays
 * opaque to Rails by design. So the payload version stays at 1 and only this, the envelope
 * version, moves.
 *
 * <p>This split matches what the code already did: the wire {@code schema_version} Rails returns on
 * a withdrawal is parsed into {@code BankingWithdrawalPrepareResult} and never consulted, because
 * decoding is governed solely by the version inside the payload.
 *
 * <h2>Why the default is still 1</h2>
 *
 * Rails rejects unknown envelope keys. A build that emits identity fields against a Rails instance
 * that has not yet deployed Milestone 16 does not degrade -- it breaks <em>every deposit on that
 * shard</em>, because the request is refused outright. Rails-side acceptance always ships first,
 * and this constant is what makes "v1 emission remains possible" true rather than aspirational.
 *
 * <p>So the compiled-in default is {@link #V1_WITHOUT_IDENTITY}, and moving to v2 is a deliberate,
 * per-deployment act once Milestone 16 is confirmed live on the instance
 * {@code ModConfig.API_BASE_URL} actually points at:
 *
 * <pre>-Dbritannia.bank.item_envelope_version=2</pre>
 *
 * An unset, unparseable, or unsupported value leaves the safe default in place rather than
 * guessing -- there is no version of "I could not read the config" that should result in breaking
 * every deposit.
 */
public final class BankItemEnvelopeVersion {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Four keys: schema_version, payload, fingerprint, weight. What Rails has always accepted. */
    public static final int V1_WITHOUT_IDENTITY = 1;

    /** Adds the optional display_name / item_key / count keys. Requires Rails Milestone 16. */
    public static final int V2_WITH_IDENTITY = 2;

    public static final String SYSTEM_PROPERTY = "britannia.bank.item_envelope_version";

    private static final int CONFIGURED = readConfigured();

    private BankItemEnvelopeVersion() {
    }

    /** The envelope version this build will actually emit. */
    public static int emitted() {
        return CONFIGURED;
    }

    /**
     * Whether identity fields belong in the envelope. The single question every caller should ask
     * -- nobody outside this class should be comparing version numbers themselves.
     */
    public static boolean emitsIdentity() {
        return CONFIGURED >= V2_WITH_IDENTITY;
    }

    public static boolean isSupported(int version) {
        return version == V1_WITHOUT_IDENTITY || version == V2_WITH_IDENTITY;
    }

    /**
     * Every path through this method logs what was decided, including the do-nothing default.
     * A silent default is indistinguishable from a setting that never arrived -- and since this
     * class runs in whichever JVM hosts the deposit (the dedicated server, not the client, when
     * one is in play), "did the flag reach the right process?" is the exact question a failed
     * identity deposit raises. It should be answerable by reading one log line, not by inference.
     */
    private static int readConfigured() {
        String raw = System.getProperty(SYSTEM_PROPERTY);
        if (raw == null || raw.isBlank()) {
            LOGGER.info("Bank item envelope version 1 (identity fields OMITTED) -- {} is not set in this JVM",
                    SYSTEM_PROPERTY);
            return V1_WITHOUT_IDENTITY;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            LOGGER.warn("Bank item envelope version 1 (identity fields OMITTED) -- ignoring unparseable {}={}",
                    SYSTEM_PROPERTY, raw);
            return V1_WITHOUT_IDENTITY;
        }
        if (!isSupported(parsed)) {
            LOGGER.warn("Bank item envelope version 1 (identity fields OMITTED) -- ignoring unsupported {}={}",
                    SYSTEM_PROPERTY, parsed);
            return V1_WITHOUT_IDENTITY;
        }
        LOGGER.info("Bank item envelope version {} (identity fields {})",
                parsed, parsed >= V2_WITH_IDENTITY ? "EMITTED" : "OMITTED");
        return parsed;
    }
}
