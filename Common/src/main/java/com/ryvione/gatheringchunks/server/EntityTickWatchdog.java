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
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.*;
import net.minecraft.world.entity.animal.fish.*;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.squid.GlowSquid;
import net.minecraft.world.entity.animal.squid.Squid;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import java.util.ArrayList;
import java.util.List;

public final class EntityTickWatchdog {
    private static final int CHECK_INTERVAL_TICKS = 100;
    private static final int AQUATIC_OUT_OF_WATER_TICKS = 200;
    private static final int ITEM_STUCK_TICKS = 3000;

    private EntityTickWatchdog() {
    }

    private static int currentLevelIndex = 0;

    public static void tick(MinecraftServer server) {
        if (server.getTickCount() % 20 != 0) {
            return;
        }
        
        List<ServerLevel> skyLevels = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator) {
                skyLevels.add(level);
            }
        }
        
        if (skyLevels.isEmpty()) return;
        
        currentLevelIndex = currentLevelIndex % skyLevels.size();
        tickLevel(skyLevels.get(currentLevelIndex));
        currentLevelIndex++;
    }

    private static void tickLevel(ServerLevel level) {
        List<Entity> toRemove = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (!entity.isAlive()) continue;

            if (isAquaticMob(entity) && isNotInWater(level, entity)) {
                int airTicks = entity.getAirSupply();
                if (airTicks <= -AQUATIC_OUT_OF_WATER_TICKS) {
                    GatheringChunksConstants.LOGGER.debug(
                            "[EntityWatchdog] Removing stranded aquatic entity {} id={} at {}",
                            entity.getType().toShortString(), entity.getId(), entity.blockPosition());
                    toRemove.add(entity);
                }
            }

            if (entity instanceof ItemEntity item) {
                int age = item.getAge();
                if (age > ITEM_STUCK_TICKS) {
                    BlockPos pos = entity.blockPosition();
                    BlockState below = level.getBlockState(pos.below());
                    BlockState at = level.getBlockState(pos);
                    boolean inSolid = !at.isAir() && at.isSolidRender();
                    boolean looping = below.is(Blocks.WATER) && at.is(Blocks.WATER);
                    if (inSolid || looping) {
                        GatheringChunksConstants.LOGGER.debug(
                                "[EntityWatchdog] Removing stuck item entity id={} at {} (inSolid={} looping={})",
                                entity.getId(), pos, inSolid, looping);
                        toRemove.add(entity);
                    }
                }
            }
        }
        for (Entity entity : toRemove) {
            entity.discard();
        }
    }

    private static boolean isAquaticMob(Entity entity) {
        return entity instanceof Cod
                || entity instanceof Salmon
                || entity instanceof TropicalFish
                || entity instanceof Pufferfish
                || entity instanceof Squid
                || entity instanceof GlowSquid
                || entity instanceof Axolotl;
    }

    private static boolean isNotInWater(ServerLevel level, Entity entity) {
        return !entity.isInWater() && !entity.isInWaterOrRain();
    }
}
