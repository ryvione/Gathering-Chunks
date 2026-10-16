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

import com.ryvione.gatheringchunks.config.system.ConfigSystem;

import java.io.BufferedReader;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;

public final class WorldConfigStore {
    public static final String FILE_NAME = "gatheringchunks-world.toml";
    private static final ConfigSystem SYSTEM = new ConfigSystem();

    private WorldConfigStore() {
    }

    public static ChunkByChunkConfig copyOfGlobalWorldSettings() {
        StringWriter text = new StringWriter();
        SYSTEM.write(text, ChunkByChunkConfig.global());
        ChunkByChunkConfig copy = new ChunkByChunkConfig();
        SYSTEM.readInto(new BufferedReader(new StringReader(text.toString())), copy);
        return copy;
    }

    public static void save(Path worldDir, ChunkByChunkConfig worldSettings) {
        SYSTEM.write(worldDir.resolve(FILE_NAME), worldSettings);
    }

    public static ChunkByChunkConfig load(Path worldDir) {
        Path file = worldDir.resolve(FILE_NAME);
        if (!Files.exists(file)) {
            return null;
        }
        ChunkByChunkConfig loaded = new ChunkByChunkConfig();
        SYSTEM.reloadConfig(file, loaded);
        return loaded;
    }
}
