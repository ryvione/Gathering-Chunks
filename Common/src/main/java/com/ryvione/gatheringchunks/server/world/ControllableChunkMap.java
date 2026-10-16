/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.server.world;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.world.level.ChunkPos;
public interface ControllableChunkMap {
    void forceReloadChunk(ChunkPos chunk);

    Iterable<ChunkHolder> getVisibleChunkHolders();
}