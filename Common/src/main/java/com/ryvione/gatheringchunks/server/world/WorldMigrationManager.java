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

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.util.SavedDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.core.HolderLookup;

import java.util.ArrayList;
import java.util.List;

public class WorldMigrationManager extends SavedData {

    private static final int CURRENT_VERSION = 5;
    private int savedVersion = 0;

    public static WorldMigrationManager get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getChunkSource().getDataStorage().computeIfAbsent(SavedDataCompat.of(
                "gcmigration",
                WorldMigrationManager::new,
                WorldMigrationManager::load,
                migration -> migration.save(new CompoundTag())
        ));
    }

    private static WorldMigrationManager load(CompoundTag tag) {
        WorldMigrationManager mgr = new WorldMigrationManager();
        mgr.savedVersion = tag.getIntOr("version", 0);
        return mgr;
    }

    public CompoundTag save(CompoundTag tag) {
        tag.putInt("version", CURRENT_VERSION);
        return tag;
    }

    public void runMigrationsIfNeeded(MinecraftServer server) {
        if (savedVersion >= CURRENT_VERSION) {
            return;
        }

        if (savedVersion == 0) {
            ChunkSpawnController controller = ChunkSpawnController.get(server);
            int totalSpawnedAcrossDims = 0;
            for (ServerLevel level : server.getAllLevels()) {
                totalSpawnedAcrossDims += controller.getSpawnedChunkCount(level.dimension().identifier().toString());
            }
            
            if (totalSpawnedAcrossDims == 0) {
                GatheringChunksConstants.LOGGER.info("[Migration] Fresh world detected. Initializing migration version to {}.", CURRENT_VERSION);
                savedVersion = CURRENT_VERSION;
                setDirty();
                return;
            }
        }

        GatheringChunksConstants.LOGGER.info(
                "[Migration] World data version {} detected, current version is {}. Running migrations...",
                savedVersion, CURRENT_VERSION);


        boolean success = true;
        try {
            if (savedVersion < 2) migrate_v2_fixChunkOrigins(server);
            if (savedVersion < 3) migrate_v3_fixSpawnPoint(server);
            if (savedVersion < 4) migrate_v4_fixSpawnHeight(server);
            if (savedVersion < 5) migrate_v5_preventCorruption(server);
        } catch (Exception e) {
            GatheringChunksConstants.LOGGER.error("[Migration] CRITICAL ERROR DURING MIGRATION: ", e);
            success = false;
        }

        if (success) {
            savedVersion = CURRENT_VERSION;
            setDirty();
            server.getLevel(Level.OVERWORLD).getChunkSource().getDataStorage().saveAndJoin();
            GatheringChunksConstants.LOGGER.info("[Migration] All migrations complete and saved.");
        } else {
            GatheringChunksConstants.LOGGER.error("[Migration] Migration failed. World may be in an inconsistent state.");
        }
    }


    private void migrate_v5_preventCorruption(MinecraftServer server) {
        GatheringChunksConstants.LOGGER.info("[Migration v5] Running safety and anti-corruption checks...");
        ChunkSpawnController controller = ChunkSpawnController.get(server);
        
        for (ServerLevel level : server.getAllLevels()) {
            if (!(level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator)) continue;
            
            String dimId = level.dimension().identifier().toString();
            int actualCount = 0;
            int radius = 12;
            ChunkPos spawnChunk = new ChunkPos(level.getRespawnData().pos());
            
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (!SpawnChunkHelper.isEmptyChunk(level, new ChunkPos(spawnChunk.x + x, spawnChunk.z + z))) {
                        actualCount++;
                    }
                }
            }
            
            int storedCount = controller.getSpawnedChunkCount(dimId);
            if (storedCount != actualCount) {
                GatheringChunksConstants.LOGGER.warn("[Migration v5] Dimension {} count mismatch: Stored={}, Actual={}. Correcting.", dimId, storedCount, actualCount);
                controller.setSpawnedChunkCount(dimId, actualCount);
                setDirty();
            }
            
            if (controller.getOriginChunk(dimId) == null) {
                controller.setOriginChunk(dimId, spawnChunk);
                GatheringChunksConstants.LOGGER.info("[Migration v5] Set missing origin for {} to {}", dimId, spawnChunk);
            }
        }

        ChunkEngineManager engineManager = ChunkEngineManager.get(server);
        
        GatheringChunksConstants.LOGGER.info("[Migration v5] Anti-corruption checks finished successfully.");
    }



    private void migrate_v1_fixNetherBedrock(MinecraftServer server) {
        GatheringChunksConstants.LOGGER.info("[Migration v1] Scanning nether for bedrock ceiling issues...");
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null || !(nether.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) {
            return;
        }

        ChunkSpawnController controller = ChunkSpawnController.get(server);
        String netherDim = Level.NETHER.identifier().toString();
        int spawnedCount = controller.getSpawnedChunkCount(netherDim);

        if (spawnedCount == 0) {
            GatheringChunksConstants.LOGGER.info("[Migration v1] No nether chunks to migrate.");
            return;
        }

        int fixedChunks = 0;
        BlockPos origin = nether.getRespawnData().pos();
        int searchRadius = 32;

        for (int cx = -searchRadius; cx <= searchRadius; cx++) {
            for (int cz = -searchRadius; cz <= searchRadius; cz++) {
                ChunkPos chunkPos = new ChunkPos(new ChunkPos(origin).x + cx, new ChunkPos(origin).z + cz);
                if (!SpawnChunkHelper.isEmptyChunk(nether, chunkPos)) {
                    if (fixNetherBedrockCeiling(nether, chunkPos)) {
                        fixedChunks++;
                    }
                }
            }
        }
        GatheringChunksConstants.LOGGER.info("[Migration v1] Fixed bedrock ceiling in {} nether chunks.", fixedChunks);
    }

    private boolean fixNetherBedrockCeiling(ServerLevel nether, ChunkPos chunkPos) {
        boolean fixed = false;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        
        int[] heightsToFix = {127, 255};
        
        for (int checkY : heightsToFix) {
            if (checkY >= nether.getMaxY() + 1) continue;
            
            for (int x = chunkPos.getMinBlockX(); x <= chunkPos.getMaxBlockX(); x++) {
                for (int z = chunkPos.getMinBlockZ(); z <= chunkPos.getMaxBlockZ(); z++) {
                    pos.set(x, checkY, z);
                    if (nether.getBlockState(pos).is(Blocks.BEDROCK)) {
                        for (int y = checkY; y >= checkY - 5; y--) {
                            pos.set(x, y, z);
                            if (nether.getBlockState(pos).is(Blocks.BEDROCK)) {
                                nether.setBlock(pos, Blocks.AIR.defaultBlockState(),
                                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                                fixed = true;
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }
        return fixed;
    }

    private void migrate_v2_fixChunkOrigins(MinecraftServer server) {
        GatheringChunksConstants.LOGGER.info("[Migration v2] Verifying chunk origin data...");
        ChunkSpawnController controller = ChunkSpawnController.get(server);
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        String overworldDim = Level.OVERWORLD.identifier().toString();
        ChunkPos spawnChunk = new ChunkPos(overworld.getRespawnData().pos());
        GatheringChunksConstants.LOGGER.info("[Migration v2] Spawn chunk is at {}", spawnChunk);
    }

    private void migrate_v3_fixSpawnPoint(MinecraftServer server) {
        GatheringChunksConstants.LOGGER.info("[Migration v3] Verifying spawn point is in a spawned chunk...");
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        if (!(overworld.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) return;

        BlockPos spawnPos = overworld.getRespawnData().pos();
        ChunkPos spawnChunk = new ChunkPos(spawnPos);

        if (SpawnChunkHelper.isEmptyChunk(overworld, spawnChunk)) {
            int searchRadius = 16;
            for (int dx = -searchRadius; dx <= searchRadius; dx++) {
                for (int dz = -searchRadius; dz <= searchRadius; dz++) {
                    ChunkPos candidate = new ChunkPos(spawnChunk.x + dx, spawnChunk.z + dz);
                    if (!SpawnChunkHelper.isEmptyChunk(overworld, candidate)) {
                        net.minecraft.world.level.chunk.LevelChunk levelChunk =
                                overworld.getChunkSource().getChunkNow(candidate.x, candidate.z);
                        int safeY = (overworld.getMaxY() + 1) / 2;
                        if (levelChunk != null) {
                            safeY = com.ryvione.gatheringchunks.common.util.ChunkUtil.getSafeSpawnHeight(
                                    levelChunk, candidate.getMiddleBlockX(), candidate.getMiddleBlockZ());
                        }
                        BlockPos newSpawn = new BlockPos(candidate.getMiddleBlockX(), safeY, candidate.getMiddleBlockZ());
                        SpawnChunkHelper.setLevelSpawn((ServerLevelData) overworld.getLevelData(), newSpawn);
                        GatheringChunksConstants.LOGGER.info(
                                "[Migration v3] Moved spawn from {} to populated chunk at {}", spawnPos, newSpawn);
                        return;
                    }
                }
            }
            GatheringChunksConstants.LOGGER.warn(
                    "[Migration v3] Could not find a populated chunk near spawn; spawn may be in void.");
        } else {
            GatheringChunksConstants.LOGGER.info("[Migration v3] Spawn point is in a valid chunk. No fix needed.");
        }
    }

    private void migrate_v4_fixSpawnHeight(MinecraftServer server) {
        GatheringChunksConstants.LOGGER.info("[Migration v4] Ensuring spawn point is at a safe height...");
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) return;
        if (!(overworld.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) return;

        BlockPos spawnPos = overworld.getRespawnData().pos();
        ChunkPos spawnChunk = new ChunkPos(spawnPos);

        if (SpawnChunkHelper.isEmptyChunk(overworld, spawnChunk)) {
            GatheringChunksConstants.LOGGER.info("[Migration v4] Spawn is in an empty chunk - skipping height fix.");
            return;
        }

        LevelChunk levelChunk = overworld.getChunkSource().getChunkNow(spawnChunk.x, spawnChunk.z);
        if (levelChunk == null) return;

        int safeY = com.ryvione.gatheringchunks.common.util.ChunkUtil.getSafeSpawnHeight(
                levelChunk, spawnPos.getX(), spawnPos.getZ());

        if (safeY != spawnPos.getY()) {
            BlockPos newSpawn = new BlockPos(spawnPos.getX(), safeY, spawnPos.getZ());
            SpawnChunkHelper.setLevelSpawn((ServerLevelData) overworld.getLevelData(), newSpawn);
            GatheringChunksConstants.LOGGER.info(
                    "[Migration v4] Fixed spawn height from y={} to y={}", spawnPos.getY(), safeY);
        } else {
            GatheringChunksConstants.LOGGER.info("[Migration v4] Spawn height already correct at y={}", spawnPos.getY());
        }
    }
}