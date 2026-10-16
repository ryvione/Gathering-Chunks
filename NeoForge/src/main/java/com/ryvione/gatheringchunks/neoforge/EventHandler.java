/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.neoforge;

import com.ryvione.gatheringchunks.common.CommonEventHandler;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.util.ConfigUtil;
import com.ryvione.gatheringchunks.common.data.ScannerDataLoader;
import com.ryvione.gatheringchunks.common.data.SkyDimensionDataLoader;
import com.ryvione.gatheringchunks.common.update.UpdateNotification;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.system.ConfigSystem;
import com.ryvione.gatheringchunks.server.ChunkBoundaryEnforcer;
import com.ryvione.gatheringchunks.server.MobLootHandler;
import com.ryvione.gatheringchunks.server.ServerEventHandler;
import com.ryvione.gatheringchunks.server.commands.ChestsCommand;
import com.ryvione.gatheringchunks.server.commands.GatheringChunksCommand;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import com.ryvione.gatheringchunks.common.util.ChunkUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.server.MinecraftServer;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.AddServerReloadListenersEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@EventBusSubscriber(modid = GatheringChunksConstants.MOD_ID)
public class EventHandler {

    private static final ConfigSystem configSystem = new ConfigSystem();
    private static boolean dimensionsConfigured = false;

    private static final Set<UUID> INITIAL_SPAWNED_PLAYERS = new HashSet<>();

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();

        GatheringChunksConstants.LOGGER.info("ServerAboutToStartEvent - loading config & preparing dimensions");

        ConfigUtil.reloadConfig(server);

        if (ChunkByChunkConfig.get().getGeneration().isEnabled()) {
            GatheringChunksConstants.LOGGER.info("Applying sky dimension configuration EARLY (before level load)");
            ServerEventHandler.applySkyDimensionConfig(server.registryAccess());
            ServerEventHandler.applyChunkByChunkWorldGeneration(server);
        } else {
            GatheringChunksConstants.LOGGER.info("Generation disabled in config - skipping dimension override");
        }

        dimensionsConfigured = true;
        INITIAL_SPAWNED_PLAYERS.clear();
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        GatheringChunksConstants.LOGGER.info("ServerStartedEvent - server fully started");
        ServerEventHandler.onServerStarted(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        ServerEventHandler.onLevelTick(event.getServer());
    }

