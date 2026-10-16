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

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ChunkBarrierManager {

    private static final Logger LOGGER = LogManager.getLogger(ChunkBarrierManager.class);

    private static final boolean TEMP_DISABLED = true;

    private static final BlockState BARRIER = Blocks.BARRIER.defaultBlockState();

    public static void placeBarriersAroundChunk(ServerLevel level, ChunkPos chunkPos) {
        if (TEMP_DISABLED) return;

        /*
        if (!ChunkByChunkConfig.get().getGameplayConfig().isEnableChunkBarriers()) {
            return;
        }

        int minY = level.getMinY();
        int maxY = level.getMaxY() + 1;

        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();

        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                placeBarrier(level, new BlockPos(x, y, minZ));
                placeBarrier(level, new BlockPos(x, y, maxZ));
            }

            for (int z = minZ + 1; z < maxZ; z++) {
                placeBarrier(level, new BlockPos(minX, y, z));
                placeBarrier(level, new BlockPos(maxX, y, z));
            }
        }
        */
    }

    public static void removeBarriersAroundChunk(ServerLevel level, ChunkPos chunkPos) {
        if (TEMP_DISABLED) return;

        /*
        if (!ChunkByChunkConfig.get().getGameplayConfig().isEnableChunkBarriers()) {
            return;
        }

        int minY = level.getMinY();
        int maxY = level.getMaxY() + 1;

        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();

        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                removeBarrier(level, new BlockPos(x, y, minZ));
                removeBarrier(level, new BlockPos(x, y, maxZ));
            }

            for (int z = minZ + 1; z < maxZ; z++) {
                removeBarrier(level, new BlockPos(minX, y, z));
                removeBarrier(level, new BlockPos(maxX, y, z));
            }
        }
        */
    }

    public static void removeBarriersBetweenChunks(ServerLevel level, ChunkPos chunk1, ChunkPos chunk2) {
        if (TEMP_DISABLED) return;

        /*
        if (!ChunkByChunkConfig.get().getGameplayConfig().isEnableChunkBarriers()) {
            return;
        }

        int minY = level.getMinY();
        int maxY = level.getMaxY() + 1;

        if (chunk1.x == chunk2.x) {
            int x = chunk1.getMinBlockX();
            int sharedZ = chunk1.z < chunk2.z ? chunk1.getMaxBlockZ() : chunk1.getMinBlockZ();

            for (int y = minY; y < maxY; y++) {
                for (int dx = 0; dx < 16; dx++) {
                    removeBarrier(level, new BlockPos(x + dx, y, sharedZ));
                }
            }
        } else if (chunk1.z == chunk2.z) {
            int z = chunk1.getMinBlockZ();
            int sharedX = chunk1.x < chunk2.x ? chunk1.getMaxBlockX() : chunk1.getMinBlockX();

            for (int y = minY; y < maxY; y++) {
                for (int dz = 0; dz < 16; dz++) {
                    removeBarrier(level, new BlockPos(sharedX, y, z + dz));
                }
            }
        }
        */
    }

    public static void updateBarriersAfterChunkSpawn(ServerLevel level, ChunkPos existingChunk, ChunkPos newChunk) {
        if (TEMP_DISABLED) return;

        /*
        removeBarriersBetweenChunks(level, existingChunk, newChunk);
        placeBarriersAroundChunk(level, newChunk);

        ChunkPos[] adjacentPositions = {
                new ChunkPos(newChunk.x + 1, newChunk.z),
                new ChunkPos(newChunk.x - 1, newChunk.z),
                new ChunkPos(newChunk.x, newChunk.z + 1),
                new ChunkPos(newChunk.x, newChunk.z - 1)
        };

        for (ChunkPos adjacent : adjacentPositions) {
            if (!adjacent.equals(existingChunk) && isChunkSpawned(level, adjacent)) {
                removeBarriersBetweenChunks(level, newChunk, adjacent);
            }
        }
        */
    }

    // --- Original helpers (currently unused) ---

    private static void placeBarrier(ServerLevel level, BlockPos pos) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.isAir()) {
            level.setBlock(pos, BARRIER, 3);
        }
    }

    private static void removeBarrier(ServerLevel level, BlockPos pos) {
        BlockState currentState = level.getBlockState(pos);
        if (currentState.is(Blocks.BARRIER)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
        }
    }

    private static boolean isChunkSpawned(ServerLevel level, ChunkPos chunkPos) {
        BlockPos bedrockCheckBlock = chunkPos.getMiddleBlockPosition(level.getMinY());
        return Blocks.BEDROCK.equals(level.getBlockState(bedrockCheckBlock).getBlock());
    }
}