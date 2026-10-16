/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.server.world.spawning;

import com.ryvione.gatheringchunks.util.SavedDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashSet;
import java.util.Set;

public class ChunkResourceTracker extends SavedData {
    private final Set<String> knownSourceChunks = new HashSet<>();
    private final Set<String> harvestedPositions = new HashSet<>();

    public static ChunkResourceTracker get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new ChunkResourceTracker();
        }
        return overworld.getChunkSource().getDataStorage().computeIfAbsent(SavedDataCompat.of(
                "gatheringchunks_resource_tracker",
                ChunkResourceTracker::new,
                ChunkResourceTracker::load,
                tracker -> tracker.save(new CompoundTag())
        ));
    }

    private static ChunkResourceTracker load(CompoundTag tag) {
        ChunkResourceTracker tracker = new ChunkResourceTracker();
        ListTag knownChunks = tag.getListOrEmpty("KnownSourceChunks");
        for (int i = 0; i < knownChunks.size(); i++) {
            tracker.knownSourceChunks.add(knownChunks.getStringOr(i, ""));
        }
        ListTag harvested = tag.getListOrEmpty("HarvestedPositions");
        for (int i = 0; i < harvested.size(); i++) {
            tracker.harvestedPositions.add(harvested.getStringOr(i, ""));
        }
        return tracker;
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag knownChunks = new ListTag();
        for (String key : knownSourceChunks) {
            knownChunks.add(StringTag.valueOf(key));
        }
        tag.put("KnownSourceChunks", knownChunks);
        ListTag harvested = new ListTag();
        for (String key : harvestedPositions) {
            harvested.add(StringTag.valueOf(key));
        }
        tag.put("HarvestedPositions", harvested);
        return tag;
    }

    private static String chunkKey(ServerLevel sourceLevel, ChunkPos sourceChunkPos) {
        return sourceLevel.dimension().identifier() + "@" + sourceChunkPos.toLong();
    }

    private static String posKey(ServerLevel sourceLevel, BlockPos pos) {
        return sourceLevel.dimension().identifier() + "@" + pos.asLong();
    }

    public boolean isChunkKnown(ServerLevel sourceLevel, ChunkPos sourceChunkPos) {
        return knownSourceChunks.contains(chunkKey(sourceLevel, sourceChunkPos));
    }

    public void markChunkKnown(ServerLevel sourceLevel, ChunkPos sourceChunkPos) {
        if (knownSourceChunks.add(chunkKey(sourceLevel, sourceChunkPos))) {
            setDirty();
        }
    }

    public boolean isPositionHarvested(ServerLevel sourceLevel, BlockPos pos) {
        return harvestedPositions.contains(posKey(sourceLevel, pos));
    }

    public void markPositionHarvested(ServerLevel sourceLevel, BlockPos pos) {
        if (harvestedPositions.add(posKey(sourceLevel, pos))) {
            setDirty();
        }
    }
}