    @SubscribeEvent
    public static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ServerEventHandler.onPlayerChangedDimension(player, event.getFrom(), event.getTo());
        }
    }

    @SubscribeEvent
    public static void onWorldTick(LevelTickEvent.Post event) {
        if (event.getLevel() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            com.ryvione.gatheringchunks.common.CauldronRainFiller.tick(serverLevel);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ChunkBoundaryEnforcer.checkPlayerBoundaries(player);
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        MobLootHandler.onMobDeath(event.getEntity(), event.getEntity().level());
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        GatheringChunksConstants.LOGGER.debug("Registering commands");
        GatheringChunksCommand.register(event.getDispatcher());
        ChestsCommand.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onRegisterReloadListeners(AddServerReloadListenersEvent event) {
        GatheringChunksConstants.LOGGER.info("Registering data reload listeners");

        event.addListener(Identifier.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "scanner_data_loader"),
                new ScannerDataLoader(event.getRegistryAccess()));
        event.addListener(Identifier.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "sky_dimension_loader"),
                new SkyDimensionDataLoader(event.getRegistryAccess()));

        GatheringChunksConstants.LOGGER.info("Data reload listeners registered");
    }

    @SubscribeEvent
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        BlockPos pos = event.getPos();
        BlockPos placePos = pos.relative(event.getFace());

        if (!CommonEventHandler.isBlockPlacementAllowed(placePos, event.getEntity(), event.getLevel())) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.CONSUME);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!ChunkByChunkConfig.get().getGeneration().isEnabled())
            return;

        if (event.getEntity() instanceof ServerPlayer player) {
            UpdateNotification.sendUpdateNotification(player);
            com.ryvione.gatheringchunks.common.util.ConfigUtil.notifyLegacyWorldJoin(player);

            try {
                com.google.gson.Gson gson = new com.google.gson.GsonBuilder().create();
                String configJson = gson.toJson(ChunkByChunkConfig.get());
                com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket packet =
                        new com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket(configJson);
                com.ryvione.gatheringchunks.interop.Services.PLATFORM.sendConfigSyncPacket(player, packet);
                GatheringChunksConstants.LOGGER.debug("[EventHandler] Synced config to player {} on login", player.getName().getString());

                ServerEventHandler.giveStarterBookIfMissing(player);
            } catch (Exception e) {

                GatheringChunksConstants.LOGGER.warn("[EventHandler] Failed to sync config to player on login: {}", e.getMessage());
            }

            boolean isFirstJoin = !player.getTags().contains("gatheringchunks.spawned");
            if (!isFirstJoin) {
                return;
            }
            player.addTag("gatheringchunks.spawned");

            ServerLevel level = player.level();
            if (!level.dimension().equals(Level.OVERWORLD))
                return;

            if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator) {
                BlockPos spawnPos = level.getRespawnData().pos();
                ChunkPos spawnChunk = new ChunkPos(spawnPos);
                ChunkPos playerChunk = new ChunkPos(player.blockPosition());

                if (playerChunk.x != spawnChunk.x || playerChunk.z != spawnChunk.z) {
                    GatheringChunksConstants.LOGGER.info(
                            "Correcting initial spawn position from chunk [{},{}] to spawn chunk [{},{}] for player {}",
                            playerChunk.x, playerChunk.z, spawnChunk.x, spawnChunk.z, player.getName().getString());

                    int safeY = -1;
                    net.minecraft.world.level.chunk.LevelChunk spawnLevelChunk =
                            level.getChunkSource().getChunkNow(spawnChunk.x, spawnChunk.z);
                    if (spawnLevelChunk != null) {
                        int candidateY = ChunkUtil.getSafeSpawnHeight(spawnLevelChunk, spawnChunk.getMiddleBlockX(), spawnChunk.getMiddleBlockZ());
                        if (candidateY > level.getMinY() + 10) {
                            safeY = candidateY;
                        } else {
                            safeY = spawnLevelChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8) + 1;
                        }
                    }

                    if (safeY < 0) {
                        GatheringChunksConstants.LOGGER.info("Spawn chunk not loaded yet - leaving {} in place", player.getName().getString());
                        return;
                    }
                    player.teleportTo(
                            level,
                            spawnChunk.getMiddleBlockX() + 0.5,
                            safeY,
                            spawnChunk.getMiddleBlockZ() + 0.5,
                            java.util.Set.of(), player.getYRot(),
                            player.getXRot(), true);
                }
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (!ChunkByChunkConfig.get().getGeneration().isEnabled())
            return;

        if (event.getEntity() instanceof ServerPlayer player) {
            if (player.getRespawnConfig() != null)
                return;

            ServerLevel level = player.level();
            if (!level.dimension().equals(Level.OVERWORLD))
                return;

            if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator) {
                BlockPos spawnPos = level.getRespawnData().pos();
                ChunkPos spawnChunk = new ChunkPos(spawnPos);

                GatheringChunksConstants.LOGGER.debug(
                        "Forcing respawn to stay inside spawn chunk [{},{}]",
                        spawnChunk.x, spawnChunk.z);

                int safeY = -1;
                LevelChunk spawnLevelChunk = level.getChunkSource().getChunkNow(spawnChunk.x, spawnChunk.z);
                if (spawnLevelChunk != null) {
                    int candidateY = ChunkUtil.getSafeSpawnHeight(spawnLevelChunk, spawnChunk.getMiddleBlockX(), spawnChunk.getMiddleBlockZ());
                    if (candidateY > level.getMinY() + 10) {
                        safeY = candidateY;
                    } else {
                        safeY = spawnLevelChunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, 8, 8) + 1;
                    }
                }

                if (safeY < 0) {
                    return;
                }
                player.teleportTo(
                        level,
                        spawnChunk.getMiddleBlockX() + 0.5,
                        safeY,
                        spawnChunk.getMiddleBlockZ() + 0.5,
                        java.util.Set.of(), player.getYRot(),
                        player.getXRot(), true);
            }
        }
    }
}