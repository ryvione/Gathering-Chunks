/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.ryvione.gatheringchunks.common.blockEntities.WorldScannerBlockEntity;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.minecraft.world.phys.Vec3;

import java.util.*;

public class ExperimentalScannerRenderer {
    
    private static final Map<ChunkPos, Map<Block, List<BlockPos>>> blockCache = new HashMap<>();
    private static long lastCacheClearTime = 0;
    private static final long CACHE_EXPIRY_MS = 10000;

    public static void renderHighlights() {
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        render(new PoseStack(), buffers, mc.gameRenderer.getMainCamera());
        buffers.endBatch(RenderTypes.LINES);
    }

    public static void render(PoseStack poseStack, MultiBufferSource bufferSource, net.minecraft.client.Camera camera) {
        if (!ChunkByChunkConfig.get().getWorldScannerConfig().isExperimentalMode()) {
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        Level level = mc.level;
        if (level == null) return;

        long currentTime = System.currentTimeMillis();
        if (currentTime - lastCacheClearTime > CACHE_EXPIRY_MS) {
            blockCache.clear();
            lastCacheClearTime = currentTime;
        }

        Vec3 camPos = camera.position();
        
        int renderDistance = mc.options.getEffectiveRenderDistance();
        int playerChunkX = SectionPos.blockToSectionCoord(camPos.x);
        int playerChunkZ = SectionPos.blockToSectionCoord(camPos.z);

        for (int cx = playerChunkX - renderDistance; cx <= playerChunkX + renderDistance; cx++) {
            for (int cz = playerChunkZ - renderDistance; cz <= playerChunkZ + renderDistance; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) continue;

                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (blockEntity instanceof WorldScannerBlockEntity scanner) {
                        if (scanner.isEspEnabled()) {
                            renderScannerHighlights(poseStack, bufferSource, level, scanner, camPos);
                        }
                    }
                }
            }
        }
    }

    private static void renderScannerHighlights(PoseStack poseStack, MultiBufferSource bufferSource, Level level, WorldScannerBlockEntity scanner, Vec3 camPos) {
        ItemStack input = scanner.getItem(WorldScannerBlockEntity.SLOT_INPUT);
        if (input.isEmpty()) return;

        int mapIdValue = scanner.dataAccess.get(WorldScannerBlockEntity.DATA_MAP);
        if (mapIdValue == WorldScannerBlockEntity.NO_MAP) return;

        MapItemSavedData mapData = level.getMapData(new net.minecraft.world.level.saveddata.maps.MapId(mapIdValue));
        if (mapData == null) return;

        Item inputItem = input.getItem();
        Collection<Block> targetBlocks = WorldScannerBlockEntity.scanItemMappings.get(inputItem);
        if (targetBlocks.isEmpty() && inputItem instanceof net.minecraft.world.item.BlockItem blockItem) {
            targetBlocks = Set.of(blockItem.getBlock());
        }
        
        if (targetBlocks.isEmpty()) return;

        BlockPos scannerPos = scanner.getBlockPos();
        ChunkPos scannerChunk = new ChunkPos(scannerPos);

        for (int xOffset = -WorldScannerBlockEntity.SCAN_CENTER; xOffset <= WorldScannerBlockEntity.SCAN_CENTER; xOffset++) {
            for (int zOffset = -WorldScannerBlockEntity.SCAN_CENTER; zOffset <= WorldScannerBlockEntity.SCAN_CENTER; zOffset++) {
                int chunkX = scannerChunk.x + xOffset;
                int chunkZ = scannerChunk.z + zOffset;

                double distSq = (chunkX * 16 + 8 - camPos.x) * (chunkX * 16 + 8 - camPos.x) + 
                               (chunkZ * 16 + 8 - camPos.z) * (chunkZ * 16 + 8 - camPos.z);
                if (distSq > 64 * 64) continue;

                LevelChunk chunk = level.getChunkSource().getChunk(chunkX, chunkZ, false);
                if (chunk == null) continue;

                int pixelX = (xOffset + WorldScannerBlockEntity.SCAN_CENTER) * WorldScannerBlockEntity.SCAN_ZOOM;
                int pixelY = (zOffset + WorldScannerBlockEntity.SCAN_CENTER) * WorldScannerBlockEntity.SCAN_ZOOM;
                
                if (pixelX < 0 || pixelX >= 128 || pixelY < 0 || pixelY >= 128) continue;
                byte colorId = mapData.colors[pixelX + pixelY * 128];
                
                if (colorId == MapColor.NONE.getPackedId(MapColor.Brightness.NORMAL)) continue;
                if (colorId == MapColor.COLOR_BLACK.getPackedId(MapColor.Brightness.NORMAL)) continue;

                renderTargetBlocksInChunk(poseStack, bufferSource, chunk, targetBlocks, colorId, camPos);
            }
        }
    }

    private static void renderTargetBlocksInChunk(PoseStack poseStack, MultiBufferSource bufferSource, LevelChunk chunk, Collection<Block> targetBlocks, byte colorId, Vec3 camPos) {
        MapColor mapColor = getMapColorFromPackedId(colorId);

        int color = mapColor.col;
        float r = ((color >> 16) & 0xFF) / 255f;
        float g = ((color >> 8) & 0xFF) / 255f;
        float b = (color & 0xFF) / 255f;

        ChunkPos cp = chunk.getPos();
        Map<Block, List<BlockPos>> chunkCache = blockCache.computeIfAbsent(cp, k -> new HashMap<>());

        VertexConsumer vertexConsumer = bufferSource.getBuffer(RenderTypes.LINES);

        for (Block targetBlock : targetBlocks) {
            List<BlockPos> positions = chunkCache.get(targetBlock);
            if (positions == null) {
                positions = new ArrayList<>();
                for (BlockPos pos : BlockPos.betweenClosed(cp.getMinBlockX(), chunk.getMinY(), cp.getMinBlockZ(), 
                                                           cp.getMaxBlockX(), chunk.getMaxY() + 1, cp.getMaxBlockZ())) {
                    if (chunk.getBlockState(pos).is(targetBlock)) {
                        positions.add(pos.immutable());
                    }
                }
                chunkCache.put(targetBlock, positions);
            }

            for (BlockPos pos : positions) {
                if (isEdgeBlock(chunk, pos, targetBlock)) {
                    renderHighlight(poseStack, vertexConsumer, pos, r, g, b, camPos);
                }
            }
        }
    }
    
    private static boolean isEdgeBlock(LevelChunk chunk, BlockPos pos, Block targetBlock) {
        BlockPos[] adjacent = {
            pos.above(), pos.below(),
            pos.north(), pos.south(),
            pos.east(), pos.west()
        };
        
        for (BlockPos adjPos : adjacent) {
            if (adjPos.getY() < chunk.getMinY() || adjPos.getY() > chunk.getMaxY() + 1) {
                return true;
            }
            
            if (!chunk.getBlockState(adjPos).is(targetBlock)) {
                return true;
            }
        }
        
        return false;
    }

    private static MapColor getMapColorFromPackedId(byte packedId) {
        MapColor[] possibleColors = {
            MapColor.COLOR_BLACK, MapColor.NETHER, MapColor.COLOR_RED, 
            MapColor.TERRACOTTA_YELLOW, MapColor.COLOR_YELLOW, MapColor.GOLD, MapColor.SNOW
        };
        MapColor.Brightness[] brightnesses = MapColor.Brightness.values();

        for (MapColor color : possibleColors) {
            for (MapColor.Brightness brightness : brightnesses) {
                if (color.getPackedId(brightness) == packedId) {
                    return color;
                }
            }
        }
        return MapColor.COLOR_BLACK;
    }

    private static void renderUnitLineBox(PoseStack poseStack, VertexConsumer consumer, float r, float g, float b, float a) {
        PoseStack.Pose pose = poseStack.last();
        float[][] corners = {
            {0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1},
            {0, 1, 0}, {1, 1, 0}, {1, 1, 1}, {0, 1, 1}
        };
        int[][] edges = {
            {0, 1}, {1, 2}, {2, 3}, {3, 0},
            {4, 5}, {5, 6}, {6, 7}, {7, 4},
            {0, 4}, {1, 5}, {2, 6}, {3, 7}
        };
        for (int[] edge : edges) {
            float[] from = corners[edge[0]];
            float[] to = corners[edge[1]];
            float dx = to[0] - from[0];
            float dy = to[1] - from[1];
            float dz = to[2] - from[2];
            float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            consumer.addVertex(pose, from[0], from[1], from[2]).setColor(r, g, b, a).setNormal(dx / len, dy / len, dz / len);
            consumer.addVertex(pose, to[0], to[1], to[2]).setColor(r, g, b, a).setNormal(dx / len, dy / len, dz / len);
        }
    }

    private static void renderHighlight(PoseStack poseStack, VertexConsumer vertexConsumer, BlockPos pos, float r, float g, float b, Vec3 camPos) {
        poseStack.pushPose();

        poseStack.translate(pos.getX() - camPos.x, pos.getY() - camPos.y, pos.getZ() - camPos.z);
        renderUnitLineBox(poseStack, vertexConsumer, r, g, b, 1.0f);
        poseStack.popPose();
    }
}