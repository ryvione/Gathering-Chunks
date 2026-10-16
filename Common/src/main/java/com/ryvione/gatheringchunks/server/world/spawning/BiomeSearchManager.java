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

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.server.world.BiomeCoordinateCache;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BiomeSearchManager {

    public static class PendingSearch {
        public final ServerLevel level;
        public final String biomeTheme;
        public final boolean immediate;
        public final boolean overwrite;
        public final Random seedFinder;
        public final TerrainProfile targetProfile;
        public final ChunkPos targetPos;
        public final UUID playerUUID;
        public int attempts;

        public PendingSearch(ServerLevel level, String biomeTheme, boolean immediate,
                boolean overwrite, Random seedFinder, TerrainProfile targetProfile,
                ChunkPos targetPos, UUID playerUUID) {
            this.level = level;
            this.biomeTheme = biomeTheme;
            this.immediate = immediate;
            this.overwrite = overwrite;
            this.seedFinder = seedFinder;
            this.targetProfile = targetProfile;
            this.targetPos = targetPos;
            this.playerUUID = playerUUID;
            this.attempts = 0;
        }
    }

    private final MinecraftServer server;
    private final Map<String, Set<ChunkPos>> knownGoodSourceChunks = new ConcurrentHashMap<>();
    private final List<PendingSearch> pendingSearches = Collections.synchronizedList(new ArrayList<>());

    public BiomeSearchManager(MinecraftServer server) {
        this.server = server;
    }

    public List<PendingSearch> getPendingSearches() {
        return pendingSearches;
    }

    public boolean doesBiomeMatchTheme(Holder<Biome> biome, String biomeTheme) {
        BiomeCoordinateCache cache = BiomeCoordinateCache.get(server);
        String theme = cache.getBiomeTheme(biome);
        return biomeTheme.equalsIgnoreCase(theme);
    }
    @Nullable
    public ChunkPos tryChainedSourceMatch(ServerLevel targetLevel, ChunkPos targetPos, String theme,
            Map<ChunkPos, TerrainProfile> chunkTerrainProfiles) {

        if (!(targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator gen)) return null;
        ResourceKey<Level> sourceLevelKey = theme.isEmpty() ? gen.getGenerationLevel() : gen.getBiomeDimension(theme);
        if (sourceLevelKey == null) return null;
        ServerLevel sourceLevel = server.getLevel(sourceLevelKey);
        if (sourceLevel == null) return null;

        int checkY = theme.equals("ocean") ? 32 : 64;
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};

        for (int[] offset : offsets) {
            ChunkPos adjTarget = new ChunkPos(targetPos.x + offset[0], targetPos.z + offset[1]);
            TerrainProfile adjProfile = chunkTerrainProfiles.get(adjTarget);
            if (adjProfile == null) continue;
            ChunkPos candidateSource = new ChunkPos(
                    adjProfile.sourcePos.x + offset[0],
                    adjProfile.sourcePos.z + offset[1]);

            if (theme.isEmpty()) {
                GatheringChunksConstants.LOGGER.debug("[Chained] Using direct offset for theme-less search: target {} -> candidate {}", targetPos, candidateSource);
                return candidateSource;
            }

            int quartX = candidateSource.getMiddleBlockX() >> 2;
            int quartZ = candidateSource.getMiddleBlockZ() >> 2;
            int quartYv = theme.equals("ocean") ? 8 : 16;
            Holder<Biome> holder = sourceLevel.getChunkSource()
                    .getGenerator().getBiomeSource()
                    .getNoiseBiome(quartX, quartYv, quartZ, null);
            
            if (doesBiomeMatchTheme(holder, theme)) {
                GatheringChunksConstants.LOGGER.info(
                        "[Chained] SUCCESS for theme '{}': target {} -> source {}",
                        theme, targetPos, candidateSource);
                return candidateSource;
            } else {
                String foundTheme = BiomeCoordinateCache.get(server).getBiomeTheme(holder);
                GatheringChunksConstants.LOGGER.debug(
                        "[Chained] Theme mismatch: Candidate {} is '{}', but need '{}'. skipping.",
                        candidateSource, foundTheme, theme);
            }
        }
        return null;
    }

    @Nullable
    public ChunkPos tryCachedSourceMatch(String theme) {
        Set<ChunkPos> cached = knownGoodSourceChunks.get(theme);
        if (cached != null && !cached.isEmpty()) {
            Iterator<ChunkPos> it = cached.iterator();
            ChunkPos pos = it.next();
            it.remove();
            return pos;
        }
        return null;
    }

    public void updatePreScanCache(ServerLevel sourceLevel, ChunkPos sourcePos, String theme) {
        if (theme.isEmpty() || theme.equals("unknown")) return;
        Set<ChunkPos> cache = knownGoodSourceChunks.computeIfAbsent(theme, k -> new HashSet<>());
        if (cache.size() > 50) return;

        int checkY = theme.equals("ocean") ? 32 : 64;
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            ChunkPos neighbor = new ChunkPos(sourcePos.x + offset[0], sourcePos.z + offset[1]);
            if (!sourceLevel.hasChunk(neighbor.x, neighbor.z)) continue;
            Holder<Biome> holder = sourceLevel.getBiome(neighbor.getMiddleBlockPosition(checkY));
            if (doesBiomeMatchTheme(holder, theme)) {
                cache.add(neighbor);
            }
        }
    }

    public List<ChunkPos> getAdjacentChunksWithTheme(ChunkPos targetPos, String biomeTheme,
            Map<ChunkPos, TerrainProfile> chunkTerrainProfiles) {
        List<ChunkPos> adjacent = new ArrayList<>();
        int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] offset : offsets) {
            ChunkPos adjPos = new ChunkPos(targetPos.x + offset[0], targetPos.z + offset[1]);
            TerrainProfile profile = chunkTerrainProfiles.get(adjPos);
            if (profile != null && profile.biomeTheme.equals(biomeTheme)) {
                adjacent.add(adjPos);
            }
        }
        return adjacent;
    }

    @Nullable
    public TerrainProfile analyzeChunkTerrain(ServerLevel level, ChunkPos pos, String biomeTheme) {
        try {
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                return new TerrainProfile(
                        new int[16], new int[16], new int[16], new int[16],
                        new String[8], new String[8], new String[8], new String[8],
                        64, 0, biomeTheme, pos);
            }

            boolean isOcean = biomeTheme.equals("ocean");
            Heightmap.Types heightmapType = isOcean
                    ? Heightmap.Types.OCEAN_FLOOR
                    : Heightmap.Types.WORLD_SURFACE;

            int[] northEdge = new int[16];
            int[] southEdge = new int[16];
            int[] eastEdge  = new int[16];
            int[] westEdge  = new int[16];

            for (int i = 0; i < 16; i++) {
                northEdge[i] = chunk.getHeight(heightmapType, i, 0);
                southEdge[i] = chunk.getHeight(heightmapType, i, 15);
                westEdge[i]  = chunk.getHeight(heightmapType, 0, i);
                eastEdge[i]  = chunk.getHeight(heightmapType, 15, i);
            }

            String[] nBio = new String[8];
            String[] sBio = new String[8];
            String[] eBio = new String[8];
            String[] wBio = new String[8];
            BiomeCoordinateCache bcache = BiomeCoordinateCache.get(server);
            int baseQX = pos.getMinBlockX() >> 2;
            int baseQZ = pos.getMinBlockZ() >> 2;
            int quartY = isOcean ? 8 : 16;

            for (int q = 0; q < 4; q++) {
                nBio[q]     = bcache.getBiomeTheme(level.getChunkSource().getGenerator()
                        .getBiomeSource().getNoiseBiome(baseQX + q, quartY, baseQZ, null));
                sBio[q]     = bcache.getBiomeTheme(level.getChunkSource().getGenerator()
                        .getBiomeSource().getNoiseBiome(baseQX + q, quartY, baseQZ + 3, null));
                wBio[q]     = bcache.getBiomeTheme(level.getChunkSource().getGenerator()
                        .getBiomeSource().getNoiseBiome(baseQX, quartY, baseQZ + q, null));
                eBio[q]     = bcache.getBiomeTheme(level.getChunkSource().getGenerator()
                        .getBiomeSource().getNoiseBiome(baseQX + 3, quartY, baseQZ + q, null));
                nBio[q + 4] = nBio[q];
                sBio[q + 4] = sBio[q];
                wBio[q + 4] = wBio[q];
                eBio[q + 4] = eBio[q];
            }

            int totalHeight = 0;
            int sampleCount = 0;
            List<Integer> heights = new ArrayList<>();
            for (int x = 0; x < 16; x += 4) {
                for (int z = 0; z < 16; z += 4) {
                    int y = chunk.getHeight(heightmapType, x, z);
                    if (y > level.getMinY()) {
                        heights.add(y);
                        totalHeight += y;
                        sampleCount++;
                    }
                }
            }

            int avgHeight = sampleCount > 0 ? totalHeight / sampleCount : 64;
            int variance = 0;
            if (!heights.isEmpty()) {
                for (int h : heights) variance += Math.abs(h - avgHeight);
                variance = variance / heights.size();
            }

            return new TerrainProfile(northEdge, southEdge, eastEdge, westEdge,
                    nBio, sBio, eBio, wBio, avgHeight, variance, biomeTheme, pos);
        } catch (Exception e) {
            GatheringChunksConstants.LOGGER.warn("Failed to analyze chunk terrain at {}: {}", pos, e.getMessage());
            return null;
        }
    }
    public boolean terrainsMatch(TerrainProfile target, TerrainProfile candidate,
            ChunkPos targetPos, ChunkPos candidatePos) {
        if (target == null || candidate == null) return true;
        if (!target.biomeTheme.equals(candidate.biomeTheme)) {
            GatheringChunksConstants.LOGGER.debug("[TerrainMatch] Theme mismatch: target={} candidate={}", target.biomeTheme, candidate.biomeTheme);
            return false;
        }

        int dx = candidatePos.x - targetPos.x;
        int dz = candidatePos.z - targetPos.z;

        int[] targetEdge    = null;
        int[] candidateEdge = null;
        String[] targetBio    = null;
        String[] candidateBio = null;
        String dirLabel = "none";

        if      (dx ==  1 && dz == 0) { targetEdge = target.eastEdge;  candidateEdge = candidate.westEdge;  targetBio = target.eastBiomes;  candidateBio = candidate.westBiomes; dirLabel="EAST"; }
        else if (dx == -1 && dz == 0) { targetEdge = target.westEdge;  candidateEdge = candidate.eastEdge;  targetBio = target.westBiomes;  candidateBio = candidate.eastBiomes; dirLabel="WEST"; }
        else if (dx == 0 && dz ==  1) { targetEdge = target.southEdge; candidateEdge = candidate.northEdge; targetBio = target.southBiomes; candidateBio = candidate.northBiomes; dirLabel="SOUTH"; }
        else if (dx == 0 && dz == -1) { targetEdge = target.northEdge; candidateEdge = candidate.southEdge; targetBio = target.northBiomes; candidateBio = candidate.southBiomes; dirLabel="NORTH"; }

        boolean isOcean = target.biomeTheme.equals("ocean");

        if (targetEdge != null) {
            if (targetBio != null && candidateBio != null) {
                int bioMatch = 0;
                for (int i = 0; i < 8; i++) {
                    if (targetBio[i] != null && candidateBio[i] != null
                            && targetBio[i].equals(candidateBio[i])) {
                        bioMatch++;
                    }
                }
                int threshold = isOcean ? 3 : 2; 
                if (bioMatch < threshold) {
                    GatheringChunksConstants.LOGGER.debug("[TerrainMatch] {} Bio match failed: {}/{}", dirLabel, bioMatch, threshold);
                    return false;
                }
            }

            int totalDiff = 0;
            for (int i = 0; i < 16; i++) {
                totalDiff += Math.abs(targetEdge[i] - candidateEdge[i]);
            }
            int avgDiff = totalDiff / 16;
            int maxAvgDiff = isOcean ? 10 : 8;
            if (avgDiff > maxAvgDiff) {
                 GatheringChunksConstants.LOGGER.debug("[TerrainMatch] {} Edge match failed: avgDiff={} max={}", dirLabel, avgDiff, maxAvgDiff);
                 return false;
            }
            return true;
        }

        int heightDiff   = Math.abs(target.averageHeight - candidate.averageHeight);
        int varianceDiff = Math.abs(target.heightVariance - candidate.heightVariance);
        int maxHeightDiff = isOcean ? 20 : 15;
        boolean matched = heightDiff <= maxHeightDiff && varianceDiff <= 30;
        if (!matched) {
            GatheringChunksConstants.LOGGER.debug("[TerrainMatch] Profile match failed: hDiff={} vDiff={}", heightDiff, varianceDiff);
        }
        return matched;
    }
}
