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

import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

    public class TreePlacementHandler {

    public static void ensureTreesInChunk(ServerLevel level, ChunkPos chunkPos) {
    if (!ChunkByChunkConfig.get().getGatheringChunksConfig().isAutoSpawnTrees()) {
        return;
    }

    TreePlacementSavedData data = TreePlacementSavedData.get(level);
    if (data.isChunkProcessed(chunkPos)) {
        return;
    }

    data.markChunkProcessed(chunkPos);

    LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);

    if (hasVillage(level, chunk)) {
        return;
    }

    if (hasWoodInChunk(level, chunk)) {
        return;
    }

    if (hasMinimumWoodInDimension(level, chunkPos)) {
        return;
    }

    placeTreeInChunk(level, chunk);
    }

    private static boolean hasVillage(ServerLevel level, LevelChunk chunk) {
        for (StructureStart start : chunk.getAllStarts().values()) {
            if (start.isValid()) {
                Holder<net.minecraft.world.level.levelgen.structure.Structure> structure =
                        level.registryAccess()
                                .lookupOrThrow(Registries.STRUCTURE)
                                .wrapAsHolder(start.getStructure());
                if (level.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(StructureTags.VILLAGE)
                        .map(tag -> tag.contains(structure)).orElse(false)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasMinimumWoodInDimension(ServerLevel level, ChunkPos center) {
        int totalLogs = 0;
        int radius = 3;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                ChunkPos pos = new ChunkPos(center.x + dx, center.z + dz);
                if (!level.hasChunk(pos.x, pos.z)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(pos.x, pos.z);

                BlockPos.MutableBlockPos mpos = new BlockPos.MutableBlockPos();
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = level.getMinY(); y < level.getMaxY() + 1; y++) {
                            mpos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                            if (level.getBlockState(mpos).is(BlockTags.LOGS)) {
                                totalLogs++;
                                if (totalLogs >= 4) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    private static boolean hasWoodInChunk(ServerLevel level, LevelChunk chunk) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = level.getMinY(); y < level.getMaxY() + 1; y++) {
                    pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                    BlockState state = level.getBlockState(pos);

                    if (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    private static boolean placeTreeInChunk(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> validPositions = findValidTreePositions(level, chunk);

        if (validPositions.isEmpty()) {
            return false;
        }

        Random random = new Random();
        
        BlockPos treePos1 = validPositions.get(random.nextInt(validPositions.size()));
        Holder<Biome> biomeHolder1 = level.getBiome(treePos1);
        placeTreeAt(level, treePos1, random, biomeHolder1);
        
        if (validPositions.size() > 1) {
            validPositions.remove(treePos1);
            
            List<BlockPos> farPositions = validPositions.stream()
                .filter(pos -> pos.distSqr(treePos1) >= 64)  
                .toList();
            
            if (!farPositions.isEmpty()) {
                BlockPos treePos2 = farPositions.get(random.nextInt(farPositions.size()));
                Holder<Biome> biomeHolder2 = level.getBiome(treePos2);
                placeTreeAt(level, treePos2, random, biomeHolder2);
            } else if (!validPositions.isEmpty()) {
                BlockPos treePos2 = validPositions.get(random.nextInt(validPositions.size()));
                Holder<Biome> biomeHolder2 = level.getBiome(treePos2);
                placeTreeAt(level, treePos2, random, biomeHolder2);
            }
        }
        return true;
    }

    private static List<BlockPos> findValidTreePositions(ServerLevel level, LevelChunk chunk) {
        List<BlockPos> positions = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 2; x < 14; x++) {
            for (int z = 2; z < 14; z++) {
                for (int y = level.getMaxY(); y >= level.getMinY(); y--) {
                    pos.set(chunk.getPos().getMinBlockX() + x, y, chunk.getPos().getMinBlockZ() + z);
                    BlockState state = level.getBlockState(pos);

                    if (!state.isAir() && isExposedToAir(level, pos)) {
                        BlockPos groundPos = pos.immutable();
                        BlockState groundState = level.getBlockState(groundPos);

                        if (groundState.is(BlockTags.DIRT) || groundState.is(Blocks.GRASS_BLOCK) ||
                                groundState.is(Blocks.PODZOL) || groundState.is(Blocks.MYCELIUM) ||
                                groundState.is(Blocks.SAND) || groundState.is(Blocks.RED_SAND)) {
                            positions.add(groundPos.above());
                            break;
                        } else if (groundState.is(Blocks.STONE) || groundState.is(Blocks.DEEPSLATE) ||
                                groundState.is(Blocks.SANDSTONE) || groundState.is(Blocks.RED_SANDSTONE)) {
                            level.setBlock(groundPos, Blocks.DIRT.defaultBlockState(), 3);
                            positions.add(groundPos.above());
                            break;
                        }
                        break;
                    }
                }
            }
        }

        return positions;
    }

    private static boolean isExposedToAir(ServerLevel level, BlockPos pos) {
        BlockPos above = pos.above();
        for (int i = 0; i < 5; i++) {
            if (!level.getBlockState(above.above(i)).isAir()) {
                return false;
            }
        }
        return true;
    }

    public static void placeTreeAtPublic(ServerLevel level, BlockPos pos, Random random, Holder<Biome> biomeHolder) {
        placeTreeAt(level, pos, random, biomeHolder);
    }

    private static void placeTreeAt(ServerLevel level, BlockPos pos, Random random, Holder<Biome> biomeHolder) {
        net.minecraft.world.level.block.Block sapling;
        if (biomeHolder.is(Biomes.SAVANNA) || biomeHolder.is(Biomes.SAVANNA_PLATEAU) ||
                biomeHolder.is(Biomes.WINDSWEPT_SAVANNA)) {
            sapling = Blocks.ACACIA_SAPLING;
        } else if (biomeHolder.is(Biomes.JUNGLE) || biomeHolder.is(Biomes.BAMBOO_JUNGLE) ||
                biomeHolder.is(Biomes.SPARSE_JUNGLE)) {
            sapling = Blocks.JUNGLE_SAPLING;
        } else if (biomeHolder.is(Biomes.TAIGA) || biomeHolder.is(Biomes.SNOWY_TAIGA) ||
                biomeHolder.is(Biomes.OLD_GROWTH_PINE_TAIGA) || biomeHolder.is(Biomes.OLD_GROWTH_SPRUCE_TAIGA)) {
            sapling = Blocks.SPRUCE_SAPLING;
        } else if (biomeHolder.is(Biomes.BIRCH_FOREST) || biomeHolder.is(Biomes.OLD_GROWTH_BIRCH_FOREST)) {
            sapling = Blocks.BIRCH_SAPLING;
        } else if (biomeHolder.is(Biomes.DARK_FOREST)) {
            sapling = random.nextBoolean() ? Blocks.DARK_OAK_SAPLING : Blocks.OAK_SAPLING;
        } else {
            sapling = Blocks.OAK_SAPLING;
        }

        net.minecraft.world.level.block.state.BlockState state = sapling.defaultBlockState();
        level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
        if (level.getBlockState(pos).getBlock() instanceof net.minecraft.world.level.block.SaplingBlock saplingBlock) {
            saplingBlock.advanceTree(level, pos, level.getBlockState(pos), level.getRandom());
            saplingBlock.advanceTree(level, pos, level.getBlockState(pos), level.getRandom());
        }
    }
}
