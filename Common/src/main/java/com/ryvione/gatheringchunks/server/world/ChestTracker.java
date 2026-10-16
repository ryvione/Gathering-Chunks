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

import com.ryvione.gatheringchunks.util.SavedDataCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.core.UUIDUtil;

import java.util.*;

public class ChestTracker extends SavedData {
    private final Set<BlockPos> chestPositions = new HashSet<>();
    private final Map<UUID, Boolean> playerTrackerEnabled = new HashMap<>();
    private final MinecraftServer server;

    public static ChestTracker get(MinecraftServer server) {
        ServerLevel overworld = server.getLevel(Level.OVERWORLD);
        if (overworld == null) {
            return new ChestTracker(server);
        }
        return overworld.getChunkSource().getDataStorage().computeIfAbsent(SavedDataCompat.of(
                "chunkbychunk_chest_tracker",
                () -> new ChestTracker(server),
                tag -> ChestTracker.load(server, tag),
                tracker -> tracker.save(new CompoundTag())
        ));
    }

    private static ChestTracker load(MinecraftServer server, CompoundTag tag) {
        ChestTracker tracker = new ChestTracker(server);
        ListTag positionsTag = tag.getListOrEmpty("chests");
        for (int i = 0; i < positionsTag.size(); i++) {
            CompoundTag posTag = positionsTag.getCompoundOrEmpty(i);
            int x = posTag.getIntOr("x", 0);
            int y = posTag.getIntOr("y", 0);
            int z = posTag.getIntOr("z", 0);
            tracker.chestPositions.add(new BlockPos(x, y, z).immutable());
        }

        ListTag playerSettingsTag = tag.getListOrEmpty("playerSettings");
        for (int i = 0; i < playerSettingsTag.size(); i++) {
            CompoundTag playerTag = playerSettingsTag.getCompoundOrEmpty(i);
            UUID playerUUID = UUIDUtil.uuidFromIntArray(playerTag.getIntArray("uuid").orElse(new int[4]));
            boolean enabled = playerTag.getBooleanOr("enabled", false);
            tracker.playerTrackerEnabled.put(playerUUID, enabled);
        }
        return tracker;
    }

    private ChestTracker(MinecraftServer server) {
        this.server = server;
    }

    public CompoundTag save(CompoundTag tag) {
        ListTag positionsTag = new ListTag();
        for (BlockPos pos : chestPositions) {
            CompoundTag posTag = new CompoundTag();
            BlockPos immutablePos = pos.immutable();
            posTag.putInt("x", immutablePos.getX());
            posTag.putInt("y", immutablePos.getY());
            posTag.putInt("z", immutablePos.getZ());
            positionsTag.add(posTag);
        }
        tag.put("chests", positionsTag);

        ListTag playerSettingsTag = new ListTag();
        for (Map.Entry<UUID, Boolean> entry : playerTrackerEnabled.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putIntArray("uuid", UUIDUtil.uuidToIntArray(entry.getKey()));
            playerTag.putBoolean("enabled", entry.getValue());
            playerSettingsTag.add(playerTag);
        }
        tag.put("playerSettings", playerSettingsTag);
        return tag;
    }

    public void addChest(BlockPos pos) {
        chestPositions.add(pos.immutable());
        setDirty();
    }

    public void removeChest(BlockPos pos) {
        boolean removed = chestPositions.remove(pos);
        if (!removed) {
            removed = chestPositions.remove(pos.immutable());
        }
        if (removed) {
            setDirty();
        }
    }

    public Set<BlockPos> getChestPositions() {
        Set<BlockPos> result = new HashSet<>();
        for (BlockPos pos : chestPositions) {
            result.add(pos.immutable());
        }
        return result;
    }

    public void checkAndRemoveIfEmpty(BlockPos pos, ServerLevel level) {
        BlockPos immutablePos = pos.immutable();
        if (!chestPositions.contains(immutablePos)) {
            return;
        }

        net.minecraft.world.level.chunk.LevelChunk chunk = level.getChunkSource()
                .getChunkNow(immutablePos.getX() >> 4, immutablePos.getZ() >> 4);
        if (chunk == null) {
            return;
        }
        BlockEntity blockEntity = chunk.getBlockEntity(immutablePos);
        if (blockEntity instanceof RandomizableContainerBlockEntity chest) {
            boolean isEmpty = true;
            for (int i = 0; i < chest.getContainerSize(); i++) {
                if (!chest.getItem(i).isEmpty()) {
                    isEmpty = false;
                    break;
                }
            }
            if (isEmpty) {
                removeChest(immutablePos);
            }
        } else {
            removeChest(immutablePos);
        }
    }

    public boolean isTracked(BlockPos pos) {
        return chestPositions.contains(pos.immutable());
    }

    public void setTrackerEnabled(UUID playerUUID, boolean enabled) {
        playerTrackerEnabled.put(playerUUID, enabled);
        setDirty();
    }

    public boolean isTrackerEnabled(UUID playerUUID) {
        return playerTrackerEnabled.getOrDefault(playerUUID, false);
    }
}