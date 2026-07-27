package com.seggellion.britannia_mod.commands;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import com.seggellion.britannia_mod.banner.api.BannerDefinitionId;
import com.seggellion.britannia_mod.banner.api.MountId;
import com.seggellion.britannia_mod.banner.data.BannerDefinition;
import com.seggellion.britannia_mod.banner.item.BannerItem;
import com.seggellion.britannia_mod.banner.item.BannerStateStatus;
import com.seggellion.britannia_mod.banner.state.BannerInstanceState;
import com.seggellion.britannia_mod.bannerdyeing.admin.AdminItemRecipient;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshot;
import com.seggellion.britannia_mod.bannerdyeing.registry.RegistrySnapshotTestFactory;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.DyeResolverFixtures;
import com.seggellion.britannia_mod.bannerdyeing.testsupport.Milestone7RegisteredTestContent;
import com.seggellion.britannia_mod.dye.api.FabricMaterialId;
import com.seggellion.britannia_mod.dye.api.ResolvedColourId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class BannerGiveCommandHierarchyTest {
    private static final BannerDefinitionId ROAD_GUARD =
            BannerDefinitionId.parse("britannia_mod:road_guard");
    private static final FabricMaterialId COTTON = FabricMaterialId.parse("britannia_mod:cotton");
    private static final ResolvedColourId COTTON_NATURAL =
            ResolvedColourId.parse("britannia_mod:cotton_natural");
    private static final ResolvedColourId COTTON_BLUE =
            ResolvedColourId.parse("britannia_mod:cotton_blue");
    private static final MountId BRASS = MountId.parse("britannia_mod:brass");
    private static final MountId IRON = MountId.parse("britannia_mod:iron");
    private static RegistrySnapshot production;
    private static BannerItem bannerItem;

    @BeforeAll
    static void setup() throws Exception {
        Milestone7RegisteredTestContent.ensureRegistered();
        production = DyeResolverFixtures.productionSnapshot();
        bannerItem = Milestone7RegisteredTestContent.banner();
    }

    @Test
    void legacyWordParserReproducesBothReportedTrailingDataFailuresAtTheNamespaceColon() throws Exception {
        CommandDispatcher<CommandSourceStack> dispatcher = legacyWordDispatcher();
        for (String command : List.of(
                "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_natural",
                "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_natural britannia_mod:brass")) {
            ParseResults<CommandSourceStack> parsed = dispatcher.parse(withoutSlash(command), source(2));
            assertTrue(parsed.getReader().canRead(), command);
            var exception = assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                    () -> dispatcher.execute(parsed), command);
            assertTrue(exception.getMessage().contains(
                    "Expected whitespace to end one argument, but found trailing data"), exception.getMessage());
            assertEquals(':', withoutSlash(command).charAt(exception.getCursor()), command);
        }

        StringReader reader = new StringReader("britannia_mod:road_guard");
        assertEquals("britannia_mod", StringArgumentType.word().parse(reader));
        assertTrue(reader.canRead());
        assertEquals(':', reader.peek());
    }

    @Test
    void productionTreeHasOneFullyNestedExecutableAndSuggestedOptionalChain() {
        CommandDispatcher<CommandSourceStack> dispatcher =
                dispatcher(() -> production, () -> true, new RecordingRecipient());
        CommandNode<CommandSourceStack> give = path(dispatcher, "britannia", "banner", "give");
        CommandNode<CommandSourceStack> targets = onlyChild(give, "targets");
        CommandNode<CommandSourceStack> definition = onlyChild(targets, "definition");
        CommandNode<CommandSourceStack> material = onlyChild(definition, "material");
        CommandNode<CommandSourceStack> colour = onlyChild(material, "colour");
        CommandNode<CommandSourceStack> mount = onlyChild(colour, "mount");

        assertNull(give.getCommand());
        assertNull(targets.getCommand());
        for (CommandNode<CommandSourceStack> executable : List.of(definition, material, colour, mount)) {
            assertNotNull(executable.getCommand(), executable.getName());
            assertNotNull(((ArgumentCommandNode<CommandSourceStack, ?>) executable).getCustomSuggestions(),
                    executable.getName());
        }
        assertTrue(mount.getChildren().isEmpty());
    }

    @Test
    void allSupportedLiteralFormsConsumeTheEntireInputExecuteAndDeliverOneValidatedBanner() throws Exception {
        List<ValidCase> cases = List.of(
                new ValidCase("/britannia banner give @s britannia_mod:road_guard",
                        COTTON, COTTON_NATURAL, BRASS),
                new ValidCase("/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton",
                        COTTON, COTTON_NATURAL, BRASS),
                new ValidCase("/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_natural", COTTON, COTTON_NATURAL, BRASS),
                new ValidCase("/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_natural britannia_mod:brass",
                        COTTON, COTTON_NATURAL, BRASS),
                new ValidCase("/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_natural britannia_mod:iron",
                        COTTON, COTTON_NATURAL, IRON),
                new ValidCase("/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_blue britannia_mod:iron",
                        COTTON, COTTON_BLUE, IRON));

        for (ValidCase expected : cases) {
            RecordingRecipient recipient = new RecordingRecipient();
            CommandDispatcher<CommandSourceStack> dispatcher =
                    dispatcher(() -> production, () -> true, recipient);
            ParseResults<CommandSourceStack> parsed =
                    dispatcher.parse(withoutSlash(expected.command()), source(2));

            assertFalse(parsed.getReader().canRead(), expected.command());
            assertTrue(parsed.getExceptions().isEmpty(), parsed.getExceptions().toString());
            assertNotNull(parsed.getContext().getCommand(), expected.command());
            assertEquals(1, dispatcher.execute(parsed), expected.command());
            assertEquals(1, recipient.delivered().size(), expected.command());

            ItemStack delivered = recipient.delivered().getFirst();
            BannerInstanceState state = bannerItem.stateAccess().read(delivered).orElseThrow();
            assertEquals(ROAD_GUARD, state.bannerDefinitionId(), expected.command());
            assertEquals(expected.material(), state.materialId(), expected.command());
            assertEquals(expected.colour(), state.resolvedColourId(), expected.command());
            assertEquals(expected.mount(), state.mountId(), expected.command());
            assertTrue(state.sourcePigmentId().isEmpty(), expected.command());
            var validation = bannerItem.stateAccess().validate(delivered, production, true);
            assertTrue(validation.validForColourUpdate(), validation.issues().toString());
            assertTrue(validation.status() == BannerStateStatus.VALID
                    || validation.status() == BannerStateStatus.VALID_WITH_DIAGNOSTICS);
        }
    }

    @Test
    void completionsAreContextualPermissionSafeAndReadTheCurrentSnapshot() throws Exception {
        AtomicReference<RegistrySnapshot> snapshot = new AtomicReference<>(production);
        RecordingRecipient recipient = new RecordingRecipient();
        CommandDispatcher<CommandSourceStack> dispatcher =
                dispatcher(snapshot::get, () -> true, recipient);

        assertEquals(Set.of(
                        "britannia_mod:cotton", "britannia_mod:linen",
                        "britannia_mod:silk", "britannia_mod:wool"),
                suggestions(dispatcher,
                        "/britannia banner give @s britannia_mod:road_guard ", source(2)));
        assertEquals(Set.of(
                        "britannia_mod:cotton_natural", "britannia_mod:cotton_red",
                        "britannia_mod:cotton_blue", "britannia_mod:cotton_green",
                        "britannia_mod:cotton_gold", "britannia_mod:cotton_charcoal",
                        "britannia_mod:cotton_ivory", "britannia_mod:cotton_taupe"),
                suggestions(dispatcher,
                        "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton ", source(2)));
        assertEquals(Set.of("britannia_mod:brass", "britannia_mod:iron"),
                suggestions(dispatcher,
                        "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                                + "britannia_mod:cotton_natural ",
                        source(2)));
        assertTrue(suggestions(dispatcher,
                "/britannia banner give @s britannia_mod:road_guard ", source(1)).isEmpty());

        snapshot.set(RegistrySnapshotTestFactory.withDisabledMaterial(production, COTTON));
        assertFalse(suggestions(dispatcher,
                "/britannia banner give @s britannia_mod:road_guard ", source(2))
                .contains("britannia_mod:cotton"));

        snapshot.set(brassOnlyRoadGuard(production));
        assertEquals(Set.of("britannia_mod:brass"),
                suggestions(dispatcher,
                        "/britannia banner give @s britannia_mod:road_guard britannia_mod:wool "
                                + "britannia_mod:wool_natural ",
                        source(2)));
        assertTrue(recipient.delivered().isEmpty());
    }

    @Test
    void semanticAndSyntacticFailuresNeverDeliverAnItem() throws Exception {
        for (String command : List.of(
                "/britannia banner give @s britannia_mod:road_guard britannia_mod:unknown",
                "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:silk_ruby",
                "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_natural britannia_mod:unknown",
                "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:brass")) {
            RecordingRecipient recipient = new RecordingRecipient();
            CommandDispatcher<CommandSourceStack> dispatcher =
                    dispatcher(() -> production, () -> true, recipient);
            assertEquals(0, dispatcher.execute(withoutSlash(command), source(2)), command);
            assertTrue(recipient.delivered().isEmpty(), command);
        }

        RecordingRecipient unsupportedRecipient = new RecordingRecipient();
        CommandDispatcher<CommandSourceStack> restricted =
                dispatcher(() -> brassOnlyRoadGuard(production), () -> true, unsupportedRecipient);
        assertEquals(0, restricted.execute(withoutSlash(
                "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_natural britannia_mod:iron"), source(2)));
        assertTrue(unsupportedRecipient.delivered().isEmpty());

        RecordingRecipient extraRecipient = new RecordingRecipient();
        CommandDispatcher<CommandSourceStack> extraDispatcher =
                dispatcher(() -> production, () -> true, extraRecipient);
        ParseResults<CommandSourceStack> extra = extraDispatcher.parse(withoutSlash(
                "/britannia banner give @s britannia_mod:road_guard britannia_mod:cotton "
                        + "britannia_mod:cotton_natural britannia_mod:brass extra"), source(2));
        assertTrue(extra.getReader().canRead());
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> extraDispatcher.execute(extra));
        assertTrue(extraRecipient.delivered().isEmpty());

        RecordingRecipient unauthorizedRecipient = new RecordingRecipient();
        CommandDispatcher<CommandSourceStack> unauthorized =
                dispatcher(() -> production, () -> true, unauthorizedRecipient);
        assertThrows(com.mojang.brigadier.exceptions.CommandSyntaxException.class,
                () -> unauthorized.execute(withoutSlash(
                        "/britannia banner give @s britannia_mod:road_guard"), source(1)));
        assertTrue(unauthorizedRecipient.delivered().isEmpty());

        RecordingRecipient unavailableRecipient = new RecordingRecipient();
        CommandDispatcher<CommandSourceStack> unavailable =
                dispatcher(RegistrySnapshot::empty, () -> false, unavailableRecipient);
        assertEquals(0, unavailable.execute(withoutSlash(
                "/britannia banner give @s britannia_mod:road_guard"), source(2)));
        assertTrue(unavailableRecipient.delivered().isEmpty());
    }

    private static CommandDispatcher<CommandSourceStack> dispatcher(
            Supplier<RegistrySnapshot> snapshots,
            BooleanSupplier availability,
            RecordingRecipient recipient) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        BannerDyeAdminCommands.register(dispatcher, new BannerDyeAdminCommands.BannerGiveDependencies(
                snapshots, availability, () -> bannerItem, context -> List.of(recipient)));
        return dispatcher;
    }

    private static CommandDispatcher<CommandSourceStack> legacyWordDispatcher() {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        dispatcher.register(Commands.literal("britannia")
                .then(Commands.literal("banner")
                        .then(Commands.literal("give")
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .then(Commands.argument("definition", StringArgumentType.word())
                                                .executes(context -> 1)
                                                .then(Commands.argument("material", StringArgumentType.word())
                                                        .executes(context -> 1)
                                                        .then(Commands.argument(
                                                                        "colour", StringArgumentType.word())
                                                                .executes(context -> 1)
                                                                .then(Commands.argument(
                                                                                "mount",
                                                                                StringArgumentType.word())
                                                                        .executes(context -> 1)))))))));
        return dispatcher;
    }

    private static RegistrySnapshot brassOnlyRoadGuard(RegistrySnapshot source) {
        BannerDefinition definition = source.banners().require(ROAD_GUARD);
        BannerDefinition replacement = new BannerDefinition(
                definition.schemaVersion(), definition.id(), definition.displayNameKey(), definition.contentStatus(),
                definition.sourceReference(), definition.catalogueGroup(), definition.dimensions(),
                definition.supportedOrientations(), List.of(BRASS), BRASS, definition.defaultMaterial(),
                definition.assets(), definition.placementProfile());
        return RegistrySnapshotTestFactory.replaceBanner(source, replacement);
    }

    private static Set<String> suggestions(
            CommandDispatcher<CommandSourceStack> dispatcher, String input, CommandSourceStack source)
            throws Exception {
        ParseResults<CommandSourceStack> parsed = dispatcher.parse(withoutSlash(input), source);
        return dispatcher.getCompletionSuggestions(parsed).get().getList().stream()
                .map(suggestion -> suggestion.getText())
                .collect(java.util.stream.Collectors.toSet());
    }

    private static CommandNode<CommandSourceStack> path(
            CommandDispatcher<CommandSourceStack> dispatcher, String... names) {
        CommandNode<CommandSourceStack> node = dispatcher.getRoot();
        for (String name : names) {
            node = node.getChild(name);
            assertNotNull(node, String.join(" ", names));
        }
        return node;
    }

    private static CommandNode<CommandSourceStack> onlyChild(
            CommandNode<CommandSourceStack> parent, String expectedName) {
        assertEquals(1, parent.getChildren().size(), parent.getName());
        CommandNode<CommandSourceStack> child = parent.getChild(expectedName);
        assertNotNull(child, expectedName);
        return child;
    }

    private static CommandSourceStack source(int permission) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, permission,
                "test", Component.literal("test"), null, null);
    }

    private static String withoutSlash(String command) {
        return command.startsWith("/") ? command.substring(1) : command;
    }

    private record ValidCase(
            String command, FabricMaterialId material, ResolvedColourId colour, MountId mount) {
    }

    private static final class RecordingRecipient implements AdminItemRecipient {
        private final java.util.ArrayList<ItemStack> delivered = new java.util.ArrayList<>();

        @Override
        public String name() {
            return "test";
        }

        @Override
        public boolean insert(ItemStack stack) {
            delivered.add(stack.copy());
            stack.setCount(0);
            return true;
        }

        @Override
        public boolean dropRemainder(ItemStack stack) {
            return false;
        }

        List<ItemStack> delivered() {
            return List.copyOf(delivered);
        }
    }
}
