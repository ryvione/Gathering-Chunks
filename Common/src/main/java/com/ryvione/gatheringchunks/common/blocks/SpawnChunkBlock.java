package com.ryvione.gatheringchunks.common.blocks;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.ChunkSpawnerMode;
import com.ryvione.gatheringchunks.interop.Services;
import com.ryvione.gatheringchunks.server.world.ChunkOverwriteConfirmation;
import com.ryvione.gatheringchunks.server.world.ChunkSpawnController;
import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

public class SpawnChunkBlock extends Block {
    private static final EnumSet<Direction> HORIZONTAL_DIR = EnumSet.of(Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST);
    private final String biomeTheme;
    private final boolean random;

    public SpawnChunkBlock(String biomeTheme, boolean random, Properties blockProperties) {
        super(blockProperties);
        this.biomeTheme = biomeTheme;
        this.random = random;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResult.PASS;
        }

        ChunkSpawnController chunkSpawnController = ChunkSpawnController.get(serverLevel.getServer());

        String effectiveBiomeTheme = biomeTheme;
        boolean effectiveRandom = random;

        if (biomeTheme.isEmpty() && !random) {
            ChunkPos currentChunk = new ChunkPos(pos);
            String inheritedBiome = findAdjacentBiomeTheme(serverLevel, currentChunk);
            if (inheritedBiome != null && !inheritedBiome.isEmpty()) {
                effectiveBiomeTheme = inheritedBiome;
            }
        }

        if (!chunkSpawnController.isValidForLevel(serverLevel, effectiveBiomeTheme, effectiveRandom)) {
            return InteractionResult.PASS;
        }

        ChunkSpawnerMode mode = ChunkByChunkConfig.get().getGeneration().getChunkSpawnerMode();
        ChunkPos ownChunk = new ChunkPos(pos);

