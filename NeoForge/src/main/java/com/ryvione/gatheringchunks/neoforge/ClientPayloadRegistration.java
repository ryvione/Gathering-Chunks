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

import com.ryvione.gatheringchunks.common.network.S2COpenConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class ClientPayloadRegistration {
    public static void register(PayloadRegistrar registrar) {
        registrar.playToClient(
                S2COpenConfigPacket.TYPE,
                S2COpenConfigPacket.CODEC,
                ClientPacketHandler::handleOpenConfig);
        registrar.playToClient(
                S2CSyncConfigPacket.TYPE,
                S2CSyncConfigPacket.CODEC,
                ClientPacketHandler::handleSyncConfig);
    }
}
