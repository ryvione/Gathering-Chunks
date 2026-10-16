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
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.WorldConfigStore;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;

import java.nio.file.Path;
import java.util.Map;
import java.util.WeakHashMap;

public final class CreateWorldSettings {
    private static final Map<CreateWorldScreen, ChunkByChunkConfig> DRAFTS = new WeakHashMap<>();
    private static ChunkByChunkConfig pendingNewWorld = null;

    private CreateWorldSettings() {
    }

    public static ChunkByChunkConfig draftFor(CreateWorldScreen screen) {
        return DRAFTS.computeIfAbsent(screen, s -> WorldConfigStore.copyOfGlobalWorldSettings());
    }

    public static void beginNewWorld(CreateWorldScreen screen) {
        ChunkByChunkConfig draft = DRAFTS.get(screen);
        pendingNewWorld = draft != null ? draft : WorldConfigStore.copyOfGlobalWorldSettings();
    }

    public static void saveToNewWorld(Path worldDir) {
        if (pendingNewWorld == null) {
            return;
        }
        try {
            WorldConfigStore.save(worldDir, pendingNewWorld);
        } catch (Exception e) {
            GatheringChunksConstants.LOGGER.error("[CreateWorldSettings] Could not save GC settings to {}", worldDir, e);
        } finally {
            pendingNewWorld = null;
        }
    }

    public static void cancelNewWorld() {
        pendingNewWorld = null;
    }
}
