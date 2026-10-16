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

import java.util.EnumMap;
import java.util.Map;

import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FlowingFluid.class)
public class FluidTickMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void preventFlowIntoVoidOnTick(ServerLevel level, BlockPos pos, BlockState blockState, FluidState state, CallbackInfo ci) {
        if (shouldCancelFluidFlow(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "spreadTo", at = @At("HEAD"), cancellable = true)
    private void preventSpreadIntoVoid(LevelAccessor level, BlockPos pos, BlockState state, Direction direction, FluidState fluidState, CallbackInfo ci) {
        if (shouldCancelFluidFlow(level, pos)) {
            ci.cancel();
        }
    }

    @Inject(method = "getSpread", at = @At("RETURN"), cancellable = true)
    private void preventSpreadIntoVoid(ServerLevel level, BlockPos pos, BlockState blockState,
            CallbackInfoReturnable<Map<Direction, FluidState>> cir) {
        Map<Direction, FluidState> spread = cir.getReturnValue();
        if (spread == null || spread.isEmpty()) {
            return;
        }
        Map<Direction, FluidState> filtered = new EnumMap<>(Direction.class);
        for (Map.Entry<Direction, FluidState> entry : spread.entrySet()) {
            if (!shouldCancelFluidFlow(level, pos.relative(entry.getKey()))) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        if (filtered.size() != spread.size()) {
            cir.setReturnValue(filtered);
        }
    }

    private boolean shouldCancelFluidFlow(BlockGetter level, BlockPos pos) {
        if (!com.ryvione.gatheringchunks.config.ChunkByChunkConfig.get().getGatheringChunksConfig().isPreventFluidFlowIntoVoid()) {
            return false;
        }

        if (!(level instanceof Level serverLevel)) {
            return false;
        }

        if (serverLevel.isClientSide()) {
            return false;
        }

        ChunkPos targetChunk = new ChunkPos(pos);

        if (SpawnChunkHelper.isEmptyChunk(serverLevel, targetChunk)) {
            return true;
        }

        return false;
    }
}