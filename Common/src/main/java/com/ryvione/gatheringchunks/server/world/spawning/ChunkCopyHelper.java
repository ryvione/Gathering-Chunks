package com.ryvione.gatheringchunks.server.world.spawning;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.server.DevMode;
import com.ryvione.gatheringchunks.server.world.ControllableChunkMap;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import io.netty.buffer.Unpooled;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

import java.util.Arrays;
import java.util.Map;

public final class ChunkCopyHelper {

    private ChunkCopyHelper() {}

    public static void copyBlocks(ServerLevel sourceLevel, ChunkPos sourceChunkPos,
            ServerLevel targetLevel, ChunkPos targetChunkPos,
            int fromLayer, int toLayer, boolean overwrite) {
        long startNanos = System.nanoTime();
        ChunkAccess targetChunkAccess = targetLevel.getChunkSource().getChunkNow(targetChunkPos.x, targetChunkPos.z);
        if (targetChunkAccess != null && !targetChunkAccess.getPersistedStatus().isOrAfter(
                net.minecraft.world.level.chunk.status.ChunkStatus.FULL)) {
            return;
        }

        int xOffset = targetChunkPos.getMinBlockX() - sourceChunkPos.getMinBlockX();
        int zOffset = targetChunkPos.getMinBlockZ() - sourceChunkPos.getMinBlockZ();

        Block sealedBlock = Blocks.BEDROCK;
        if (targetLevel.getChunkSource().getGenerator() instanceof SkyChunkGenerator skyChunkGenerator
                && skyChunkGenerator.getGenerationType() == SkyChunkGenerator.EmptyGenerationType.Sealed) {
            sealedBlock = skyChunkGenerator.getSealBlock();
        }

        ChunkResourceTracker tracker = ChunkResourceTracker.get(sourceLevel.getServer());
        boolean chunkPreviouslyGathered = tracker.isChunkKnown(sourceLevel, sourceChunkPos);

        BlockPos.MutableBlockPos sourceBlock = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos targetBlock = new BlockPos.MutableBlockPos();

        for (int y = fromLayer; y < toLayer; y++) {
            for (int z = sourceChunkPos.getMinBlockZ(); z <= sourceChunkPos.getMaxBlockZ(); z++) {
                for (int x = sourceChunkPos.getMinBlockX(); x <= sourceChunkPos.getMaxBlockX(); x++) {
                    sourceBlock.set(x, y, z);
                    targetBlock.set(x + xOffset, y, z + zOffset);

                    BlockState existingState = targetLevel.getBlockState(targetBlock);
                    Block existingBlock = existingState.getBlock();
                    boolean isLavaInNether = targetLevel.dimension() == Level.NETHER && existingBlock == Blocks.LAVA;
                    boolean isReplaceableEmptySpace = existingBlock instanceof AirBlock || existingBlock instanceof LiquidBlock
                            || existingBlock == sealedBlock || existingBlock == Blocks.SNOW || isLavaInNether;

                    if (overwrite || isReplaceableEmptySpace) {
                        BlockState newBlock = sourceLevel.getBlockState(sourceBlock);
                        if (overwrite && chunkPreviouslyGathered && isOreBlock(newBlock.getBlock())) {
                            boolean harvested = tracker.isPositionHarvested(sourceLevel, sourceBlock);
                            if (!harvested && !existingState.is(newBlock.getBlock())) {
                                tracker.markPositionHarvested(sourceLevel, sourceBlock);
                                harvested = true;
                            }
                            if (harvested) {
                                newBlock = getFillerBlockFor(sourceLevel, y).defaultBlockState();
                            }
                        }
                        if (ChunkByChunkConfig.get().getGameplayConfig().isChunkSpawnLeafDecayDisabled()
                                && newBlock.getBlock() instanceof LeavesBlock) {
                            newBlock = newBlock.setValue(LeavesBlock.PERSISTENT, true);
                        }
                        targetLevel.setBlock(targetBlock, newBlock, Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                        BlockEntity fromBlockEntity = sourceLevel.getBlockEntity(sourceBlock);
                        BlockEntity toBlockEntity = targetLevel.getBlockEntity(targetBlock);
                        if (fromBlockEntity != null && toBlockEntity != null) {
                            toBlockEntity.loadWithComponents(
                                    fromBlockEntity.saveWithFullMetadata(targetLevel.registryAccess()),
                                    targetLevel.registryAccess());
                            targetLevel.setBlockEntity(toBlockEntity);
                        }
                    }
                }
            }
        }
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs > 200) {
            GatheringChunksConstants.LOGGER.info("[Perf][copyBlocks] {} -> {} layers {}-{} ({} blocks/layer) took {}ms",
                    sourceChunkPos, targetChunkPos, fromLayer, toLayer,
                    (sourceChunkPos.getMaxBlockX() - sourceChunkPos.getMinBlockX() + 1)
                            * (sourceChunkPos.getMaxBlockZ() - sourceChunkPos.getMinBlockZ() + 1),
                    elapsedMs);
        }
    }

