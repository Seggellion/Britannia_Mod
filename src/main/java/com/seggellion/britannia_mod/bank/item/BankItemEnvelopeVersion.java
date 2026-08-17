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
 * <h2>Why the default is full capability</h2>
 *
 * Rails rejects unknown envelope keys, so emitting a key the target Rails has not deployed does
 * not degrade -- it breaks <em>every deposit on that shard</em>, because the request is refused
 * outright. That is why the default started at {@link #V1_WITHOUT_IDENTITY}: Rails-side
 * acceptance always ships first, and until it had, silence was the only safe thing to send.
 *
 * <p>That acceptance has since shipped. Rails accepts all three identity keys
 * ({@code BankTransferOperations::PayloadValidator::IDENTITY_FIELDS}) and the cheque link
 * ({@code LINK_FIELDS}), and {@code BankTransferOperations::Create} persists every one of them
 * verbatim. With nothing left to wait for, a default of 1 stopped protecting anything and
 * started costing something: an operator who does not know this flag exists silently banks
 * unnamed, unrenderable items -- and those rows never backfill, because identity is written once
 * at deposit and Rails never decodes the payload to re-derive it. A safe default that quietly
 * produces permanently degraded data is not the safer choice.
 *
 * <p>So the compiled-in default is now {@link #DEFAULT_CAPABILITY}, and no launch flag is needed
 * for full functionality. The property remains as an escape hatch in the one direction that
 * still matters -- pointing a current build at an older Rails, where it must be turned
 * <em>down</em>:
 *
 * <pre>-Dbritannia.bank.item_envelope_version=1</pre>
 *
 * An unset, unparseable, or unsupported value leaves {@link #DEFAULT_CAPABILITY} in place rather
 * than guessing. Note this is deliberately no longer fail-safe in the "emit nothing" sense: a
 * mistyped value now yields full emission, which is correct against a current Rails and wrong
 * only against one predating Milestone 16. If you run such an instance, set the property
 * explicitly rather than relying on a typo to protect you.
 */
public final class BankItemEnvelopeVersion {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Four keys: schema_version, payload, fingerprint, weight. What Rails has always accepted. */
    public static final int V1_WITHOUT_IDENTITY = 1;

    /** Adds the optional display_name / item_key / count keys. Requires Rails Milestone 16. */
    public static final int V2_WITH_IDENTITY = 2;

    /**
     * Adds the optional {@code cheque_public_id} key, which links a stored bank item to the
     * cheque inside its opaque payload so a stored cheque can be cashed from the vault. Requires
     * the Rails stored-redemption change (docs/banking_bank_cheque_stored_redemption.md).
     *
     * <p><b>This level does not name a wire {@code schema_version}.</b> Rails defines only 1 and
     * 2 and accepts this key at either -- see {@link #MAX_WIRE_SCHEMA_VERSION} for the regression
     * that taught us the difference.
     *
     * <p>Gated for exactly the reason v2 is: the key is sent only on cheque deposits, so against
     * a Rails without it the blast radius is every cheque deposit on that shard refused outright
     * with {@code UNEXPECTED_FIELD}. Ordinary deposits would be unaffected, which makes it
     * <em>worse</em> to leave ungated, not better -- a failure that hits one item type is the
     * kind that reaches production unnoticed.
     */
    public static final int V3_WITH_CHEQUE_LINK = 3;

    public static final String SYSTEM_PROPERTY = "britannia.bank.item_envelope_version";

    /**
     * What this build is capable of when nothing says otherwise -- the highest level, so full
     * functionality needs no launch flag. Kept as a named constant rather than inlined so the
     * one place that decides "what does unconfigured mean" stays greppable and testable.
     */
    public static final int DEFAULT_CAPABILITY = V3_WITH_CHEQUE_LINK;

    private static final int CONFIGURED = readConfigured();

    private BankItemEnvelopeVersion() {
    }

    /**
     * The highest {@code schema_version} Rails will accept on the wire, mirroring its
     * {@code BankTransferOperations::PayloadValidator::SUPPORTED_SCHEMA_VERSIONS = [1, 2]}.
     *
     * <h2>Why this constant has to exist</h2>
     * This class's number is a <b>client capability level</b>, and for v1 and v2 it happened to
     * equal the wire {@code schema_version} -- so the two were sent as one value. The cheque link
     * broke that coincidence: Rails accepts {@code cheque_public_id} at schema_version 1 or 2 and
     * defines no version 3 at all. Rails says so in the validator itself -- "schema_version is
     * the client's capability declaration, not a per-key allowlist, and coupling the two would
     * invent a second negotiation mechanism".
     *
     * <p>Sending the level as the wire value therefore made <em>every</em> deposit fail with
     * {@code UNSUPPORTED_SCHEMA_VERSION} the moment level 3 was configured -- not merely cheque
     * deposits, because the field is on every envelope. Capping here is what keeps the level free
     * to describe this client while the wire value stays something Rails recognises.
     */
    public static final int MAX_WIRE_SCHEMA_VERSION = 2;

    /**
     * The {@code schema_version} this build puts on the wire -- capped at what Rails accepts.
     * Never the raw configured level; see {@link #MAX_WIRE_SCHEMA_VERSION}.
     */
    public static int emitted() {
        return Math.min(CONFIGURED, MAX_WIRE_SCHEMA_VERSION);
    }

    /**
     * What this build is configured to be capable of, which is <em>not</em> what goes on the
     * wire. Diagnostics and tests only -- every behavioural question has its own named method
     * ({@link #emitsIdentity}, {@link #emitsChequeLink}), so nothing outside this class compares
     * version numbers.
     */
    public static int capabilityLevel() {
        return CONFIGURED;
    }

    /**
     * Whether identity fields belong in the envelope. The single question every caller should ask
     * -- nobody outside this class should be comparing version numbers themselves.
     */
    public static boolean emitsIdentity() {
        return CONFIGURED >= V2_WITH_IDENTITY;
    }

    /**
     * Whether the cheque link belongs in the envelope -- the same single-question rule
     * {@link #emitsIdentity} follows. A build that omits it still deposits cheques perfectly;
     * they simply store as ordinary items that cannot be cashed from the vault, which is the
     * documented legacy-row story (withdraw it and cash it from the pack).
     */
    public static boolean emitsChequeLink() {
        return CONFIGURED >= V3_WITH_CHEQUE_LINK;
    }

    public static boolean isSupported(int version) {
        return version == V1_WITHOUT_IDENTITY
                || version == V2_WITH_IDENTITY
                || version == V3_WITH_CHEQUE_LINK;
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
            LOGGER.info("Bank item {} -- {} is not set in this JVM, using the default",
                    describe(DEFAULT_CAPABILITY), SYSTEM_PROPERTY);
            return DEFAULT_CAPABILITY;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException notANumber) {
            LOGGER.warn("Bank item {} -- ignoring unparseable {}={}",
                    describe(DEFAULT_CAPABILITY), SYSTEM_PROPERTY, raw);
            return DEFAULT_CAPABILITY;
        }
        if (!isSupported(parsed)) {
            LOGGER.warn("Bank item {} -- ignoring unsupported {}={}",
                    describe(DEFAULT_CAPABILITY), SYSTEM_PROPERTY, parsed);
            return DEFAULT_CAPABILITY;
        }
        LOGGER.info("Bank item {}", describe(parsed));
        return parsed;
    }

    /**
     * One line naming <em>every</em> capability the version gates, not just the newest. Two
     * gated capabilities and a message that mentions one is the same silent-default problem this
     * logging exists to prevent -- an operator who set the flag for stored cheques must be able
     * to confirm the cheque link specifically, not infer it from a version number.
     */
    private static String describe(int version) {
        return "envelope version " + version
                + " (identity fields " + emitted(version >= V2_WITH_IDENTITY)
                + ", cheque link " + emitted(version >= V3_WITH_CHEQUE_LINK) + ")";
    }

    private static String emitted(boolean on) {
        return on ? "EMITTED" : "OMITTED";
    }
}
