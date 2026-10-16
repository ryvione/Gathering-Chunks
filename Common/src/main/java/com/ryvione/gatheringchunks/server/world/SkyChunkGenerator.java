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

import com.google.common.collect.ImmutableMap;
import com.mojang.serialization.Lifecycle;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.util.random.WeightedList;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.*;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.AirBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

public class SkyChunkGenerator extends ChunkGenerator {
    public static final MapCodec<? extends SkyChunkGenerator> CODEC = RecordCodecBuilder.mapCodec((encoded) -> encoded
            .group(ChunkGenerator.CODEC.withLifecycle(Lifecycle.stable()).fieldOf("parent")
                    .forGetter(SkyChunkGenerator::getParent))
            .apply(encoded, encoded.stable(SkyChunkGenerator::new)));
    public static final MapCodec<? extends SkyChunkGenerator> OLD_NETHER_CODEC = RecordCodecBuilder
            .mapCodec((encoded) -> encoded
                    .group(ChunkGenerator.CODEC.withLifecycle(Lifecycle.stable()).fieldOf("parent")
                            .forGetter(SkyChunkGenerator::getParent))
                    .apply(encoded, encoded.stable(SkyChunkGenerator::new)));
    private final ChunkGenerator parent;
    private ResourceKey<Level> generationLevel;
    private List<ResourceKey<Level>> synchedLevels = new ArrayList<>();
    private int initialChunks;
    private boolean chunkSpawnerAllowed;
    private boolean randomChunkSpawnerAllowed;
    private EmptyGenerationType generationType = EmptyGenerationType.Normal;
    private Block sealBlock;
    @Nullable
    private Block sealCoverBlock;
    @Nullable
    private Holder<Biome> unspawnedBiome;
    private final Map<String, ResourceKey<Level>> biomeDimensions = new ConcurrentHashMap<>();
    private Set<Long> spawnedChunks = ConcurrentHashMap.newKeySet();
    @Nullable
    private transient net.minecraft.server.level.ServerLevel associatedLevel;

    public enum EmptyGenerationType {
        Normal,
        Sealed,
        Nether;

        private static final Map<String, EmptyGenerationType> STRING_LOOKUP;
        static {
            ImmutableMap.Builder<String, EmptyGenerationType> builder = new ImmutableMap.Builder<>();
            for (EmptyGenerationType value : EmptyGenerationType.values()) {
                builder.put(value.name().toLowerCase(Locale.ROOT), value);
            }
            STRING_LOOKUP = builder.build();
        }

        public static EmptyGenerationType getFromString(String asString) {
            return STRING_LOOKUP.getOrDefault(asString.toLowerCase(Locale.ROOT), Normal);
        }
    }

    public SkyChunkGenerator(ChunkGenerator parent) {
        super(parent.getBiomeSource());
        this.parent = parent;
    }

    public void configure(ResourceKey<Level> generationLevel, EmptyGenerationType generationType, Block sealBlock,
            Block sealCoverBlock, int initialChunks, boolean chunkSpawnerAllowed, boolean randomChunkSpawnerAllowed) {
        this.generationLevel = generationLevel;
        this.generationType = generationType;
        this.initialChunks = initialChunks;
        this.chunkSpawnerAllowed = chunkSpawnerAllowed;
        this.randomChunkSpawnerAllowed = randomChunkSpawnerAllowed;
        this.sealBlock = sealBlock;
        this.sealCoverBlock = sealCoverBlock;
    }

    public boolean isChunkSpawnerAllowed() {
        return chunkSpawnerAllowed;
    }

    public boolean isRandomChunkSpawnerAllowed() {
        return randomChunkSpawnerAllowed;
    }

    public void addSynchLevel(ResourceKey<Level> dimension) {
        com.ryvione.gatheringchunks.common.GatheringChunksConstants.LOGGER
                .info("[SkyGenerator] Adding sync level {} to generator {}", dimension.identifier(), this);
        synchedLevels.add(dimension);
    }

    public List<ResourceKey<Level>> getSynchedLevels() {
        return synchedLevels;
    }

    public EmptyGenerationType getGenerationType() {
        return generationType;
    }

    public Block getSealBlock() {
        return sealBlock;
    }

    @Nullable
    public Holder<Biome> getUnspawnedBiome() {
        return unspawnedBiome;
    }

    public void setUnspawnedBiome(Holder<Biome> unspawnedBiome) {
        this.unspawnedBiome = unspawnedBiome;
    }

