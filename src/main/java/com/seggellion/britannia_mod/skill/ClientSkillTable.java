package com.seggellion.britannia_mod.skill;

import com.seggellion.britannia_mod.farming.FarmingPlantingItemPresentation;

import java.util.Map;

/** Client-owned projection of versioned, server-authoritative skill state. */
public final class ClientSkillTable {
    private static volatile Snapshot snapshot = Snapshot.empty();

    private ClientSkillTable() {
    }

    public static synchronized boolean applyAuthoritativeSync(
            SkillManager.SkillDataState nextState,
            Map<String, Float> fromServer,
            long nextRevision,
            boolean nextIdentificationBypass
    ) {
        Snapshot current = snapshot;
        if (nextRevision <= current.revision()) {
            return false;
        }
        Map<String, Float> nextSkills = nextState == SkillManager.SkillDataState.AVAILABLE
                ? Map.copyOf(fromServer)
                : Map.of();
        snapshot = new Snapshot(nextSkills, nextState, nextRevision, nextIdentificationBypass);
        return true;
    }

    public static float get(String skill) {
        return snapshot.skills().getOrDefault(skill, 0f);
    }

    public static Map<String, Float> snapshot() {
        return snapshot.skills();
    }

    public static SkillManager.SkillDataState state() {
        return snapshot.state();
    }

    public static long revision() {
        return snapshot.revision();
    }

    public static boolean identificationBypass() {
        return snapshot.identificationBypass();
    }

    public static FarmingPlantingItemPresentation.ViewerState farmingPresentationState() {
        Snapshot current = snapshot;
        return new FarmingPlantingItemPresentation.ViewerState(
                current.state(), current.skills().getOrDefault("farming", 0f),
                current.identificationBypass(), current.revision()
        );
    }

    /** Starts a new connection epoch before the server's LOADING snapshot arrives. */
    public static synchronized void beginSession() {
        snapshot = Snapshot.empty();
    }

    public static synchronized void clear() {
        beginSession();
    }

    private record Snapshot(
            Map<String, Float> skills,
            SkillManager.SkillDataState state,
            long revision,
            boolean identificationBypass
    ) {
        private Snapshot {
            skills = Map.copyOf(skills);
        }

        private static Snapshot empty() {
            return new Snapshot(Map.of(), SkillManager.SkillDataState.NOT_LOADED, -1L, false);
        }
    }
}
