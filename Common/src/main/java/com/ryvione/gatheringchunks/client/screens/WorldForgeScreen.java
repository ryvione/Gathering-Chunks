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
import com.ryvione.gatheringchunks.common.menus.WorldForgeMenu;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;

public class WorldForgeScreen extends AbstractContainerScreen<WorldForgeMenu> {

    public static final Identifier CONTAINER_TEXTURE = Identifier.fromNamespaceAndPath(
            GatheringChunksConstants.MOD_ID, "textures/gui/container/worldforge.png"
    );

    public static final float TICKS_PER_FRAME = 2f;
    public static final int NUM_FRAMES = 8;
    private static final int PROGRESS_WIDTH_MAX = 30;
    private static final int PROGRESS_HEIGHT = 11;

    private float animationTime = 0f;

    public WorldForgeScreen(WorldForgeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        animationTime += partialTick;
        while (animationTime >= TICKS_PER_FRAME * NUM_FRAMES) {
            animationTime -= TICKS_PER_FRAME * NUM_FRAMES;
        }
        int frame = Mth.floor(animationTime / TICKS_PER_FRAME);

        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        if (menu.getProgress() > 0) {
            int goal = menu.getGoal();
            if (goal > 0) {
                int progress = Math.min(goal, menu.getProgress());
                int barWidth = PROGRESS_WIDTH_MAX * progress / goal;
                guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos + 78, topPos + 37, 176, frame * PROGRESS_HEIGHT, barWidth, PROGRESS_HEIGHT, 256, 256);
            }
        }
    }

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
