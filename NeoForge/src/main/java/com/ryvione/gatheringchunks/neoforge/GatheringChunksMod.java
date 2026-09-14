package com.ryvione.gatheringchunks.neoforge;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.network.C2SSaveConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2COpenConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import com.ryvione.gatheringchunks.config.system.ConfigSystem;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.function.Supplier;

@Mod(GatheringChunksConstants.MOD_ID)
public class GatheringChunksMod {

    private static final Logger LOGGER = LogManager.getLogger(GatheringChunksConstants.MOD_ID);

    public static final DeferredRegister<MapCodec<? extends ChunkGenerator>> CHUNK_GENERATORS =
            DeferredRegister.create(BuiltInRegistries.CHUNK_GENERATOR, GatheringChunksConstants.MOD_ID);

    public static final Supplier<MapCodec<? extends ChunkGenerator>> SKY_CHUNK_CODEC =
            CHUNK_GENERATORS.register("sky_chunk_generator",
                    () -> SkyChunkGenerator.CODEC);

    public GatheringChunksMod(IEventBus modEventBus) {
        LOGGER.info("Gathering Chunks (NeoForge) initializing...");

        ConfigSystem.initCentralConfigDir(FMLPaths.GAMEDIR.get());
        com.ryvione.gatheringchunks.common.util.ConfigUtil.startConfigWatcher();
        LOGGER.info("[GatheringChunksMod] Centralized config directory initialized");

        CHUNK_GENERATORS.register(modEventBus);

        ModRegistry.BLOCKS.register(modEventBus);
        ModRegistry.ITEMS.register(modEventBus);
        ModRegistry.BLOCK_ENTITIES.register(modEventBus);
        ModRegistry.MENU_TYPES.register(modEventBus);
        ModRegistry.SOUND_EVENTS.register(modEventBus);
        ModRegistry.CREATIVE_TABS.register(modEventBus);

        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::registerPayloads);

        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            modEventBus.addListener(ClientModEvents::onClientSetup);
            modEventBus.addListener(ClientModEvents::registerScreens);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientGameEvents::onClientJoin);
            net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(ClientGameEvents::onClientTick);
        }
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        LOGGER.info("Common setup complete");
    }

    private void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(GatheringChunksConstants.MOD_ID).versioned("1.0");
        
        if (net.neoforged.fml.loading.FMLEnvironment.dist.isClient()) {
            ClientPayloadRegistration.register(registrar);
        } else {
            registrar.playToClient(S2COpenConfigPacket.TYPE, S2COpenConfigPacket.CODEC, (p, c) -> {});
            registrar.playToClient(S2CSyncConfigPacket.TYPE, S2CSyncConfigPacket.CODEC, (p, c) -> {});
        }

        registrar.playToServer(
                C2SSaveConfigPacket.TYPE,
                C2SSaveConfigPacket.CODEC,
                ServerPacketHandler::handleSaveConfig);
        LOGGER.info("[GatheringChunksMod] Registered network payloads");
    }

    public static class ClientModEvents {
        public static void onClientSetup(FMLClientSetupEvent event) {
            com.ryvione.gatheringchunks.client.ClientConfigStorage.init(FMLPaths.GAMEDIR.get());
            com.ryvione.gatheringchunks.client.DevSnapshotWarning.init(FMLPaths.GAMEDIR.get());
            LOGGER.info("[GatheringChunksMod] ClientConfigStorage initialized");
        }

        public static void registerScreens(RegisterMenuScreensEvent event) {
            LOGGER.info("Client Initializing");
            event.register(ModRegistry.BEDROCK_CHEST_MENU.get(), com.ryvione.gatheringchunks.client.screens.BedrockChestScreen::new);
            event.register(ModRegistry.WORLD_FORGE_MENU.get(), com.ryvione.gatheringchunks.client.screens.WorldForgeScreen::new);
            event.register(ModRegistry.WORLD_SCANNER_MENU.get(), com.ryvione.gatheringchunks.client.screens.WorldScannerScreen::new);
            event.register(ModRegistry.WORLD_MENDER_MENU.get(), com.ryvione.gatheringchunks.client.screens.WorldMenderScreen::new);
            event.register(ModRegistry.CHUNK_ENGINE_MENU.get(), com.ryvione.gatheringchunks.client.screens.ChunkEngineScreen::new);
            event.register(ModRegistry.CAVE_SCANNER_MENU.get(), com.ryvione.gatheringchunks.client.screens.CaveScannerScreen::new);
        }
    }

    public static class ClientGameEvents {
        private static boolean devWarningShown = false;
        public static void onClientJoin(ClientPlayerNetworkEvent.LoggingIn event) {
            String address = "singleplayer";
            try {
                net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
                if (mc.getCurrentServer() != null) {
                    address = mc.getCurrentServer().ip;
                }
            } catch (Exception ignored) {}
            com.ryvione.gatheringchunks.client.ClientConfigStorage.setCurrentServer(com.ryvione.gatheringchunks.client.ClientConfigStorage.getServerIdFromConnection(address));
            LOGGER.info("[GatheringChunksMod] Joined server, set config scope: {}", address);
        }

        public static void onClientTick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
            if (devWarningShown) {
                return;
            }
            if (!com.ryvione.gatheringchunks.client.DevSnapshotWarning.shouldShow()) {
                return;
            }
            net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                devWarningShown = true;
                mc.setScreen(new com.ryvione.gatheringchunks.client.screens.DevSnapshotWarningScreen());
            }
        }
    }
}