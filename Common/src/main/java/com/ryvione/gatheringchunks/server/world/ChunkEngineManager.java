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

import com.ryvione.gatheringchunks.common.blockEntities.ChunkEngineBlockEntity;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.interop.Services;
import com.ryvione.gatheringchunks.util.SavedDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ChunkEngineManager extends SavedData {
    private final MinecraftServer server;

    public record ChunkId(String level, long pos) {
        public static ChunkId from(ServerLevel level, ChunkPos pos) {
            return new ChunkId(level.dimension().identifier().toString(), pos.toLong());
        }

        public CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("level", level);
            tag.putLong("pos", pos);
            return tag;
        }

        public static ChunkId load(CompoundTag tag) {
            return new ChunkId(tag.getStringOr("level", ""), tag.getLongOr("pos", 0L));
        }
    }

    private static class ResetTask {
        final ChunkId id;
        int currentLayer;
        boolean forced;

        ResetTask(ChunkId id, int startLayer) {
            this.id = id;
            this.currentLayer = startLayer;
        }
    }

    private final Map<ChunkId, Long> pendingChunks = new HashMap<>();
    private final Map<ChunkId, Long> activeChunks = new HashMap<>();
    private final Map<ChunkId, ResetTask> resettingChunks = new HashMap<>();

    private static final long GRACE_PERIOD_TICKS = 1200;
    private static final long MAINTENANCE_TIMEOUT_TICKS = 200;

    public ChunkEngineManager(MinecraftServer server) {
        this.server = server;
    }

    public static ChunkEngineManager get(MinecraftServer server) {
        return server.getLevel(Level.OVERWORLD).getChunkSource().getDataStorage().computeIfAbsent(SavedDataCompat.of(
                "chunkenginemanager",
                () -> new ChunkEngineManager(server),
                tag -> ChunkEngineManager.load(server, tag),
                manager -> manager.save(new CompoundTag())
        ));
    }

    private static ChunkEngineManager load(MinecraftServer server, CompoundTag tag) {
        ChunkEngineManager manager = new ChunkEngineManager(server);
        ListTag pendingList = tag.getListOrEmpty("PendingChunks");
        for (int i = 0; i < pendingList.size(); i++) {
            CompoundTag entry = pendingList.getCompoundOrEmpty(i);
            manager.pendingChunks.put(ChunkId.load(entry.getCompoundOrEmpty("id")), entry.getLongOr("time", 0L));
        }
        ListTag activeList = tag.getListOrEmpty("ActiveChunks");
        for (int i = 0; i < activeList.size(); i++) {
            CompoundTag entry = activeList.getCompoundOrEmpty(i);
            manager.activeChunks.put(ChunkId.load(entry.getCompoundOrEmpty("id")), entry.getLongOr("time", 0L));
        }
        return manager;
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag pendingList = new ListTag();
        for (Map.Entry<ChunkId, Long> entry : pendingChunks.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("id", entry.getKey().save());
            entryTag.putLong("time", entry.getValue());
            pendingList.add(entryTag);
        }
        tag.put("PendingChunks", pendingList);

        ListTag activeList = new ListTag();
        for (Map.Entry<ChunkId, Long> entry : activeChunks.entrySet()) {
            CompoundTag entryTag = new CompoundTag();
            entryTag.put("id", entry.getKey().save());
            entryTag.putLong("time", entry.getValue());
            activeList.add(entryTag);
        }
        tag.put("ActiveChunks", activeList);
        return tag;
    }

    public void notifyInitialChunkSpawned(ServerLevel level, ChunkPos pos) {
        if (!ChunkByChunkConfig.get().getDifficulty().getHardMode().isEnabled()) return;

        int x = pos.getMiddleBlockX();
        int z = pos.getMiddleBlockZ();
        int y = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING, x, z);

        BlockPos enginePos = new BlockPos(x, y, z);

        if (ChunkByChunkConfig.get().getDifficulty().getHardMode().isSpawnInitialEngine()) {
            level.setBlock(enginePos, Services.PLATFORM.chunkEngineBlock().defaultBlockState(), 3);

            BlockEntity be = level.getBlockEntity(enginePos);
            if (be instanceof ChunkEngineBlockEntity engine && ChunkByChunkConfig.get().getDifficulty().getHardMode().isInitialEngineFuel()) {
                engine.setItem(ChunkEngineBlockEntity.SLOT_FUEL, new ItemStack(Services.PLATFORM.worldShardItem(), 16));
                engine.setChanged();
            }

            registerEngine(level, enginePos);
        }
    }

    public void notifyChunkSpawned(ServerLevel level, ChunkPos pos) {
        if (!ChunkByChunkConfig.get().getDifficulty().getHardMode().isEnabled()) return;
        if (!ChunkByChunkConfig.get().getDifficulty().isEngineRequiresFuel()) return;

        ChunkId id = ChunkId.from(level, pos);
        if (!activeChunks.containsKey(id) && !resettingChunks.containsKey(id)) {
            pendingChunks.put(id, server.getTickCount() + 0L);
            setDirty();
        }
    }

    public void registerEngine(ServerLevel level, BlockPos pos) {
        if (!ChunkByChunkConfig.get().getDifficulty().getHardMode().isEnabled()) return;
        if (!ChunkByChunkConfig.get().getDifficulty().isEngineRequiresFuel()) return;

        ChunkId id = ChunkId.from(level, new ChunkPos(pos));
        activeChunks.put(id, (long) server.getTickCount());
        pendingChunks.remove(id);
        ResetTask cancelledReset = resettingChunks.remove(id);
        if (cancelledReset != null && cancelledReset.forced) {
            ChunkPos enginePos = new ChunkPos(pos);
            level.setChunkForced(enginePos.x, enginePos.z, false);
        }
        setDirty();
    }

    public void tick() {
        if (!ChunkByChunkConfig.get().getDifficulty().getHardMode().isEnabled()) return;
        if (!ChunkByChunkConfig.get().getDifficulty().isEngineRequiresFuel()) return;

        long currentTick = server.getTickCount();

        pendingChunks.entrySet().removeIf(entry -> {
            ChunkId id = entry.getKey();
            ServerLevel level = getLevelFromId(id.level);
            if (level != null && level.getChunkSource().hasChunk(new ChunkPos(id.pos).x, new ChunkPos(id.pos).z)) {
                if (currentTick - entry.getValue() > GRACE_PERIOD_TICKS) {
                    startReset(id);
                    return true;
                }
            } else {
                entry.setValue(currentTick); 
            }
            return false;
        });

        activeChunks.entrySet().removeIf(entry -> {
            ChunkId id = entry.getKey();
            ServerLevel level = getLevelFromId(id.level);
            if (level != null && level.getChunkSource().hasChunk(new ChunkPos(id.pos).x, new ChunkPos(id.pos).z)) {
                if (currentTick - entry.getValue() > MAINTENANCE_TIMEOUT_TICKS) {
                    startReset(id);
                    return true;
                }
            } else {
                entry.setValue(currentTick); 
            }
            return false;
        });


        processResets();

        if (currentTick % 100 == 0) setDirty();
    }

    private ServerLevel getLevelFromId(String dimId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimId)) {
                return level;
            }
        }
        return null;
    }

    private void startReset(ChunkId id) {

        if (resettingChunks.containsKey(id)) return;

        server.getAllLevels().forEach(level -> {
            if (level.dimension().identifier().toString().equals(id.level)) {
                resettingChunks.put(id, new ResetTask(id, level.getMinY()));
                setDirty();
            }
        });
    }

    private void processResets() {
        if (resettingChunks.isEmpty()) return;

        int layersPerTick = ChunkByChunkConfig.get().getGeneration().getChunkLayerSpawnRate();
        Iterator<Map.Entry<ChunkId, ResetTask>> it = resettingChunks.entrySet().iterator();

        while (it.hasNext()) {
            Map.Entry<ChunkId, ResetTask> entry = it.next();
            ChunkId id = entry.getKey();
            ResetTask task = entry.getValue();

            ServerLevel level = null;
            for (ServerLevel l : server.getAllLevels()) {
                if (l.dimension().identifier().toString().equals(id.level)) {
                    level = l;
                    break;
                }
            }

            if (level == null) {
                it.remove();
                continue;
            }

            if (!task.forced) {
                level.setChunkForced(new ChunkPos(id.pos).x, new ChunkPos(id.pos).z, true);
                task.forced = true;
            }

            int minLayer = task.currentLayer;
            int maxLayer = Math.min(minLayer + layersPerTick, level.getMaxY() + 1);

            performResetStep(level, new ChunkPos(id.pos), minLayer, maxLayer);

            if (maxLayer >= level.getMaxY() + 1) {
                level.setChunkForced(new ChunkPos(id.pos).x, new ChunkPos(id.pos).z, false);
                it.remove();
                if (ChunkByChunkConfig.get().getDifficulty().isExperimentalChunkLimit()) {
                    ChunkSpawnController.get(server).decreaseSpawnedChunkCount(id.level);
                }
            } else {
                task.currentLayer = maxLayer;
            }
        }
        setDirty();
    }

    private void performResetStep(ServerLevel level, ChunkPos pos, int fromY, int toY) {
        BlockState defaultState = Blocks.AIR.defaultBlockState();

        if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator skyGen) {
            switch (skyGen.getGenerationType()) {
                case Sealed -> {
                }
                case Nether -> {
                }
            }
        }

        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        for (int y = fromY; y < toY; y++) {
            for (int x = pos.getMinBlockX(); x <= pos.getMaxBlockX(); x++) {
                for (int z = pos.getMinBlockZ(); z <= pos.getMaxBlockZ(); z++) {
                    mutable.set(x, y, z);
                    level.setBlock(mutable, defaultState, 3 | 16);
                }
            }
        }

        if (fromY == level.getMinY()) {
            BlockPos bedrockPos = pos.getMiddleBlockPosition(level.getMinY());
            level.setBlock(bedrockPos, Blocks.AIR.defaultBlockState(), 3);
        }
    }
}