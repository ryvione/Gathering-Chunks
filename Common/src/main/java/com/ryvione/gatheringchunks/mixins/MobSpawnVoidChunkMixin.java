/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.mixins;

import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.ServerLevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(SpawnPlacements.class)
public abstract class MobSpawnVoidChunkMixin {

    @Inject(
        method = "checkSpawnRules",
        at = @At("HEAD"),
        cancellable = true
    )
    private static void gc$suppressSpawnInVoidChunk(
            EntityType<?> entityType,
            ServerLevelAccessor level,
            EntitySpawnReason spawnType,
            BlockPos pos,
            RandomSource random,
            CallbackInfoReturnable<Boolean> cir) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!ChunkByChunkConfig.get().getGeneration().isEnabled()) {
            return;
        }
        if (!(serverLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) {
            return;
        }
        if (SpawnChunkHelper.isEmptyChunk(serverLevel, new ChunkPos(pos))) {
            cir.setReturnValue(false);
        }
    }
}