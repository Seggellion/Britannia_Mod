package com.seggellion.britannia_mod.quest.handin;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Rails' own removal-proof matcher, transcribed, so this repository can prove against it rather
 * than against its own opinion of it.
 *
 * <h2>Why a transcription and not a mock</h2>
 * A refused proof is the one answer this feature cannot recover from. Rails leaves the hand-in
 * exactly as it was for {@code evidence_rejected} -- not consumed, not cancelled, not paid -- and
 * the proof this server would resend is stored and byte-stable, so every retry earns the identical
 * refusal. A transaction that reaches it has taken an item and can never finish. The whole safety
 * argument therefore rests on "the mod cannot produce a proof Rails refuses", and that claim is
 * worth an executable oracle rather than a comment.
 *
 * <p>Transcribed from {@code QuestItemHandins::Confirm#matched_proof} and
 * {@code #requirement_satisfied?} at {@code release/public} @ {@code e8a0d58}. Every refusal below
 * is one of that method's own {@code return nil} / {@code return false} branches, in its order.
 *
 * <p><b>Note the one thing it does not check.</b> A resolver requirement is satisfied by the
 * resolver name and the pinned flag value alone -- the concrete item is never compared, because not
 * knowing the crop-to-produce mapping is the entire reason the resolver shape exists (protocol
 * section 1.5.5, "the one thing Rails takes on trust"). That is why the frozen fixture may name an
 * illustrative item this mod does not even register, and the live mapping still agrees with it
 * contractually.
 */
final class RailsEvidenceMatcher {

    private RailsEvidenceMatcher() {}

    /** Accepted, or the refusal Rails would answer with. */
    record Verdict(boolean accepted, String reason) {
        static Verdict ok() {
            return new Verdict(true, "");
        }

        static Verdict refused(String reason) {
            return new Verdict(false, reason);
        }
    }

    /**
     * @param requirements the hand-in's requirements <b>as Rails persisted them</b>, which is also
     *                     what it republishes in the demand
     * @param proof        what the shard reports it removed
     */
    static Verdict match(List<QuestHandinRequirement> requirements, List<QuestHandinRemoval> proof) {
        // return nil unless @removed_items.size == requirements.size
        if (proof.size() != requirements.size()) {
            return Verdict.refused("size " + proof.size() + " != requirements " + requirements.size());
        }
        Map<Integer, QuestHandinRemoval> matched = new HashMap<>();
        for (QuestHandinRemoval entry : proof) {
            int index = entry.requirementIndex();
            // return nil unless index.is_a?(Integer) && index >= 0 && index < requirements.size
            if (index < 0 || index >= requirements.size()) {
                return Verdict.refused("requirement_index " + index + " out of range");
            }
            // return nil if matched.key?(index)
            if (matched.containsKey(index)) {
                return Verdict.refused("requirement_index " + index + " reported twice");
            }
            QuestHandinRequirement requirement = requirements.get(index);
            // return nil unless entry[:count] == requirement[:count]
            if (entry.count() != requirement.count()) {
                return Verdict.refused("count " + entry.count() + " != " + requirement.count()
                        + " at index " + index);
            }
            Verdict satisfied = satisfies(requirement, entry);
            if (!satisfied.accepted()) return satisfied;
            matched.put(index, entry);
        }
        return Verdict.ok();
    }

    /** {@code requirement_satisfied?}, both branches. */
    private static Verdict satisfies(QuestHandinRequirement requirement, QuestHandinRemoval entry) {
        if (requirement instanceof QuestHandinRequirement.Resolver resolver) {
            // return false unless entry[:resolver].to_s == requirement[:resolver].to_s
            if (!resolver.resolver().equals(entry.resolver())) {
                return Verdict.refused("resolver " + entry.resolver() + " != " + resolver.resolver());
            }
            // return true if requirement[:flag_value].blank?  -- unreachable here: Prepare refuses to
            // publish a resolver requirement with no pinned value, and parseDemand refuses to read one.
            if (!resolver.flagValue().equals(entry.flagValue())) {
                return Verdict.refused("flag_value " + entry.flagValue() + " != " + resolver.flagValue());
            }
            // The concrete item is deliberately NOT compared.
            return Verdict.ok();
        }
        QuestHandinRequirement.Literal literal = (QuestHandinRequirement.Literal) requirement;
        // !entry.key?(:resolver) && entry[:id].to_s == requirement[:id].to_s
        if (entry.answersResolver()) {
            return Verdict.refused("a literal requirement was dressed up as a resolver at index "
                    + literal.index());
        }
        if (!literal.itemId().equals(entry.itemId())) {
            return Verdict.refused("item " + entry.itemId() + " != " + literal.itemId());
        }
        return Verdict.ok();
    }
}