        ChunkPos originChunk = chunkSpawnController.getOriginChunk(serverLevel.dimension().location().toString());
        if (originChunk != null && originChunk.equals(ownChunk)) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                    "§c[ChunkByChunk] §eThis chunk holds this dimension's origin data and can't be spawned into."));
            return InteractionResult.CONSUME;
        }

        boolean ownChunkEmpty = SpawnChunkHelper.isEmptyChunk(serverLevel, ownChunk);
        boolean ownChunkDeletedButMarked = false;

        if (serverLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
            if (ownChunkEmpty && chunkSpawnController.isChunkSpawned(serverLevel, ownChunk)) {
                ownChunkEmpty = false;
                ownChunkDeletedButMarked = true;
            } else if (!ownChunkEmpty && generator.getGenerationType() == SkyChunkGenerator.EmptyGenerationType.Sealed
                    && !chunkSpawnController.isChunkSpawned(serverLevel, ownChunk)) {
                ownChunkEmpty = true;
            }
        }

        if (mode == ChunkSpawnerMode.Edge || mode == ChunkSpawnerMode.Both) {
            Direction targetDirection = hit.getDirection();
            if (!HORIZONTAL_DIR.contains(targetDirection)) {
                targetDirection = Direction.NORTH;
            }

            List<ChunkPos> adjacentChunks = new ArrayList<>();
            List<BlockPos> candidates = List.of(
                    pos.relative(targetDirection),
                    pos.relative(targetDirection.getCounterClockWise()),
                    pos.relative(targetDirection.getClockWise()),
                    pos.relative(targetDirection.getOpposite()),
                    pos.relative(targetDirection).relative(targetDirection.getClockWise()),
                    pos.relative(targetDirection).relative(targetDirection.getCounterClockWise()),
                    pos.relative(targetDirection.getOpposite()).relative(targetDirection.getClockWise()),
                    pos.relative(targetDirection.getOpposite()).relative(targetDirection.getCounterClockWise())
            );
            for (BlockPos tp : candidates) {
                ChunkPos cp = new ChunkPos(tp);
                if (!cp.equals(ownChunk) && !adjacentChunks.contains(cp)) {
                    adjacentChunks.add(cp);
                }
            }

            for (ChunkPos targetChunkPos : adjacentChunks) {
                if (!serverLevel.hasChunk(targetChunkPos.x, targetChunkPos.z)) continue;
                if (originChunk != null && originChunk.equals(targetChunkPos)) continue;
                
                boolean targetEmpty = SpawnChunkHelper.isEmptyChunk(level, targetChunkPos);
                if (serverLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator generator) {
                    if (targetEmpty && chunkSpawnController.isChunkSpawned(serverLevel, targetChunkPos)) {
                        targetEmpty = false;
                    } else if (!targetEmpty && generator.getGenerationType() == SkyChunkGenerator.EmptyGenerationType.Sealed
                            && !chunkSpawnController.isChunkSpawned(serverLevel, targetChunkPos)) {
                        targetEmpty = true;
                    }
                }
                
                if (!targetEmpty) continue;

                BlockPos requestPos = targetChunkPos.getMiddleBlockPosition(pos.getY());
                if (chunkSpawnController.request(serverLevel, effectiveBiomeTheme, effectiveRandom,
                        requestPos, false, false, serverPlayer.getUUID())) {
                    level.playSound(null, pos, Services.PLATFORM.spawnChunkSoundEffect(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        if (mode == ChunkSpawnerMode.Void || mode == ChunkSpawnerMode.Both) {
            if (ownChunkEmpty) {
                if (chunkSpawnController.request(serverLevel, effectiveBiomeTheme, effectiveRandom, pos, false, false, serverPlayer.getUUID())) {
                    level.playSound(null, pos, Services.PLATFORM.spawnChunkSoundEffect(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    return InteractionResult.SUCCESS;
                }
            }
        }

        if (ownChunkDeletedButMarked) {
            serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "gatheringchunks.message.deleted_chunk_needs_mender"));
            return InteractionResult.CONSUME;
        }

        if (!ownChunkEmpty) {
            ChunkOverwriteConfirmation.PendingOverwrite pending =
                    ChunkOverwriteConfirmation.getAnyPendingOverwrite(serverPlayer);

            if (pending != null
                    && pending.targetChunk.equals(ownChunk)
                    && pending.biomeTheme.equals(effectiveBiomeTheme)
                    && pending.random == effectiveRandom) {
                ChunkOverwriteConfirmation.removePendingOverwrite(serverPlayer);
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§6[ChunkByChunk] §eOverwriting chunk at [" + ownChunk.x + ", " + ownChunk.z + "]"));

                if (chunkSpawnController.request(serverLevel, effectiveBiomeTheme, effectiveRandom, pos,
                        false, true, serverPlayer.getUUID())) {
                    level.playSound(null, pos, Services.PLATFORM.spawnChunkSoundEffect(), SoundSource.BLOCKS, 1.0f, 1.0f);
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    return InteractionResult.SUCCESS;
                } else {
                    serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                            "§c[ChunkByChunk] §eOverwrite request failed."));
                    return InteractionResult.CONSUME;
                }
            } else {
                ChunkOverwriteConfirmation.addPendingOverwrite(serverPlayer, ownChunk,
                        effectiveBiomeTheme, effectiveRandom);
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§c[ChunkByChunk] §6WARNING: §eThis chunk is already generated!"));
                serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.literal(
                        "§eClick again within 30 seconds to overwrite chunk ["
                                + ownChunk.x + ", " + ownChunk.z + "]."));
                return InteractionResult.CONSUME;
            }
        }

        serverPlayer.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                "gatheringchunks.message.no_empty_adjacent_chunks"));
        return InteractionResult.CONSUME;
    }


    private String findAdjacentBiomeTheme(ServerLevel level, ChunkPos currentChunk) {
        for (Direction dir : HORIZONTAL_DIR) {
            ChunkPos adjacentChunk = new ChunkPos(
                    currentChunk.x + dir.getStepX(),
                    currentChunk.z + dir.getStepZ());

            if (!SpawnChunkHelper.isEmptyChunk(level, adjacentChunk)) {
                BlockPos centerPos = adjacentChunk.getMiddleBlockPosition(level.getMaxBuildHeight() - 10);
                for (int y = level.getMaxBuildHeight() - 10; y >= level.getMinBuildHeight(); y--) {
                    BlockPos checkPos = new BlockPos(centerPos.getX(), y, centerPos.getZ());
                    Block block = level.getBlockState(checkPos).getBlock();

                    if (block instanceof SpawnChunkBlock spawnBlock) {
                        String stheme = spawnBlock.getBiomeTheme();
                        if (!stheme.isEmpty()) {
                            return stheme;
                        }
                    }
                }
            }
        }
        return null;
    }

    public String getBiomeTheme() {
        return biomeTheme;
    }

    public boolean isRandom() {
        return random;
    }
}