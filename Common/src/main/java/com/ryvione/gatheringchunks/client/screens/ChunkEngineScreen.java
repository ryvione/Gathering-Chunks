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
import com.ryvione.gatheringchunks.common.blockEntities.ChunkEngineBlockEntity;
import com.ryvione.gatheringchunks.common.menus.ChunkEngineMenu;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class ChunkEngineScreen extends AbstractContainerScreen<ChunkEngineMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "textures/gui/container/chunk_engine.png");

    private static final int FUEL_BAR_X = 58;
    private static final int FUEL_BAR_Y = 35;
    private static final int FUEL_BAR_WIDTH = 16;
    private static final int FUEL_BAR_MAX_HEIGHT = 14;

    public ChunkEngineScreen(ChunkEngineMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 166;
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos, topPos, 0, 0, imageWidth, imageHeight, 256, 256);

        int remainingFuel = this.menu.getRemainingTicks();
        int chargedFuel = this.menu.getData().get(ChunkEngineBlockEntity.DATA_CHARGED_FUEL);
        if (chargedFuel > 0 && remainingFuel > 0) {
            int barHeight = FUEL_BAR_MAX_HEIGHT * remainingFuel / chargedFuel;
            int barY = FUEL_BAR_Y + FUEL_BAR_MAX_HEIGHT - barHeight;
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, leftPos + FUEL_BAR_X, topPos + barY, 176, FUEL_BAR_MAX_HEIGHT - barHeight, FUEL_BAR_WIDTH, barHeight, 256, 256);
        }

        renderHelpIcon(guiGraphics, mouseX, mouseY);
    }


    private void renderHelpIcon(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int helpX = imageWidth - 18;
        int helpY = 6;
        boolean hovered = mouseX >= leftPos + helpX && mouseX <= leftPos + helpX + 10 &&
                mouseY >= topPos + helpY && mouseY <= topPos + helpY + 10;

        int bgColor = hovered ? 0xFF888888 : 0xFF444444;
        int textColor = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;

        guiGraphics.fill(leftPos + helpX, topPos + helpY, leftPos + helpX + 10, topPos + helpY + 10, bgColor);
        guiGraphics.drawString(font, "?", leftPos + helpX + 3, topPos + helpY + 1, textColor, false);
    }


    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);

        int helpX = imageWidth - 18;
        int helpY = 6;
        if (mouseX >= leftPos + helpX && mouseX <= leftPos + helpX + 10 &&
                mouseY >= topPos + helpY && mouseY <= topPos + helpY + 10) {
            guiGraphics.setTooltipForNextFrame(font, java.util.List.of(
                    Component.literal("§6Chunk Engine").getVisualOrderText(),
                    Component.literal("§fMaintains chunks in §cHard Mode§f.").getVisualOrderText(),
                    Component.literal("§7If a chunk has no active engine,").getVisualOrderText(),
                    Component.literal("§7it will eventually reset to air.").getVisualOrderText(),
                    Component.literal("§f- §eRequires Fuel: §7World Shards/Crystals").getVisualOrderText()
            ), mouseX, mouseY);
        }
    }


    @Override
    protected void renderLabels(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        guiGraphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0xFF404040, false);
        guiGraphics.drawString(this.font, this.playerInventoryTitle, this.inventoryLabelX, this.inventoryLabelY, 0xFF404040, false);
    }
}
