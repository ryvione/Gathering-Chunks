/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.neoforge;

import com.ryvione.gatheringchunks.client.ClientConfigStorage;
import com.ryvione.gatheringchunks.client.screens.GatheringChunksConfigScreen;
import com.ryvione.gatheringchunks.common.network.S2COpenConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ClientPacketHandler {

    private static final Logger LOGGER = LogManager.getLogger("GatheringChunks");

    public static void handleOpenConfig(final S2COpenConfigPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.info("[ClientPacketHandler] Opening config screen");
            Minecraft.getInstance().setScreen(GatheringChunksConfigScreen.forLoadedWorld(null));
        });
    }

    public static void handleSyncConfig(final S2CSyncConfigPacket payload, final IPayloadContext context) {
        context.enqueueWork(() -> {
            LOGGER.info("[ClientPacketHandler] Received config sync from server");
            ClientConfigStorage.handleServerConfigSync(payload.configJson());
        });
    }
}