    public static void markSourceDepleted(ServerLevel sourceLevel, ChunkPos sourceChunkPos) {
        ChunkResourceTracker.get(sourceLevel.getServer()).markChunkKnown(sourceLevel, sourceChunkPos);
    }

    private static boolean isOreBlock(Block block) {
        net.minecraft.resources.ResourceLocation key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block);
        if (key == null) {
            return false;
        }
        String path = key.getPath();
        return path.contains("_ore") || path.equals("ore") || path.equals("ancient_debris");
    }

    private static Block getFillerBlockFor(ServerLevel level, int y) {
        if (level.dimension() == Level.NETHER) {
            return Blocks.NETHERRACK;
        } else if (level.dimension() == Level.END) {
            return Blocks.END_STONE;
        }
        return y < 0 ? Blocks.DEEPSLATE : Blocks.STONE;
    }

    private static void debugLog(String format, Object... args) {
        if (DevMode.isEnabled()) {
            GatheringChunksConstants.LOGGER.info("[DevMode] " + format, args);
        } else {
            GatheringChunksConstants.LOGGER.debug(format, args);
        }
    }

    public static void copyStructureData(ServerLevel sourceLevel, ChunkAccess sourceChunk,
            ServerLevel targetLevel, ChunkAccess targetChunk, ChunkPos sourceChunkPos, ChunkPos targetChunkPos) {
        long startNanos = System.nanoTime();
        int blockOffsetX = targetChunkPos.getMinBlockX() - sourceChunkPos.getMinBlockX();
        int blockOffsetZ = targetChunkPos.getMinBlockZ() - sourceChunkPos.getMinBlockZ();
        int chunkOffsetX = targetChunkPos.x - sourceChunkPos.x;
        int chunkOffsetZ = targetChunkPos.z - sourceChunkPos.z;

        Map<Structure, StructureStart> starts = sourceChunk.getAllStarts();
        Map<Structure, LongSet> references = sourceChunk.getAllReferences();

        debugLog("[StructureCopy] {} -> {} ({} -> {}): source chunk has {} structure start(s), {} reference set(s), fast-path={}",
                sourceChunkPos, targetChunkPos, sourceLevel.dimension().location(), targetLevel.dimension().location(),
                starts.size(), references.size(), blockOffsetX == 0 && blockOffsetZ == 0);

        if (starts.isEmpty() && references.isEmpty()) {
            return;
        }

        if (blockOffsetX == 0 && blockOffsetZ == 0) {
            for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
                targetChunk.setStartForStructure(entry.getKey(), entry.getValue());
                debugLog("[StructureCopy] Direct-copied start for {} ({} pieces) into {}",
                        entry.getKey(), entry.getValue().getPieces().size(), targetChunkPos);
            }
            for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
                for (long ref : entry.getValue()) {
                    targetChunk.addReferenceForStructure(entry.getKey(), ref);
                }
            }
            targetChunk.setUnsaved(true);
            logStructureCopyTime(startNanos, sourceChunkPos, targetChunkPos);
            return;
        }

        StructurePieceSerializationContext context = StructurePieceSerializationContext.fromLevel(sourceLevel);
        for (Map.Entry<Structure, StructureStart> entry : starts.entrySet()) {
            StructureStart sourceStart = entry.getValue();
            if (!sourceStart.isValid()) {
                debugLog("[StructureCopy] Skipping invalid/absent start for {} at {}", entry.getKey(), sourceChunkPos);
                continue;
            }
            int pieceCount = sourceStart.getPieces().size();
            try {
                CompoundTag tag = sourceStart.createTag(context, sourceChunkPos);
                shiftStructureTag(tag, blockOffsetX, blockOffsetZ);
                tag.putInt("ChunkX", targetChunkPos.x);
                tag.putInt("ChunkZ", targetChunkPos.z);
                StructureStart translatedStart = StructureStart.loadStaticStart(context, tag, targetLevel.getSeed());
                if (translatedStart != null) {
                    targetChunk.setStartForStructure(entry.getKey(), translatedStart);
                    debugLog("[StructureCopy] Translated start for {} ({} pieces) from {} to {} (offset {},{})",
                            entry.getKey(), pieceCount, sourceChunkPos, targetChunkPos, blockOffsetX, blockOffsetZ);
                } else {
                    GatheringChunksConstants.LOGGER.warn(
                            "[StructureCopy] Translated start for {} ({} pieces) from {} to {} came back null after loadStaticStart - structure will be missing from this chunk",
                            entry.getKey(), pieceCount, sourceChunkPos, targetChunkPos);
                }
            } catch (Exception e) {
                GatheringChunksConstants.LOGGER.warn(
                        "[StructureCopy] Failed to translate structure start {} ({} pieces) from {} to {}: {}",
                        entry.getKey(), pieceCount, sourceChunkPos, targetChunkPos, e.getMessage());
            }
        }

        for (Map.Entry<Structure, LongSet> entry : references.entrySet()) {
            for (long ref : entry.getValue()) {
                ChunkPos refPos = new ChunkPos(ref);
                ChunkPos translatedRef = new ChunkPos(refPos.x + chunkOffsetX, refPos.z + chunkOffsetZ);
                targetChunk.addReferenceForStructure(entry.getKey(), translatedRef.toLong());
            }
        }
        targetChunk.setUnsaved(true);
        logStructureCopyTime(startNanos, sourceChunkPos, targetChunkPos);
    }

    private static void logStructureCopyTime(long startNanos, ChunkPos sourceChunkPos, ChunkPos targetChunkPos) {
        long elapsedMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (elapsedMs > 50) {
            GatheringChunksConstants.LOGGER.info("[Perf][copyStructureData] {} -> {} took {}ms", sourceChunkPos, targetChunkPos, elapsedMs);
        }
    }

    private static void shiftStructureTag(CompoundTag tag, int dx, int dz) {
        if (tag.contains("BB", Tag.TAG_INT_ARRAY)) {
            int[] bb = tag.getIntArray("BB");
            if (bb.length == 6) {
                bb[0] += dx;
                bb[3] += dx;
                bb[2] += dz;
                bb[5] += dz;
                tag.putIntArray("BB", bb);
            }
        }
        if (tag.contains("Children", Tag.TAG_LIST)) {
            ListTag children = tag.getList("Children", Tag.TAG_COMPOUND);
            for (int i = 0; i < children.size(); i++) {
                shiftStructureTag(children.getCompound(i), dx, dz);
            }
        }
    }

    public static void updateBiomes(ServerLevel sourceLevel, ChunkAccess sourceChunk,
            ServerLevel targetLevel, ChunkAccess targetChunk, ChunkPos targetChunkPos) {
        if (sourceChunk.getSections().length != targetChunk.getSections().length) {
            GatheringChunksConstants.LOGGER.warn(
                    "[BiomeCopy] Section count mismatch between {} and {} at {} - {} vs {}. This means the two "
                    + "dimensions' height ranges don't match, which will cause biome (and possibly block) "
                    + "misalignment for this chunk - check that the generation dimension's dimension_type has the "
                    + "same min_y/height/logical_height as the visible dimension it feeds.",
                    sourceLevel.dimension().location(), targetLevel.dimension().location(), targetChunkPos,
                    sourceChunk.getSections().length, targetChunk.getSections().length);
        }
        if (!(targetChunk instanceof LevelChunk levelChunk)) return;

        Holder<Biome> beforeCenterBiome = null;
        if (DevMode.isEnabled()) {
            beforeCenterBiome = targetChunk.getNoiseBiome(
                    targetChunkPos.getMinBlockX() >> 2, 8, targetChunkPos.getMinBlockZ() >> 2);
        }

        boolean biomesUpdated = false;
        for (int targetIndex = 0; targetIndex < targetChunk.getSections().length; targetIndex++) {
            int sourceIndex = (targetIndex < sourceChunk.getSections().length)
                    ? targetIndex : sourceChunk.getSections().length - 1;

            PalettedContainerRO<Holder<Biome>> sourceBiomes = sourceChunk.getSections()[sourceIndex].getBiomes();
            LevelChunkSection targetSection = levelChunk.getSections()[targetIndex];

            byte[] sourceBuffer = new byte[sourceBiomes.getSerializedSize()];
            FriendlyByteBuf sourceBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(sourceBuffer));
            sourceBuf.writerIndex(0);
            sourceBiomes.write(sourceBuf);

            PalettedContainerRO<Holder<Biome>> targetBiomesRO = targetSection.getBiomes();
            byte[] targetBuffer = new byte[targetBiomesRO.getSerializedSize()];
            FriendlyByteBuf targetBuf = new FriendlyByteBuf(Unpooled.wrappedBuffer(targetBuffer));
            targetBuf.writerIndex(0);
            targetBiomesRO.write(targetBuf);

            if (!Arrays.equals(sourceBuffer, targetBuffer)) {
                sourceBuf.readerIndex(0);
                PalettedContainer<Holder<Biome>> newBiomes = targetBiomesRO.recreate();
                newBiomes.read(sourceBuf);
                try {
                    PalettedContainer<BlockState> blockStates =
                            (PalettedContainer<BlockState>) targetSection.getStates();
                    levelChunk.getSections()[targetIndex] = new LevelChunkSection(blockStates, newBiomes);
                    biomesUpdated = true;
                } catch (Exception e) {
                    GatheringChunksConstants.LOGGER.error("Failed to update biomes", e);
                }
                targetChunk.setUnsaved(true);
            }
        }
        if (biomesUpdated) {
            ((ControllableChunkMap) targetLevel.getChunkSource().chunkMap).forceReloadChunk(targetChunkPos);
            debugLog("[BiomeCopy] {}: biome sections differed from source - updated and resent chunk to trackers", targetChunkPos);
        } else {
            debugLog("[BiomeCopy] {}: biome sections already matched source - no update needed (no resend sent)", targetChunkPos);
        }
        if (DevMode.isEnabled()) {
            Holder<Biome> afterCenterBiome = targetChunk.getNoiseBiome(
                    targetChunkPos.getMinBlockX() >> 2, 8, targetChunkPos.getMinBlockZ() >> 2);
            Holder<Biome> sourceCenterBiome = sourceChunk.getNoiseBiome(
                    targetChunkPos.getMinBlockX() >> 2, 8, targetChunkPos.getMinBlockZ() >> 2);
            GatheringChunksConstants.LOGGER.info(
                    "[DevMode][BiomeCopy] {}: center-column biome before={} after={} source={}{}",
                    targetChunkPos, describeBiome(beforeCenterBiome), describeBiome(afterCenterBiome),
                    describeBiome(sourceCenterBiome),
                    afterCenterBiome != null && sourceCenterBiome != null && !afterCenterBiome.equals(sourceCenterBiome)
                            ? " <-- MISMATCH: target still doesn't match source after copy" : "");
        }
    }

    private static String describeBiome(Holder<Biome> biome) {
        if (biome == null) return "?";
        return biome.unwrapKey().map(key -> key.location().toString()).orElse(biome.toString());
    }

    public static void triggerLightingUpdate(ServerLevel level, ChunkPos chunkPos) {
        try {
            int minX = chunkPos.getMinBlockX();
            int minZ = chunkPos.getMinBlockZ();
            int maxX = chunkPos.getMaxBlockX();
            int maxZ = chunkPos.getMaxBlockZ();
            int minY = level.getMinBuildHeight();
            int maxY = level.getMaxBuildHeight();

            for (int y = minY; y < maxY; y++) {
                for (int x = minX; x <= maxX; x++) {
                    level.getLightEngine().checkBlock(new BlockPos(x, y, minZ));
                    level.getLightEngine().checkBlock(new BlockPos(x, y, maxZ));
                }
                for (int z = minZ + 1; z < maxZ; z++) {
                    level.getLightEngine().checkBlock(new BlockPos(minX, y, z));
                    level.getLightEngine().checkBlock(new BlockPos(maxX, y, z));
                }
            }

            int[][] borderOffsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            for (int[] off : borderOffsets) {
                ChunkPos neighbor = new ChunkPos(chunkPos.x + off[0], chunkPos.z + off[1]);
                if (level.hasChunk(neighbor.x, neighbor.z)) {
                    int edgeX = off[0] == 1 ? maxX : (off[0] == -1 ? minX : minX);
                    int edgeZ = off[1] == 1 ? maxZ : (off[1] == -1 ? minZ : minZ);
                    boolean alongX = off[1] != 0;
                    for (int y = minY; y < maxY; y++) {
                        if (alongX) {
                            for (int x = minX; x <= maxX; x++) {
                                level.getLightEngine().checkBlock(new BlockPos(x, y, edgeZ));
                            }
                        } else {
                            for (int z = minZ; z <= maxZ; z++) {
                                level.getLightEngine().checkBlock(new BlockPos(edgeX, y, z));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            GatheringChunksConstants.LOGGER.warn("[LightUpdate] Failed to trigger lighting update for chunk {}: {}",
                    chunkPos, e.getMessage());
        }
    }
}
