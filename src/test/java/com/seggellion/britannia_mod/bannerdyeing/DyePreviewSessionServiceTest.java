package com.seggellion.britannia_mod.bannerdyeing;

import static com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone8TestFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone6RegisteredTestContent;
import com.seggellion.britannia_mod.dye.preview.DyeApplicationResultCode;
import com.seggellion.britannia_mod.dye.preview.DyePreviewPlan;
import com.seggellion.britannia_mod.dye.preview.DyePreviewSession;
import com.seggellion.britannia_mod.dye.preview.DyePreviewSessionService;
import com.seggellion.britannia_mod.dye.preview.DyePreviewValidationService;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class DyePreviewSessionServiceTest {
    private static RegistrySnapshot snapshot;
    private static DyePreviewPlan plan;

    @BeforeAll
    static void setup() throws Exception {
        snapshot = snapshot();
        plan = new DyePreviewValidationService(new DyeResolver()).plan(
                loadedTub(MADDER, Optional.of(4)), Milestone6RegisteredTestContent.tub(), naturalBanner(snapshot),
                bannerItem(), snapshot, true, Milestone6RegisteredTestContent.component());
        assertTrue(plan.successful());
    }

    @Test
    void opaqueUniqueIdsOneActiveSessionAndReplacement() {
        AtomicLong now = new AtomicLong(1_000);
        UUID firstId = UUID.fromString("81646fa0-734d-4cc1-a5d8-c0a8d5aa8fa1");
        UUID secondId = UUID.fromString("a9a738a7-29fd-476d-a2c5-d783c5fab17f");
        Queue<UUID> ids = new ArrayDeque<>(java.util.List.of(firstId, secondId));
        DyePreviewSessionService service = new DyePreviewSessionService(now::get, ids::remove, 30_000);
        UUID player = UUID.randomUUID();
        DyePreviewSession first = create(service, player);
        DyePreviewSession second = create(service, player);
        assertNotEquals(first.sessionId(), second.sessionId());
        assertEquals(secondId, second.sessionId());
        assertEquals(1, service.activeCount());
        assertEquals(secondId, service.activeFor(player).orElseThrow().sessionId());
        assertEquals(DyeApplicationResultCode.SESSION_REPLAYED,
                service.claimForConfirmation(player, firstId).result());
    }

    @Test
    void sessionStoresExactAuthorityAndCopiesBothStackFingerprints() {
        AtomicLong now = new AtomicLong(5_000);
        DyePreviewSessionService service = new DyePreviewSessionService(now::get, UUID::randomUUID, 30_000);
        ItemStack tub = loadedTub(MADDER, Optional.of(3));
        ItemStack banner = naturalBanner(snapshot);
        DyePreviewSession session = service.create(UUID.randomUUID(), tub, banner, plan, snapshot).orElseThrow();
        tub.setCount(0);
        banner.setCount(0);
        assertEquals(1, session.expectedMainStack().getCount());
        assertEquals(1, session.expectedOffStack().getCount());
        assertSame(snapshot, session.registrySnapshot());
        assertEquals(plan.result().orElseThrow(), session.resolvedResult());
        assertEquals(plan.bannerState().orElseThrow(), session.bannerState());
        assertEquals(plan.tubState().orElseThrow(), session.tubState());
    }

    @Test
    void expiryCancelDisconnectSuccessAndFailureAllInvalidate() {
        AtomicLong now = new AtomicLong(10_000);
        DyePreviewSessionService service = new DyePreviewSessionService(now::get, UUID::randomUUID, 30_000);
        UUID player = UUID.randomUUID();
        DyePreviewSession expired = create(service, player);
        now.addAndGet(30_000);
        assertEquals(DyeApplicationResultCode.SESSION_EXPIRED,
                service.claimForConfirmation(player, expired.sessionId()).result());

        DyePreviewSession cancelled = create(service, player);
        assertEquals(DyeApplicationResultCode.CANCELLED, service.cancel(player, cancelled.sessionId()));
        assertEquals(DyeApplicationResultCode.CANCELLED,
                service.claimForConfirmation(player, cancelled.sessionId()).result());

        DyePreviewSession consumed = create(service, player);
        assertTrue(service.claimForConfirmation(player, consumed.sessionId()).session().isPresent());
        assertEquals(DyeApplicationResultCode.SESSION_REPLAYED,
                service.claimForConfirmation(player, consumed.sessionId()).result());

        DyePreviewSession disconnected = create(service, player);
        service.invalidatePlayer(player);
        assertEquals(DyeApplicationResultCode.SESSION_MISSING,
                service.claimForConfirmation(player, disconnected.sessionId()).result());
        assertEquals(0, service.activeCount());
    }

    @Test
    void unknownWrongPlayerAndWrongActiveIdAreRejectedWithoutCrossPlayerAccess() {
        DyePreviewSessionService service = new DyePreviewSessionService();
        UUID owner = UUID.randomUUID();
        UUID attacker = UUID.randomUUID();
        DyePreviewSession session = create(service, owner);
        assertEquals(DyeApplicationResultCode.SESSION_MISMATCH,
                service.claimForConfirmation(attacker, session.sessionId()).result());
        assertEquals(DyeApplicationResultCode.SESSION_MISMATCH,
                service.claimForConfirmation(owner, UUID.randomUUID()).result());
        service.invalidatePlayer(owner);
        assertEquals(DyeApplicationResultCode.SESSION_MISSING,
                service.claimForConfirmation(owner, UUID.randomUUID()).result());
    }

    @Test
    void collisionIsRejectedRatherThanSharingPredictableIdentity() {
        UUID duplicate = UUID.randomUUID();
        DyePreviewSessionService service = new DyePreviewSessionService(
                System::currentTimeMillis, () -> duplicate, 30_000);
        assertTrue(service.create(UUID.randomUUID(), loadedTub(MADDER, Optional.empty()),
                naturalBanner(snapshot), plan, snapshot).isPresent());
        assertTrue(service.create(UUID.randomUUID(), loadedTub(MADDER, Optional.empty()),
                naturalBanner(snapshot), plan, snapshot).isEmpty());
    }

    private static DyePreviewSession create(DyePreviewSessionService service, UUID player) {
        return service.create(player, loadedTub(MADDER, Optional.of(4)), naturalBanner(snapshot), plan, snapshot)
                .orElseThrow();
    }
}
