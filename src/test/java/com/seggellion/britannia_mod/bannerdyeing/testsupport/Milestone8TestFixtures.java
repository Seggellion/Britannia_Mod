package com.seggellion.britannia_mod.bannerdyeing.testsupport;

import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.item.DyeTubStateAccess;
import com.seggellion.britannia_mod.dye.state.DyeTubState;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class Milestone8TestFixtures {
    public static final BannerDefinitionId WARD = BannerDefinitionId.parse("britannia_mod:ward_of_serpents");
    public static final FabricMaterialId COTTON = FabricMaterialId.parse("britannia_mod:cotton");
    public static final FabricMaterialId SILK = FabricMaterialId.parse("britannia_mod:silk");
    public static final PigmentId MADDER = PigmentId.parse("britannia_mod:madder_red");
    public static final PigmentId WOAD = PigmentId.parse("britannia_mod:woad_blue");

    private Milestone8TestFixtures() {
    }

    public static void ensureRegistered() {
        Milestone7RegisteredTestContent.ensureRegistered();
    }

    public static RegistrySnapshot snapshot() throws Exception {
        ensureRegistered();
        return DyeResolverFixtures.productionSnapshot();
    }

    public static BannerItem bannerItem() {
        ensureRegistered();
        return Milestone7RegisteredTestContent.banner();
    }

    public static ItemStack naturalBanner(RegistrySnapshot snapshot) {
        return new BannerItemFactory(bannerItem(), bannerItem().stateAccess())
                .naturalCottonAdminBanner(WARD, snapshot, true).stack().orElseThrow();
    }

    public static ItemStack naturalBanner(RegistrySnapshot snapshot, FabricMaterialId material) {
        return new BannerItemFactory(bannerItem(), bannerItem().stateAccess())
                .craftedMaterialBanner(WARD, material, Optional.empty(), snapshot, true).stack().orElseThrow();
    }

    public static ItemStack loadedTub(PigmentId pigmentId, Optional<Integer> uses) {
        ensureRegistered();
        ItemStack stack = new ItemStack(Milestone6RegisteredTestContent.tub());
        DyeTubStateAccess.write(stack, Milestone6RegisteredTestContent.component(),
                new DyeTubState(1, Optional.of(pigmentId), uses));
        return stack;
    }
}
