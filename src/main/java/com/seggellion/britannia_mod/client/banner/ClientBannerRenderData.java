package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.renderdata.BannerRenderDataSnapshot;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Client-only atomic publication of server-synchronized, display-only metadata. */
@OnlyIn(Dist.CLIENT)
public final class ClientBannerRenderData {
    private static final AtomicReference<ClientBannerRenderPublication> CURRENT = new AtomicReference<>(
            new ClientBannerRenderPublication(BannerRenderDataSnapshot.empty(), 0, false));

    private ClientBannerRenderData() {
    }

    public static ClientBannerRenderPublication current() {
        return CURRENT.get();
    }

    public static void replace(BannerRenderDataSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        CURRENT.updateAndGet(previous -> new ClientBannerRenderPublication(snapshot, previous.generation() + 1, true));
        BannerRenderCache.onClientDataReplaced();
    }

    public static void clear() {
        CURRENT.updateAndGet(previous -> new ClientBannerRenderPublication(
                BannerRenderDataSnapshot.empty(), previous.generation() + 1, false));
        BannerRenderCache.onClientDataReplaced();
    }
}
