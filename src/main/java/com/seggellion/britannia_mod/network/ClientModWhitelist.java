package com.seggellion.britannia_mod.network;

import com.mojang.logging.LogUtils;
import com.seggellion.britannia_mod.BritanniaMod;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ConfigurationTask;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.network.configuration.ICustomConfigurationTask;
import net.neoforged.neoforge.network.event.RegisterConfigurationTasksEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforgespi.language.IModInfo;
import org.slf4j.Logger;

public final class ClientModWhitelist {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_REPORTED_ITEMS = 512;

    private static final Set<String> ALLOWED_USER_MODS = Set.of(
        "distanthorizons",
        "iris",
        "sodium",
        "worldedit",
        "freecam",
        "cloth_config"
    );

    private static final Set<String> ALLOWED_SYSTEM_MODS = Set.of(
        "minecraft",
        "neoforge",
        "javafml",
        "lowcodefml",
        "geckolib",
        BritanniaMod.MODID
    );

    private static final Set<String> ALLOWED_MODS = java.util.stream.Stream
        .concat(ALLOWED_USER_MODS.stream(), ALLOWED_SYSTEM_MODS.stream())
        .collect(Collectors.toUnmodifiableSet());

    // Expand this list to include any external resource packs you want to explicitly allow
    private static final Set<String> ALLOWED_RESOURCE_PACKS = Set.of(
        "vanilla",
        "mod_resources",
        "server" // Standard ID for your enforced server resource packs
    );

