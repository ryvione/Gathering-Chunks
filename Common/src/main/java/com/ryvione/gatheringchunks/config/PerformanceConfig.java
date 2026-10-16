/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.config;

import com.ryvione.gatheringchunks.config.system.Comment;
import com.ryvione.gatheringchunks.config.system.IntRange;
import com.ryvione.gatheringchunks.config.system.Name;

public class PerformanceConfig {

    @Comment("Longest time, in seconds, the search for the first spawn chunk may run before it gives up and "
            + "uses the fallback spawn")
    @Name("Spawn Search Time Limit (s)")
    @IntRange(min = 10, max = 600)
    private int spawnSearchTimeLimitSeconds = 60;

    @Comment("Most chunks the search for the first spawn chunk will check")
    @Name("Spawn Search Max Chunks")
    @IntRange(min = 50, max = 5000)
    private int spawnSearchMaxChunks = 500;

    @Comment("How many candidate chunks the first spawn search starts loading ahead of the one it is checking, so "
            + "they generate in parallel. -1 picks a value from the number of processor threads (none below 6). "
            + "0 turns it off. Each extra chunk is wasted work if the search finds its spawn earlier.")
    @Name("Spawn Search Prefetch")
    @IntRange(min = -1, max = 4)
    private int spawnSearchPrefetch = -1;

    public int getSpawnSearchPrefetch() {
        return spawnSearchPrefetch;
    }

    public void setSpawnSearchPrefetch(int prefetch) {
        this.spawnSearchPrefetch = Math.max(-1, Math.min(4, prefetch));
    }

    public int effectiveSpawnSearchPrefetch() {
        if (spawnSearchPrefetch >= 0) {
            return spawnSearchPrefetch;
        }
        int threads = Runtime.getRuntime().availableProcessors();
        if (threads >= 16) return 3;
        if (threads >= 10) return 2;
        if (threads >= 6) return 1;
        return 0;
    }

    public int getSpawnSearchTimeLimitSeconds() {
        return spawnSearchTimeLimitSeconds;
    }

    public void setSpawnSearchTimeLimitSeconds(int seconds) {
        this.spawnSearchTimeLimitSeconds = Math.max(10, Math.min(600, seconds));
    }

    public int getSpawnSearchMaxChunks() {
        return spawnSearchMaxChunks;
    }

    public void setSpawnSearchMaxChunks(int chunks) {
        this.spawnSearchMaxChunks = Math.max(50, Math.min(5000, chunks));
    }
}
