/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.util;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import net.minecraft.resources.Identifier;

public class GuiTextures {

    public static final Identifier WORLD_FORGE_TEXTURE = Identifier.fromNamespaceAndPath(
            GatheringChunksConstants.MOD_ID, "textures/gui/container/worldforge.png"
    );
    public static final int WORLD_FORGE_TEX_WIDTH = 256;
    public static final int WORLD_FORGE_TEX_HEIGHT = 256;
    public static final float WORLD_FORGE_TICKS_PER_FRAME = 2f;
    public static final int WORLD_FORGE_FRAME_COUNT = 8;
    public static final int WORLD_FORGE_PROGRESS_U = 176;
    public static final int WORLD_FORGE_PROGRESS_V_BASE = 0;
    public static final int WORLD_FORGE_PROGRESS_HEIGHT = 11;
    public static final Identifier WORLD_MENDER_TEXTURE = Identifier.fromNamespaceAndPath(
            GatheringChunksConstants.MOD_ID, "textures/gui/container/worldmender.png"
    );
    public static final int WORLD_MENDER_TEX_WIDTH = 512;
    public static final int WORLD_MENDER_TEX_HEIGHT = 512;
    public static final float WORLD_MENDER_TICKS_PER_FRAME = 4f;
    public static final int WORLD_MENDER_FRAME_COUNT = 8;
    public static final int WORLD_MENDER_HIGHLIGHT_SIZE = 128;
    public static final Identifier BEDROCK_CHEST_TEXTURE = Identifier.fromNamespaceAndPath(
            GatheringChunksConstants.MOD_ID, "textures/gui/container/bedrockchest.png"
    );
}