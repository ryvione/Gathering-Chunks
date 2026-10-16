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
import com.ryvione.gatheringchunks.interop.Services;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.Random;

public class MobLootHandler {

    private static final Random RANDOM = new Random();

    public static void onMobDeath(LivingEntity entity, Level level) {
        if (level.isClientSide()) {
            return;
        }

        if (!(entity instanceof Monster)) {
            return;
        }

        if (!ChunkByChunkConfig.get().getGatheringChunksConfig().isMobsDropFragments()) {
            return;
        }

        int dropChance = ChunkByChunkConfig.get().getGatheringChunksConfig().getFragmentDropChance();
        int roll = RANDOM.nextInt(100);

        if (roll < dropChance) {
            int minDrop = ChunkByChunkConfig.get().getGatheringChunksConfig().getMinFragmentDrop();
            int maxDrop = ChunkByChunkConfig.get().getGatheringChunksConfig().getMaxFragmentDrop();
            int amount = RANDOM.nextInt(minDrop, maxDrop + 1);

            ItemStack fragments = new ItemStack(Services.PLATFORM.worldFragmentItem(), amount);
            ItemEntity itemEntity = new ItemEntity(
                    level,
                    entity.getX(),
                    entity.getY(),
                    entity.getZ(),
                    fragments
            );

            level.addFreshEntity(itemEntity);
        }
    }
}