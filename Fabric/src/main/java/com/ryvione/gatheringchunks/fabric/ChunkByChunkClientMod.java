/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.fabric;

import com.ryvione.gatheringchunks.client.ClientConfigStorage;
import com.ryvione.gatheringchunks.client.DevSnapshotWarning;
import com.ryvione.gatheringchunks.client.screens.*;
import com.ryvione.gatheringchunks.common.CommonRegistry;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.network.S2COpenConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import com.ryvione.gatheringchunks.common.update.UpdateChecker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.MenuScreens;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkByChunkClientMod implements ClientModInitializer {
    public static final Logger LOGGER = LogManager.getLogger(GatheringChunksConstants.MOD_ID);

    private static boolean devWarningShown = false;

    @Override
    public void onInitializeClient() {
        LOGGER.info("Client Initializing");

        ClientConfigStorage.init(FabricLoader.getInstance().getGameDir());
        DevSnapshotWarning.init(FabricLoader.getInstance().getGameDir());

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (devWarningShown || !DevSnapshotWarning.shouldShow()) {
                return;
            }
            if (client.screen instanceof net.minecraft.client.gui.screens.TitleScreen) {
                devWarningShown = true;
                client.setScreen(new DevSnapshotWarningScreen());
            }
        });

        MenuScreens.register(CommonRegistry.BEDROCK_CHEST_MENU, BedrockChestScreen::new);
        MenuScreens.register(CommonRegistry.WORLD_FORGE_MENU, WorldForgeScreen::new);
        MenuScreens.register(CommonRegistry.WORLD_SCANNER_MENU, WorldScannerScreen::new);
        MenuScreens.register(CommonRegistry.WORLD_MENDER_MENU, WorldMenderScreen::new);
        MenuScreens.register(CommonRegistry.CHUNK_ENGINE_MENU, ChunkEngineScreen::new);
        MenuScreens.register(CommonRegistry.CAVE_SCANNER_MENU, CaveScannerScreen::new);

        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            String serverId = handler.getConnection().getRemoteAddress() != null
                    ? handler.getConnection().getRemoteAddress().toString()
                    : "singleplayer";
            ClientConfigStorage.setCurrentServer(ClientConfigStorage.getServerIdFromConnection(serverId));
            LOGGER.info("[ClientMod] Joined server, set config scope: {}", serverId);
        });

        ClientPlayNetworking.registerGlobalReceiver(S2COpenConfigPacket.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    LOGGER.info("[ClientMod] Opening config screen from server packet");
                    Minecraft.getInstance().setScreen(new GatheringChunksConfigScreen(null));
                }));

        ClientPlayNetworking.registerGlobalReceiver(S2CSyncConfigPacket.TYPE,
                (payload, context) -> context.client().execute(() -> {
                    LOGGER.info("[ClientMod] Received config sync from server");
                    ClientConfigStorage.handleServerConfigSync(payload.configJson());
                }));

        LOGGER.info("Checking for mod updates...");
        UpdateChecker.checkForUpdates();
    }
}