package com.ryvione.gatheringchunks.neoforge;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.network.C2SSaveConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.system.ConfigSystem;
import com.ryvione.gatheringchunks.interop.Services;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public class ServerPacketHandler {
    private static final Gson GSON = new GsonBuilder().create();

    public static void handleSaveConfig(C2SSaveConfigPacket packet, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.literal("§c[Gathering Chunks] No permission to modify server config."));
                return;
            }
            try {
                ChunkByChunkConfig incoming = GSON.fromJson(packet.configJson(), ChunkByChunkConfig.class);
                if (incoming == null) {
                    GatheringChunksConstants.LOGGER.warn("[ServerPacketHandler] Received null config from {}", player.getName().getString());
                    return;
                }

                applyConfig(incoming);

                com.ryvione.gatheringchunks.common.util.ConfigUtil.saveDefaultConfig(player.getServer());

                String syncJson = GSON.toJson(ChunkByChunkConfig.get().getGatheringChunksConfig());
                S2CSyncConfigPacket syncPacket = new S2CSyncConfigPacket(syncJson);
                for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                    Services.PLATFORM.sendConfigSyncPacket(p, syncPacket);
                }

                GatheringChunksConstants.LOGGER.info("[ServerPacketHandler] Config saved by {}", player.getName().getString());
                player.sendSystemMessage(Component.literal("§a[Gathering Chunks] Config saved to server."));
            } catch (Exception e) {
                GatheringChunksConstants.LOGGER.error("[ServerPacketHandler] Failed to apply config from client", e);
                player.sendSystemMessage(Component.literal("§c[Gathering Chunks] Failed to save config: " + e.getMessage()));
            }
        });
    }

    private static void applyConfig(ChunkByChunkConfig incoming) {
        ChunkByChunkConfig current = ChunkByChunkConfig.get();

        current.getGeneration().setEnabled(incoming.getGeneration().isEnabled());
        current.getGeneration().setSealWorld(incoming.getGeneration().sealWorld());
        current.getGeneration().setSynchNether(incoming.getGeneration().isSynchNether());
        current.getGeneration().setUseBedrockChest(incoming.getGeneration().useBedrockChest());
        current.getGeneration().setChestContents(incoming.getGeneration().getChestContents());
        current.getGeneration().setChestQuantity(incoming.getGeneration().getChestQuantity());
        current.getGeneration().setChestsPerChunk(incoming.getGeneration().getChestsPerChunk());
        current.getGeneration().setChunkSpawnerMode(incoming.getGeneration().getChunkSpawnerMode());
        current.getGeneration().setMinChestSpawnDepth(incoming.getGeneration().getMinChestSpawnDepth());
        current.getGeneration().setMaxChestSpawnDepth(incoming.getGeneration().getMaxChestSpawnDepth());
        current.getGeneration().setInitialChunks(incoming.getGeneration().getInitialChunks());
        current.getGeneration().setChunkLayerSpawnRate(incoming.getGeneration().getChunkLayerSpawnRate());
        current.getGeneration().setInitialChunkBiomes(incoming.getGeneration().getInitialChunkBiomes());

        current.getDifficulty().getHardMode().setEnabled(incoming.getDifficulty().getHardMode().isEnabled());
        current.getDifficulty().getHardMode().setEnforceChunkBoundaries(incoming.getDifficulty().getHardMode().isEnforceChunkBoundaries());
        current.getDifficulty().getHardMode().setDisableVillages(incoming.getDifficulty().getHardMode().isDisableVillages());
        current.getDifficulty().getHardMode().setSpawnInitialEngine(incoming.getDifficulty().getHardMode().isSpawnInitialEngine());
        current.getDifficulty().getHardMode().setInitialEngineFuel(incoming.getDifficulty().getHardMode().isInitialEngineFuel());
        current.getDifficulty().getHardMode().setDisableChestsCommand(incoming.getDifficulty().getHardMode().isDisableChestsCommand());
        current.getDifficulty().setEngineRequiresFuel(incoming.getDifficulty().isEngineRequiresFuel());
        current.getDifficulty().setExperimentalChunkLimit(incoming.getDifficulty().isExperimentalChunkLimit());
        current.getDifficulty().setEnableProgressionHelper(incoming.getDifficulty().isEnableProgressionHelper());
        current.getDifficulty().setStartRestriction(incoming.getDifficulty().getStartRestriction());
        current.getDifficulty().setStartingBiome(incoming.getDifficulty().getStartingBiome());
        current.getDifficulty().setAlwaysSpawnVillage(incoming.getDifficulty().isAlwaysSpawnVillage());
        current.getDifficulty().setSpawnNewChunkChest(incoming.getDifficulty().spawnNewChunkChest());
        current.getDifficulty().setSpawnChunkStrip(incoming.getDifficulty().isSpawnChunkStrip());
        current.getDifficulty().setSpawnChestInInitialChunkOnly(incoming.getDifficulty().spawnChestInInitialChunkOnly());

        current.getGameplayConfig().setBlockPlacementAllowedOutsideSpawnedChunks(incoming.getGameplayConfig().isBlockPlacementAllowedOutsideSpawnedChunks());
        current.getGameplayConfig().setChunkSpawnLeafDecayDisabled(incoming.getGameplayConfig().isChunkSpawnLeafDecayDisabled());
        current.getGameplayConfig().setEnableChunkBarriers(incoming.getGameplayConfig().isEnableChunkBarriers());
        current.getGameplayConfig().setUnstableChunkChance(incoming.getGameplayConfig().getUnstableChunkChance());

        current.getGatheringChunksConfig().setMobsDropFragments(incoming.getGatheringChunksConfig().isMobsDropFragments());
        current.getGatheringChunksConfig().setFragmentDropChance(incoming.getGatheringChunksConfig().getFragmentDropChance());
        current.getGatheringChunksConfig().setMinFragmentDrop(incoming.getGatheringChunksConfig().getMinFragmentDrop());
        current.getGatheringChunksConfig().setMaxFragmentDrop(incoming.getGatheringChunksConfig().getMaxFragmentDrop());
        current.getGatheringChunksConfig().setAutoSpawnTrees(incoming.getGatheringChunksConfig().isAutoSpawnTrees());
        current.getGatheringChunksConfig().setPreventFluidFlowIntoVoid(incoming.getGatheringChunksConfig().isPreventFluidFlowIntoVoid());
    }
}