    private static final ResourceLocation TASK_ID = ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "client_mod_audit");
    private static final ConfigurationTask.Type TASK_TYPE = new ConfigurationTask.Type(TASK_ID);

    private ClientModWhitelist() {
    }

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");

        registrar.configurationToClient(
            ClientModAuditRequestPayload.TYPE,
            ClientModAuditRequestPayload.STREAM_CODEC,
            ClientModWhitelist::handleAuditRequestOnClient
        );

        registrar.configurationToServer(
            ClientModAuditResponsePayload.TYPE,
            ClientModAuditResponsePayload.STREAM_CODEC,
            ClientModWhitelist::handleAuditResponseOnServer
        );
    }

    public static void registerConfigurationTasks(RegisterConfigurationTasksEvent event) {
        event.register(new ClientModAuditTask());
    }

    private static void handleAuditRequestOnClient(ClientModAuditRequestPayload payload, IPayloadContext context) {
        context.reply(new ClientModAuditResponsePayload(collectClientMods(), collectClientPacks()));
    }

    private static void handleAuditResponseOnServer(ClientModAuditResponsePayload payload, IPayloadContext context) {
        // --- Process Mods ---
        List<ReportedMod> reportedMods = payload.mods().stream()
            .map(ReportedMod::normalized)
            .filter(mod -> !mod.id().isBlank())
            .distinct()
            .sorted(Comparator.comparing(ReportedMod::id))
            .toList();

        List<String> blockedMods = reportedMods.stream()
            .map(ReportedMod::id)
            .filter(id -> !ALLOWED_MODS.contains(id))
            .filter(id -> !id.startsWith("fabric_")) 
            .distinct()
            .sorted()
            .toList();

        // --- Process Resource Packs ---
        List<String> reportedPacks = payload.resourcePacks().stream()
            .filter(id -> !id.isBlank())
            .distinct()
            .sorted()
            .toList();

        List<String> blockedPacks = reportedPacks.stream()
            .filter(id -> !ALLOWED_RESOURCE_PACKS.contains(id))
            .filter(id -> !id.startsWith("file/Ultimacraft")) // Example of permitting a specific prefix
            .toList();

        LOGGER.info("Client reported {} mods and {} resource packs during connection audit.", reportedMods.size(), reportedPacks.size());

        // --- Evaluate and Disconnect ---
        if (!blockedMods.isEmpty() || !blockedPacks.isEmpty()) {
            List<String> reasons = new ArrayList<>();
            if (!blockedMods.isEmpty()) {
                reasons.add("Mods: " + String.join(", ", blockedMods));
            }
            if (!blockedPacks.isEmpty()) {
                reasons.add("Packs: " + String.join(", ", blockedPacks));
            }

            String blockReason = String.join(" | ", reasons);
            LOGGER.warn("Connection blocked for unsupported client features: {}", blockReason);
            context.disconnect(Component.literal("Connection blocked. Remove unsupported items. " + blockReason));
            return;
        }

        context.finishCurrentTask(TASK_TYPE);
    }

    private static List<ReportedMod> collectClientMods() {
        TreeMap<String, String> mods = new TreeMap<>();
        for (IModInfo modInfo : ModList.get().getMods()) {
            String id = normalizeModId(modInfo.getModId());
            if (id.isBlank()) {
                continue;
            }
            String version = modInfo.getVersion() == null ? "" : modInfo.getVersion().toString();
            mods.putIfAbsent(id, version == null ? "" : version);
        }

        return mods.entrySet().stream()
            .map(entry -> new ReportedMod(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static List<String> collectClientPacks() {
        // Prevent NoClassDefFoundError if the dedicated server accidentally checks this flow
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            return ClientAccess.getResourcePacks();
        }
        return List.of();
    }

    private static String normalizeModId(String modId) {
        return modId == null ? "" : modId.trim().toLowerCase(Locale.ROOT);
    }

    // Isolate client-only code completely from the server classpath execution
    private static class ClientAccess {
        @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
        static List<String> getResourcePacks() {
            return new ArrayList<>(net.minecraft.client.Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
        }
    }

    public record ClientModAuditTask() implements ICustomConfigurationTask {
        @Override
        public void run(Consumer<CustomPacketPayload> sender) {
            sender.accept(ClientModAuditRequestPayload.INSTANCE);
        }

        @Override
        public ConfigurationTask.Type type() {
            return TASK_TYPE;
        }
    }

    public record ClientModAuditRequestPayload() implements CustomPacketPayload {
        public static final ClientModAuditRequestPayload INSTANCE = new ClientModAuditRequestPayload();
        public static final CustomPacketPayload.Type<ClientModAuditRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "client_mod_audit_request"));
        public static final StreamCodec<FriendlyByteBuf, ClientModAuditRequestPayload> STREAM_CODEC = StreamCodec.unit(INSTANCE);

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ClientModAuditResponsePayload(List<ReportedMod> mods, List<String> resourcePacks) implements CustomPacketPayload {
        private static final StreamCodec<FriendlyByteBuf, List<ReportedMod>> MODS_STREAM_CODEC =
            ByteBufCodecs.collection(ArrayList::new, ReportedMod.STREAM_CODEC, MAX_REPORTED_ITEMS);
        private static final StreamCodec<FriendlyByteBuf, List<String>> PACKS_STREAM_CODEC =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8, MAX_REPORTED_ITEMS);

        public static final CustomPacketPayload.Type<ClientModAuditResponsePayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(BritanniaMod.MODID, "client_mod_audit_response"));
        
        // Combine both collections into a single composite codec
        public static final StreamCodec<FriendlyByteBuf, ClientModAuditResponsePayload> STREAM_CODEC = StreamCodec.composite(
            MODS_STREAM_CODEC,
            ClientModAuditResponsePayload::mods,
            PACKS_STREAM_CODEC,
            ClientModAuditResponsePayload::resourcePacks,
            ClientModAuditResponsePayload::new
        );

        public ClientModAuditResponsePayload {
            mods = List.copyOf(mods);
            resourcePacks = List.copyOf(resourcePacks);
        }

        @Override
        public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ReportedMod(String id, String version) {
        private static final StreamCodec<FriendlyByteBuf, ReportedMod> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8,
            ReportedMod::id,
            ByteBufCodecs.STRING_UTF8,
            ReportedMod::version,
            ReportedMod::new
        );

        public ReportedMod {
            id = normalizeModId(id);
            version = version == null ? "" : version.trim();
        }

        private ReportedMod normalized() {
            return new ReportedMod(id, version);
        }
    }
}