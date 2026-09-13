package com.ryvione.gatheringchunks.server.world;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.util.ChangeDimensionHelper;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.GatheringChunksConfig;
import com.ryvione.gatheringchunks.server.world.spawning.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import com.ryvione.gatheringchunks.common.CommonRegistry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ChunkResult;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

public class ChunkSpawnController extends SavedData {

    private final MinecraftServer server;
    private final Deque<SpawnRequest> requests = new ConcurrentLinkedDeque<>();
    private final BiomeSearchManager biomeSearch;
    private final Map<ChunkPos, TerrainProfile> chunkTerrainProfiles = new ConcurrentHashMap<>();
    private final Map<String, Integer> maxChunks = new ConcurrentHashMap<>();
    private final Map<String, Integer> spawnedChunkCount = new ConcurrentHashMap<>();
    private final Map<String, ChunkPos> originChunks = new ConcurrentHashMap<>();
    private final Map<String, ResourceKey<Level>> baseGenerationLevels = new ConcurrentHashMap<>();
    private final Map<String, Set<Long>> spawnedChunkRecords = new ConcurrentHashMap<>();

    @Nullable private SpawnRequest currentSpawnRequest = null;
    private SpawnPhase phase = SpawnPhase.COPY_BIOMES;
    private boolean forcedTargetChunk = false;
    private int phaseTimer = 0;
    private int currentLayer;

    @Nullable private transient ServerLevel sourceLevel;
    @Nullable private transient ServerLevel targetLevel;
    @Nullable private transient CompletableFuture<ChunkResult<ChunkAccess>> sourceChunkFuture;

    private static final int PHASE_TIMEOUT_TICKS = 200;
    private static final int MAX_TERRAIN_PROFILES = 2000;
    private long terrainProfileCleanupCounter = 0;

    private enum SpawnPhase {
        COPY_BIOMES, SPAWN_BLOCKS, UPDATE_BARRIERS, SYNCH_CHUNKS, SPAWN_ENTITIES
    }

    private ChunkSpawnController(MinecraftServer server) {
        this.server = server;
        this.biomeSearch = new BiomeSearchManager(server);
    }

