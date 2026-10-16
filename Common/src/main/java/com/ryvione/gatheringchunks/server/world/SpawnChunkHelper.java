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

import com.ryvione.gatheringchunks.common.util.ChunkUtil;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.interop.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class SpawnChunkHelper {

    public static void setLevelSpawn(net.minecraft.world.level.storage.ServerLevelData levelData, net.minecraft.core.BlockPos pos) {
        net.minecraft.world.level.storage.LevelData.RespawnData current = levelData.getRespawnData();
        levelData.setSpawn(net.minecraft.world.level.storage.LevelData.RespawnData.of(
                current.dimension(), pos, current.yaw(), current.pitch()));
    }
    private SpawnChunkHelper() {
    }

    public static boolean isEmptyChunk(Level level, ChunkPos chunkPos) {
        net.minecraft.world.level.chunk.ChunkAccess chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) {
            return true;
        }

        int minY = level.getMinY();
        BlockPos bedrockCheckBlock = chunkPos.getMiddleBlockPosition(minY);
        if (Blocks.BEDROCK.equals(chunk.getBlockState(bedrockCheckBlock).getBlock())) {
            return false;
        }

        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] samples = {{8, 8}, {2, 2}, {13, 13}, {2, 13}, {13, 2}};
        int solidCount = 0;
        for (int[] sample : samples) {
            for (int dy = 0; dy <= 4; dy++) {
                pos.set(chunkPos.getMinBlockX() + sample[0], minY + dy, chunkPos.getMinBlockZ() + sample[1]);
                if (!chunk.getBlockState(pos).isAir()) {
                    solidCount++;
                    break;
                }
            }
        }

        if (solidCount >= 2) {
            return false;
        }

        for (int[] sample : samples) {
            int surfaceY = chunk.getHeight(
                    net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    sample[0], sample[1]);
            if (surfaceY > minY + 2) {
                return false;
            }
        }

        return true;
    }

    public static boolean isWaterSurfaceChunk(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) return false;

        int waterCount = 0;
        int totalSamples = 0;

        for (int lx = 0; lx < 16; lx += 4) {
            for (int lz = 0; lz < 16; lz += 4) {
                totalSamples++;
                int surfaceY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, lx, lz);
                if (surfaceY <= level.getMinY()) continue;

                BlockPos checkPos = new BlockPos(chunkPos.getMinBlockX() + lx, surfaceY, chunkPos.getMinBlockZ() + lz);
                if (level.getBlockState(checkPos).getBlock() instanceof LiquidBlock) {
                    waterCount++;
                } else {
                    BlockPos oneBelow = checkPos.below();
                    if (level.getBlockState(oneBelow).getBlock() instanceof LiquidBlock) {
                        waterCount++;
                    }
                }
            }
        }

        if (totalSamples == 0) return false;
        return (float) waterCount / totalSamples >= 1.0f;
    }

    public static BlockPos buildIslandOnWaterChunk(ServerLevel level, ChunkPos chunkPos) {
        LevelChunk chunk = level.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        if (chunk == null) return null;

        int centerX = chunkPos.getMiddleBlockX();
        int centerZ = chunkPos.getMiddleBlockZ();
        int localCX = 8;
        int localCZ = 8;
        int waterY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, localCX, localCZ);

        int islandY = waterY;

        int[][] islandOffsets = {
            {0, 0}, {1, 0}, {-1, 0}, {0, 1}, {0, -1},
            {2, 0}, {-2, 0}, {0, 2}, {0, -2},
            {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
        };

        for (int[] off : islandOffsets) {
            int bx = centerX + off[0];
            int bz = centerZ + off[1];
            int dist = Math.abs(off[0]) + Math.abs(off[1]);

            boolean isCenter = (off[0] == 0 && off[1] == 0);
            boolean isDiag = (Math.abs(off[0]) == 1 && Math.abs(off[1]) == 1);
            boolean isEdge = dist == 2 || isDiag;

            Block topBlock = isCenter ? Blocks.GRASS_BLOCK : (isEdge ? Blocks.DIRT : Blocks.GRASS_BLOCK);

            level.setBlock(new BlockPos(bx, islandY, bz), topBlock.defaultBlockState(), Block.UPDATE_ALL);
            for (int dy = 1; dy <= 3; dy++) {
                level.setBlock(new BlockPos(bx, islandY - dy, bz), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
            }
        }

        BlockPos islandSurface = new BlockPos(centerX, islandY + 1, centerZ);

        Random random = ChunkUtil.getChunkRandom(level, chunkPos);
        Holder<Biome> biome = level.getBiome(islandSurface);

        BlockPos tree1Pos = islandSurface;
        BlockPos tree2Pos = new BlockPos(centerX + 3, islandY + 1, centerZ + 3);

        TreePlacementHandler.placeTreeAtPublic(level, tree1Pos, random, biome);
        TreePlacementHandler.placeTreeAtPublic(level, tree2Pos, random, biome);

        return islandSurface;
    }

    private static BlockPos pickChestPos(ServerLevel targetLevel, ChunkPos chunkPos, Random random, List<BlockPos> usedPositions) {
        int minDepth = ChunkByChunkConfig.get().getGeneration().getMinChestSpawnDepth();
        int maxDepth = ChunkByChunkConfig.get().getGeneration().getMaxChestSpawnDepth();

        int minPos = Math.min(minDepth, maxDepth);
        int maxPos = Math.max(minDepth, maxDepth);

        minPos = Math.max(minPos, targetLevel.getMinY());
        maxPos = Math.min(maxPos, targetLevel.getMaxY() + 1);

        if (minPos > maxPos) minPos = maxPos;

        int attempts = 0;
        while (attempts < 20) {
            int yPos;
            if (minPos == maxPos) {
                yPos = minPos;
            } else {
                yPos = random.nextInt(maxPos - minPos + 1) + minPos;
            }
            yPos = Math.max(targetLevel.getMinY(), Math.min(targetLevel.getMaxY() + 1, yPos));

            int xPos = chunkPos.getMinBlockX() + random.nextInt(16);
            int zPos = chunkPos.getMinBlockZ() + random.nextInt(16);
            BlockPos candidate = new BlockPos(xPos, yPos, zPos);

            boolean tooClose = false;
            for (BlockPos used : usedPositions) {
                if (candidate.distSqr(used) < 9) {
                    tooClose = true;
                    break;
                }
            }
            if (!tooClose) {
                return candidate;
            }
            attempts++;
        }

        int yPos;
        if (minPos == maxPos) {
            yPos = minPos;
        } else {
            yPos = random.nextInt(maxPos - minPos + 1) + minPos;
        }
        return new BlockPos(
                chunkPos.getMinBlockX() + random.nextInt(16),
                yPos,
                chunkPos.getMinBlockZ() + random.nextInt(16));
    }

    public static void createNextSpawner(ServerLevel targetLevel, ChunkPos chunkPos) {
        Random random = ChunkUtil.getChunkRandom(targetLevel, chunkPos);

        int chestsToSpawn = ChunkByChunkConfig.get().getGeneration().getChestsPerChunk();

        List<BlockPos> spawnedPositions = new ArrayList<>();
        ChestTracker tracker = ChestTracker.get(targetLevel.getServer());

        for (int i = 0; i < chestsToSpawn; i++) {
            BlockPos blockPos = pickChestPos(targetLevel, chunkPos, random, spawnedPositions);

            if (ChunkByChunkConfig.get().getGeneration().useBedrockChest()) {
                targetLevel.setBlock(blockPos, Services.PLATFORM.bedrockChestBlock().defaultBlockState(), Block.UPDATE_CLIENTS);
            } else {
                targetLevel.setBlock(blockPos, Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);
            }

            if (targetLevel.getBlockEntity(blockPos) instanceof RandomizableContainerBlockEntity chestEntity) {
                List<ItemStack> items = ChunkByChunkConfig.get().getGeneration().getChestContents().getItems(random,
                        ChunkByChunkConfig.get().getGeneration().getChestQuantity());
                for (int slot = 0; slot < items.size(); slot++) {
                    chestEntity.setItem(slot, items.get(slot));
                }
            }

            spawnedPositions.add(blockPos);
            tracker.addChest(blockPos);

            final BlockPos notifyPos = blockPos;
            for (ServerPlayer player : targetLevel.getServer().getPlayerList().getPlayers()) {
                if (tracker.isTrackerEnabled(player.getUUID())) {
                    if (targetLevel.getBlockState(notifyPos).getBlock() instanceof net.minecraft.world.level.block.ChestBlock ||
                            targetLevel.getBlockState(notifyPos).getBlock() == Services.PLATFORM.bedrockChestBlock()) {
                        player.sendSystemMessage(
                                Component.literal("§6[Chunk Chest] §eA new chest has been spawned at §b" +
                                        notifyPos.getX() + ", " + notifyPos.getY() + ", " + notifyPos.getZ() +
                                        " §ein chunk §b[" + chunkPos.x + ", " + chunkPos.z + "]")
                        );
                    }
                }
            }
        }
    }
}
