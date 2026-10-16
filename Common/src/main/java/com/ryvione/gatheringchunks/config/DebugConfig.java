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
import com.ryvione.gatheringchunks.config.system.Name;

public class DebugConfig {

    @Comment("Log extra detail about spawning and chunk copying. The same as /gatheringchunks devmode on, "
            + "but kept across restarts")
    @Name("Verbose Logging")
    private boolean verboseLogging = false;

    @Comment("Log how long slow chunk copies and structure searches take (the [Perf] lines)")
    @Name("Performance Timings")
    private boolean performanceTimings = true;

    public boolean isVerboseLogging() {
        return verboseLogging;
    }

    public void setVerboseLogging(boolean verboseLogging) {
        this.verboseLogging = verboseLogging;
    }

    public boolean isPerformanceTimings() {
        return performanceTimings;
    }

    public void setPerformanceTimings(boolean performanceTimings) {
        this.performanceTimings = performanceTimings;
    }
}
