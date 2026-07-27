package com.seggellion.britannia_mod.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerItemFactory;
import com.seggellion.britannia_mod.bannerdyeing.admin.AdminItemDelivery;
import com.seggellion.britannia_mod.bannerdyeing.admin.AdminItemRecipient;
import com.seggellion.britannia_mod.bannerdyeing.admin.BannerAdminResult;
import com.seggellion.britannia_mod.bannerdyeing.admin.BannerAdminService;
import com.seggellion.britannia_mod.bannerdyeing.admin.BannerAdminSuggestions;
import com.seggellion.britannia_mod.bannerdyeing.admin.BannerCatalogueAdminService;
import com.seggellion.britannia_mod.bannerdyeing.admin.DyeDebugResult;
import com.seggellion.britannia_mod.bannerdyeing.admin.DyeResolutionDebugService;
import com.seggellion.britannia_mod.bannerdyeing.admin.DyeTubAdminResult;
import com.seggellion.britannia_mod.bannerdyeing.admin.DyeTubAdminService;
import com.seggellion.britannia_mod.bannerdyeing.registry.BannerDataRegistries;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.PigmentId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import com.seggellion.britannia_mod.dye.service.DyeResolver;
import com.seggellion.britannia_mod.dye.service.MatchType;
import com.seggellion.britannia_mod.dye.source.RegistryPigmentSourceService;
import com.seggellion.britannia_mod.registry.BannerItemRegistry;
import com.seggellion.britannia_mod.registry.DyeItemRegistry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** One common/server-only administrator tree for Milestone 15 acquisition and diagnostics. */
public final class BannerDyeAdminCommands {
    public static final String ROOT = "britannia";
    public static final int REQUIRED_PERMISSION_LEVEL = 2;

