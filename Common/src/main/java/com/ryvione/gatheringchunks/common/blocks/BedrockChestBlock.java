/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.common.blocks;

import com.mojang.serialization.MapCodec;
import com.ryvione.gatheringchunks.common.blockEntities.BedrockChestBlockEntity;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
public class BedrockChestBlock extends BaseEntityBlock {
    public BedrockChestBlock(Properties properties) {
        super(properties);
    }
    public static final MapCodec<BedrockChestBlock> CODEC = Block.simpleCodec(BedrockChestBlock::new);
    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
    @Override
    public RenderShape getRenderShape(BlockState p_49232_) {
        return RenderShape.MODEL;
    }
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BedrockChestBlockEntity(pos, state);
    }
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hitResult) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        } else if (player.isSpectator()) {
            return InteractionResult.CONSUME;
        } else {
            BlockEntity blockentity = level.getBlockEntity(pos);
            if (blockentity instanceof BedrockChestBlockEntity bedrockChestBlockEntity) {
                int blockCount = getBlockCount(level, new ChunkPos(pos), pos.getY());
                if (blockCount <= ChunkByChunkConfig.get().getBedrockChest().getBedrockChestBlocksRemainingThreshold()) {
                    player.openMenu(bedrockChestBlockEntity);
                } else {
                    player.displayClientMessage(Component.translatable("ui.chunkbychunk.bedrockchest.sealedmessage", Component.literal(Integer.toString(blockCount - ChunkByChunkConfig.get().getBedrockChest().getBedrockChestBlocksRemainingThreshold())).withStyle(ChatFormatting.RED)), true);
                }
                return InteractionResult.CONSUME;
            } else {
                return InteractionResult.PASS;
            }
        }
    }
    private static int getBlockCount(Level level, ChunkPos chunkPos, int aboveY) {
        LevelChunk chunk = level.getChunk(chunkPos.x, chunkPos.z);
        int count = 0;
        for (int x = chunkPos.getMinBlockX(); x < chunkPos.getMaxBlockX(); x++) {
            for (int y = aboveY + 1; y < level.getMaxY() + 1; y++) {
                for (int z = chunkPos.getMinBlockZ(); z < chunkPos.getMaxBlockZ(); z++) {
                    Block block = chunk.getBlockState(new BlockPos(x, y, z)).getBlock();
                    if (!(block instanceof AirBlock) &&
                            !(block instanceof LiquidBlock) &&
                            !(block instanceof LadderBlock) &&
                            !(block instanceof LeavesBlock) &&
                            block != Blocks.GLOW_LICHEN &&
                            block != Blocks.VINE &&
                            !(block instanceof TorchBlock)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }
}