package com.seggellion.britannia_mod.bannerdyeing.registry;

import com.seggellion.britannia_mod.bannerdyeing.validation.ValidationPolicy;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.AddReloadListenerEvent;

/** Explicit common-side registration; merely loading this class has no event-bus side effects. */
public final class BannerDataReloadRegistration {
    private BannerDataReloadRegistration() {
    }

    public static void register(IEventBus gameEventBus) {
        gameEventBus.addListener(BannerDataReloadRegistration::addReloadListener);
    }

    private static void addReloadListener(AddReloadListenerEvent event) {
        ValidationPolicy policy = FMLEnvironment.production
                ? ValidationPolicy.PRODUCTION_DISABLE_INVALID
                : ValidationPolicy.DEVELOPMENT_FAIL_FAST;
        event.addListener(new BannerDataReloadListener(policy));
    }
}
