/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.server.world;

import com.google.gson.Gson;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.data.SkyDimensionData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
public final class SkyDimensions {
    private SkyDimensions() {
    }
    private static final Map<Identifier, SkyDimensionData> skyDimensions = new LinkedHashMap<>();
    public static void loadSkyDimensionData(ResourceManager resourceManager, Gson gson) {
        int count = 0;
        skyDimensions.clear();
        Map<Identifier, Resource> resources = resourceManager.listResources(GatheringChunksConstants.SKY_DIMENSION_DATA_PATH, r -> r.getPath().length() > GatheringChunksConstants.SKY_DIMENSION_DATA_PATH.length());
        for (Map.Entry<Identifier, Resource> entry : resources.entrySet()) {
            Identifier location = entry.getKey();
            Resource resource = entry.getValue();
            try (InputStreamReader reader = new InputStreamReader(resource.open())) {
                SkyDimensionData data = gson.fromJson(reader, SkyDimensionData.class);
                skyDimensions.put(location, data);
                count++;
            } catch (IOException |RuntimeException e) {
                GatheringChunksConstants.LOGGER.error("Failed to read sky dimension data '{}'", location, e);
            }
        }
        GatheringChunksConstants.LOGGER.info("Loaded {} sky dimensions", count);
    }

    public static void clear() {
        skyDimensions.clear();
    }

    public static Map<Identifier, SkyDimensionData> getSkyDimensions() {
        return Collections.unmodifiableMap(skyDimensions);
    }
}