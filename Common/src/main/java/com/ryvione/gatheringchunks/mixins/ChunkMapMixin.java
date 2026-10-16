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
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.server.world.ChunkSpawnController;
import com.ryvione.gatheringchunks.server.world.ControllableChunkMap;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import net.minecraft.world.level.chunk.storage.SimpleRegionStorage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.nio.file.Path;
import java.util.List;
@Mixin(targets = "net.minecraft.server.level.ChunkMap")
public abstract class ChunkMapMixin extends SimpleRegionStorage implements ControllableChunkMap {
    public ChunkMapMixin(RegionStorageInfo $$0, Path $$1, DataFixer $$2, boolean $$3, DataFixTypes $$4) {
        super($$0, $$1, $$2, $$3, $$4);
    }
    @Final
    @Shadow
    ServerLevel level;
    @Shadow
    @Nullable
    protected abstract ChunkHolder getVisibleChunkIfPresent(long pos);

    @Shadow
    private volatile Long2ObjectLinkedOpenHashMap<ChunkHolder> visibleChunkMap;

    @Override
    public Iterable<ChunkHolder> getVisibleChunkHolders() {
        return this.visibleChunkMap.values();
    }
    public void forceReloadChunk(ChunkPos chunkPos) {
        ChunkHolder chunkHolder = this.getVisibleChunkIfPresent(chunkPos.toLong());
        if (chunkHolder != null) {
            ChunkMap thisMap = (ChunkMap) (Object) this;
            List<ServerPlayer> players = thisMap.getPlayers(chunkPos, false);
            chunkHolder.getFullChunkFuture().thenAccept(chunkResult -> {
                chunkResult.ifSuccess(levelChunk -> {
                    ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                            levelChunk,
                            this.level.getLightEngine(),
                            null,
                            null
                    );
                    for (ServerPlayer player : players) {
                        player.connection.send(packet);
                    }
                });
            });
        }
    }
    @Inject(method = "onFullChunkStatusChange", at = @At("HEAD"))
    public void onFullStatusChange(ChunkPos pos, FullChunkStatus status, CallbackInfo ci) {
        if (ChunkByChunkConfig.get().getDifficulty().isSpawnChunkStrip()
                && status.isOrAfter(FullChunkStatus.ENTITY_TICKING)
                && level.dimension().equals(Level.OVERWORLD)
                && new ChunkPos(level.getRespawnData().pos()).x == pos.x) {
            BlockPos blockPos = pos.getMiddleBlockPosition(level.getMaxY());
            ChunkSpawnController.get(level.getServer()).request(level, "", false, blockPos);
        }
    }
}