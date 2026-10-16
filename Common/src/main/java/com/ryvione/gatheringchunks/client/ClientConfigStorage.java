/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.GatheringChunksConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ClientConfigStorage {
    private static final Logger LOGGER = LogManager.getLogger(GatheringChunksConstants.MOD_ID);
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String STORAGE_FILE = "client_server_configs.json";

    private static Path storagePath;
    private static Map<String, ServerConfigOverride> serverConfigs = new HashMap<>();
    private static String currentServerId = null;
    private static boolean remoteSessionActive = false;

    public static class ServerConfigOverride {
        public Boolean preventFluidFlowIntoVoid;
        public Boolean autoSpawnTrees;
        public Boolean mobsDropFragments;
        public Integer fragmentDropChance;

        public void applyToConfig(GatheringChunksConfig config) {
            if (preventFluidFlowIntoVoid != null) {
                config.setPreventFluidFlowIntoVoid(preventFluidFlowIntoVoid);
            }
            if (autoSpawnTrees != null) {
                config.setAutoSpawnTrees(autoSpawnTrees);
            }
            if (mobsDropFragments != null) {
                config.setMobsDropFragments(mobsDropFragments);
            }
            if (fragmentDropChance != null) {
                config.setFragmentDropChance(fragmentDropChance);
            }
        }
    }

    public static void init(Path gameDir) {
        storagePath = gameDir.resolve("config").resolve("GatheringChunks").resolve(STORAGE_FILE);
        try {
            Files.createDirectories(storagePath.getParent());
            loadFromDisk();
            LOGGER.info("[ClientConfigStorage] Initialized at: {}", storagePath);
        } catch (IOException e) {
            LOGGER.error("[ClientConfigStorage] Failed to initialize", e);
        }
    }

    public static String getServerIdFromConnection(String serverAddress) {
        if (serverAddress == null || serverAddress.isEmpty()) {
            return "singleplayer";
        }
        return serverAddress.toLowerCase();
    }

    public static void endServerSession() {
        currentServerId = null;
        if (remoteSessionActive) {
            ChunkByChunkConfig.clearActiveWorld();
            remoteSessionActive = false;
        }
    }

    public static void setCurrentServer(String serverId) {
        currentServerId = serverId;
        LOGGER.debug("[ClientConfigStorage] Current server set to: {}", serverId);
    }

    public static void handleServerConfigSync(String configJson) {
        if (currentServerId == null) {
            LOGGER.warn("[ClientConfigStorage] Received config sync but no current server set");
            return;
        }

        try {
            if (configJson == null || configJson.trim().isEmpty()) {
                LOGGER.error("[ClientConfigStorage] Received empty config JSON");
                return;
            }

            ChunkByChunkConfig syncedWorld = GSON.fromJson(configJson, ChunkByChunkConfig.class);
            if (syncedWorld == null) {
                LOGGER.error("[ClientConfigStorage] Failed to deserialize config from JSON");
                return;
            }
            GatheringChunksConfig syncedConfig = syncedWorld.getGatheringChunksConfig();

            if (Minecraft.getInstance().getSingleplayerServer() != null) {
                return;
            }

            GatheringChunksConfig localConfig = ChunkByChunkConfig.get().getGatheringChunksConfig();
            List<String> mismatches = detectMismatches(localConfig, syncedConfig);

            ChunkByChunkConfig.setActiveWorld(syncedWorld);
            remoteSessionActive = true;

            if (!mismatches.isEmpty()) {
                LOGGER.info("[ClientConfigStorage] Config mismatch detected with server - {} fields overridden", mismatches.size());
                sendConfigMismatchMessage(mismatches);
            }

            rememberServerConfig(currentServerId, syncedConfig);
            LOGGER.info("[ClientConfigStorage] Applied synced config from server: {}", currentServerId);
        } catch (Exception e) {
            LOGGER.error("[ClientConfigStorage] Failed to apply synced config: {}", e.getMessage());
            LOGGER.debug("[ClientConfigStorage] Full error:", e);
        }
    }

    private static List<String> detectMismatches(GatheringChunksConfig local, GatheringChunksConfig server) {
        List<String> mismatches = new ArrayList<>();
        if (local.isPreventFluidFlowIntoVoid() != server.isPreventFluidFlowIntoVoid()) {
            mismatches.add("Prevent Fluid Flow Into Void: " + local.isPreventFluidFlowIntoVoid() + " -> " + server.isPreventFluidFlowIntoVoid());
        }
        if (local.isAutoSpawnTrees() != server.isAutoSpawnTrees()) {
            mismatches.add("Auto-Spawn Trees: " + local.isAutoSpawnTrees() + " -> " + server.isAutoSpawnTrees());
        }
        if (local.isMobsDropFragments() != server.isMobsDropFragments()) {
            mismatches.add("Mobs Drop Fragments: " + local.isMobsDropFragments() + " -> " + server.isMobsDropFragments());
        }
        if (local.getFragmentDropChance() != server.getFragmentDropChance()) {
            mismatches.add("Fragment Drop Chance: " + local.getFragmentDropChance() + "% -> " + server.getFragmentDropChance() + "%");
        }
        if (local.getMinFragmentDrop() != server.getMinFragmentDrop()) {
            mismatches.add("Min Fragment Drop: " + local.getMinFragmentDrop() + " -> " + server.getMinFragmentDrop());
        }
        if (local.getMaxFragmentDrop() != server.getMaxFragmentDrop()) {
            mismatches.add("Max Fragment Drop: " + local.getMaxFragmentDrop() + " -> " + server.getMaxFragmentDrop());
        }
        return mismatches;
    }

    private static void sendConfigMismatchMessage(List<String> mismatches) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        mc.player.displayClientMessage(Component.literal(
                "§6[Gathering Chunks] §eYour config differed from the server's. The server config has been applied:"), false);
        for (String mismatch : mismatches) {
            mc.player.displayClientMessage(Component.literal("§7  » §f" + mismatch), false);
        }
    }

    public static void rememberServerConfig(String serverId, GatheringChunksConfig config) {
        ServerConfigOverride override = new ServerConfigOverride();
        override.preventFluidFlowIntoVoid = config.isPreventFluidFlowIntoVoid();
        override.autoSpawnTrees = config.isAutoSpawnTrees();
        override.mobsDropFragments = config.isMobsDropFragments();
        override.fragmentDropChance = config.getFragmentDropChance();

        serverConfigs.put(serverId, override);
        saveToDisk();

        LOGGER.info("[ClientConfigStorage] Remembered config for server: {}", serverId);
    }

    public static void applyServerConfig(String serverId, GatheringChunksConfig config) {
        ServerConfigOverride override = serverConfigs.get(serverId);
        if (override != null) {
            override.applyToConfig(config);
            LOGGER.info("[ClientConfigStorage] Applied remembered config for server: {}", serverId);
        } else {
            LOGGER.info("[ClientConfigStorage] No remembered config for server: {}", serverId);
        }
    }

    public static boolean hasConfigForServer(String serverId) {
        return serverConfigs.containsKey(serverId);
    }

    private static void loadFromDisk() {
        if (!Files.exists(storagePath)) {
            LOGGER.info("[ClientConfigStorage] No storage file found, starting fresh");
            return;
        }

        try {
            String json = Files.readString(storagePath);
            @SuppressWarnings("unchecked")
            Map<String, ServerConfigOverride> loaded = GSON.fromJson(json, Map.class);
            if (loaded != null) {
                serverConfigs = loaded;
                LOGGER.info("[ClientConfigStorage] Loaded configs for {} servers", serverConfigs.size());
            }
        } catch (IOException e) {
            LOGGER.error("[ClientConfigStorage] Failed to load from disk", e);
        }
    }

    private static void saveToDisk() {
        try {
            String json = GSON.toJson(serverConfigs);
            Files.writeString(storagePath, json);
            LOGGER.debug("[ClientConfigStorage] Saved to disk");
        } catch (IOException e) {
            LOGGER.error("[ClientConfigStorage] Failed to save to disk", e);
        }
    }
}