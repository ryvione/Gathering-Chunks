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

import com.ryvione.gatheringchunks.client.screens.StarterBookScreen;
import com.ryvione.gatheringchunks.common.network.C2SSaveConfigPacket;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;

public class FabricClientHelper {
    public static void openStarterBook() {
        Minecraft.getInstance().setScreen(new StarterBookScreen());
    }

    public static void sendConfigSavePacket(C2SSaveConfigPacket packet) {
        ClientPlayNetworking.send(packet);
    }
}
