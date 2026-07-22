package com.seggellion.britannia_mod.client.banner;

import com.seggellion.britannia_mod.banner.api.BannerOrientation;
import java.util.List;
import java.util.Optional;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Display-only mirror of the server preference; it is never read by server placement validation. */
@OnlyIn(Dist.CLIENT)
public final class ClientBannerPlacementState {
    private static volatile BannerOrientation selected;

    private ClientBannerPlacementState() {
    }

    public static void replace(BannerOrientation orientation) {
        selected = java.util.Objects.requireNonNull(orientation, "orientation");
    }

    public static BannerOrientation normalized(List<BannerOrientation> supported) {
        List<BannerOrientation> ordered = BannerOrientation.orderedSupported(supported);
        if (ordered.isEmpty()) {
            return BannerOrientation.WALL_PARALLEL;
        }
        BannerOrientation current = selected;
        return current != null && ordered.contains(current) ? current : ordered.getFirst();
    }

    public static Optional<BannerOrientation> selected() {
        return Optional.ofNullable(selected);
    }

    public static void clear() {
        selected = null;
    }
}
