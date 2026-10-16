/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.client.screens;

import com.mojang.blaze3d.systems.RenderSystem;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.menus.WorldMenderMenu;
import com.ryvione.gatheringchunks.common.util.SpiralIterator;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class WorldMenderScreen extends AbstractContainerScreen<WorldMenderMenu> {

    public static final Identifier CONTAINER_TEXTURE = Identifier.fromNamespaceAndPath(
            GatheringChunksConstants.MOD_ID, "textures/gui/container/worldmender.png"
    );

    private static final int TEX_WIDTH = 512;
    private static final int TEX_HEIGHT = 512;
    private static final float TICKS_PER_FRAME = 4f;
    private static final int FRAME_COUNT = 8;
    private static final int HIGHLIGHT_SIZE = 128;
    private static final int HIGHLIGHT_INSET_X = 24;
    private static final int HIGHLIGHT_INSET_Y = 13;

    private static final int[][] NODE_OFFSETS = {
            {3},
            {5, 3},
            {6, 5, 3},
            {7, 6, 5, 3},
            {7, 7, 7, 6, 4},
            {7, 8, 8, 8, 7, 4},
            {7, 8, 9, 9, 9, 7, 5}
    };

    private float animationTime = 0f;

    public WorldMenderScreen(WorldMenderMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 235;
        this.titleLabelX = 8;
        this.titleLabelY = 4;
        this.inventoryLabelX = 8;
        this.inventoryLabelY = 143;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        animationTime += partialTick;
        while (animationTime >= TICKS_PER_FRAME * FRAME_COUNT) {
            animationTime -= TICKS_PER_FRAME * FRAME_COUNT;
        }
        int frame = Mth.floor(animationTime / TICKS_PER_FRAME);

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, TEX_WIDTH, TEX_HEIGHT);

        SpiralIterator iterator = new SpiralIterator(0, 0);
        for (int i = 0; i < menu.getChunksSpawned(); i++) {
            Pos pos = getChunkPos(iterator.getX(), iterator.getY());
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos + HIGHLIGHT_INSET_X + pos.x(), topPos + HIGHLIGHT_INSET_Y + pos.y(), HIGHLIGHT_SIZE + pos.x(), frame * HIGHLIGHT_SIZE + pos.y(), 2, 2, TEX_WIDTH, TEX_HEIGHT);
            iterator.next();
        }
    }

    private Pos getChunkPos(int chunkX, int chunkZ) {
        int absX = Math.abs(chunkX);
        int absZ = Math.abs(chunkZ);
        int signX = Integer.signum(chunkX);
        int signZ = Integer.signum(chunkZ);

        int xOffset = chunkX * 7 + 6 * signX + 63;
        int yOffset = chunkZ * 7 + 6 * signZ + 63;

        if (absX > 0 && absZ > absX) {
            xOffset -= signX * NODE_OFFSETS[absZ - 2][absX - 1];
        } else if (absZ > 0 && absX > absZ) {
            yOffset -= signZ * NODE_OFFSETS[absX - 2][absZ - 1];
        }

        return new Pos(xOffset, yOffset);
    }

    private record Pos(int x, int y) {}

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
        guiGraphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF404040, false);
    }
}
