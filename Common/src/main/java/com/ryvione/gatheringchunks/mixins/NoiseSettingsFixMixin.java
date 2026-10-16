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

import com.mojang.datafixers.DataFixer;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.thread.BlockableEventLoop;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LightChunkGetter;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.TicketStorage;
import net.minecraft.world.level.entity.ChunkStatusUpdateListener;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.concurrent.Executor;
import java.util.function.Supplier;

@Mixin(net.minecraft.server.level.ChunkMap.class)
public abstract class NoiseSettingsFixMixin {

    @Redirect(
            method = "<init>",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;dummy"
                            + "()Lnet/minecraft/world/level/levelgen/NoiseGeneratorSettings;"))
    private NoiseGeneratorSettings gatheringchunks$useWrappedGeneratorSettings(
            ServerLevel level,
            LevelStorageSource.LevelStorageAccess storageAccess,
            DataFixer fixerUpper,
            StructureTemplateManager structureTemplateManager,
            Executor executor,
            BlockableEventLoop<Runnable> mainThreadExecutor,
            LightChunkGetter lightChunk,
            ChunkGenerator chunkGenerator,
            ChunkStatusUpdateListener chunkStatusListener,
            Supplier<net.minecraft.world.level.storage.DimensionDataStorage> dimensionDataStorageSupplier,
            TicketStorage ticketStorage,
            int viewDistance,
            boolean simulate) {
        if (chunkGenerator instanceof SkyChunkGenerator skyGenerator
                && skyGenerator.getParent() instanceof NoiseBasedChunkGenerator parentNoiseGenerator) {
            return parentNoiseGenerator.generatorSettings().value();
        }
        return NoiseGeneratorSettings.dummy();
    }
}
