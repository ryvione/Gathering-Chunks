/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.server;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.interop.Services;
import com.ryvione.gatheringchunks.server.world.ChestTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class PlayerProgressionHelper {

    private static final Map<UUID, Long> lastCheckTime = new HashMap<>();
    private static final Map<UUID, Boolean> hasBeenWarned = new HashMap<>();
    private static final long CHECK_INTERVAL = 6000;
    private static final long GRACE_PERIOD = 12000;
    private static final int SEARCH_RADIUS = 64;

    public static void checkPlayers(MinecraftServer server) {
        long currentTime = server.overworld().getGameTime();

        if (currentTime < GRACE_PERIOD || !ChunkByChunkConfig.get().getDifficulty().isEnableProgressionHelper()) {
            return;
        }

        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            Long lastCheck = lastCheckTime.get(player.getUUID());
            if (lastCheck == null || currentTime - lastCheck >= CHECK_INTERVAL) {
                checkPlayerProgression(player, currentTime, server);
                lastCheckTime.put(player.getUUID(), currentTime);
            }
        }
    }

    private static void checkPlayerProgression(ServerPlayer player, long currentTime, MinecraftServer server) {
        Item chunkSpawnerItem = Services.PLATFORM.spawnChunkBlockItem();
        Item worldCoreItem = Services.PLATFORM.worldCoreBlockItem();
        Item worldCrystalItem = Services.PLATFORM.worldCrystalItem();
        Item worldShardItem = Services.PLATFORM.worldShardItem();
        Item worldFragmentItem = Services.PLATFORM.worldFragmentItem();

        int chunkSpawners = 0;
        int worldCores = 0;
        int worldCrystals = 0;
        int worldShards = 0;
        int worldFragments = 0;

        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (stack.isEmpty())
                continue;

            if (stack.is(chunkSpawnerItem)) {
                chunkSpawners += stack.getCount();
            } else if (stack.is(worldCoreItem)) {
                worldCores += stack.getCount();
            } else if (stack.is(worldCrystalItem)) {
                worldCrystals += stack.getCount();
            } else if (stack.is(worldShardItem)) {
                worldShards += stack.getCount();
            } else if (stack.is(worldFragmentItem)) {
                worldFragments += stack.getCount();
            }
        }

        ChestTracker tracker = ChestTracker.get(server);
        Set<BlockPos> trackedChests = tracker.getChestPositions();

        BlockPos playerPos = player.blockPosition();
        net.minecraft.server.level.ServerLevel level = (net.minecraft.server.level.ServerLevel) player.level();
        Item[] watched = {chunkSpawnerItem, worldCoreItem, worldCrystalItem, worldShardItem, worldFragmentItem};
        int[] counts = {chunkSpawners, worldCores, worldCrystals, worldShards, worldFragments};

        int minChunkX = (playerPos.getX() - SEARCH_RADIUS) >> 4;
        int maxChunkX = (playerPos.getX() + SEARCH_RADIUS) >> 4;
        int minChunkZ = (playerPos.getZ() - SEARCH_RADIUS) >> 4;
        int maxChunkZ = (playerPos.getZ() + SEARCH_RADIUS) >> 4;
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (be instanceof Container container && isInSearchBox(be.getBlockPos(), playerPos)) {
                        countItems(container, watched, counts);
                    }
                }
            }
        }

        boolean unreadableChest = false;
        for (BlockPos trackedPos : trackedChests) {
            if (isInSearchBox(trackedPos, playerPos)) {
                continue;
            }

            LevelChunk chunk = level.getChunkSource().getChunkNow(trackedPos.getX() >> 4, trackedPos.getZ() >> 4);
            if (chunk == null) {
                unreadableChest = true;
                continue;
            }
            if (chunk.getBlockEntity(trackedPos) instanceof Container container) {
                countItems(container, watched, counts);
            }
        }

        chunkSpawners = counts[0];
        worldCores = counts[1];
        worldCrystals = counts[2];
        worldShards = counts[3];
        worldFragments = counts[4];

        if (chunkSpawners > 0) {
            hasBeenWarned.put(player.getUUID(), false);
            return;
        }

        int totalFragmentEquivalents = worldFragments + (worldShards * 4) + (worldCrystals * 16) + (worldCores * 64);

        GatheringChunksConstants.LOGGER.debug(
                "Player {} progression check: Spawners={}, Cores={}, Crystals={}, Shards={}, Fragments={}, Total Equivalents={}",
                player.getName().getString(), chunkSpawners, worldCores, worldCrystals, worldShards, worldFragments,
                totalFragmentEquivalents);

        if (totalFragmentEquivalents < 64 && unreadableChest) {
            GatheringChunksConstants.LOGGER.debug("Player {} has chests in unloaded chunks - not giving a spawner this round",
                    player.getName().getString());
            return;
        }

        if (totalFragmentEquivalents < 64) {
            Boolean warned = hasBeenWarned.get(player.getUUID());
            if (warned != null && warned) {
                return;
            }

            GatheringChunksConstants.LOGGER.info(
                    "Player {} is stuck without progression materials - giving chunk spawner",
                    player.getName().getString());

            player.sendSystemMessage(Component.literal("§c§l[Progression Helper]"));
            player.sendSystemMessage(Component.literal("§eIt looks like you can't craft a chunk spawner!"));
            player.sendSystemMessage(
                    Component.literal("§7You need §664 fragment equivalents §7to craft a §bWorld Core§7."));
            player.sendSystemMessage(Component
                    .literal("§7You currently have: §a" + totalFragmentEquivalents + " §7fragment equivalents."));
            player.sendSystemMessage(Component.literal("§6Giving you a chunk spawner to help you progress!"));

            ItemStack spawnerStack = new ItemStack(chunkSpawnerItem, 1);
            if (!player.addItem(spawnerStack)) {
                player.drop(spawnerStack, false);
            }

            hasBeenWarned.put(player.getUUID(), true);
        } else {
            hasBeenWarned.put(player.getUUID(), false);
        }
    }

    private static boolean isInSearchBox(BlockPos pos, BlockPos center) {
        return Math.abs(pos.getX() - center.getX()) <= SEARCH_RADIUS
                && Math.abs(pos.getY() - center.getY()) <= SEARCH_RADIUS
                && Math.abs(pos.getZ() - center.getZ()) <= SEARCH_RADIUS;
    }

    private static void countItems(Container container, Item[] watched, int[] counts) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            for (int i = 0; i < watched.length; i++) {
                if (stack.is(watched[i])) {
                    counts[i] += stack.getCount();
                    break;
                }
            }
        }
    }
}