    public static ChunkSpawnController get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getChunkSource().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(
                        () -> new ChunkSpawnController(server),
                        (tag, provider) -> ChunkSpawnController.load(server, tag, provider),
                        DataFixTypes.LEVEL),
                "chunkspawncontroller");
    }

    private static ChunkSpawnController load(MinecraftServer server, CompoundTag tag, HolderLookup.Provider provider) {
        ChunkSpawnController c = new ChunkSpawnController(server);
        c.loadInternal(tag, provider);
        return c;
    }

    private void loadInternal(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag requestsTag = tag.getList("requests", ListTag.TAG_COMPOUND);
        for (int i = 0; i < requestsTag.size(); i++) {
            requests.add(SpawnRequest.load(requestsTag.getCompound(i)));
        }
        if (tag.contains("currentRequest")) {
            currentSpawnRequest = SpawnRequest.load(tag.getCompound("currentRequest"));
            phase = SpawnPhase.valueOf(tag.getString("phase"));
            forcedTargetChunk = tag.getBoolean("forcedTargetChunk");
            currentLayer = tag.getInt("currentLayer");
            sourceLevel = server.getLevel(currentSpawnRequest.sourceLevel());
            targetLevel = server.getLevel(currentSpawnRequest.targetLevel());
            sourceChunkFuture = sourceLevel.getChunkSource().getChunkFuture(
                    currentSpawnRequest.sourceChunkPos().x,
                    currentSpawnRequest.sourceChunkPos().z,
                    ChunkStatus.FULL, true);
        }
        if (tag.contains("maxChunksMap")) {
            CompoundTag map = tag.getCompound("maxChunksMap");
            for (String key : map.getAllKeys()) maxChunks.put(key, map.getInt(key));
        } else if (tag.contains("maxChunks")) {
            maxChunks.put("minecraft:overworld", tag.getInt("maxChunks"));
        }
        if (tag.contains("spawnedChunkCountMap")) {
            CompoundTag map = tag.getCompound("spawnedChunkCountMap");
            for (String key : map.getAllKeys()) spawnedChunkCount.put(key, map.getInt(key));
        } else if (tag.contains("spawnedChunkCount")) {
            spawnedChunkCount.put("minecraft:overworld", tag.getInt("spawnedChunkCount"));
        }
        if (tag.contains("terrainProfiles")) {
            CompoundTag profilesTag = tag.getCompound("terrainProfiles");
            for (String key : profilesTag.getAllKeys()) {
                String[] parts = key.split(",");
                if (parts.length == 2) {
                    try {
                        ChunkPos pos = new ChunkPos(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                        chunkTerrainProfiles.put(pos, TerrainProfile.load(profilesTag.getCompound(key)));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        if (tag.contains("originChunksMap")) {
            CompoundTag map = tag.getCompound("originChunksMap");
            for (String key : map.getAllKeys()) originChunks.put(key, new ChunkPos(map.getLong(key)));
        }
        if (tag.contains("baseGenerationLevelsMap")) {
            CompoundTag map = tag.getCompound("baseGenerationLevelsMap");
            for (String key : map.getAllKeys()) {
                baseGenerationLevels.put(key, ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                        ResourceLocation.parse(map.getString(key))));
            }
        }
        if (tag.contains("spawnedChunkRecordsMap")) {
            CompoundTag map = tag.getCompound("spawnedChunkRecordsMap");
            for (String key : map.getAllKeys()) {
                Set<Long> records = ConcurrentHashMap.newKeySet();
                for (long value : map.getLongArray(key)) records.add(value);
                spawnedChunkRecords.put(key, records);
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        ListTag requestsTag = new ListTag();
        for (SpawnRequest r : requests) requestsTag.add(r.save());
        tag.put("requests", requestsTag);

        if (currentSpawnRequest != null) {
            tag.put("currentRequest", currentSpawnRequest.save());
            tag.putString("phase", phase.name());
            tag.putBoolean("forcedTargetChunk", forcedTargetChunk);
            tag.putInt("currentLayer", currentLayer);
        }

        CompoundTag maxChunksMap = new CompoundTag();
        maxChunks.forEach(maxChunksMap::putInt);
        tag.put("maxChunksMap", maxChunksMap);

        CompoundTag spawnedCountMap = new CompoundTag();
        spawnedChunkCount.forEach(spawnedCountMap::putInt);
        tag.put("spawnedChunkCountMap", spawnedCountMap);

        CompoundTag profilesTag = new CompoundTag();
        chunkTerrainProfiles.forEach((pos, profile) -> profilesTag.put(pos.x + "," + pos.z, profile.save()));
        tag.put("terrainProfiles", profilesTag);

        CompoundTag originsMap = new CompoundTag();
        originChunks.forEach((dim, pos) -> originsMap.putLong(dim, pos.toLong()));
        tag.put("originChunksMap", originsMap);

        CompoundTag baseGenMap = new CompoundTag();
        baseGenerationLevels.forEach((dim, key) -> baseGenMap.putString(dim, key.location().toString()));
        tag.put("baseGenerationLevelsMap", baseGenMap);

        CompoundTag spawnedRecordsMap = new CompoundTag();
        spawnedChunkRecords.forEach((dim, records) -> {
            long[] array = new long[records.size()];
            int i = 0;
            for (long value : records) array[i++] = value;
            spawnedRecordsMap.putLongArray(dim, array);
        });
        tag.put("spawnedChunkRecordsMap", spawnedRecordsMap);

        return tag;
    }

    public void tick() {
        terrainProfileCleanupCounter++;
        if (terrainProfileCleanupCounter % 6000 == 0 && chunkTerrainProfiles.size() > MAX_TERRAIN_PROFILES) {
            int toRemove = chunkTerrainProfiles.size() - MAX_TERRAIN_PROFILES / 2;
            Iterator<ChunkPos> profileIt = chunkTerrainProfiles.keySet().iterator();
            int removed = 0;
            while (profileIt.hasNext() && removed < toRemove) { profileIt.next(); profileIt.remove(); removed++; }
            GatheringChunksConstants.LOGGER.debug("[ChunkSpawnController] Trimmed terrain profile cache from {} to {} entries",
                    chunkTerrainProfiles.size() + removed, chunkTerrainProfiles.size());
        }

        processPendingSearches();
        processSpawnRequest();
    }

    private void processPendingSearches() {
        Iterator<BiomeSearchManager.PendingSearch> it = biomeSearch.getPendingSearches().iterator();
        int totalAttemptsThisTick = 0;
        int maxAttemptsPerTick = 1;

        while (it.hasNext() && totalAttemptsThisTick < maxAttemptsPerTick) {
            BiomeSearchManager.PendingSearch search = it.next();
            ResourceKey<Level> sourceLevelKey = ((SkyChunkGenerator) search.level.getChunkSource().getGenerator())
                    .getBiomeDimension(search.biomeTheme);
            ServerLevel sourceLevelInstance = server.getLevel(sourceLevelKey);

            if (sourceLevelInstance == null) { it.remove(); continue; }

            totalAttemptsThisTick++;
            search.attempts++;

            ChunkPos candidatePos = null;

            if (search.attempts % 5 == 1) {
                BlockPos centerPos = new BlockPos(
                        search.seedFinder.nextInt(-500000, 500000), 64,
                        search.seedFinder.nextInt(-500000, 500000));
                var randomState = sourceLevelInstance.getChunkSource().randomState();
                var result = sourceLevelInstance.getChunkSource().getGenerator().getBiomeSource()
                        .findBiomeHorizontal(
                                centerPos.getX(), centerPos.getY(), centerPos.getZ(),
                                2000, 64,
                                holder -> biomeSearch.doesBiomeMatchTheme(holder, search.biomeTheme),
                                net.minecraft.util.RandomSource.create(search.seedFinder.nextLong()),
                                true,
                                randomState.sampler());
                if (result != null) candidatePos = new ChunkPos(result.getFirst());
            }

            if (candidatePos == null) {
                if (!search.biomeTheme.isEmpty()) {
                    if (search.attempts >= 1000) handleFailedSearch(search, it);
                    continue;
                }
                candidatePos = new ChunkPos(
                        search.seedFinder.nextInt(-500000, 500000),
                        search.seedFinder.nextInt(-500000, 500000));
            }

            boolean alreadyLoaded = sourceLevelInstance.hasChunk(candidatePos.x, candidatePos.z);
            if (!alreadyLoaded) sourceLevelInstance.setChunkForced(candidatePos.x, candidatePos.z, true);

            try {
                int quartYcheck = search.biomeTheme.equals("ocean") ? 8 : 16;
                int quartXcheck = candidatePos.getMiddleBlockX() >> 2;
                int quartZcheck = candidatePos.getMiddleBlockZ() >> 2;
                Holder<Biome> holder = sourceLevelInstance.getChunkSource()
                        .getGenerator().getBiomeSource()
                        .getNoiseBiome(quartXcheck, quartYcheck, quartZcheck, null);
                
                String candidateTheme = BiomeCoordinateCache.get(server).getBiomeTheme(holder);
                GatheringChunksConstants.LOGGER.debug("[Search] Candidate at {} has biome theme '{}' (searching for '{}')", 
                        candidatePos, candidateTheme, search.biomeTheme);

                if (!search.biomeTheme.isEmpty() && !biomeSearch.doesBiomeMatchTheme(holder, search.biomeTheme)) continue;

                if (search.targetProfile != null) {
                    TerrainProfile candidateProfile = biomeSearch.analyzeChunkTerrain(sourceLevelInstance, candidatePos, search.biomeTheme);
                    if (candidateProfile != null && biomeSearch.terrainsMatch(search.targetProfile, candidateProfile, search.targetPos, candidatePos)) {
                        GatheringChunksConstants.LOGGER.info("[Search] Match found with Terrain Profile at {} for theme '{}'", candidatePos, search.biomeTheme);
                        requests.add(new SpawnRequest(search.targetPos, search.level.dimension(), candidatePos,
                                sourceLevelKey, search.immediate, search.overwrite, false, search.playerUUID));
                        chunkTerrainProfiles.put(search.targetPos, candidateProfile);
                        biomeSearch.updatePreScanCache(sourceLevelInstance, candidatePos, search.biomeTheme);
                        setDirty();
                        it.remove();
                    }
                } else {
                    GatheringChunksConstants.LOGGER.info("[Search] Match found (direct) at {} for theme '{}'", candidatePos, search.biomeTheme);
                    requests.add(new SpawnRequest(search.targetPos, search.level.dimension(), candidatePos,
                            sourceLevelKey, search.immediate, search.overwrite, false, search.playerUUID));
                    TerrainProfile prof = biomeSearch.analyzeChunkTerrain(sourceLevelInstance, candidatePos, search.biomeTheme);
                    if (prof != null) {
                        chunkTerrainProfiles.put(search.targetPos, prof);
                        biomeSearch.updatePreScanCache(sourceLevelInstance, candidatePos, search.biomeTheme);
                        setDirty();
                    }
                    it.remove();
                }
            } finally {
                if (!alreadyLoaded) sourceLevelInstance.setChunkForced(candidatePos.x, candidatePos.z, false);
            }

            if (search.attempts >= 2000) handleFailedSearch(search, it);
        }
    }

    private void handleFailedSearch(BiomeSearchManager.PendingSearch search, Iterator<BiomeSearchManager.PendingSearch> it) {
        GatheringChunksConstants.LOGGER.warn("Async search for '{}' failed after 2000 attempts at {}", search.biomeTheme, search.targetPos);
        if (search.playerUUID != null) {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(search.playerUUID);
            if (player != null) {
                player.sendSystemMessage(Component.literal(
                        "§c[ChunkByChunk] §6Oops! §eFailed to find a matching biome for "
                        + (search.biomeTheme.isEmpty() ? "this chunk" : "§b" + search.biomeTheme)
                        + " after 1000 attempts. Spawner returned."));
                net.minecraft.world.level.block.Block spawnerBlock = CommonRegistry.SPAWN_CHUNK_BLOCK;
                if (search.biomeTheme.equals("unstable")) {
                    spawnerBlock = CommonRegistry.UNSTABLE_SPAWN_CHUNK_BLOCK;
                } else if (!search.biomeTheme.isEmpty()) {
                    spawnerBlock = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                            ResourceLocation.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID,
                                    search.biomeTheme + GatheringChunksConstants.BIOME_CHUNK_BLOCK_SUFFIX));
                }
                ItemStack stack = new ItemStack(spawnerBlock);
                if (!player.getInventory().add(stack)) player.drop(stack, false);
            }
        }
        it.remove();
    }

    private void processSpawnRequest() {
        if (currentSpawnRequest != null) {
            phaseTimer++;
            if (phaseTimer > PHASE_TIMEOUT_TICKS) {
                GatheringChunksConstants.LOGGER.warn("[ChunkSpawnController] Phase {} timed out after {} ticks for chunk {}.",
                        phase, PHASE_TIMEOUT_TICKS, currentSpawnRequest.targetChunkPos());
                if (phase == SpawnPhase.SPAWN_ENTITIES || phase == SpawnPhase.SYNCH_CHUNKS || phase == SpawnPhase.UPDATE_BARRIERS) {
                    completeSpawnRequest();
                } else {
                    currentSpawnRequest = null;
                }
                return;
            }

            if (!sourceChunkFuture.isDone() && phase != SpawnPhase.SPAWN_ENTITIES) return;

            switch (phase) {
                case COPY_BIOMES -> {
                    ChunkAccess sourceChunk = sourceChunkFuture.getNow(ChunkResult.error("Chunk not loaded")).orElse(null);
                    if (sourceChunk != null) {
                        ChunkCopyHelper.updateBiomes(sourceLevel, sourceChunk, targetLevel,
                                targetLevel.getChunk(currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z),
                                currentSpawnRequest.targetChunkPos());
                    }
                    phase = SpawnPhase.SPAWN_BLOCKS;
                    currentLayer = targetLevel.getMinBuildHeight();
                    setDirty();
                }
                case SPAWN_BLOCKS -> {
                    int minLayer = currentLayer;
                    int maxLayer = Math.min(
                            currentLayer + ChunkByChunkConfig.get().getGeneration().getChunkLayerSpawnRate(),
                            targetLevel.getMaxBuildHeight() + 1);
                    ChunkCopyHelper.copyBlocks(sourceLevel, currentSpawnRequest.sourceChunkPos(),
                            targetLevel, currentSpawnRequest.targetChunkPos(), minLayer, maxLayer, currentSpawnRequest.overwrite());
                    if (maxLayer > targetLevel.getMaxBuildHeight()) {
                        ChunkCopyHelper.copyStructureData(sourceLevel, sourceChunkFuture.getNow(ChunkResult.error("Chunk not loaded")).orElse(null),
                                targetLevel, targetLevel.getChunk(currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z),
                                currentSpawnRequest.sourceChunkPos(), currentSpawnRequest.targetChunkPos());
                        ChunkCopyHelper.markSourceDepleted(sourceLevel, currentSpawnRequest.sourceChunkPos());
                        TreePlacementHandler.ensureTreesInChunk(targetLevel, currentSpawnRequest.targetChunkPos());
                        if (ChunkByChunkConfig.get().getDifficulty().spawnNewChunkChest()
                                && !ChunkByChunkConfig.get().getDifficulty().spawnChestInInitialChunkOnly()) {
                            SpawnChunkHelper.createNextSpawner(targetLevel, currentSpawnRequest.targetChunkPos());
                        }
                        if (targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
                            if (generator.getGenerationType() == SkyChunkGenerator.EmptyGenerationType.Nether
                                    || generator.getGenerationType() == SkyChunkGenerator.EmptyGenerationType.Sealed) {
                                generator.markChunkSpawned(currentSpawnRequest.targetChunkPos().toLong());
                            }
                            recordSpawnedChunk(targetLevel, currentSpawnRequest.targetChunkPos());
                        }
                        phase = SpawnPhase.UPDATE_BARRIERS;
                    } else {
                        currentLayer = maxLayer;
                    }
                    setDirty();
                }
                case UPDATE_BARRIERS -> {
                    ChunkBarrierManager.updateBarriersAfterChunkSpawn(targetLevel,
                            currentSpawnRequest.sourceChunkPos(), currentSpawnRequest.targetChunkPos());
                    ChunkCopyHelper.triggerLightingUpdate(targetLevel, currentSpawnRequest.targetChunkPos());
                    phase = SpawnPhase.SYNCH_CHUNKS;
                    setDirty();
                }
                case SYNCH_CHUNKS -> {
                    synchChunks();
                    phase = SpawnPhase.SPAWN_ENTITIES;
                    phaseTimer = 0;
                    setDirty();
                }
                case SPAWN_ENTITIES -> {
                    if (sourceLevel.areEntitiesLoaded(currentSpawnRequest.sourceChunkPos().toLong()) || phaseTimer > 100) {
                        spawnChunkEntities();
                        completeSpawnRequest();
                        setDirty();
                    }
                }
            }
        } else if (!requests.isEmpty()) {
            currentSpawnRequest = requests.poll();
            targetLevel = server.getLevel(currentSpawnRequest.targetLevel());
            sourceLevel = server.getLevel(currentSpawnRequest.sourceLevel());
            forcedTargetChunk = targetLevel.setChunkForced(
                    currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z, true);
            sourceLevel.setChunkForced(
                    currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z, true);
            sourceChunkFuture = sourceLevel.getChunkSource().getChunkFuture(
                    currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z,
                    ChunkStatus.FULL, true);
            phase = currentSpawnRequest.immediate() ? SpawnPhase.UPDATE_BARRIERS : SpawnPhase.COPY_BIOMES;
            GatheringChunksConstants.LOGGER.info("Spawning chunk {} in {}", currentSpawnRequest.targetChunkPos(), targetLevel.dimension());
            phaseTimer = 0;
            setDirty();
        }
    }

    private void spawnChunkEntities() {
        AABB boundingBox = new AABB(
                currentSpawnRequest.sourceChunkPos().getMinBlockX(), sourceLevel.getMinBuildHeight(),
                currentSpawnRequest.sourceChunkPos().getMinBlockZ(),
                currentSpawnRequest.sourceChunkPos().getMaxBlockX(), sourceLevel.getMaxBuildHeight(),
                currentSpawnRequest.sourceChunkPos().getMaxBlockZ());
        List<Entity> entities = sourceLevel.getEntitiesOfClass(Entity.class, boundingBox, x -> true);
        for (Entity e : entities) {
            Vec3 pos = new Vec3(
                    e.getX() + (currentSpawnRequest.targetChunkPos().x - currentSpawnRequest.sourceChunkPos().x) * 16,
                    e.getY(),
                    e.getZ() + (currentSpawnRequest.targetChunkPos().z - currentSpawnRequest.sourceChunkPos().z) * 16);
            Entity movedEntity = ChangeDimensionHelper.changeDimension(e, targetLevel, pos);
            if (movedEntity != null) movedEntity.setPos(pos);
        }
    }

    private void completeSpawnRequest() {
        if (!forcedTargetChunk) return;
        targetLevel.setChunkForced(currentSpawnRequest.targetChunkPos().x, currentSpawnRequest.targetChunkPos().z, false);
        sourceLevel.setChunkForced(currentSpawnRequest.sourceChunkPos().x, currentSpawnRequest.sourceChunkPos().z, false);

        if (currentSpawnRequest.isMenderRequest()) {
            String dim = targetLevel.dimension().location().toString();
            if (!originChunks.containsKey(dim)) {
                originChunks.put(dim, currentSpawnRequest.targetChunkPos());
                if (targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator targetGenerator
                        && targetGenerator.getGenerationLevel() != null) {
                    baseGenerationLevels.put(dim, targetGenerator.getGenerationLevel());
                }
                setDirty();
                GatheringChunksConstants.LOGGER.info("[Sync] Set origin for {} to {}, base generation level {}", 
                        dim, currentSpawnRequest.targetChunkPos(), currentSpawnRequest.sourceLevel().location());
            }
            if (targetLevel.dimension().equals(Level.OVERWORLD)) {
                ChunkPos initialPos = currentSpawnRequest.targetChunkPos();
                net.minecraft.world.level.storage.ServerLevelData levelData =
                        (net.minecraft.world.level.storage.ServerLevelData) targetLevel.getLevelData();
                if (SpawnChunkHelper.isWaterSurfaceChunk(targetLevel, initialPos)) {
                    GatheringChunksConstants.LOGGER.info("[InitialSpawn] Water-surface chunk detected at {} - building island", initialPos);
                    BlockPos islandTop = SpawnChunkHelper.buildIslandOnWaterChunk(targetLevel, initialPos);
                    if (islandTop != null) {
                        levelData.setSpawn(islandTop, levelData.getSpawnAngle());
                        GatheringChunksConstants.LOGGER.info("[InitialSpawn] Island built, spawn set to {}", islandTop);
                    }
                } else {
                    int spawnX = initialPos.getMiddleBlockX();
                    int spawnZ = initialPos.getMiddleBlockZ();
                    LevelChunk placedChunk = targetLevel.getChunkSource().getChunkNow(initialPos.x, initialPos.z);
                    if (placedChunk != null) {
                        int surfaceY = com.ryvione.gatheringchunks.common.util.ChunkUtil.getSafeSpawnHeight(placedChunk, spawnX, spawnZ);
                        BlockPos correctedSpawn = new BlockPos(spawnX, surfaceY, spawnZ);
                        levelData.setSpawn(correctedSpawn, levelData.getSpawnAngle());
                        GatheringChunksConstants.LOGGER.info("[InitialSpawn] Corrected spawn Y to surface: {}", correctedSpawn);
                    }
                }
            }
            ChunkEngineManager.get(server).notifyInitialChunkSpawned(targetLevel, currentSpawnRequest.targetChunkPos());
        } else {
            ChunkEngineManager.get(server).notifyChunkSpawned(targetLevel, currentSpawnRequest.targetChunkPos());
        }

        if (!currentSpawnRequest.overwrite()) {
            String dim = targetLevel.dimension().location().toString();
            spawnedChunkCount.put(dim, spawnedChunkCount.getOrDefault(dim, 0) + 1);
            setDirty();
        }
        currentSpawnRequest = null;
    }

    private void synchChunks() {
        if (!ChunkByChunkConfig.get().getGeneration().isSynchNether()) return;
        if (!(targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator)) return;

        for (ResourceKey<Level> synchLevelId : generator.getSynchedLevels()) {
            ServerLevel synchLevel = server.getLevel(synchLevelId);
            if (synchLevel == null) {
                GatheringChunksConstants.LOGGER.warn("[Sync] Could not sync with {}; level is null", synchLevelId.location());
                continue;
            }
            if (!(synchLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator synchGenerator)) {
                GatheringChunksConstants.LOGGER.warn("[Sync] Could not sync with {}; not a sky dimension", synchLevelId.location());
                continue;
            }
            ChunkPos mappedChunk = getSyncedChunkPosWithScale(targetLevel, synchLevel, currentSpawnRequest.targetChunkPos());
            if (SpawnChunkHelper.isEmptyChunk(synchLevel, mappedChunk)) {
                GatheringChunksConstants.LOGGER.info("[Sync] Scheduling deferred sync spawn for chunk {} in {}", mappedChunk, synchLevelId.location());
                final ChunkPos finalMappedChunk = mappedChunk;
                final ResourceKey<Level> finalSynchLevelId = synchLevelId;
                final ResourceKey<Level> finalGenerationLevel = synchGenerator.getGenerationLevel();
                final boolean finalOverwrite = currentSpawnRequest.overwrite();
                server.tell(new TickTask(server.getTickCount() + 1, () ->
                        request(finalMappedChunk, finalSynchLevelId, finalMappedChunk, finalGenerationLevel, false, finalOverwrite, false, null)
                ));
            }
        }
    }

    public boolean request(ServerLevel level, String biomeTheme, boolean random, BlockPos targetPos) {
        return request(level, biomeTheme, random, targetPos, false, false, null);
    }

    public boolean request(ServerLevel level, String biomeTheme, boolean random, BlockPos targetPos,
            boolean immediate, boolean overwrite) {
        return request(level, biomeTheme, random, targetPos, immediate, overwrite, null);
    }

    public boolean request(ServerLevel level, String biomeTheme, boolean random, BlockPos targetPos,
            boolean immediate, boolean overwrite, @Nullable UUID playerUUID) {
        ChunkPos targetChunkPos = new ChunkPos(targetPos);
        boolean isEmptyChunk = SpawnChunkHelper.isEmptyChunk(level, targetChunkPos);
        if (!isEmptyChunk && !overwrite && level.getChunkSource().getGenerator() instanceof SkyChunkGenerator sealedCheckGenerator
                && sealedCheckGenerator.getGenerationType() == SkyChunkGenerator.EmptyGenerationType.Sealed
                && !isChunkSpawned(level, targetChunkPos)) {
            isEmptyChunk = true;
        }
        boolean canSpawn = isEmptyChunk || overwrite;

        boolean experimentalLimit = ChunkByChunkConfig.get().getDifficulty().isExperimentalChunkLimit();
        String dim = level.dimension().location().toString();
        int max = getMaxChunks(dim);
        int current = getSpawnedChunkCount(dim);

        if (experimentalLimit && !overwrite && current >= max) {
            if (playerUUID != null) {
                Player player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("gatheringchunks.message.chunk_limit_reached", current, max));
                    player.getInventory().add(new ItemStack(Items.BARRIER));
                }
            }
            GatheringChunksConstants.LOGGER.info("Spawn prevented: Chunk Limit Reached ({}/{})", current, max);
            return false;
        }

        if (!canSpawn) {
            if (playerUUID != null) {
                Player player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("gatheringchunks.message.chunk_already_generated"));
                    player.getInventory().add(new ItemStack(Items.BARRIER));
                }
            }
            GatheringChunksConstants.LOGGER.info("Spawn prevented: target chunk already generated");
            return false;
        }

        if (!isValidForLevel(level, biomeTheme, random)
                || !(level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator)) {
            if (playerUUID != null) {
                Player player = server.getPlayerList().getPlayer(playerUUID);
                if (player != null) {
                    player.sendSystemMessage(Component.translatable("gatheringchunks.message.invalid_level_or_generator"));
                    player.getInventory().add(new ItemStack(Items.BARRIER));
                }
            }
            return false;
        }

        if (random) {
            Random rng = new Random(targetPos.asLong() ^ System.nanoTime());
            ChunkPos sourceChunkPos = new ChunkPos(
                    rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE),
                    rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE));
            ResourceKey<Level> genLevel = generator.getGenerationLevel();
            return request(targetChunkPos, level.dimension(), sourceChunkPos, genLevel,
                    immediate, overwrite, false, playerUUID);
        }

        if (!biomeTheme.isEmpty()) {
            ResourceKey<Level> sourceLevelKey = generator.getBiomeDimension(biomeTheme);
            if (sourceLevelKey == null) {
                GatheringChunksConstants.LOGGER.warn("Biome theme '{}' has no associated dimension!", biomeTheme);
                if (playerUUID != null) {
                    Player player = server.getPlayerList().getPlayer(playerUUID);
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable("gatheringchunks.message.biome_theme_not_found", biomeTheme));
                        player.getInventory().add(new ItemStack(Items.BARRIER));
                    }
                }
                return false;
            }

            ServerLevel sourceLevelInstance = server.getLevel(sourceLevelKey);
            if (sourceLevelInstance == null) {
                if (playerUUID != null) {
                    Player player = server.getPlayerList().getPlayer(playerUUID);
                    if (player != null) {
                        player.sendSystemMessage(Component.translatable("gatheringchunks.message.source_level_not_found", sourceLevelKey.location()));
                        player.getInventory().add(new ItemStack(Items.BARRIER));
                    }
                }
                return false;
            }

            ChunkPos sourceChunkPos = targetChunkPos;
            if (ChunkByChunkConfig.get().getGatheringChunksConfig().getThemedSpawnerSource()
                    == GatheringChunksConfig.ThemedSpawnerSource.RANDOM) {
                Random rng = new Random(targetChunkPos.toLong() ^ System.nanoTime());
                sourceChunkPos = new ChunkPos(
                        rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE),
                        rng.nextInt(Short.MIN_VALUE, Short.MAX_VALUE));
                GatheringChunksConstants.LOGGER.info("Using RANDOM source {} for theme '{}' from {}",
                        sourceChunkPos, biomeTheme, sourceLevelKey);
            } else {
                GatheringChunksConstants.LOGGER.info("Using DIRECT source for '{}' at {}", biomeTheme, targetChunkPos);
            }
            registerChunkTheme(targetChunkPos, biomeTheme);
            return request(targetChunkPos, level.dimension(), sourceChunkPos, sourceLevelKey, immediate, overwrite, false, playerUUID);
        }

        ChunkPos originTarget = originChunks.get(dim);
        if (originTarget != null) {
            TerrainProfile originProfile = chunkTerrainProfiles.get(originTarget);
            if (originProfile != null && originProfile.sourcePos != null
                    && originProfile.sourcePos.toLong() != ChunkPos.INVALID_CHUNK_POS) {
                int dx = targetChunkPos.x - originTarget.x;
                int dz = targetChunkPos.z - originTarget.z;
                ChunkPos lockedSource = new ChunkPos(originProfile.sourcePos.x + dx, originProfile.sourcePos.z + dz);
                GatheringChunksConstants.LOGGER.info("Using LOCKED source {} for basic spawner at {} (origin-locked)", lockedSource, targetChunkPos);
                registerChunkTheme(targetChunkPos, "");
                ResourceKey<Level> genLevel = generator.getGenerationLevel();
                return request(targetChunkPos, level.dimension(), lockedSource, genLevel, immediate, overwrite, false, playerUUID);
            }
        }

        GatheringChunksConstants.LOGGER.info("Using DIRECT source for basic spawner at {}", targetChunkPos);
        registerChunkTheme(targetChunkPos, "");
        ResourceKey<Level> genLevel = generator.getGenerationLevel();
        return request(targetChunkPos, level.dimension(), targetChunkPos, genLevel, immediate, overwrite, false, playerUUID);
    }

    public void registerChunkTheme(ChunkPos targetChunkPos, String biomeTheme) {
        chunkTerrainProfiles.put(targetChunkPos, new TerrainProfile(
                new int[16], new int[16], new int[16], new int[16],
                new String[8], new String[8], new String[8], new String[8],
                64, 0, biomeTheme, targetChunkPos));
        setDirty();
    }

    public String getChunkBiomeTheme(ChunkPos chunkPos) {
        TerrainProfile profile = chunkTerrainProfiles.get(chunkPos);
        return profile != null ? profile.biomeTheme : null;
    }

    public boolean request(ChunkPos targetChunkPos, ResourceKey<Level> targetLevel, ChunkPos sourceChunkPos,
            ResourceKey<Level> sourceLevel, boolean immediate, boolean overwrite) {
        return request(targetChunkPos, targetLevel, sourceChunkPos, sourceLevel, immediate, overwrite, false, null);
    }

    public boolean request(ChunkPos targetChunkPos, ResourceKey<Level> targetLevel, ChunkPos sourceChunkPos,
            ResourceKey<Level> sourceLevel, boolean immediate, boolean overwrite,
            boolean isMenderRequest, @Nullable UUID playerUUID) {
        SpawnRequest spawnRequest = new SpawnRequest(targetChunkPos, targetLevel, sourceChunkPos, sourceLevel,
                immediate, overwrite, isMenderRequest, playerUUID);

        if (spawnRequest.equals(currentSpawnRequest)) return false;
        for (SpawnRequest r : requests) if (r.equals(spawnRequest)) return false;

        if (immediate) {
            ServerLevel toLevel = server.getLevel(targetLevel);
            ServerLevel fromLevel = server.getLevel(sourceLevel);
            LevelChunk toChunk = toLevel.getChunk(targetChunkPos.x, targetChunkPos.z);
            LevelChunk fromChunk = fromLevel.getChunk(sourceChunkPos.x, sourceChunkPos.z);
            ChunkCopyHelper.updateBiomes(fromLevel, fromChunk, toLevel, toChunk, targetChunkPos);
            biomeSearch.updatePreScanCache(fromLevel, sourceChunkPos, "unknown");
            ChunkCopyHelper.copyBlocks(fromLevel, spawnRequest.sourceChunkPos(), toLevel, spawnRequest.targetChunkPos(),
                    toLevel.getMinBuildHeight(), toLevel.getMaxBuildHeight() + 1, overwrite);
            ChunkCopyHelper.copyStructureData(fromLevel, fromChunk, toLevel, toChunk,
                    spawnRequest.sourceChunkPos(), spawnRequest.targetChunkPos());
            requests.addFirst(spawnRequest);
        } else {
            requests.add(spawnRequest);
        }
        setDirty();
        return true;
    }

    public void checkAndSyncExistingChunks() {
        if (!ChunkByChunkConfig.get().getGeneration().isSynchNether()) return;
        for (ServerLevel level : server.getAllLevels()) {
            if (!(level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator)) continue;
            for (ResourceKey<Level> synchLevelId : generator.getSynchedLevels()) {
                ServerLevel synchLevel = server.getLevel(synchLevelId);
                if (synchLevel == null) continue;
                if (!(synchLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator synchGenerator)) continue;
                String dim = level.dimension().location().toString();
                ChunkPos origin = originChunks.get(dim);
                if (origin == null) continue;
                int spawnedCount = spawnedChunkCount.getOrDefault(dim, 0);
                if (spawnedCount == 0) continue;
                int radius = (int) Math.ceil(Math.sqrt(spawnedCount)) + 2;
                int delay = 1;
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        ChunkPos sourceChunk = new ChunkPos(origin.x + dx, origin.z + dz);
                        if (SpawnChunkHelper.isEmptyChunk(level, sourceChunk)) continue;
                        ChunkPos mappedChunk = getSyncedChunkPosWithScale(level, synchLevel, sourceChunk);
                        if (SpawnChunkHelper.isEmptyChunk(synchLevel, mappedChunk)) {
                            final ChunkPos finalMappedChunk = mappedChunk;
                            final ResourceKey<Level> finalSynchLevelId = synchLevelId;
                            final ResourceKey<Level> finalGenerationLevel = synchGenerator.getGenerationLevel();
                            final int tickDelay = delay++;
                            server.tell(new TickTask(server.getTickCount() + tickDelay, () ->
                                    request(finalMappedChunk, finalSynchLevelId, finalMappedChunk, finalGenerationLevel, false, false, false, null)
                            ));
                        }
                    }
                }
            }
        }
    }

    public void syncErase(ServerLevel level, ChunkPos chunkPos) {
        if (!ChunkByChunkConfig.get().getGeneration().isSynchNether()) return;
        if (!(level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator)) return;
        for (ResourceKey<Level> synchLevelId : generator.getSynchedLevels()) {
            ServerLevel synchLevel = server.getLevel(synchLevelId);
            if (synchLevel != null) {
                ChunkPos targetChunk = getSyncedChunkPosWithScale(level, synchLevel, chunkPos);
                GatheringChunksConstants.LOGGER.info("[Sync] Triggering sync erase for chunk {} in {}", targetChunk, synchLevelId.location());
                com.ryvione.gatheringchunks.common.blocks.ChunkEraserBlock.eraseChunkDirectly(synchLevel, targetChunk);
            }
        }
    }

    public ChunkPos getSyncedChunkPos(ServerLevel source, ServerLevel target, ChunkPos sourcePos) {
        String sourceDim = source.dimension().location().toString();
        String targetDim = target.dimension().location().toString();
        ChunkPos sourceOrigin = originChunks.get(sourceDim);
        ChunkPos targetOrigin = originChunks.get(targetDim);
        if (sourceOrigin != null && targetOrigin != null) {
            return new ChunkPos(targetOrigin.x + (sourcePos.x - sourceOrigin.x), targetOrigin.z + (sourcePos.z - sourceOrigin.z));
        }
        return sourcePos;
    }

    public ChunkPos getSyncedChunkPosWithScale(ServerLevel source, ServerLevel target, ChunkPos sourcePos) {
        double sourceScale = source.dimensionType().coordinateScale();
        double targetScale = target.dimensionType().coordinateScale();
        String sourceDim = source.dimension().location().toString();
        String targetDim = target.dimension().location().toString();
        ChunkPos sourceOrigin = originChunks.get(sourceDim);
        ChunkPos targetOrigin = originChunks.get(targetDim);
        if (sourceOrigin != null && targetOrigin != null) {
            double scaleFactor = sourceScale / targetScale;
            int mappedDx = (int) Math.round((sourcePos.x - sourceOrigin.x) * scaleFactor);
            int mappedDz = (int) Math.round((sourcePos.z - sourceOrigin.z) * scaleFactor);
            return new ChunkPos(targetOrigin.x + mappedDx, targetOrigin.z + mappedDz);
        }
        if (Math.abs(sourceScale - targetScale) > 0.001) {
            double scaleFactor = sourceScale / targetScale;
            return new ChunkPos((int) Math.round(sourcePos.x * scaleFactor), (int) Math.round(sourcePos.z * scaleFactor));
        }
        return sourcePos;
    }

    public boolean isValidForLevel(ServerLevel level, String biomeTheme, boolean random) {
        if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            if (!biomeTheme.isEmpty()) return generator.getBiomeDimension(biomeTheme) != null;
            else if (random) return generator.isRandomChunkSpawnerAllowed();
            else return generator.isChunkSpawnerAllowed();
        }
        return false;
    }

    public boolean isBusy() {
        return currentSpawnRequest != null || !requests.isEmpty();
    }

    public int getMaxChunks(String dimensionId) {
        return maxChunks.getOrDefault(dimensionId, dimensionId.equals("minecraft:the_nether") ? 1 : 4);
    }

    public void setMaxChunks(String dimensionId, int max) {
        maxChunks.put(dimensionId, max);
        setDirty();
    }

    public int getSpawnedChunkCount(String dimensionId) {
        return spawnedChunkCount.getOrDefault(dimensionId, 0);
    }

    public void decreaseSpawnedChunkCount(String dimensionId) {
        int val = spawnedChunkCount.getOrDefault(dimensionId, 0);
        if (val > 0) { spawnedChunkCount.put(dimensionId, val - 1); setDirty(); }
    }

    public void setSpawnedChunkCount(String dimensionId, int count) {
        spawnedChunkCount.put(dimensionId, count);
        setDirty();
    }

    public ChunkPos getOriginChunk(String dimensionId) {
        return originChunks.get(dimensionId);
    }

    public void recordSpawnedChunk(ServerLevel level, ChunkPos pos) {
        spawnedChunkRecords.computeIfAbsent(level.dimension().location().toString(),
                k -> ConcurrentHashMap.newKeySet()).add(pos.toLong());
        setDirty();
    }

    public boolean isChunkSpawned(ServerLevel level, ChunkPos pos) {
        Set<Long> records = spawnedChunkRecords.get(level.dimension().location().toString());
        return records != null && records.contains(pos.toLong());
    }

    public ResourceKey<Level> getEffectiveGenerationLevel(String dimensionId, ResourceKey<Level> fallback) {
        return fallback;
    }

    public void setOriginChunk(String dimensionId, ChunkPos pos) {
        originChunks.put(dimensionId, pos);
        setDirty();
    }
}
