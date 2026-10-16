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
import com.ryvione.gatheringchunks.common.blockEntities.CaveScannerBlockEntity;
import com.ryvione.gatheringchunks.common.menus.CaveScannerMenu;
import com.ryvione.gatheringchunks.common.util.ConfigUtil;
import com.ryvione.gatheringchunks.config.WorldScannerConfig;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.state.MapRenderState;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;

public class CaveScannerScreen extends AbstractContainerScreen<CaveScannerMenu> {

    public static final Identifier CONTAINER_TEXTURE = Identifier.fromNamespaceAndPath(
            GatheringChunksConstants.MOD_ID, "textures/gui/container/cavescanner.png"
    );

    private static final int MAIN_TEXTURE_DIM = 512;
    private static final int MAP_DIMENSIONS = 128;
    private static final float TICKS_PER_FRAME = 4f;
    private static final int NUM_FRAMES = 8;
    private static final int DRAG_THRESHOLD = 3;

    private float animCounter = 0f;
    private Button espButton;
    private boolean lastExperimental = false;

    private float zoomLevel = 1.0f;
    private static final float MIN_ZOOM = 0.5f;
    private static final float MAX_ZOOM = 3.0f;
    private static final float ZOOM_STEP = 0.1f;
    private float panX = 0f;
    private float panY = 0f;

    private boolean isMiddlePanning = false;
    private boolean isLeftPanning = false;
    private double lastMouseX = 0;
    private double lastMouseY = 0;
    private double clickStartX = 0;
    private double clickStartY = 0;
    private boolean didDrag = false;

    private long lastClickTime = 0;
    private int lastClickChunkX = Integer.MIN_VALUE;
    private int lastClickChunkZ = Integer.MIN_VALUE;
    private static final long DOUBLE_CLICK_MS = 400;
    private int selectedChunkX = Integer.MIN_VALUE;
    private int selectedChunkZ = Integer.MIN_VALUE;

    private static final int PROGRESS_BAR_WIDTH = 50;
    private static final int PROGRESS_BAR_HEIGHT = 5;

    public CaveScannerScreen(CaveScannerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 310;
        this.imageHeight = 166;
    }