    public void addBiomeDimension(String name, ResourceKey<Level> level) {
        biomeDimensions.put(name, level);
    }

    @Nullable
    public ResourceKey<Level> getBiomeDimension(String name) {
        return biomeDimensions.get(name);
    }

    public Map<String, ResourceKey<Level>> getBiomeDimensions() {
        return Collections.unmodifiableMap(biomeDimensions);
    }

    public int getInitialChunks() {
        return initialChunks;
    }

    public ChunkGenerator getParent() {
        return parent;
    }

    public ResourceKey<Level> getGenerationLevel() {
        return generationLevel;
    }

    public void markChunkSpawned(long chunkPos) {
        spawnedChunks.add(chunkPos);
    }

    public boolean isChunkSpawned(long chunkPos) {
        return spawnedChunks.contains(chunkPos);
    }

    public void unmarkChunkSpawned(long chunkPos) {
        spawnedChunks.remove(chunkPos);
    }

    public void setAssociatedLevel(net.minecraft.server.level.ServerLevel level) {
        this.associatedLevel = level;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager,
            StructureManager structureManager, ChunkAccess chunk) {
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
            StructureManager structureManager, ChunkAccess chunk) {
        EmptyGenerationType type = generationType != null ? generationType : EmptyGenerationType.Normal;
        return switch (type) {
            case Sealed -> parent.fillFromNoise(blender, randomState, structureManager, chunk)
                    .thenApply(chunkAccess -> {
                        BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);
                        for (blockPos.setZ(0); blockPos.getZ() < 16; blockPos.setZ(blockPos.getZ() + 1)) {
                            for (blockPos.setX(0); blockPos.getX() < 16; blockPos.setX(blockPos.getX() + 1)) {
                                blockPos.setY(chunkAccess.getMaxY());
                                while (blockPos.getY() > chunkAccess.getMinY()
                                        && chunkAccess.getBlockState(blockPos).getBlock() instanceof AirBlock) {
                                    blockPos.setY(blockPos.getY() - 1);
                                }
                                if (sealCoverBlock != null) {
                                    blockPos.setY(blockPos.getY() + 1);
                                    chunkAccess.setBlockState(blockPos, sealCoverBlock.defaultBlockState(), 0);
                                    blockPos.setY(blockPos.getY() - 1);
                                }
                                while (blockPos.getY() > chunkAccess.getMinY() + 1) {
                                    chunkAccess.setBlockState(blockPos, sealBlock.defaultBlockState(), 0);
                                    blockPos.setY(blockPos.getY() - 1);
                                }
                                chunkAccess.setBlockState(blockPos, Blocks.BEDROCK.defaultBlockState(), 0);
                                blockPos.setY(blockPos.getY() - 1);
                                chunkAccess.setBlockState(blockPos, Blocks.VOID_AIR.defaultBlockState(), 0);
                            }
                        }
                        return chunkAccess;
                    });
            case Nether -> CompletableFuture.completedFuture(chunk).thenApply(chunkAccess -> {
                BlockPos.MutableBlockPos blockPos = new BlockPos.MutableBlockPos(0, 0, 0);

                for (int y = chunkAccess.getMinY(); y < chunkAccess.getMaxY() + 1; y++) {
                    for (int z = 0; z < 16; z++) {
                        for (int x = 0; x < 16; x++) {
                            blockPos.set(x, y, z);
                            chunkAccess.setBlockState(blockPos, Blocks.AIR.defaultBlockState(), 0);
                        }
                    }
                }
                return chunkAccess;
            });
            default -> CompletableFuture.completedFuture(chunk);
        };
    }

    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender,
            StructureManager structureManager, ChunkAccess chunk) {
        if (unspawnedBiome == null) {
            return parent.createBiomes(randomState, blender, structureManager, chunk);
        } else {
            chunk.fillBiomesFromNoise((var1, var2, var3, var4) -> unspawnedBiome, randomState.sampler());
            return CompletableFuture.completedFuture(chunk);
        }
    }

    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState,
            ChunkAccess chunk) {
        if (generationType == EmptyGenerationType.Nether) {
            return;
        }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getSpawnHeight(LevelHeightAccessor heightAccessor) {
        return parent.getSpawnHeight(heightAccessor);
    }

    @Override
    public BiomeSource getBiomeSource() {
        return parent.getBiomeSource();
    }

    @Override
    public int getGenDepth() {
        return parent.getGenDepth();
    }

    @Override
    public WeightedList<MobSpawnSettings.SpawnerData> getMobsAt(Holder<Biome> biome,
            StructureManager structureManager, MobCategory mobCategory, BlockPos pos) {
        if (associatedLevel != null) {
            ChunkPos chunkPos = new ChunkPos(pos);
            if (SpawnChunkHelper.isEmptyChunk(associatedLevel, chunkPos)) {
                return WeightedList.of();
            }
        }
        return parent.getMobsAt(biome, structureManager, mobCategory, pos);
    }

    @Override
    public int getSeaLevel() {
        return parent.getSeaLevel();
    }

    @Override
    public int getMinY() {
        return parent.getMinY();
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        return parent.getBaseHeight(x, z, type, heightAccessor, randomState);
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor heightAccessor, RandomState randomState) {
        return parent.getBaseColumn(x, z, heightAccessor, randomState);
    }

    @Override
    public int getFirstFreeHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        return parent.getBaseHeight(x, z, type, heightAccessor, randomState);
    }

    @Override
    public int getFirstOccupiedHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor heightAccessor,
            RandomState randomState) {
        return parent.getBaseHeight(x, z, type, heightAccessor, randomState) - 1;
    }

    @Override
    public void addDebugScreenInfo(List<String> list, RandomState randomState, BlockPos pos) {
        parent.addDebugScreenInfo(list, randomState, pos);
    }

    protected List<StructurePlacement> getPlacementsForFeatureCompat(Holder<Structure> structure) {
        return ChunkGeneratorAccess.getPlacementsForFeature(parent, structure);
    }

    @Override
    @Nullable
    public com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>> findNearestMapStructure(
            net.minecraft.server.level.ServerLevel level, net.minecraft.core.HolderSet<Structure> structureTags,
            BlockPos pos, int searchRadius, boolean skipReferencedStructures) {
        net.minecraft.server.level.ServerLevel searchLevel = level;
        ResourceKey<Level> effectiveGenerationLevel = generationLevel;
        if (generationLevel != null) {
            effectiveGenerationLevel = com.ryvione.gatheringchunks.server.world.ChunkSpawnController
                    .get(level.getServer())
                    .getEffectiveGenerationLevel(level.dimension().identifier().toString(), generationLevel);
            net.minecraft.server.level.ServerLevel genLevel = level.getServer().getLevel(effectiveGenerationLevel);
            if (genLevel != null) {
                searchLevel = genLevel;
            } else {
                com.ryvione.gatheringchunks.common.GatheringChunksConstants.LOGGER.warn(
                        "[locate] Generation level {} not loaded/found - searching {} directly instead, which is "
                        + "usually empty for ungathered chunks and may return nothing or hang",
                        effectiveGenerationLevel.identifier(), level.dimension().identifier());
            }
        }
        boolean devMode = com.ryvione.gatheringchunks.server.DevMode.isEnabled();
        if (devMode) {
            com.ryvione.gatheringchunks.common.GatheringChunksConstants.LOGGER.info(
                    "[DevMode][locate] Searching {} (redirected from {}) from {} radius {} for {}",
                    searchLevel.dimension().identifier(), level.dimension().identifier(), pos, searchRadius, structureTags);
        }
        long startNanos = System.nanoTime();
        com.mojang.datafixers.util.Pair<BlockPos, Holder<Structure>> result =
                parent.findNearestMapStructure(searchLevel, structureTags, pos, searchRadius, skipReferencedStructures);
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs > 2000 && com.ryvione.gatheringchunks.config.ChunkByChunkConfig.get().getDebug().isPerformanceTimings()) {
            com.ryvione.gatheringchunks.common.GatheringChunksConstants.LOGGER.info(
                    "[Perf][locate] Search of {} from {} radius {} for {} took {}ms",
                    searchLevel.dimension().identifier(), pos, searchRadius, structureTags, elapsedMs);
        }
        if (devMode) {
            if (result == null) {
                com.ryvione.gatheringchunks.common.GatheringChunksConstants.LOGGER.info(
                        "[DevMode][locate] No match found within radius {} of {}", searchRadius, pos);
            } else {
                com.ryvione.gatheringchunks.common.GatheringChunksConstants.LOGGER.info(
                        "[DevMode][locate] Found {} at {} in {} (this position is only accurate in the visible "
                        + "dimension for direct-source spawning - random/themed sourcing will not line up)",
                        result.getSecond(), result.getFirst(), searchLevel.dimension().identifier());
            }
        }
        return result;
    }
}