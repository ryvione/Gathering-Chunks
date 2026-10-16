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
import com.google.gson.JsonDeserializer;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import com.ryvione.gatheringchunks.server.world.SkyDimensions;
import net.minecraft.core.RegistryAccess;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;



public class SkyDimensionDataLoader extends SimplePreparableReloadListener<Void> {
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .registerTypeAdapter(SkyChunkGenerator.EmptyGenerationType.class,
                    (JsonDeserializer<SkyChunkGenerator.EmptyGenerationType>) (json, typeOfT, context) -> 
                            SkyChunkGenerator.EmptyGenerationType.getFromString(json.getAsString()))
            .create();
    private final RegistryAccess registryAccess;

    public SkyDimensionDataLoader(RegistryAccess registryAccess) {
        this.registryAccess = registryAccess;
    }

    @Override
    protected Void prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        return null;
    }

    @Override
    protected void apply(Void preparations, ResourceManager resourceManager, ProfilerFiller profiler) {
        GatheringChunksConstants.LOGGER.debug("SkyDimensionDataLoader: Delegating to SkyDimensions.loadSkyDimensionData()");
        SkyDimensions.loadSkyDimensionData(resourceManager, GSON);
    }
}