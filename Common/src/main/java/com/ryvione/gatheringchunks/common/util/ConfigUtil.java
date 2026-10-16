/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.common.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.WorldConfigStore;
import com.ryvione.gatheringchunks.config.system.ConfigSystem;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.security.MessageDigest;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class ConfigUtil {
    private static final Logger LOGGER = LogManager.getLogger(GatheringChunksConstants.MOD_ID);
    private ConfigUtil() {
    }

    private static final ConfigSystem system = new ConfigSystem();
    private static final Gson GSON = new GsonBuilder().create();
    private static final Map<Path, String> knownFileChecksums = new ConcurrentHashMap<>();
    private static volatile Thread configWatcherThread;
    private static final long WATCH_DEBOUNCE_MS = 500;
    private static volatile boolean legacyWorldMigrated = false;
    private static volatile ChunkByChunkConfig activeWorldSettings = null;
    private static final String LEGACY_WORLD_NOTICE =
            "[Gathering Chunks] This world was created before per-world settings. It is being migrated now, "
            + "using the current global settings. The migration may take a while, depending on what is ported.";

    public static void loadDefaultConfig() {
        synchronized (system) {
            Path configPath = ConfigSystem.getCentralConfigPath(GatheringChunksConstants.CONFIG_FILE);
            LOGGER.info("[ConfigUtil] Loading config from centralized location: {}", configPath);
            system.synchConfig(configPath, ChunkByChunkConfig.global());
            rememberChecksum(configPath);
        }
    }

    public static void startConfigWatcher() {
        if (configWatcherThread != null) {
            return;
        }
        Path configPath = ConfigSystem.getCentralConfigPath(GatheringChunksConstants.CONFIG_FILE);
        Path dir = configPath.getParent();
        if (dir == null || !Files.isDirectory(dir)) {
            LOGGER.warn("[ConfigUtil] Cannot start config watcher - directory missing: {}", dir);
            return;
        }
        Thread watcher = new Thread(() -> runConfigWatcher(dir, configPath), "GatheringChunks-ConfigWatcher");
        watcher.setDaemon(true);
        watcher.start();
        configWatcherThread = watcher;
        LOGGER.info("[ConfigUtil] Config hot-reload watcher started for {}", configPath);
    }

    private static void runConfigWatcher(Path dir, Path configPath) {
        try {
            WatchService watchService = dir.getFileSystem().newWatchService();
            dir.register(watchService, StandardWatchEventKinds.ENTRY_MODIFY,
                    StandardWatchEventKinds.ENTRY_CREATE, StandardWatchEventKinds.ENTRY_DELETE);
            String fileName = configPath.getFileName().toString();
            long pendingSince = -1;
            while (true) {
                WatchKey key = watchService.take();
                for (WatchEvent<?> event : key.pollEvents()) {
                    if (event.context() != null && fileName.equals(event.context().toString())) {
                        LOGGER.debug("[ConfigUtil] Watcher received {} event for {}", event.kind(), fileName);
                        if (pendingSince < 0) {
                            pendingSince = System.currentTimeMillis();
                        }
                    }
                }
                key.reset();
                if (pendingSince >= 0) {
                    long elapsed = System.currentTimeMillis() - pendingSince;
                    if (elapsed < WATCH_DEBOUNCE_MS) {
                        Thread.sleep(WATCH_DEBOUNCE_MS - elapsed);
                        drainAndReset(watchService);
                    }
                    pendingSince = -1;
                    hotReloadIfChanged(configPath);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.error("[ConfigUtil] Config hot-reload watcher crashed; hot reload disabled for this session", e);
        }
    }

    private static void drainAndReset(WatchService watchService) {
        WatchKey key = watchService.poll();
        while (key != null) {
            key.pollEvents();
            key.reset();
            key = watchService.poll();
        }
    }

    private static void hotReloadIfChanged(Path configPath) {
        synchronized (system) {
            if (!hasExternalChange(configPath)) {
                LOGGER.debug("[ConfigUtil] Watcher fired for {} but content is unchanged (our own write or duplicate event) - skipping reload", configPath);
                return;
            }
            LOGGER.info("[ConfigUtil] Detected external config change at {}, hot-reloading", configPath);
            ChunkByChunkConfig backup = deepCopyConfig();
            try {
                system.reloadConfig(configPath, ChunkByChunkConfig.global());
                rememberChecksum(configPath);
                LOGGER.info("[ConfigUtil] Config hot-reloaded successfully from {}", configPath);
            } catch (Exception e) {
                restoreConfig(backup);
                rememberChecksum(configPath);
                LOGGER.error("[ConfigUtil] Hot-reload of config failed - keeping previous values (error fallback)", e);
            }
        }
    }

    private static boolean hasExternalChange(Path path) {
        if (!Files.exists(path)) {
            return false;
        }
        String current = computeChecksum(path);
        return current != null && !current.equals(knownFileChecksums.get(path));
    }

    private static void rememberChecksum(Path path) {
        String checksum = computeChecksum(path);
        if (checksum != null) {
            knownFileChecksums.put(path, checksum);
        }
    }

    private static String computeChecksum(Path path) {
        try {
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            LOGGER.warn("[ConfigUtil] Could not checksum config file {}: {}", path, e.getMessage());
            return null;
        }
    }

    private static ChunkByChunkConfig deepCopyConfig() {
        try {
            return GSON.fromJson(GSON.toJson(ChunkByChunkConfig.global()), ChunkByChunkConfig.class);
        } catch (Exception e) {
            LOGGER.warn("[ConfigUtil] Could not snapshot config for error fallback", e);
            return null;
        }
    }

    private static void restoreConfig(ChunkByChunkConfig backup) {
        if (backup == null) {
            return;
        }
        try {
            ChunkByChunkConfig restored = GSON.fromJson(GSON.toJson(backup), ChunkByChunkConfig.class);
            copyInto(restored, ChunkByChunkConfig.global());
        } catch (Exception e) {
            LOGGER.error("[ConfigUtil] Failed to restore previous config values after reload failure", e);
        }
    }

    private static void copyInto(ChunkByChunkConfig from, ChunkByChunkConfig to) {
        copyField(from, to, "generation");
        copyField(from, to, "difficulty");
        copyField(from, to, "gameplayConfig");
        copyField(from, to, "gatheringChunksConfig");
        copyField(from, to, "worldForge");
        copyField(from, to, "worldScannerConfig");
        copyField(from, to, "worldMenderConfig");
        copyField(from, to, "bedrockChest");
        copyField(from, to, "debug");
        copyField(from, to, "performance");
    }

    private static void copyField(ChunkByChunkConfig from, ChunkByChunkConfig to, String fieldName) {
        try {
            java.lang.reflect.Field field = ChunkByChunkConfig.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(to, field.get(from));
        } catch (Exception e) {
            LOGGER.warn("[ConfigUtil] Could not restore config section '{}' during error fallback: {}",
                    fieldName, e.getMessage());
        }
    }

    public static void reloadConfig() {
        synchronized (system) {
            Path configPath = ConfigSystem.getCentralConfigPath(GatheringChunksConstants.CONFIG_FILE);
            LOGGER.info("[ConfigUtil] Reloading config from: {}", configPath);
            ChunkByChunkConfig backup = deepCopyConfig();
            try {
                system.reloadConfig(configPath, ChunkByChunkConfig.global());
                rememberChecksum(configPath);
            } catch (Exception e) {
                restoreConfig(backup);
                LOGGER.error("[ConfigUtil] Config reload failed - keeping previous values (error fallback)", e);
            }
        }
    }

    public static void reloadConfig(net.minecraft.server.MinecraftServer server) {
        synchronized (system) {
            Path centralConfigPath = ConfigSystem.getCentralConfigPath(GatheringChunksConstants.CONFIG_FILE);
            ChunkByChunkConfig backup = deepCopyConfig();
            try {
                system.reloadConfig(centralConfigPath, ChunkByChunkConfig.global());
            } catch (Exception e) {
                restoreConfig(backup);
                LOGGER.error("[ConfigUtil] Config reload failed - keeping previous values (error fallback)", e);
                return;
            }
            rememberChecksum(centralConfigPath);
            activateWorldSettings(server);
        }
    }

    private static void activateWorldSettings(net.minecraft.server.MinecraftServer server) {
        if (server == null || server.isDedicatedServer()) {
            ChunkByChunkConfig.clearActiveWorld();
            activeWorldSettings = null;
            legacyWorldMigrated = false;
            return;
        }
        Path worldDir = server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT);
        ChunkByChunkConfig settings = WorldConfigStore.load(worldDir);
        legacyWorldMigrated = settings == null;
        if (settings == null) {
            settings = WorldConfigStore.copyOfGlobalWorldSettings();
            WorldConfigStore.save(worldDir, settings);
            LOGGER.warn("[ConfigUtil] World has no saved GC settings - migrated it with the current global settings");
        }
        activeWorldSettings = settings;
        ChunkByChunkConfig.setActiveWorld(ChunkByChunkConfig.global().worldView(settings));
        LOGGER.info("[ConfigUtil] Using the world's own GC settings from {}", worldDir.resolve(WorldConfigStore.FILE_NAME));
    }

    public static boolean writeActiveWorldSettings(net.minecraft.server.MinecraftServer server) {
        ChunkByChunkConfig settings = activeWorldSettings;
        if (server == null || settings == null) {
            return false;
        }
        try {
            WorldConfigStore.save(server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT), settings);
            LOGGER.info("[ConfigUtil] Saved the world's GC settings");
            return true;
        } catch (Exception e) {
            LOGGER.error("[ConfigUtil] Could not save the world's GC settings", e);
            return false;
        }
    }

    public static void saveActiveWorldSettings(net.minecraft.server.MinecraftServer server) {
        if (!writeActiveWorldSettings(server)) {
            return;
        }
        var packet = new com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket(GSON.toJson(ChunkByChunkConfig.get()));
        for (net.minecraft.server.level.ServerPlayer player : server.getPlayerList().getPlayers()) {
            com.ryvione.gatheringchunks.interop.Services.PLATFORM.sendConfigSyncPacket(player, packet);
        }
    }

    public static void notifyLegacyWorldJoin(net.minecraft.server.level.ServerPlayer player) {
        if (legacyWorldMigrated) {
            player.sendSystemMessage(net.minecraft.network.chat.Component.literal("§e" + LEGACY_WORLD_NOTICE));
        }
    }

    public static void saveDefaultConfig() {
        saveDefaultConfig(null);
    }

    public static void saveDefaultConfig(net.minecraft.server.MinecraftServer server) {
        synchronized (system) {
            Path configPath = ConfigSystem.getCentralConfigPath(GatheringChunksConstants.CONFIG_FILE);
            reconcileExternalChanges(configPath);
            LOGGER.info("[ConfigUtil] Saving config to: {}", configPath);
            system.write(configPath, ChunkByChunkConfig.global());
            rememberChecksum(configPath);
        }
    }

    private static void reconcileExternalChanges(Path configPath) {
        if (!hasExternalChange(configPath)) {
            return;
        }
        LOGGER.info("[ConfigUtil] Detected manual config edit at {} before save - reloading it first", configPath);
        ChunkByChunkConfig backup = deepCopyConfig();
        try {
            system.reloadConfig(configPath, ChunkByChunkConfig.global());
            LOGGER.info("[ConfigUtil] Manual config edits loaded successfully");
        } catch (Exception e) {
            restoreConfig(backup);
            LOGGER.error("[ConfigUtil] Loading manual config edits failed - keeping previous values (error fallback)", e);
        }
        rememberChecksum(configPath);
    }
}