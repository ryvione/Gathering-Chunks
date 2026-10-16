/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.common.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.HashMap;
import java.util.Map;

public class ScannerDataLoader extends SimplePreparableReloadListener<Map<Identifier, JsonElement>> {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String DIRECTORY = "scanner_data";

    private final RegistryAccess registryAccess;

    public ScannerDataLoader(RegistryAccess registryAccess) {
        this.registryAccess = registryAccess;
    }

    @Override
    protected Map<Identifier, JsonElement> prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<Identifier, JsonElement> result = new HashMap<>();
        String prefix = DIRECTORY + "/";
        for (Map.Entry<Identifier, Resource> entry
                : resourceManager.listResources(DIRECTORY, id -> id.getPath().endsWith(".json")).entrySet()) {
            Identifier fileId = entry.getKey();
            String path = fileId.getPath();
            Identifier id = fileId.withPath(path.substring(prefix.length(), path.length() - ".json".length()));
            try (Reader reader = entry.getValue().openAsReader()) {
                result.put(id, GSON.fromJson(reader, JsonElement.class));
            } catch (Exception e) {
                GatheringChunksConstants.LOGGER.error("Failed to read scanner data {}", fileId, e);
            }
        }
        return result;
    }

    @Override
    protected void apply(Map<Identifier, JsonElement> map, ResourceManager resourceManager, ProfilerFiller profiler) {
        GatheringChunksConstants.LOGGER.info("Loading scanner data configs...");
        com.ryvione.gatheringchunks.common.blockEntities.WorldScannerBlockEntity.clearItemMappings();
        int loaded = 0;

        for (Map.Entry<Identifier, JsonElement> entry : map.entrySet()) {
            Identifier id = entry.getKey();
            JsonElement json = entry.getValue();

            try {
                ScannerDataJson data = GSON.fromJson(json, ScannerDataJson.class);

                if (data == null || data.inputItems == null || data.targetBlocks == null) {
                    GatheringChunksConstants.LOGGER.error("Invalid scanner data format for {}", id);
                    continue;
                }

                java.util.List<String> processedInputs = new java.util.ArrayList<>();
                for (String item : data.inputItems) {
                    processedInputs.add(item.contains(":") ? item : "minecraft:" + item);
                }

                java.util.List<String> processedBlocks = new java.util.ArrayList<>();
                for (String block : data.targetBlocks) {
                    processedBlocks.add(block.contains(":") ? block : "minecraft:" + block);
                }

                ScannerData scannerData = new ScannerData(processedInputs, processedBlocks);
                scannerData.process(id, registryAccess);
                loaded++;

                GatheringChunksConstants.LOGGER.debug("Loaded scanner data: {}", id);

            } catch (Exception e) {
                GatheringChunksConstants.LOGGER.error("Failed to load scanner data {}: {}", id, e.getMessage(), e);
            }
        }

        GatheringChunksConstants.LOGGER.info("Loaded {} scanner data configs", loaded);
    }


    private static class ScannerDataJson {
        public java.util.List<String> inputItems;
        public java.util.List<String> targetBlocks;
    }
}