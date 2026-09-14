/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.client;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class DevSnapshotWarning {
    private static final Logger LOGGER = LogManager.getLogger(GatheringChunksConstants.MOD_ID);
    private static final String MARKER_RESOURCE = "/META-INF/" + GatheringChunksConstants.MOD_ID + "/dev_snapshot";
    private static final String FLAG_FILE = "config" + File.separator + "GatheringChunks" + File.separator + "dev_snapshot_hide.flag";

    private static boolean detected = false;
    private static boolean initialized = false;
    private static Path flagPath = null;

    private DevSnapshotWarning() {
    }

    public static void init(Path gameDir) {
        if (initialized) {
            return;
        }
        initialized = true;
        try {
            detected = DevSnapshotWarning.class.getResource(MARKER_RESOURCE) != null;
        } catch (Exception e) {
            detected = false;
        }
        if (gameDir != null) {
            flagPath = gameDir.resolve(FLAG_FILE);
        }
        if (detected) {
            LOGGER.info("[GatheringChunks] Development Snapshot build detected");
        }
    }

    public static boolean isDevSnapshot() {
        return detected;
    }

    public static boolean shouldShow() {
        if (!initialized) {
            init(null);
        }
        if (!detected) {
            return false;
        }
        return flagPath == null || !Files.exists(flagPath);
    }

    public static void setDoNotShowAgain() {
        if (flagPath == null) {
            return;
        }
        try {
            Files.createDirectories(flagPath.getParent());
            Files.writeString(flagPath, "The user chose to hide the Development Snapshot warning.\n");
        } catch (IOException e) {
            LOGGER.warn("[GatheringChunks] Failed to save dev snapshot 'do not show again' flag", e);
        }
    }
}
