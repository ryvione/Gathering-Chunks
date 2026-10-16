/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.common;

import com.ryvione.gatheringchunks.server.world.ControllableChunkMap;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.Random;

public class CauldronRainFiller {

    private static final Random RANDOM = new Random();
    private static final float FILL_CHANCE = 0.05f;

    @SuppressWarnings("unchecked")
    public static void tick(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) return;
        if (!level.isRaining()) return;

        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Iterable<ChunkHolder> chunks = ((ControllableChunkMap) chunkMap).getVisibleChunkHolders();

        for (ChunkHolder holder : chunks) {
            if (RANDOM.nextFloat() > FILL_CHANCE * 16) continue;

            LevelChunk levelChunk = holder.getChunkToSend();
            if (levelChunk == null) continue;

            ChunkPos chunkPos = levelChunk.getPos();
            int localX = RANDOM.nextInt(16);
            int localZ = RANDOM.nextInt(16);
            int x = chunkPos.getMinBlockX() + localX;
            int z = chunkPos.getMinBlockZ() + localZ;
            int y = levelChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, localX, localZ);
            BlockPos top = new BlockPos(x, y, z);
            var state = levelChunk.getBlockState(top);

            if (!state.is(Blocks.CAULDRON) && !state.is(Blocks.WATER_CAULDRON) && !state.is(Blocks.POWDER_SNOW_CAULDRON)) continue;
            if (SpawnChunkHelper.isEmptyChunk(level, chunkPos)) continue;

            Holder<Biome> biomeHolder = level.getBiome(top);
            boolean isColdBiome = biomeHolder.value().coldEnoughToSnow(top, level.getSeaLevel());

            if (state.is(Blocks.CAULDRON)) {
                if (isColdBiome) {
                    level.setBlockAndUpdate(top, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState()
                            .setValue(LayeredCauldronBlock.LEVEL, 1));
                } else {
                    level.setBlockAndUpdate(top, Blocks.WATER_CAULDRON.defaultBlockState()
                            .setValue(LayeredCauldronBlock.LEVEL, 1));
                }
            } else if (state.is(Blocks.WATER_CAULDRON) && !isColdBiome) {
                int current = state.getValue(LayeredCauldronBlock.LEVEL);
                if (current < 3) {
                    level.setBlockAndUpdate(top, state.setValue(LayeredCauldronBlock.LEVEL, current + 1));
                }
            } else if (state.is(Blocks.POWDER_SNOW_CAULDRON) && isColdBiome) {
                int current = state.getValue(LayeredCauldronBlock.LEVEL);
                if (current < 3) {
                    level.setBlockAndUpdate(top, state.setValue(LayeredCauldronBlock.LEVEL, current + 1));
                }
            }
        }
    }
}