    @Override
    protected void init() {
        super.init();
        boolean experimental = ChunkByChunkConfig.get().getWorldScannerConfig().isExperimentalMode();
        this.lastExperimental = experimental;
        if (experimental) {
            espButton = this.addRenderableWidget(Button.builder(getEspMessage(), (button) ->
                    this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 0)
            ).bounds(leftPos + 80, topPos + 20, 60, 20).build());
        }
    }

    private Component getEspMessage() {
        return Component.literal("ESP: ").append(menu.isEspEnabled()
                ? Component.literal("ON").withStyle(ChatFormatting.GREEN)
                : Component.literal("OFF").withStyle(ChatFormatting.RED));
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        boolean experimental = ChunkByChunkConfig.get().getWorldScannerConfig().isExperimentalMode();
        if (experimental != this.lastExperimental) {
            this.init();
        }
        if (espButton != null) {
            espButton.visible = experimental;
            if (experimental) {
                espButton.setMessage(getEspMessage());
            }
        }
        renderBackground(guiGraphics, mouseX, mouseY, partialTick);
        super.render(guiGraphics, mouseX, mouseY, partialTick);
        renderTooltip(guiGraphics, mouseX, mouseY);
    }

    @Override
    protected void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        super.renderTooltip(guiGraphics, mouseX, mouseY);

        int mapX = (int) ((mouseX - leftPos - 174 - panX) / zoomLevel);
        int mapY = (int) ((mouseY - topPos - 18 - panY) / zoomLevel);

        if (mapX >= 0 && mapY >= 0 && mapX < MAP_DIMENSIONS && mapY < MAP_DIMENSIONS) {
            int chunkX = mapX / CaveScannerBlockEntity.SCAN_ZOOM - CaveScannerBlockEntity.SCAN_CENTER;
            int chunkZ = mapY / CaveScannerBlockEntity.SCAN_ZOOM - CaveScannerBlockEntity.SCAN_CENTER;
            guiGraphics.setTooltipForNextFrame(font, Component.literal("Chunk: [" + (menu.getScannerChunkX() + chunkX) + ", "
                    + (menu.getScannerChunkZ() + chunkZ) + "]  (offset " + chunkX + ", " + chunkZ + ")"), mouseX, mouseY);
        }

        int helpX = 285;
        int helpY = 6;
        if (mouseX >= leftPos + helpX && mouseX <= leftPos + helpX + 10 &&
                mouseY >= topPos + helpY && mouseY <= topPos + helpY + 10) {
            guiGraphics.setTooltipForNextFrame(font, java.util.List.of(
                    Component.literal("§6Cave Types:").getVisualOrderText(),
                    Component.literal("§f- §aGreen: §7Lush Caves").getVisualOrderText(),
                    Component.literal("§f- §6Brown: §7Dripstone Caves").getVisualOrderText(),
                    Component.literal("§f- §8Dark: §7Deep Dark").getVisualOrderText(),
                    Component.literal("§f- §bCyan: §7Frozen/Ice Cave").getVisualOrderText(),
                    Component.literal("§f- §cRed: §7Nether Biome").getVisualOrderText(),
                    Component.literal("§f- §7Gray: §7Generic Cave").getVisualOrderText(),
                    Component.literal("§f- §0Black: §7No Special Cave").getVisualOrderText(),
                    Component.literal("").getVisualOrderText(),
                    Component.literal("§6Controls:").getVisualOrderText(),
                    Component.literal("§f- §eCtrl+Scroll: §7Zoom in/out").getVisualOrderText(),
                    Component.literal("§f- §eDrag: §7Pan map").getVisualOrderText(),
                    Component.literal("§f- §eSingle Click: §7Select chunk").getVisualOrderText(),
                    Component.literal("§f- §eDouble Click: §7Scan selected chunk").getVisualOrderText()
            ), mouseX, topPos + helpY + 12);
        }
    }

    @Override
    protected void renderBg(GuiGraphics guiGraphics, float partialTick, int mouseX, int mouseY) {
        animCounter += partialTick;
        while (animCounter > TICKS_PER_FRAME * NUM_FRAMES) {
            animCounter -= TICKS_PER_FRAME * NUM_FRAMES;
        }


        guiGraphics.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_TEXTURE, leftPos, topPos, 0, 0, this.imageWidth, this.imageHeight, MAIN_TEXTURE_DIM, MAIN_TEXTURE_DIM);

        if (menu.getMaxEnergy() > 0) {
            float energyPercent = (float) menu.getEnergy() / menu.getMaxEnergy();
            int barX = leftPos + 92;
            int barY = topPos + 63;
            int filledWidth = (int) (PROGRESS_BAR_WIDTH * energyPercent);

            guiGraphics.fill(barX - 1, barY - 1, barX + PROGRESS_BAR_WIDTH + 1, barY + PROGRESS_BAR_HEIGHT + 1, 0x88FFFFFF);
            guiGraphics.fill(barX, barY, barX + PROGRESS_BAR_WIDTH, barY + PROGRESS_BAR_HEIGHT, 0xFF333333);
            guiGraphics.fill(barX, barY, barX + filledWidth, barY + PROGRESS_BAR_HEIGHT, 0xFF00FFFF);
            guiGraphics.drawString(font, (int) (energyPercent * 100) + "%", barX + PROGRESS_BAR_WIDTH + 3, barY - 1, 0xFFFFFFFF);
        }

        if (menu.isMapAvailable()) {
            renderMap(guiGraphics);
            renderGridOverlay(guiGraphics);
        }

        renderCoordinatesAndControls(guiGraphics);
        renderHelpIcon(guiGraphics, mouseX, mouseY);
    }

    private void renderHelpIcon(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        int helpX = 285;
        int helpY = 6;
        boolean hovered = mouseX >= leftPos + helpX && mouseX <= leftPos + helpX + 10 &&
                mouseY >= topPos + helpY && mouseY <= topPos + helpY + 10;

        int bgColor = hovered ? 0xFF888888 : 0xFF444444;
        int textColor = hovered ? 0xFFFFFFFF : 0xFFCCCCCC;

        guiGraphics.fill(leftPos + helpX, topPos + helpY, leftPos + helpX + 10, topPos + helpY + 10, bgColor);
        guiGraphics.drawString(font, "?", leftPos + helpX + 3, topPos + helpY + 1, textColor, false);
    }


    private void renderMap(GuiGraphics guiGraphics) {
        int mapScreenX = leftPos + 174;
        int mapScreenY = topPos + 18;

        guiGraphics.enableScissor(mapScreenX, mapScreenY, mapScreenX + MAP_DIMENSIONS, mapScreenY + MAP_DIMENSIONS);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(mapScreenX + panX, mapScreenY + panY);
        guiGraphics.pose().scale(zoomLevel, zoomLevel);

        var mapId = menu.getMapId();
        if (mapId != null) {
            var mapData = minecraft.level.getMapData(mapId);
            if (mapData != null) {
                var mapState = new MapRenderState();
                minecraft.getMapRenderer().extractRenderState(mapId, mapData, mapState);
                guiGraphics.submitMapRenderState(mapState);
            }
        }

        guiGraphics.pose().popMatrix();
        guiGraphics.disableScissor();
    }

    private void renderGridOverlay(GuiGraphics guiGraphics) {
        WorldScannerConfig config = ChunkByChunkConfig.get().getWorldScannerConfig();
        if (config.getWorldScannerScanMode() != WorldScannerConfig.WorldScannerMode.Manual) {
            return;
        }

        int mapScreenX = leftPos + 174;
        int mapScreenY = topPos + 18;
        int chunkPixelSize = CaveScannerBlockEntity.SCAN_ZOOM;

        float visMinX = -panX / zoomLevel;
        float visMaxX = (MAP_DIMENSIONS - panX) / zoomLevel;
        float visMinY = -panY / zoomLevel;
        float visMaxY = (MAP_DIMENSIONS - panY) / zoomLevel;

        int startX = (int) Math.floor(visMinX / chunkPixelSize) * chunkPixelSize;
        int endX = (int) Math.ceil(visMaxX / chunkPixelSize) * chunkPixelSize;
        int startY = (int) Math.floor(visMinY / chunkPixelSize) * chunkPixelSize;
        int endY = (int) Math.ceil(visMaxY / chunkPixelSize) * chunkPixelSize;

        guiGraphics.enableScissor(mapScreenX, mapScreenY, mapScreenX + MAP_DIMENSIONS, mapScreenY + MAP_DIMENSIONS);
        guiGraphics.pose().pushMatrix();
        guiGraphics.pose().translate(mapScreenX + panX, mapScreenY + panY);
        guiGraphics.pose().scale(zoomLevel, zoomLevel);

        for (int x = startX; x <= endX; x += chunkPixelSize) {
            guiGraphics.fill(x, startY, x + 1, endY, 0x8800FF00);
        }
        for (int y = startY; y <= endY; y += chunkPixelSize) {
            guiGraphics.fill(startX, y, endX, y + 1, 0x8800FF00);
        }

        if (selectedChunkX != Integer.MIN_VALUE && selectedChunkZ != Integer.MIN_VALUE) {
            int selX = (CaveScannerBlockEntity.SCAN_CENTER + selectedChunkX) * chunkPixelSize;
            int selZ = (CaveScannerBlockEntity.SCAN_CENTER + selectedChunkZ) * chunkPixelSize;
            boolean isPending = selectedChunkX != config.getManualChunkOffsetX()
                    || selectedChunkZ != config.getManualChunkOffsetZ();
            int color = isPending ? 0xFFFFFFFF : 0xFFFFFF00;
            guiGraphics.fill(selX, selZ, selX + chunkPixelSize, selZ + 1, color);
            guiGraphics.fill(selX, selZ + chunkPixelSize - 1, selX + chunkPixelSize, selZ + chunkPixelSize, color);
            guiGraphics.fill(selX, selZ, selX + 1, selZ + chunkPixelSize, color);
            guiGraphics.fill(selX + chunkPixelSize - 1, selZ, selX + chunkPixelSize, selZ + chunkPixelSize, color);
        }

        guiGraphics.pose().popMatrix();
        guiGraphics.disableScissor();
    }

    private void renderCoordinatesAndControls(GuiGraphics guiGraphics) {
        WorldScannerConfig config = ChunkByChunkConfig.get().getWorldScannerConfig();
        if (config.getWorldScannerScanMode() == WorldScannerConfig.WorldScannerMode.Manual) {
            int x = leftPos + 8;
            int y = topPos + 102;
            guiGraphics.drawString(font, "§eX: " + config.getManualChunkOffsetX(), x, y, 0xFFFFFFFF);
            guiGraphics.drawString(font, "§eZ: " + config.getManualChunkOffsetZ(), x, y + 10, 0xFFFFFFFF);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (minecraft.hasControlDown()) {
            int relX = (int) (mouseX - leftPos - 174);
            int relY = (int) (mouseY - topPos - 18);

            if (relX >= -10 && relY >= -10 && relX < MAP_DIMENSIONS + 10 && relY < MAP_DIMENSIONS + 10) {
                float oldZoom = zoomLevel;
                if (scrollY > 0) {
                    zoomLevel = Math.min(MAX_ZOOM, zoomLevel + ZOOM_STEP);
                } else {
                    zoomLevel = Math.max(MIN_ZOOM, zoomLevel - ZOOM_STEP);
                }

                if (oldZoom != zoomLevel) {
                    float ratio = zoomLevel / oldZoom;
                    panX = relX - (relX - panX) * ratio;
                    panY = relY - (relY - panY) * ratio;
                    clampPan();
                }
                return true;
            }
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    private void clampPan() {
        float scaledSize = MAP_DIMENSIONS * zoomLevel;
        float minPan = Math.min(0, MAP_DIMENSIONS - scaledSize);
        float maxPan = Math.max(0, MAP_DIMENSIONS - scaledSize);
        panX = Math.max(minPan, Math.min(maxPan, panX));
        panY = Math.max(minPan, Math.min(maxPan, panY));
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        int relX = (int) (mouseX - leftPos - 174);
        int relY = (int) (mouseY - topPos - 18);
        boolean overMap = relX >= 0 && relY >= 0 && relX < MAP_DIMENSIONS && relY < MAP_DIMENSIONS;

        if (button == 2 && overMap) {
            isMiddlePanning = true;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }

        if (button == 0 && overMap) {
            isLeftPanning = true;
            clickStartX = mouseX;
            clickStartY = mouseY;
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            didDrag = false;
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 2) {
            isMiddlePanning = false;
            return true;
        }
        if (button == 0 && isLeftPanning) {
            isLeftPanning = false;
            if (!didDrag && ChunkByChunkConfig.get().getWorldScannerConfig().getWorldScannerScanMode()
                    == WorldScannerConfig.WorldScannerMode.Manual) {
                int relX = (int) ((mouseX - leftPos - 174 - panX) / zoomLevel);
                int relY = (int) ((mouseY - topPos - 18 - panY) / zoomLevel);
                if (relX >= 0 && relY >= 0 && relX < MAP_DIMENSIONS && relY < MAP_DIMENSIONS) {
                    int chunkX = Mth.clamp(relX / CaveScannerBlockEntity.SCAN_ZOOM - CaveScannerBlockEntity.SCAN_CENTER, -15, 15);
                    int chunkZ = Mth.clamp(relY / CaveScannerBlockEntity.SCAN_ZOOM - CaveScannerBlockEntity.SCAN_CENTER, -15, 15);

                    long now = System.currentTimeMillis();
                    boolean isDoubleClick = (now - lastClickTime) < DOUBLE_CLICK_MS
                            && lastClickChunkX == chunkX
                            && lastClickChunkZ == chunkZ;

                    if (isDoubleClick) {
                        WorldScannerConfig config = ChunkByChunkConfig.get().getWorldScannerConfig();
                        config.setManualChunkOffsetX(chunkX);
                        config.setManualChunkOffsetZ(chunkZ);
                        selectedChunkX = chunkX;
                        selectedChunkZ = chunkZ;
                        ConfigUtil.saveDefaultConfig();
                        this.minecraft.gameMode.handleInventoryButtonClick(menu.containerId, 1);
                        lastClickTime = 0;
                    } else {
                        selectedChunkX = chunkX;
                        selectedChunkZ = chunkZ;
                        lastClickTime = now;
                        lastClickChunkX = chunkX;
                        lastClickChunkZ = chunkZ;
                    }
                }
            }
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (isMiddlePanning && button == 2) {
            panX += (float) (mouseX - lastMouseX);
            panY += (float) (mouseY - lastMouseY);
            clampPan();
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        if (isLeftPanning && button == 0) {
            double dx = mouseX - clickStartX;
            double dy = mouseY - clickStartY;
            if (Math.sqrt(dx * dx + dy * dy) > DRAG_THRESHOLD) {
                didDrag = true;
            }
            if (didDrag) {
                panX += (float) (mouseX - lastMouseX);
                panY += (float) (mouseY - lastMouseY);
                clampPan();
            }
            lastMouseX = mouseX;
            lastMouseY = mouseY;
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }
}
