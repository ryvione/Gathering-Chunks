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

import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class ChunkBoundaryEnforcer {

    private static final Map<UUID, Long> lastWarningTime = new HashMap<>();
    private static final Map<UUID, Vec3> lastValidPosition = new HashMap<>();
    private static final long WARNING_COOLDOWN = 100;

    public static void checkPlayerBoundaries(ServerPlayer player) {
        if (!ChunkByChunkConfig.get().getDifficulty().getHardMode().isEnforceChunkBoundaries()) {
            return;
        }

        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        BlockPos playerPos = player.blockPosition();
        ChunkPos currentChunk = new ChunkPos(playerPos);

        if (SpawnChunkHelper.isEmptyChunk(player.level(), currentChunk)) {
            Vec3 lastValid = lastValidPosition.get(player.getUUID());
            if (lastValid != null) {
                player.teleportTo(lastValid.x, lastValid.y, lastValid.z);
                player.setDeltaMovement(Vec3.ZERO);
                player.fallDistance = 0;
            } else {
                net.minecraft.server.level.ServerPlayer.RespawnConfig respawnConfig = player.getRespawnConfig();
                BlockPos respawn = respawnConfig != null ? respawnConfig.respawnData().pos() : null;
                if (respawn != null) {
                    player.teleportTo(respawn.getX() + 0.5, respawn.getY(), respawn.getZ() + 0.5);
                    player.setDeltaMovement(Vec3.ZERO);
                    player.fallDistance = 0;
                }
            }

            long currentTime = player.level().getGameTime();
            Long lastWarning = lastWarningTime.get(player.getUUID());

            if (lastWarning == null || currentTime - lastWarning >= WARNING_COOLDOWN) {
                player.sendSystemMessage(Component.literal("§c§lYou cannot leave spawned chunks!"));
                player.sendSystemMessage(Component.literal("§eSpawn more chunks using a Chunk Spawner."));
                lastWarningTime.put(player.getUUID(), currentTime);
            }
        } else {
            lastValidPosition.put(player.getUUID(), player.position());
        }
    }
}