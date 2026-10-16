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

import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

public class FluidFlowPrevention {

    public static void register() {
        ServerTickEvents.END_WORLD_TICK.register(world -> {
            if (!com.ryvione.gatheringchunks.config.ChunkByChunkConfig.get()
                    .getGatheringChunksConfig().isPreventFluidFlowIntoVoid()) {
                return;
            }

        });
    }

    public static boolean shouldCancelFluidFlow(ServerLevel level, BlockPos pos) {
        if (level.isClientSide()) {
            return false;
        }

        ChunkPos currentChunk = new ChunkPos(pos);

        if (SpawnChunkHelper.isEmptyChunk(level, currentChunk)) {
            return true;
        }

        BlockPos[] adjacentPositions = {
                pos.north(), pos.south(), pos.east(), pos.west(), pos.below()
        };

        for (BlockPos adjacentPos : adjacentPositions) {
            ChunkPos adjacentChunk = new ChunkPos(adjacentPos);

            if (!currentChunk.equals(adjacentChunk)) {
                if (SpawnChunkHelper.isEmptyChunk(level, adjacentChunk)) {
                    BlockState adjacentState = level.getBlockState(adjacentPos);
                    if (adjacentState.isAir() || adjacentState.getBlock() == Blocks.CAVE_AIR) {
                        return true;
                    }
                }
            }
        }

        return false;
    }
}