    private BannerDyeAdminCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        register(dispatcher, new BannerGiveDependencies(
                BannerDataRegistries::current,
                BannerDataRegistries::isAvailable,
                () -> BannerItemRegistry.BANNER.get(),
                BannerDyeAdminCommands::playerRecipients));
    }

    static void register(
            CommandDispatcher<CommandSourceStack> dispatcher, BannerGiveDependencies bannerDependencies) {
        var pigmentSource = new RegistryPigmentSourceService();
        var suggestions = new BannerAdminSuggestions(pigmentSource);
        var catalogue = new BannerCatalogueAdminService();
        var debug = new DyeResolutionDebugService(new DyeResolver());

        dispatcher.register(Commands.literal(ROOT)
                .requires(source -> source.hasPermission(REQUIRED_PERMISSION_LEVEL))
                .then(Commands.literal("banner")
                        .then(bannerGive(suggestions, bannerDependencies))
                        .then(Commands.literal("validate")
                                .executes(context -> validate(context, catalogue)))
                        .then(Commands.literal("placeholders")
                                .executes(context -> placeholders(context, catalogue, 1))
                                .then(Commands.argument("page", IntegerArgumentType.integer())
                                        .executes(context -> placeholders(
                                                context, catalogue, IntegerArgumentType.getInteger(context, "page"))))))
                .then(Commands.literal("dye")
                        .then(Commands.literal("tub")
                                .then(dyeTubGive(suggestions)))
                        .then(Commands.literal("resolve")
                                .then(Commands.argument("pigment", StringArgumentType.word())
                                        .suggests((context, builder) -> suggest(
                                                suggestions.pigments(
                                                        BannerDataRegistries.current(),
                                                        BannerDataRegistries.isAvailable()), builder))
                                        .then(Commands.argument("material", StringArgumentType.word())
                                                .suggests((context, builder) -> suggest(
                                                        suggestions.materials(
                                                                BannerDataRegistries.current(),
                                                                BannerDataRegistries.isAvailable()), builder))
                                                .executes(context -> resolve(context, debug)))))));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> bannerGive(
            BannerAdminSuggestions suggestions, BannerGiveDependencies dependencies) {
        var mount = Commands.argument("mount", ResourceLocationArgument.id())
                .suggests((context, builder) -> suggest(suggestions.mounts(
                        argumentDefinition(context), dependencies.snapshot(), dependencies.registryAvailable()),
                        builder))
                .executes(context -> giveBanner(context, argumentMaterial(context), argumentColour(context),
                        argumentMount(context), dependencies));
        var colour = Commands.argument("colour", ResourceLocationArgument.id())
                .suggests((context, builder) -> suggest(suggestions.colours(
                        argumentMaterial(context), dependencies.snapshot(), dependencies.registryAvailable()),
                        builder))
                .executes(context -> giveBanner(context, argumentMaterial(context), argumentColour(context),
                        Optional.empty(), dependencies))
                .then(mount);
        var material = Commands.argument("material", ResourceLocationArgument.id())
                .suggests((context, builder) -> suggest(suggestions.materials(
                        dependencies.snapshot(), dependencies.registryAvailable()), builder))
                .executes(context -> giveBanner(context, argumentMaterial(context), Optional.empty(),
                        Optional.empty(), dependencies))
                .then(colour);
        var definition = Commands.argument("definition", ResourceLocationArgument.id())
                .suggests((context, builder) -> suggest(suggestions.definitions(
                        dependencies.snapshot(), dependencies.registryAvailable()), builder))
                .executes(context -> giveBanner(context, Optional.empty(), Optional.empty(), Optional.empty(),
                        dependencies))
                .then(material);
        var targets = Commands.argument("targets", EntityArgument.players()).then(definition);
        return Commands.literal("give").then(targets);
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> dyeTubGive(
            BannerAdminSuggestions suggestions) {
        return Commands.literal("give")
                .then(Commands.argument("targets", EntityArgument.players())
                        .executes(context -> giveTub(context, Optional.empty()))
                        .then(Commands.argument("pigment", StringArgumentType.word())
                                .suggests((context, builder) -> suggest(suggestions.pigments(
                                        BannerDataRegistries.current(), BannerDataRegistries.isAvailable()), builder))
                                .executes(context -> giveTub(context, argumentPigment(context)))));
    }

    private static int giveBanner(
            CommandContext<CommandSourceStack> context,
            Optional<FabricMaterialId> material,
            Optional<ResolvedColourId> colour,
            Optional<MountId> mount,
            BannerGiveDependencies dependencies) {
        Optional<BannerDefinitionId> definition = argumentDefinition(context);
        if (definition.isEmpty() || (hasArgument(context, "material") && material.isEmpty())
                || (hasArgument(context, "colour") && colour.isEmpty())
                || (hasArgument(context, "mount") && mount.isEmpty())) {
            context.getSource().sendFailure(Component.translatable("command.britannia_mod.admin.invalid_id"));
            return 0;
        }
        RegistrySnapshot snapshot = dependencies.snapshot();
        BannerItem item = dependencies.bannerItem();
        BannerAdminService service = new BannerAdminService(
                new BannerItemFactory(item, item.stateAccess()), item.stateAccess());
        BannerAdminResult initial = service.create(definition.orElseThrow(), material, colour, mount,
                snapshot, dependencies.registryAvailable());
        if (!initial.successful()) {
            context.getSource().sendFailure(Component.translatable(
                    "command.britannia_mod.admin.banner.failure." + initial.failure().name().toLowerCase(),
                    initial.diagnosticId()));
            return 0;
        }
        List<? extends AdminItemRecipient> recipients;
        try {
            recipients = dependencies.targets().resolve(context);
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal(exception.getRawMessage().getString()));
            return 0;
        }
        var delivery = AdminItemDelivery.deliverFresh(recipients, () -> service.create(
                definition.orElseThrow(), material, colour, mount, snapshot, true).stack().orElseThrow());
        var state = initial.state().orElseThrow();
        for (String target : delivery.deliveredTargets()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.britannia_mod.admin.banner.given", target, state.bannerDefinitionId().toString(),
                    state.materialId().toString(), state.resolvedColourId().toString(), state.mountId().toString()),
                    false);
        }
        for (String target : delivery.failedTargets()) {
            context.getSource().sendFailure(Component.translatable(
                    "command.britannia_mod.admin.delivery_failed", target));
        }
        return delivery.successCount();
    }

    private static int giveTub(CommandContext<CommandSourceStack> context, Optional<PigmentId> pigment) {
        if (hasArgument(context, "pigment") && pigment.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.britannia_mod.admin.invalid_id"));
            return 0;
        }
        RegistrySnapshot snapshot = BannerDataRegistries.current();
        DyeTubAdminService service = new DyeTubAdminService(
                DyeItemRegistry.DYE_TUB.get(), new RegistryPigmentSourceService());
        DyeTubAdminResult initial = service.create(pigment, snapshot, BannerDataRegistries.isAvailable());
        if (!initial.successful()) {
            context.getSource().sendFailure(Component.translatable(
                    "command.britannia_mod.admin.dye_tub.failure." + initial.failure().name().toLowerCase(),
                    initial.diagnosticId()));
            return 0;
        }
        Collection<ServerPlayer> players;
        try {
            players = EntityArgument.getPlayers(context, "targets");
        } catch (com.mojang.brigadier.exceptions.CommandSyntaxException exception) {
            context.getSource().sendFailure(Component.literal(exception.getRawMessage().getString()));
            return 0;
        }
        List<PlayerRecipient> recipients = players.stream().map(PlayerRecipient::new).toList();
        var delivery = AdminItemDelivery.deliverFresh(recipients,
                () -> service.create(pigment, snapshot, true).stack().orElseThrow());
        for (String target : delivery.deliveredTargets()) {
            Component message = pigment
                    .<Component>map(id -> Component.translatable(
                            "command.britannia_mod.admin.dye_tub.loaded", target, id.toString()))
                    .orElseGet(() -> Component.translatable(
                            "command.britannia_mod.admin.dye_tub.empty", target));
            context.getSource().sendSuccess(() -> message, false);
        }
        for (String target : delivery.failedTargets()) {
            context.getSource().sendFailure(Component.translatable(
                    "command.britannia_mod.admin.delivery_failed", target));
        }
        return delivery.successCount();
    }

    private static int validate(
            CommandContext<CommandSourceStack> context, BannerCatalogueAdminService service) {
        RegistrySnapshot snapshot = BannerDataRegistries.current();
        var result = service.validate(snapshot, BannerDataRegistries.isAvailable(),
                BannerDataRegistries.validationIssues(snapshot));
        Component summary = Component.translatable(
                "command.britannia_mod.admin.validate.summary",
                result.activeBanners(), result.disabledBanners(), result.materials(), result.palettes(),
                result.pigments(), result.mounts(), result.placeholders(), result.provisionalNames(),
                result.provisionalDimensions(), result.errors(), result.warnings());
        if (result.successful()) {
            context.getSource().sendSuccess(() -> summary, false);
        } else {
            context.getSource().sendFailure(summary);
        }
        result.boundedIssues().forEach(issue ->
                context.getSource().sendFailure(Component.literal(issue)));
        return result.successful() ? 1 : 0;
    }

    private static int placeholders(
            CommandContext<CommandSourceStack> context, BannerCatalogueAdminService service, int requestedPage) {
        if (!BannerDataRegistries.isAvailable()) {
            context.getSource().sendFailure(Component.translatable(
                    "command.britannia_mod.admin.registry_unavailable"));
            return 0;
        }
        var page = service.placeholders(BannerDataRegistries.current(), requestedPage);
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.britannia_mod.admin.placeholders.title",
                page.totalCount(), page.page(), page.pageCount()), false);
        if (page.entries().isEmpty()) {
            context.getSource().sendSuccess(() -> Component.translatable(
                    "command.britannia_mod.admin.placeholders.none"), false);
        }
        page.entries().forEach(entry -> context.getSource().sendSuccess(() -> Component.translatable(
                "command.britannia_mod.admin.placeholders.entry",
                entry.definitionId().toString(), Component.translatable(entry.displayNameKey()),
                entry.width(), entry.height(), entry.contentStatus().serializedName(),
                entry.provisionalName(), entry.provisionalDimensions()), false));
        return page.totalCount();
    }

    private static int resolve(
            CommandContext<CommandSourceStack> context, DyeResolutionDebugService service) {
        Optional<PigmentId> pigment = argumentPigment(context);
        Optional<FabricMaterialId> material = argumentMaterial(context);
        if (pigment.isEmpty() || material.isEmpty()) {
            context.getSource().sendFailure(Component.translatable("command.britannia_mod.admin.invalid_id"));
            return 0;
        }
        RegistrySnapshot snapshot = BannerDataRegistries.current();
        DyeDebugResult result = service.resolve(
                pigment.orElseThrow(), material.orElseThrow(), snapshot, BannerDataRegistries.isAvailable());
        if (!result.successful()) {
            context.getSource().sendFailure(Component.translatable(
                    "command.britannia_mod.admin.resolve.failure." + result.failure().name().toLowerCase(),
                    result.diagnosticId()));
            return 0;
        }
        var explained = result.resolution().orElseThrow();
        var dye = explained.outcome().result().orElseThrow();
        var detail = explained.explanation();
        String matchKey = dye.matchType() == MatchType.EXPLICIT_MAPPING
                ? "command.britannia_mod.admin.resolve.explicit"
                : "command.britannia_mod.admin.resolve.nearest";
        context.getSource().sendSuccess(() -> Component.translatable(
                "command.britannia_mod.admin.resolve.result",
                pigment.orElseThrow().toString(), material.orElseThrow().toString(),
                dye.resolvedColourId().toString(), Component.translatable(result.resolvedColourNameKey().orElseThrow()),
                Component.translatable(matchKey), detail.explicitMappingUsed(),
                dye.perceptualDistance(), detail.compatibleCandidates().size(),
                detail.rejectedEntries().size(), detail.finalTieBreak().name()), false);
        return 1;
    }

    private static CompletableFuture<Suggestions> suggest(List<String> values, SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(values, builder);
    }

    private static Optional<BannerDefinitionId> argumentDefinition(CommandContext<?> context) {
        return parse(context, "definition", BannerDefinitionId::parse);
    }

    private static Optional<FabricMaterialId> argumentMaterial(CommandContext<?> context) {
        return parse(context, "material", FabricMaterialId::parse);
    }

    private static Optional<ResolvedColourId> argumentColour(CommandContext<?> context) {
        return parse(context, "colour", ResolvedColourId::parse);
    }

    private static Optional<MountId> argumentMount(CommandContext<?> context) {
        return parse(context, "mount", MountId::parse);
    }

    private static Optional<PigmentId> argumentPigment(CommandContext<?> context) {
        return parse(context, "pigment", PigmentId::parse);
    }

    private static <T> Optional<T> parse(
            CommandContext<?> context, String argument, java.util.function.Function<String, T> parser) {
        try {
            Object value = context.getArgument(argument, Object.class);
            String serialized = value instanceof ResourceLocation id ? id.toString() : (String) value;
            return Optional.of(parser.apply(serialized));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }

    private static boolean hasArgument(CommandContext<?> context, String argument) {
        try {
            context.getArgument(argument, Object.class);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static List<PlayerRecipient> playerRecipients(
            CommandContext<CommandSourceStack> context)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        return players.stream().map(PlayerRecipient::new).toList();
    }

    @FunctionalInterface
    interface AdminTargetResolver {
        List<? extends AdminItemRecipient> resolve(CommandContext<CommandSourceStack> context)
                throws com.mojang.brigadier.exceptions.CommandSyntaxException;
    }

    record BannerGiveDependencies(
            Supplier<RegistrySnapshot> snapshots,
            BooleanSupplier availability,
            Supplier<BannerItem> bannerItems,
            AdminTargetResolver targets) {
        BannerGiveDependencies {
            java.util.Objects.requireNonNull(snapshots, "snapshots");
            java.util.Objects.requireNonNull(availability, "availability");
            java.util.Objects.requireNonNull(bannerItems, "bannerItems");
            java.util.Objects.requireNonNull(targets, "targets");
        }

        RegistrySnapshot snapshot() {
            return java.util.Objects.requireNonNull(snapshots.get(), "snapshot");
        }

        boolean registryAvailable() {
            return availability.getAsBoolean();
        }

        BannerItem bannerItem() {
            return java.util.Objects.requireNonNull(bannerItems.get(), "bannerItem");
        }
    }

    private record PlayerRecipient(ServerPlayer player) implements AdminItemRecipient {
        @Override
        public String name() {
            return player.getGameProfile().getName();
        }

        @Override
        public boolean insert(ItemStack stack) {
            return player.getInventory().add(stack);
        }

        @Override
        public boolean dropRemainder(ItemStack stack) {
            return player.drop(stack, false) != null;
        }
    }
}
