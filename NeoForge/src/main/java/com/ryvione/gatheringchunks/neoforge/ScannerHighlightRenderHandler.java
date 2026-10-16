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

import com.ryvione.gatheringchunks.client.render.ExperimentalScannerRenderer;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

@EventBusSubscriber(modid = GatheringChunksConstants.MOD_ID, value = Dist.CLIENT)
public class ScannerHighlightRenderHandler {

    @SubscribeEvent
    public static void onAfterLevel(RenderLevelStageEvent.AfterLevel event) {
        ExperimentalScannerRenderer.renderHighlights();
    }
}
