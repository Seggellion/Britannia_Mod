package com.seggellion.britannia_mod.structure.persistence;

import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The queue that stops a placed house being lost because Rails happened to be down.
 *
 * <p>The old send was one inline POST with a logged warning for failure handling, so a transient
 * outage meant the house existed in the world and in no durable record at all — and, because house
 * regions are rehydrated from Rails at boot, lost its region at the next restart along with its
 * door locks and its owner's build rights.
 */
class HousePersistenceOutboxTest {

    private static final String BODY = "{\"uuid\":\"%s\",\"x\":1,\"y\":64,\"z\":2}";

    private static String bodyFor(UUID houseUuid) {
        return String.format(BODY, houseUuid);
    }

    @Test
    void aHouseIsOwedFromTheMomentItIsQueued() {
        HousePersistenceOutbox outbox = new HousePersistenceOutbox();
        UUID house = UUID.randomUUID();

        outbox.enqueue(house, bodyFor(house));

        assertEquals(1, outbox.outstanding());
        assertEquals(1, outbox.due(0L).size(),
                "a freshly queued house is due immediately; the first attempt is not a retry");
    }

    /**
     * Queueing the same house twice leaves one entry.
     *
     * <p>This is the shard half of the idempotency guarantee, and it holds without any cooperation
     * from Rails: the queue is keyed by house UUID, so a house cannot be owed twice however many
     * times delivery is attempted.
     */
    @Test
    void queueingTheSameHouseTwiceDoesNotOweItTwice() {
        HousePersistenceOutbox outbox = new HousePersistenceOutbox();
        UUID house = UUID.randomUUID();

        outbox.enqueue(house, bodyFor(house));
        outbox.enqueue(house, bodyFor(house));

        assertEquals(1, outbox.outstanding());
    }

    @Test
    void settlingClearsTheDebtAndSettlingTwiceIsHarmless() {
        HousePersistenceOutbox outbox = new HousePersistenceOutbox();
        UUID house = UUID.randomUUID();
        outbox.enqueue(house, bodyFor(house));

        outbox.settle(house);
        outbox.settle(house);

        assertEquals(0, outbox.outstanding());
        assertTrue(outbox.due(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void aFailedAttemptIsNotDueAgainUntilItsBackoffElapses() {
        HousePersistenceOutbox outbox = new HousePersistenceOutbox();
        UUID house = UUID.randomUUID();
        outbox.enqueue(house, bodyFor(house));

        outbox.deferAfterFailure(house, 10_000L);

        assertTrue(outbox.due(9_999L).isEmpty(), "a deferred house must not be retried early");
        assertEquals(1, outbox.due(10_000L).size());
        assertEquals(1, outbox.due(10_000L).get(0).attempts(),
                "the attempt count has to grow, or the backoff never widens");
    }

    @Test
    void deferringSomethingAlreadySettledDoesNotResurrectIt() {
        HousePersistenceOutbox outbox = new HousePersistenceOutbox();
        UUID house = UUID.randomUUID();
        outbox.enqueue(house, bodyFor(house));
        outbox.settle(house);

        outbox.deferAfterFailure(house, 10_000L);

        assertEquals(0, outbox.outstanding(),
                "a late failure report from an attempt that had already succeeded must not re-queue "
                        + "the house; the second delivery would be the duplicate");
    }

    /**
     * The queue survives a restart, which is the entire point of it being SavedData.
     *
     * <p>And the body is stored verbatim: a retry has to send the house that was placed, not a
     * house re-derived from a world that has since been dug up or reverted.
     */
    @Test
    void theQueueSurvivesASaveAndLoadWithItsBodyIntact() {
        HousePersistenceOutbox outbox = new HousePersistenceOutbox();
        UUID house = UUID.randomUUID();
        String body = bodyFor(house);
        outbox.enqueue(house, body);
        outbox.deferAfterFailure(house, 5_000L);

        CompoundTag saved = outbox.save(new CompoundTag(), null);
        HousePersistenceOutbox reloaded = HousePersistenceOutbox.load(saved, null);

        assertEquals(1, reloaded.outstanding());
        List<HousePersistenceOutbox.PendingHouse> due = reloaded.due(Long.MAX_VALUE);
        assertEquals(body, due.get(0).requestBody());
        assertEquals(1, due.get(0).attempts());
        assertEquals(5_000L, due.get(0).nextAttemptEpochMillis());
    }

    @Test
    void anUnreadableEntryIsKeptRatherThanDropped() {
        CompoundTag corrupt = new CompoundTag();
        net.minecraft.nbt.ListTag entries = new net.minecraft.nbt.ListTag();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("HouseUuid", UUID.randomUUID());
        entry.putString("Body", "this is not json");
        entries.add(entry);
        corrupt.put("Pending", entries);

        HousePersistenceOutbox reloaded = HousePersistenceOutbox.load(corrupt, null);

        assertEquals(0, reloaded.outstanding(),
                "an unreadable entry is not deliverable, so it is not outstanding work");
        CompoundTag round = reloaded.save(new CompoundTag(), null);
        assertEquals(1, round.getList("Pending", net.minecraft.nbt.Tag.TAG_COMPOUND).size(),
                "the record of a house must survive a decode bug; losing it is the failure this "
                        + "class exists to prevent");
    }

    @Test
    void aBodyWithNoHouseIdIsRefusedRatherThanQueued() {
        CompoundTag entry = new CompoundTag();
        entry.putUUID("HouseUuid", UUID.randomUUID());
        entry.putString("Body", "{\"x\":1}");

        assertNull(HousePersistenceOutbox.PendingHouse.fromTag(entry),
                "a body Rails could not key on is not a deliverable house");
        assertNotNull(HousePersistenceOutbox.PendingHouse.fromTag(readable()));
    }

    private static CompoundTag readable() {
        UUID house = UUID.randomUUID();
        CompoundTag entry = new CompoundTag();
        entry.putUUID("HouseUuid", house);
        entry.putString("Body", bodyFor(house));
        return entry;
    }
}
