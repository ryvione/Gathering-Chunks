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

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import net.minecraft.core.MappedRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.dimension.LevelStem;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class SkyDimensionData {

    public String dimensionId;
    public String genDimensionId = "";
    public boolean enabled = true;
    public boolean allowChunkSpawner = true;
    public boolean allowUnstableChunkSpawner = true;
    public SkyChunkGenerator.EmptyGenerationType generationType = SkyChunkGenerator.EmptyGenerationType.Normal;
    public String sealBlock = "minecraft:bedrock";
    public String sealCoverBlock = "";
    public String unspawnedBiome = "";
    public int initialChunks = 1;
    public List<String> synchToDimensions = new ArrayList<>();

    public String biomeThemeDimensionType;

    public Map<String, List<String>> biomeThemes = new LinkedHashMap<>();

    public boolean validate(Identifier dataId, MappedRegistry<LevelStem> dimensions) {
        if (!dimensions.containsKey(Identifier.parse(dimensionId))) {
            GatheringChunksConstants.LOGGER.error("Invalid dimension '{}' for sky dimension {}", dimensionId, dataId);
            return false;
        }

        if (synchToDimensions == null) {
            GatheringChunksConstants.LOGGER.error("Invalid synchDimensions array for sky dimension {}", dataId);
            return false;
        }
        for (String dim : synchToDimensions) {
            if (!dimensions.containsKey(Identifier.parse(dim))) {
                GatheringChunksConstants.LOGGER.error("Invalid synch dimension '{}' for sky dimension {}", dim, dataId);
                return false;
            }
        }
        return true;
    }

    public Identifier getGenDimensionId() {
        if (genDimensionId == null) {
            return Identifier.parse(dimensionId + "_gen");
        } else {
            return Identifier.parse(genDimensionId);
        }
    }
}