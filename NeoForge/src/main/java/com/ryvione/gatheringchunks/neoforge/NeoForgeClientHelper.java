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

import com.ryvione.gatheringchunks.client.screens.StarterBookScreen;
import com.ryvione.gatheringchunks.common.network.C2SSaveConfigPacket;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

public class NeoForgeClientHelper {
    public static void openStarterBook() {
        Minecraft.getInstance().setScreen(new StarterBookScreen());
    }

    public static void sendConfigSavePacket(C2SSaveConfigPacket packet) {
        ClientPacketDistributor.sendToServer(packet);
    }
}
