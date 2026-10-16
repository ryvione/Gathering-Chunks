/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.server;

public final class DevMode {
    private DevMode() {
    }

    private static volatile boolean enabled = false;

    public static boolean isEnabled() {
        return enabled || com.ryvione.gatheringchunks.config.ChunkByChunkConfig.global().getDebug().isVerboseLogging();
    }

    public static boolean toggle() {
        enabled = !enabled;
        return enabled;
    }

    public static void set(boolean value) {
        enabled = value;
    }
}
