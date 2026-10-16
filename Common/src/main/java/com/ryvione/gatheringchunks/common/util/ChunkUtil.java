/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.common.util;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Random;
import java.util.Set;

public final class ChunkUtil {
    private ChunkUtil() {
    }

    public static Random getChunkRandom(ServerLevel targetLevel, ChunkPos chunkPos) {
        long seed = targetLevel.getSeed() + GatheringChunksConstants.MOD_ID.hashCode();
        Random random = new Random(seed);
        long xModifier = random.nextLong();
        long yModifier = random.nextLong();
        long chunkSeed = (long) chunkPos.x & xModifier ^ (long) chunkPos.z * yModifier ^ seed;
        random.setSeed(chunkSeed);
        return random;
    }

    public static int getSafeSpawnHeight(ChunkAccess chunk, int x, int z) {
        int localX = Math.max(0, Math.min(15, x - chunk.getPos().getMinBlockX()));
        int localZ = Math.max(0, Math.min(15, z - chunk.getPos().getMinBlockZ()));

        int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localX, localZ);

        if (surfaceY <= chunk.getMinY()) {
            surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, localX, localZ);
        }
        if (surfaceY <= chunk.getMinY()) {
            return chunk.getMinY() + 1;
        }

        return surfaceY;
    }

    public static int countBlocks(ChunkAccess chunk, Set<Block> blocks) {
        if (blocks.size() == 0) {
            return 0;
        }
        if (blocks.size() == 1) {
            return countBlocks(chunk, blocks.stream().findFirst().get());
        }
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);
        int count = 0;
        for (pos.setX(chunkPos.getMinBlockX()); pos.getX() <= chunkPos.getMaxBlockX(); pos.setX(pos.getX() + 1)) {
            for (pos.setY(chunk.getMinY()); pos.getY() <= chunk.getMaxY(); pos.setY(pos.getY() + 1)) {
                for (pos.setZ(chunkPos.getMinBlockZ()); pos.getZ() <= chunkPos.getMaxBlockZ(); pos.setZ(pos.getZ() + 1)) {
                    if (blocks.contains(chunk.getBlockState(pos).getBlock())) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static int countBlocks(ChunkAccess chunk, Block block) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);
        int count = 0;
        for (pos.setX(chunkPos.getMinBlockX()); pos.getX() <= chunkPos.getMaxBlockX(); pos.setX(pos.getX() + 1)) {
            for (pos.setY(chunk.getMinY()); pos.getY() <= chunk.getMaxY(); pos.setY(pos.getY() + 1)) {
                for (pos.setZ(chunkPos.getMinBlockZ()); pos.getZ() <= chunkPos.getMaxBlockZ(); pos.setZ(pos.getZ() + 1)) {
                    if (chunk.getBlockState(pos).getBlock() == block) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    public static int countBlocks(ChunkAccess chunk, TagKey<Block> blockTag) {
        ChunkPos chunkPos = chunk.getPos();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(0, 0, 0);
        int count = 0;
        for (pos.setX(chunkPos.getMinBlockX()); pos.getX() <= chunkPos.getMaxBlockX(); pos.setX(pos.getX() + 1)) {
            for (pos.setY(chunk.getMinY()); pos.getY() <= chunk.getMaxY() + 1; pos.setY(pos.getY() + 1)) {
                for (pos.setZ(chunkPos.getMinBlockZ()); pos.getZ() <= chunkPos.getMaxBlockZ(); pos.setZ(pos.getZ() + 1)) {
                    if (chunk.getBlockState(pos).is(blockTag)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}