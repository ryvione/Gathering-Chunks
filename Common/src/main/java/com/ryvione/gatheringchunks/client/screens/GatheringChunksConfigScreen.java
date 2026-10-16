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

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.config.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GatheringChunksConfigScreen extends Screen {

    private final Screen parentScreen;
    private final GatheringChunksConfig config;

    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 24;
    private static final int SECTION_SPACING = 10;
    private int scrollOffset = 0;
    private static final int SCROLL_SPEED = 20;

    private int contentHeight = 0;
    private boolean isScrollbarDragging = false;
    private int scrollbarWidth = 10;

    public enum Mode {
        GLOBAL,
        WORLD_CREATION,
        WORLD_LOCKED
    }

    private final Mode mode;
    private final boolean fullyLocked;

    private static final String[] EDITABLE_AFTER_CREATION = {
            "Chest Contents", "Chest Quantity", "Chests/Chunk", "Min Chest Depth", "Max Chest Depth",
            "Fragment Ore", "Disable Water-Only Chunks", "Chunk Layer Spawn Rate",
            "Block Placement Outside Chunks", "Disable Leaf Decay", "Enable Chunk Barriers", "Unstable Chunk Chance",
            "Auto-Spawn Trees", "Prevent Fluid Flow Into Void", "Enable Fragment Ore", "Enable Cave Scanner",
            "Mobs Drop Fragments", "Fragment Drop Chance", "Min Fragment Drop", "Max Fragment Drop",
            "Themed Spawner Source"
    };

    private static boolean isEditableAfterCreation(String label) {
        for (String prefix : EDITABLE_AFTER_CREATION) {
            if (label.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    public GatheringChunksConfigScreen(Screen parentScreen) {
        this(parentScreen, Mode.GLOBAL);
    }

    private GatheringChunksConfigScreen(Screen parentScreen, Mode mode) {
        super(Component.literal("Gathering Chunks Configuration"));
        this.parentScreen = parentScreen;
        this.mode = mode;
        this.fullyLocked = mode == Mode.WORLD_LOCKED && Minecraft.getInstance().getSingleplayerServer() == null;
        this.config = ChunkByChunkConfig.get().getGatheringChunksConfig();
    }

    public static GatheringChunksConfigScreen forWorldCreation(Screen parentScreen, ChunkByChunkConfig draft) {
        ChunkByChunkConfig.setActiveWorld(ChunkByChunkConfig.global().worldView(draft));
        return new GatheringChunksConfigScreen(parentScreen, Mode.WORLD_CREATION);
    }

    public static GatheringChunksConfigScreen forLoadedWorld(Screen parentScreen) {
        return new GatheringChunksConfigScreen(parentScreen, Mode.WORLD_LOCKED);
    }

    @Override
    protected <T extends GuiEventListener & Renderable & NarratableEntry> T addRenderableWidget(T widget) {
        if (mode == Mode.WORLD_LOCKED && widget instanceof AbstractWidget control) {
            String label = control.getMessage().getString();
            if (!"Done".equals(label) && (fullyLocked || !isEditableAfterCreation(label))) {
                control.active = false;
            }
        }
        return super.addRenderableWidget(widget);
    }

    @Override
    protected void init() {
        super.init();
        this.clearWidgets();

        int centerX = this.width / 2;
        int startY = 40 - scrollOffset;
        int currentY = startY;

        if (mode == Mode.GLOBAL) {
            currentY = addSectionLabel(centerX, currentY, "These settings apply to every world.", 0xFFAAAAAA);

            currentY += SECTION_SPACING;
            currentY = addSectionLabel(centerX, currentY, "Debug", 0xFF55FFFF);

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.global().getDebug().isVerboseLogging())
                    .withTooltip(value -> Tooltip.create(Component.literal("Log extra detail about spawning and chunk copying")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Verbose Logging"),
                            (button, value) -> ChunkByChunkConfig.global().getDebug().setVerboseLogging(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.global().getDebug().isPerformanceTimings())
                    .withTooltip(value -> Tooltip.create(Component.literal("Log how long slow chunk copies and structure searches take")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Performance Timings"),
                            (button, value) -> ChunkByChunkConfig.global().getDebug().setPerformanceTimings(value)));
            currentY += SPACING;

            currentY += SECTION_SPACING;
            currentY = addSectionLabel(centerX, currentY, "Performance", 0xFF55FF55);

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Spawn Search Time Limit: " + ChunkByChunkConfig.global().getPerformance().getSpawnSearchTimeLimitSeconds() + "s"),
                    (ChunkByChunkConfig.global().getPerformance().getSpawnSearchTimeLimitSeconds() - 10) / 590.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Longest time the first spawn chunk search may run (10-600 seconds)")));
                }
                @Override
                protected void updateMessage() {
                    setMessage(Component.literal("Spawn Search Time Limit: " + ((int) (this.value * 590) + 10) + "s"));
                }
                @Override
                protected void applyValue() {
                    ChunkByChunkConfig.global().getPerformance().setSpawnSearchTimeLimitSeconds((int) (this.value * 590) + 10);
                }
            });
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Spawn Search Max Chunks: " + ChunkByChunkConfig.global().getPerformance().getSpawnSearchMaxChunks()),
                    (ChunkByChunkConfig.global().getPerformance().getSpawnSearchMaxChunks() - 50) / 4950.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Most chunks the first spawn chunk search will check (50-5000)")));
                }
                @Override
                protected void updateMessage() {
                    setMessage(Component.literal("Spawn Search Max Chunks: " + ((int) (this.value * 4950) + 50)));
                }
                @Override
                protected void applyValue() {
                    ChunkByChunkConfig.global().getPerformance().setSpawnSearchMaxChunks((int) (this.value * 4950) + 50);
                }
            });
            currentY += SPACING;

            this.addRenderableWidget(Button.builder(
                            Component.literal(prefetchLabel()),
                            button -> {
                                int next = ChunkByChunkConfig.global().getPerformance().getSpawnSearchPrefetch() + 1;
                                ChunkByChunkConfig.global().getPerformance().setSpawnSearchPrefetch(next > 4 ? -1 : next);
                                button.setMessage(Component.literal(prefetchLabel()));
                            })
                    .bounds(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Candidate chunks the first spawn search loads ahead so they generate in parallel. Auto uses your core count; 0 is off.")))
                    .build());
            currentY += SPACING;

            currentY += SECTION_SPACING;

            currentY = addSectionLabel(centerX, currentY, "Experimental", 0xFFFF5555);

            boolean isExperimental = ChunkByChunkConfig.get().getWorldScannerConfig().isExperimentalMode();

            this.addRenderableWidget(CycleButton.onOffBuilder(isExperimental)
                    .withTooltip(value -> Tooltip.create(Component.literal("Enable experimental features for world scanner (ESP, Manual Mode)")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("World Scanner: Experimental Mode"),
                            (button, value) -> {
                                ChunkByChunkConfig.get().getWorldScannerConfig().setExperimentalMode(value);
                                if (!value) {
                                    ChunkByChunkConfig.get().getWorldScannerConfig().setWorldScannerScanMode(
                                            com.ryvione.gatheringchunks.config.WorldScannerConfig.WorldScannerMode.Auto);
                                }
                                this.rebuildWidgets();
                            }));
            currentY += SPACING;

            if (isExperimental) {
                Component warning = Component.literal("⚠ Warning: May cause crashes or bugs!").withStyle(style -> style.withColor(0xFFFF5555));
                int warningWidth = this.font.width(warning);
                StringWidget warnWidget = new StringWidget(centerX - warningWidth / 2, currentY, warningWidth, 10, warning, this.font);
                this.addRenderableWidget(warnWidget);
                currentY += 14;

                this.addRenderableWidget(Button.builder(
                                Component.literal("Scanner Mode: " + ChunkByChunkConfig.get().getWorldScannerConfig().getWorldScannerScanMode().name()),
                                button -> {
                                    com.ryvione.gatheringchunks.config.WorldScannerConfig.WorldScannerMode current = ChunkByChunkConfig.get().getWorldScannerConfig().getWorldScannerScanMode();
                                    com.ryvione.gatheringchunks.config.WorldScannerConfig.WorldScannerMode[] values = com.ryvione.gatheringchunks.config.WorldScannerConfig.WorldScannerMode.values();
                                    int nextIndex = (current.ordinal() + 1) % values.length;
                                    ChunkByChunkConfig.get().getWorldScannerConfig().setWorldScannerScanMode(values[nextIndex]);
                                    button.setMessage(Component.literal("Scanner Mode: " + values[nextIndex].name()));
                                })
                        .bounds(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                        .tooltip(Tooltip.create(Component.literal("Auto = scan from center outward | Manual = click map to select chunk")))
                        .build());
                currentY += SPACING;
            }

            currentY += SECTION_SPACING + 10;
        } else {
            currentY = addSectionLabel(centerX, currentY, mode == Mode.WORLD_CREATION
                    ? "These settings apply only to the world you are creating."
                    : fullyLocked
                    ? "These are the server's settings. Only the server's owner can change them."
                    : "Greyed-out options were fixed when this world was created. The others only affect new chunks.", 0xFFAAAAAA);
            currentY = addSectionLabel(centerX, currentY, "Hard Mode", 0xFFFF5555);

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().getHardMode().isEnabled())
                    .withTooltip(value -> Tooltip.create(Component.literal("Enable Hard Mode - Enables all Hard Mode features at once")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Enable Hard Mode"),
                            (button, value) -> {
                                ChunkByChunkConfig.get().getDifficulty().getHardMode().setEnabled(value);
                                this.rebuildWidgets();
                            }));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().getHardMode().isEnforceChunkBoundaries())
                    .withTooltip(value -> Tooltip.create(Component.literal("Prevent players from leaving spawned chunks")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Enforce Chunk Boundaries"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().getHardMode().setEnforceChunkBoundaries(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().getHardMode().isDisableVillages())
                    .withTooltip(value -> Tooltip.create(Component.literal("Prevent villages from spawning")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Disable Villages"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().getHardMode().setDisableVillages(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().getHardMode().isSpawnInitialEngine())
                    .withTooltip(value -> Tooltip.create(Component.literal("Spawn a Chunk Engine in the first chunk")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Spawn Initial Engine"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().getHardMode().setSpawnInitialEngine(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().getHardMode().isInitialEngineFuel())
                    .withTooltip(value -> Tooltip.create(Component.literal("Give the initial Chunk Engine starting fuel")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Initial Engine Fuel"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().getHardMode().setInitialEngineFuel(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().getHardMode().isDisableChestsCommand())
                    .withTooltip(value -> Tooltip.create(Component.literal("Disable the /chests command")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Disable /chests Command"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().getHardMode().setDisableChestsCommand(value)));
            currentY += SPACING;

            currentY += SECTION_SPACING;

            currentY = addSectionLabel(centerX, currentY, "Difficulty", 0xFFFFAA00);

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().isEngineRequiresFuel())
                    .withTooltip(value -> Tooltip.create(Component.literal("Does the Chunk Engine require fuel to operate?")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Engine Requires Fuel"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().setEngineRequiresFuel(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().isExperimentalChunkLimit())
                    .withTooltip(value -> Tooltip.create(Component.literal("Start with 9 chunks max, upgrade via Chunk Engine")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Experimental Chunk Limit"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().setExperimentalChunkLimit(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().isEnableProgressionHelper())
                    .withTooltip(value -> Tooltip.create(Component.literal("Automatically give chunk spawner if player gets stuck")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Enable Progression Helper"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().setEnableProgressionHelper(value)));
            currentY += SPACING;

            this.addRenderableWidget(Button.builder(
                            Component.literal("Start Restriction: " + ChunkByChunkConfig.get().getDifficulty().getStartRestriction().name()),
                            button -> {
                                GameplayConfig.StartRestriction current = ChunkByChunkConfig.get().getDifficulty().getStartRestriction();
                                GameplayConfig.StartRestriction[] values = GameplayConfig.StartRestriction.values();
                                int nextIndex = (current.ordinal() + 1) % values.length;
                                ChunkByChunkConfig.get().getDifficulty().setStartRestriction(values[nextIndex]);
                                button.setMessage(Component.literal("Start Restriction: " + values[nextIndex].name()));
                                this.rebuildWidgets();
                            })
                    .bounds(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("What restriction to place on starting location (None, Village, Biome)")))
                    .build());
            currentY += SPACING;

            if (ChunkByChunkConfig.get().getDifficulty().getStartRestriction() == GameplayConfig.StartRestriction.Biome) {
                EditBox biomeBox = new EditBox(this.font, centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                        Component.literal("Starting Biome"));
                biomeBox.setValue(ChunkByChunkConfig.get().getDifficulty().getStartingBiome());
                biomeBox.setResponder(value -> ChunkByChunkConfig.get().getDifficulty().setStartingBiome(value));
                biomeBox.setTooltip(Tooltip.create(Component.literal("The tag or name of the biome for starting spawn (e.g. #minecraft:is_forest)")));
                this.addRenderableWidget(biomeBox);
                currentY += SPACING;
            }

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().isAlwaysSpawnVillage())
                    .withTooltip(value -> Tooltip.create(Component.literal("Always attempt to spawn the initial chunk in a village")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Always Spawn Village"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().setAlwaysSpawnVillage(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().spawnNewChunkChest())
                    .withTooltip(value -> Tooltip.create(Component.literal("Should chunks include a chest with materials?")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Enable Chest System"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().setSpawnNewChunkChest(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().spawnChestInInitialChunkOnly())
                    .withTooltip(value -> Tooltip.create(Component.literal("Should the chest spawn in the initial chunk only?")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Chest in Initial Chunk Only"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().setSpawnChestInInitialChunkOnly(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getDifficulty().isSpawnChunkStrip())
                    .withTooltip(value -> Tooltip.create(Component.literal("Spawn a full strip of chunks along an axis")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Spawn Chunk Strip"),
                            (button, value) -> ChunkByChunkConfig.get().getDifficulty().setSpawnChunkStrip(value)));
            currentY += SPACING;

            currentY += SECTION_SPACING;

            currentY = addSectionLabel(centerX, currentY, "Generation", 0xFF55FF55);

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGeneration().isEnabled())
                    .withTooltip(value -> Tooltip.create(Component.literal("Enable ChunkByChunk generation")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("CBC Generation Enabled"),
                            (button, value) -> ChunkByChunkConfig.get().getGeneration().setEnabled(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGeneration().sealWorld())
                    .withTooltip(value -> Tooltip.create(Component.literal("Generate empty chunks as bedrock")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Seal World (Bedrock)"),
                            (button, value) -> ChunkByChunkConfig.get().getGeneration().setSealWorld(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGeneration().isSynchNether())
                    .withTooltip(value -> Tooltip.create(Component.literal("Nether chunks spawn in response to overworld spawns")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Sync Nether Chunk Spawn"),
                            (button, value) -> ChunkByChunkConfig.get().getGeneration().setSynchNether(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGeneration().useBedrockChest())
                    .withTooltip(value -> Tooltip.create(Component.literal("Use indestructible bedrock chest")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Use Bedrock Chest"),
                            (button, value) -> ChunkByChunkConfig.get().getGeneration().setUseBedrockChest(value)));
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Initial Chunks: " + ChunkByChunkConfig.get().getGeneration().getInitialChunks()),
                    (ChunkByChunkConfig.get().getGeneration().getInitialChunks() - 1) / 99.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Number of chunks to spawn initially (1-100)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 99) + 1;
                    setMessage(Component.literal("Initial Chunks: " + value));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 99) + 1;
                    ChunkByChunkConfig.get().getGeneration().setInitialChunks(value);
                }
            });
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Chunk Layer Spawn Rate: " + ChunkByChunkConfig.get().getGeneration().getChunkLayerSpawnRate()),
                    (ChunkByChunkConfig.get().getGeneration().getChunkLayerSpawnRate() - 1) / 511.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Chunk layers spawned per tick (1-512)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 511) + 1;
                    setMessage(Component.literal("Chunk Layer Spawn Rate: " + value));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 511) + 1;
                    ChunkByChunkConfig.get().getGeneration().setChunkLayerSpawnRate(value);
                }
            });
            currentY += SPACING;

            this.addRenderableWidget(Button.builder(
                            Component.literal("Chest Contents: " + ChunkByChunkConfig.get().getGeneration().getChestContents().name()),
                            button -> {
                                ChunkRewardChestContent current = ChunkByChunkConfig.get().getGeneration().getChestContents();
                                ChunkRewardChestContent[] values = ChunkRewardChestContent.values();
                                int nextIndex = (current.ordinal() + 1) % values.length;
                                ChunkByChunkConfig.get().getGeneration().setChestContents(values[nextIndex]);
                                button.setMessage(Component.literal("Chest Contents: " + values[nextIndex].name()));
                            })
                    .bounds(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Type of items in reward chest")))
                    .build());
            currentY += SPACING;

            this.addRenderableWidget(Button.builder(
                            Component.literal("Spawner Mode: " + ChunkByChunkConfig.get().getGeneration().getChunkSpawnerMode().name()),
                            button -> {
                                ChunkSpawnerMode current = ChunkByChunkConfig.get().getGeneration().getChunkSpawnerMode();
                                ChunkSpawnerMode[] values = ChunkSpawnerMode.values();
                                int nextIndex = (current.ordinal() + 1) % values.length;
                                ChunkByChunkConfig.get().getGeneration().setChunkSpawnerMode(values[nextIndex]);
                                button.setMessage(Component.literal("Spawner Mode: " + values[nextIndex].name()));
                            })
                    .bounds(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("Edge = spawn adjacent chunks, Void = spawn in void, Both = allow both")))
                    .build());
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Chest Quantity: " + ChunkByChunkConfig.get().getGeneration().getChestQuantity()),
                    (ChunkByChunkConfig.get().getGeneration().getChestQuantity() - 1) / 63.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Number of items in chest (1-64)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 63) + 1;
                    setMessage(Component.literal("Chest Quantity: " + value));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 63) + 1;
                    ChunkByChunkConfig.get().getGeneration().setChestQuantity(value);
                }
            });
            currentY += SPACING;

            int sliderWidth = BUTTON_WIDTH - 60;
            int fieldWidth = 54;
            int fieldX = centerX - BUTTON_WIDTH / 2 + sliderWidth + 4;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, sliderWidth, BUTTON_HEIGHT,
                    Component.literal("Chests/Chunk: " + ChunkByChunkConfig.get().getGeneration().getChestsPerChunk()),
                    (ChunkByChunkConfig.get().getGeneration().getChestsPerChunk() - 1) / 65535.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Number of chests to spawn per chunk (1-65536). Values above 1500 may cause performance issues!")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 65535) + 1;
                    if (value > 1500) {
                        setMessage(Component.literal("⚠ Chests/Chunk: " + value).withStyle(style -> style.withColor(0xFFFF5555)));
                    } else {
                        setMessage(Component.literal("Chests/Chunk: " + value));
                    }
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 65535) + 1;
                    ChunkByChunkConfig.get().getGeneration().setChestsPerChunk(value);
                }
            });

            EditBox chestsField = new EditBox(this.font, fieldX, currentY, fieldWidth, BUTTON_HEIGHT,
                    Component.literal("Chests Per Chunk"));
            chestsField.setValue(String.valueOf(ChunkByChunkConfig.get().getGeneration().getChestsPerChunk()));
            chestsField.setMaxLength(6);
            chestsField.setTooltip(Tooltip.create(Component.literal("Enter exact chest count (1-65536)")));
            chestsField.setResponder(val -> {
                try {
                    int parsed = Integer.parseInt(val.trim());
                    if (parsed >= 1 && parsed <= 65536) {
                        ChunkByChunkConfig.get().getGeneration().setChestsPerChunk(parsed);
                    }
                } catch (NumberFormatException ignored) {}
            });
            this.addRenderableWidget(chestsField);
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Min Chest Depth: " + ChunkByChunkConfig.get().getGeneration().getMinChestSpawnDepth()),
                    (ChunkByChunkConfig.get().getGeneration().getMinChestSpawnDepth() + 64) / 192.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Minimum Y level for chest spawn (-64 to 128)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 192) - 64;
                    setMessage(Component.literal("Min Chest Depth: " + value));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 192) - 64;
                    ChunkByChunkConfig.get().getGeneration().setMinChestSpawnDepth(value);
                }
            });
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Max Chest Depth: " + ChunkByChunkConfig.get().getGeneration().getMaxChestSpawnDepth()),
                    (ChunkByChunkConfig.get().getGeneration().getMaxChestSpawnDepth() + 64) / 192.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Maximum Y level for chest spawn (-64 to 128)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 192) - 64;
                    setMessage(Component.literal("Max Chest Depth: " + value));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 192) - 64;
                    ChunkByChunkConfig.get().getGeneration().setMaxChestSpawnDepth(value);
                }
            });
            currentY += SPACING;

            int enabledCount = ChunkByChunkConfig.get().getGeneration().getInitialChunkBiomes().size();
            String biomeBtnLabel = enabledCount == 0
                    ? "[EXPERIMENTAL] Initial Chunk Biomes: Any biome"
                    : "[EXPERIMENTAL] Initial Chunk Biomes: " + enabledCount + " selected";
            this.addRenderableWidget(Button.builder(
                            Component.literal(biomeBtnLabel),
                            button -> {
                                if (this.minecraft != null) {
                                    this.minecraft.setScreen(new InitialBiomeSelectorScreen(this));
                                }
                            })
                    .bounds(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal("⚠ EXPERIMENTAL: Biome filtering may not work correctly and can cause unexpected spawns.\nSelect which biomes are allowed for the initial chunk spawn.\nEmpty selection = any biome allowed.")))
                    .build());
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGeneration().isFragmentOreEnabled())
                    .withTooltip(value -> Tooltip.create(Component.literal("Enable World Fragment Ore generation in the world")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Fragment Ore Enabled"),
                            (button, value) -> ChunkByChunkConfig.get().getGeneration().setFragmentOreEnabled(value)));
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Fragment Ore Vein Size: " + ChunkByChunkConfig.get().getGeneration().getFragmentOreVeinSize()),
                    (ChunkByChunkConfig.get().getGeneration().getFragmentOreVeinSize() - 1) / 63.0) {
                { this.setTooltip(Tooltip.create(Component.literal("Size of Fragment Ore veins (1-64)"))); }
                @Override protected void updateMessage() { int v = (int)(this.value * 63) + 1; setMessage(Component.literal("Fragment Ore Vein Size: " + v)); }
                @Override protected void applyValue() { int v = (int)(this.value * 63) + 1; ChunkByChunkConfig.get().getGeneration().setFragmentOreVeinSize(v); }
            });
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Fragment Ore Veins/Chunk: " + ChunkByChunkConfig.get().getGeneration().getFragmentOreCount()),
                    (ChunkByChunkConfig.get().getGeneration().getFragmentOreCount() - 1) / 63.0) {
                { this.setTooltip(Tooltip.create(Component.literal("Number of Fragment Ore veins per chunk (1-64). Increase for more fragments."))); }
                @Override protected void updateMessage() { int v = (int)(this.value * 63) + 1; setMessage(Component.literal("Fragment Ore Veins/Chunk: " + v)); }
                @Override protected void applyValue() { int v = (int)(this.value * 63) + 1; ChunkByChunkConfig.get().getGeneration().setFragmentOreCount(v); }
            });
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Fragment Ore Min Y: " + ChunkByChunkConfig.get().getGeneration().getFragmentOreMinHeight()),
                    (ChunkByChunkConfig.get().getGeneration().getFragmentOreMinHeight() + 64) / 384.0) {
                { this.setTooltip(Tooltip.create(Component.literal("Min Y level for Fragment Ore (-64 to 320)"))); }
                @Override protected void updateMessage() { int v = (int)(this.value * 384) - 64; setMessage(Component.literal("Fragment Ore Min Y: " + v)); }
                @Override protected void applyValue() { int v = (int)(this.value * 384) - 64; ChunkByChunkConfig.get().getGeneration().setFragmentOreMinHeight(v); }
            });
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Fragment Ore Max Y: " + ChunkByChunkConfig.get().getGeneration().getFragmentOreMaxHeight()),
                    (ChunkByChunkConfig.get().getGeneration().getFragmentOreMaxHeight() + 64) / 384.0) {
                { this.setTooltip(Tooltip.create(Component.literal("Max Y level for Fragment Ore (-64 to 320)"))); }
                @Override protected void updateMessage() { int v = (int)(this.value * 384) - 64; setMessage(Component.literal("Fragment Ore Max Y: " + v)); }
                @Override protected void applyValue() { int v = (int)(this.value * 384) - 64; ChunkByChunkConfig.get().getGeneration().setFragmentOreMaxHeight(v); }
            });
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGeneration().isDisableWaterOnlyChunks())
                    .withTooltip(value -> Tooltip.create(Component.literal("Prevent spawning chunks that are mostly water")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Disable Water-Only Chunks"),
                            (button, value) -> ChunkByChunkConfig.get().getGeneration().setDisableWaterOnlyChunks(value)));
            currentY += SPACING;

            currentY += SECTION_SPACING;

            currentY = addSectionLabel(centerX, currentY, "Gameplay", 0xFF5555FF);

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGameplayConfig().isBlockPlacementAllowedOutsideSpawnedChunks())
                    .withTooltip(value -> Tooltip.create(Component.literal("Allow block placement outside spawned chunks")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Block Placement Outside Chunks"),
                            (button, value) -> ChunkByChunkConfig.get().getGameplayConfig().setBlockPlacementAllowedOutsideSpawnedChunks(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGameplayConfig().isChunkSpawnLeafDecayDisabled())
                    .withTooltip(value -> Tooltip.create(Component.literal("Prevent leaves from chunk spawners from decaying")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Disable Leaf Decay"),
                            (button, value) -> ChunkByChunkConfig.get().getGameplayConfig().setChunkSpawnLeafDecayDisabled(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGameplayConfig().isEnableChunkBarriers())
                    .withTooltip(value -> Tooltip.create(Component.literal("Place barriers around chunks to prevent liquid flow")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Enable Chunk Barriers"),
                            (button, value) -> ChunkByChunkConfig.get().getGameplayConfig().setEnableChunkBarriers(value)));
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Unstable Chunk Chance: " + ChunkByChunkConfig.get().getGameplayConfig().getUnstableChunkChance() + "%"),
                    ChunkByChunkConfig.get().getGameplayConfig().getUnstableChunkChance() / 100.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Percentage chance for unstable chunks to spawn in chests (0-100%)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 100);
                    setMessage(Component.literal("Unstable Chunk Chance: " + value + "%"));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 100);
                    ChunkByChunkConfig.get().getGameplayConfig().setUnstableChunkChance(value);
                }
            });
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(config.isAutoSpawnTrees())
                    .withTooltip(value -> Tooltip.create(Component.literal("Automatically spawn trees in chunks without wood")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Auto-Spawn Trees"),
                            (button, value) -> config.setAutoSpawnTrees(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(config.isPreventFluidFlowIntoVoid())
                    .withTooltip(value -> Tooltip.create(Component.literal("Prevent fluids from flowing into empty chunks")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Prevent Fluid Flow Into Void"),
                            (button, value) -> config.setPreventFluidFlowIntoVoid(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGameplayConfig().isEnableWorldFragmentOre())
                    .withTooltip(value -> Tooltip.create(Component.literal("Enable World Fragment Ore drops (disabling overrides ore gen setting)")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Enable Fragment Ore"),
                            (button, value) -> ChunkByChunkConfig.get().getGameplayConfig().setEnableWorldFragmentOre(value)));
            currentY += SPACING;

            this.addRenderableWidget(CycleButton.onOffBuilder(ChunkByChunkConfig.get().getGameplayConfig().isEnableCaveScanner())
                    .withTooltip(value -> Tooltip.create(Component.literal("Enable the Cave Scanner block functionality")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Enable Cave Scanner"),
                            (button, value) -> ChunkByChunkConfig.get().getGameplayConfig().setEnableCaveScanner(value)));
            currentY += SPACING;

            currentY += SECTION_SPACING;

            currentY = addSectionLabel(centerX, currentY, "Other", 0xFFFFFFFF);
        
            this.addRenderableWidget(CycleButton.onOffBuilder(config.isMobsDropFragments())
                    .withTooltip(value -> Tooltip.create(Component.literal("Allow mobs to drop world fragments when killed")))
                    .create(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                            Component.literal("Mobs Drop Fragments"),
                            (button, value) -> config.setMobsDropFragments(value)));
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Fragment Drop Chance: " + config.getFragmentDropChance() + "%"),
                    config.getFragmentDropChance() / 100.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Percentage chance for mobs to drop fragments (0-100%)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 100);
                    setMessage(Component.literal("Fragment Drop Chance: " + value + "%"));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 100);
                    config.setFragmentDropChance(value);
                }
            });
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Min Fragment Drop: " + config.getMinFragmentDrop()),
                    (config.getMinFragmentDrop() - 1) / 15.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Minimum fragments dropped by mobs (1-16)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 15) + 1;
                    setMessage(Component.literal("Min Fragment Drop: " + value));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 15) + 1;
                    config.setMinFragmentDrop(value);
                }
            });
            currentY += SPACING;

            this.addRenderableWidget(Button.builder(
                            Component.literal("Themed Spawner Source: " + config.getThemedSpawnerSource().name()),
                            button -> {
                                GatheringChunksConfig.ThemedSpawnerSource[] values = GatheringChunksConfig.ThemedSpawnerSource.values();
                                int nextIndex = (config.getThemedSpawnerSource().ordinal() + 1) % values.length;
                                config.setThemedSpawnerSource(values[nextIndex]);
                                button.setMessage(Component.literal("Themed Spawner Source: " + values[nextIndex].name()));
                            })
                    .bounds(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
                    .tooltip(Tooltip.create(Component.literal(
                            "DIRECT = themed chunk copies terrain from the same coordinate (matches surroundings). "
                            + "RANDOM = terrain comes from a random spot in the theme dimension (theme-shaped landform, may not blend). "
                            + "Affects themed spawner blocks and the World Mender.")))
                    .build());
            currentY += SPACING;

            this.addRenderableWidget(new AbstractSliderButton(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT,
                    Component.literal("Max Fragment Drop: " + config.getMaxFragmentDrop()),
                    (config.getMaxFragmentDrop() - 1) / 15.0) {
                {
                    this.setTooltip(Tooltip.create(Component.literal("Maximum fragments dropped by mobs (1-16)")));
                }
                @Override
                protected void updateMessage() {
                    int value = (int) (this.value * 15) + 1;
                    setMessage(Component.literal("Max Fragment Drop: " + value));
                }
                @Override
                protected void applyValue() {
                    int value = (int) (this.value * 15) + 1;
                    config.setMaxFragmentDrop(value);
                }
            });
            currentY += SPACING;

            currentY += SECTION_SPACING + 10;

        }

        this.addRenderableWidget(Button.builder(
                Component.literal("Done"),
                button -> this.onClose())
            .bounds(centerX - BUTTON_WIDTH / 2, currentY, BUTTON_WIDTH, BUTTON_HEIGHT)
            .build());

        this.contentHeight = currentY - (40 - scrollOffset);
    }

    private static String prefetchLabel() {
        PerformanceConfig performance = ChunkByChunkConfig.global().getPerformance();
        int value = performance.getSpawnSearchPrefetch();
        return "Spawn Search Prefetch: " + (value < 0 ? "Auto (" + performance.effectiveSpawnSearchPrefetch() + ")" : String.valueOf(value));
    }

    private int addSectionLabel(int centerX, int y, String text, int color) {
        Component labelText = Component.literal(text).withStyle(style -> style.withColor(color));
        int labelWidth = this.font.width(labelText);
        StringWidget label = new StringWidget(centerX - labelWidth / 2, y, labelWidth, 15, labelText, this.font);
        this.addRenderableWidget(label);
        return y + 20;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, "Gathering Chunks Config", this.width / 2, 10, 0xFFFFFFFF);
        drawScrollBar(graphics, mouseX, mouseY);
    }

    private void drawScrollBar(GuiGraphics graphics, int mouseX, int mouseY) {
        int viewY = 40;
        int viewHeight = Math.max(0, this.height - 60);
        if (contentHeight <= viewHeight) return;

        int trackX = this.width - 16;
        int trackY = viewY;
        int trackHeight = viewHeight;
        int thumbHeight = Math.max(20, viewHeight * viewHeight / Math.max(1, contentHeight));
        int maxOffset = Math.max(0, contentHeight - viewHeight);
        int thumbRange = Math.max(1, trackHeight - thumbHeight);
        int thumbY = trackY + (int) ((long) scrollOffset * thumbRange / Math.max(1, maxOffset));

        graphics.fill(trackX, trackY, trackX + scrollbarWidth, trackY + trackHeight, 0xFF333333);
        graphics.fill(trackX, thumbY, trackX + scrollbarWidth, thumbY + thumbHeight, 0xFFAAAAAA);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == 0 && contentHeight > 0) {
            int viewY = 40;
            int viewHeight = Math.max(0, this.height - 60);
            if (contentHeight > viewHeight) {
                int trackX = this.width - 16;
                int thumbHeight = Math.max(20, viewHeight * viewHeight / Math.max(1, contentHeight));
                int maxOffset = Math.max(0, contentHeight - viewHeight);
                int thumbRange = Math.max(1, viewHeight - thumbHeight);
                int thumbY = viewY + (int) ((long) scrollOffset * thumbRange / Math.max(1, maxOffset));

                if (mouseX >= trackX && mouseX <= trackX + scrollbarWidth && mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                    isScrollbarDragging = true;
                    return true;
                } else if (mouseX >= trackX && mouseX <= trackX + scrollbarWidth && mouseY >= viewY && mouseY <= viewY + viewHeight) {
                    int clickPos = (int) mouseY - viewY - thumbHeight / 2;
                    scrollOffset = (int) ((long) clickPos * maxOffset / Math.max(1, thumbRange));
                    scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
                    this.rebuildWidgets();
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (isScrollbarDragging && button == 0) {
            isScrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (isScrollbarDragging && button == 0 && contentHeight > 0) {
            int viewY = 40;
            int viewHeight = Math.max(0, this.height - 60);
            int thumbHeight = Math.max(20, viewHeight * viewHeight / Math.max(1, contentHeight));
            int maxOffset = Math.max(0, contentHeight - viewHeight);
            int thumbRange = Math.max(1, viewHeight - thumbHeight);
            int clickPos = (int) mouseY - viewY - thumbHeight / 2;
            scrollOffset = (int) ((long) clickPos * maxOffset / Math.max(1, thumbRange));
            scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
            this.rebuildWidgets();
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int viewHeight = Math.max(0, this.height - 60);
        int maxOffset = Math.max(0, contentHeight - viewHeight);
        scrollOffset -= (int) (scrollY * SCROLL_SPEED);
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset));
        this.rebuildWidgets();
        return true;
    }

    @Override
    public void onClose() {
        closeAndReturn(parentScreen, mode);
    }

    private static void closeAndReturn(Screen parentScreen, Mode mode) {
        Minecraft mc = Minecraft.getInstance();
        if (mode == Mode.WORLD_CREATION) {
            ChunkByChunkConfig.clearActiveWorld();
        } else if (mode == Mode.WORLD_LOCKED) {
            com.ryvione.gatheringchunks.common.util.ConfigUtil.saveActiveWorldSettings(mc.getSingleplayerServer());
        } else if (mode == Mode.GLOBAL) {
            com.ryvione.gatheringchunks.common.util.ConfigUtil.saveDefaultConfig();
        }
        mc.setScreen(parentScreen);
    }

    public static class InitialBiomeSelectorScreen extends Screen {

        private static final java.util.List<String> ALL_SELECTABLE_BIOMES = java.util.List.of(
                "minecraft:plains",
                "minecraft:sunflower_plains",
                "minecraft:forest",
                "minecraft:flower_forest",
                "minecraft:birch_forest",
                "minecraft:old_growth_birch_forest",
                "minecraft:dark_forest",
                "minecraft:taiga",
                "minecraft:old_growth_pine_taiga",
                "minecraft:old_growth_spruce_taiga",
                "minecraft:savanna",
                "minecraft:savanna_plateau",
                "minecraft:windswept_savanna",
                "minecraft:jungle",
                "minecraft:sparse_jungle",
                "minecraft:bamboo_jungle",
                "minecraft:cherry_grove",
                "minecraft:meadow",
                "minecraft:windswept_forest",
                "minecraft:windswept_hills",
                "minecraft:grove",
                "minecraft:stony_peaks",
                "minecraft:wooded_badlands",
                "minecraft:eroded_badlands",
                "minecraft:badlands",
                "minecraft:desert",
                "minecraft:snowy_plains",
                "minecraft:snowy_taiga",
                "minecraft:snowy_slopes",
                "minecraft:frozen_peaks",
                "minecraft:jagged_peaks",
                "minecraft:ice_spikes",
                "minecraft:swamp",
                "minecraft:mangrove_swamp",
                "minecraft:river",
                "minecraft:beach",
                "minecraft:ocean",
                "minecraft:deep_ocean",
                "minecraft:mushroom_fields"
        );

        private static final java.util.Map<String, String> BIOME_DISPLAY_NAMES = new java.util.LinkedHashMap<>();
        static {
            BIOME_DISPLAY_NAMES.put("minecraft:plains", "Plains");
            BIOME_DISPLAY_NAMES.put("minecraft:sunflower_plains", "Sunflower Plains");
            BIOME_DISPLAY_NAMES.put("minecraft:forest", "Forest");
            BIOME_DISPLAY_NAMES.put("minecraft:flower_forest", "Flower Forest");
            BIOME_DISPLAY_NAMES.put("minecraft:birch_forest", "Birch Forest");
            BIOME_DISPLAY_NAMES.put("minecraft:old_growth_birch_forest", "Old Growth Birch Forest");
            BIOME_DISPLAY_NAMES.put("minecraft:dark_forest", "Dark Forest");
            BIOME_DISPLAY_NAMES.put("minecraft:taiga", "Taiga");
            BIOME_DISPLAY_NAMES.put("minecraft:old_growth_pine_taiga", "Old Growth Pine Taiga");
            BIOME_DISPLAY_NAMES.put("minecraft:old_growth_spruce_taiga", "Old Growth Spruce Taiga");
            BIOME_DISPLAY_NAMES.put("minecraft:savanna", "Savanna");
            BIOME_DISPLAY_NAMES.put("minecraft:savanna_plateau", "Savanna Plateau");
            BIOME_DISPLAY_NAMES.put("minecraft:windswept_savanna", "Windswept Savanna");
            BIOME_DISPLAY_NAMES.put("minecraft:jungle", "Jungle");
            BIOME_DISPLAY_NAMES.put("minecraft:sparse_jungle", "Sparse Jungle");
            BIOME_DISPLAY_NAMES.put("minecraft:bamboo_jungle", "Bamboo Jungle");
            BIOME_DISPLAY_NAMES.put("minecraft:cherry_grove", "Cherry Grove");
            BIOME_DISPLAY_NAMES.put("minecraft:meadow", "Meadow");
            BIOME_DISPLAY_NAMES.put("minecraft:windswept_forest", "Windswept Forest");
            BIOME_DISPLAY_NAMES.put("minecraft:windswept_hills", "Windswept Hills");
            BIOME_DISPLAY_NAMES.put("minecraft:grove", "Grove");
            BIOME_DISPLAY_NAMES.put("minecraft:stony_peaks", "Stony Peaks");
            BIOME_DISPLAY_NAMES.put("minecraft:wooded_badlands", "Wooded Badlands");
            BIOME_DISPLAY_NAMES.put("minecraft:eroded_badlands", "Eroded Badlands");
            BIOME_DISPLAY_NAMES.put("minecraft:badlands", "Badlands");
            BIOME_DISPLAY_NAMES.put("minecraft:desert", "Desert");
            BIOME_DISPLAY_NAMES.put("minecraft:snowy_plains", "Snowy Plains");
            BIOME_DISPLAY_NAMES.put("minecraft:snowy_taiga", "Snowy Taiga");
            BIOME_DISPLAY_NAMES.put("minecraft:snowy_slopes", "Snowy Slopes");
            BIOME_DISPLAY_NAMES.put("minecraft:frozen_peaks", "Frozen Peaks");
            BIOME_DISPLAY_NAMES.put("minecraft:jagged_peaks", "Jagged Peaks");
            BIOME_DISPLAY_NAMES.put("minecraft:ice_spikes", "Ice Spikes");
            BIOME_DISPLAY_NAMES.put("minecraft:swamp", "Swamp");
            BIOME_DISPLAY_NAMES.put("minecraft:mangrove_swamp", "Mangrove Swamp");
            BIOME_DISPLAY_NAMES.put("minecraft:river", "River");
            BIOME_DISPLAY_NAMES.put("minecraft:beach", "Beach");
            BIOME_DISPLAY_NAMES.put("minecraft:ocean", "Ocean");
            BIOME_DISPLAY_NAMES.put("minecraft:deep_ocean", "Deep Ocean");
            BIOME_DISPLAY_NAMES.put("minecraft:mushroom_fields", "Mushroom Fields");
        }

        private static final int ENTRY_HEIGHT = 22;
        private static final int ENTRY_WIDTH = 220;
        private static final int SCROLLBAR_WIDTH = 8;
        private static final int SCROLL_SPEED = 20;

        private int VIEW_TOP;
        private int VIEW_BOTTOM;
        private int viewHeight;

        private final Screen parentScreen;
        private int scrollOffset = 0;
        private int contentHeight = 0;
        private boolean isScrollbarDragging = false;

        public InitialBiomeSelectorScreen(Screen parent) {
            super(Component.literal("Initial Chunk Biomes"));
            this.parentScreen = parent;
        }

        @Override
        protected void init() {
            super.init();
            this.clearWidgets();

            VIEW_TOP = 44;
            VIEW_BOTTOM = this.height - 36;
            viewHeight = Math.max(0, VIEW_BOTTOM - VIEW_TOP);

            int centerX = this.width / 2;

            java.util.List<String> enabled = ChunkByChunkConfig.get().getGeneration().getInitialChunkBiomes();
            int currentY = VIEW_TOP - scrollOffset;

            for (String biomeId : ALL_SELECTABLE_BIOMES) {
                boolean active = enabled.contains(biomeId);
                String displayName = BIOME_DISPLAY_NAMES.getOrDefault(biomeId, biomeId);
                final String fBiomeId = biomeId;

                int btnY = currentY;
                if (btnY + ENTRY_HEIGHT > VIEW_TOP && btnY < VIEW_BOTTOM) {
                    Button btn = Button.builder(
                            buildBiomeLabel(displayName, active),
                            button -> {
                                java.util.List<String> current =
                                        new java.util.ArrayList<>(ChunkByChunkConfig.get().getGeneration().getInitialChunkBiomes());
                                if (current.contains(fBiomeId)) {
                                    current.remove(fBiomeId);
                                } else {
                                    current.add(fBiomeId);
                                }
                                ChunkByChunkConfig.get().getGeneration().setInitialChunkBiomes(current);
                                this.rebuildWidgets();
                            })
                            .bounds(centerX - ENTRY_WIDTH / 2, btnY, ENTRY_WIDTH, ENTRY_HEIGHT - 2)
                            .build();
                    this.addRenderableWidget(btn);
                }
                currentY += ENTRY_HEIGHT;
            }

            contentHeight = ALL_SELECTABLE_BIOMES.size() * ENTRY_HEIGHT;

            int bottomBtnY = this.height - 28;

            this.addRenderableWidget(Button.builder(
                    Component.literal("Select All"),
                    button -> {
                        ChunkByChunkConfig.get().getGeneration().setInitialChunkBiomes(
                                new java.util.ArrayList<>(ALL_SELECTABLE_BIOMES));
                        this.rebuildWidgets();
                    })
                    .bounds(centerX - ENTRY_WIDTH / 2, bottomBtnY, ENTRY_WIDTH / 2 - 2, 20)
                    .build());

            this.addRenderableWidget(Button.builder(
                    Component.literal("Clear All  (any biome)"),
                    button -> {
                        ChunkByChunkConfig.get().getGeneration().setInitialChunkBiomes(new java.util.ArrayList<>());
                        this.rebuildWidgets();
                    })
                    .bounds(centerX + 2, bottomBtnY, ENTRY_WIDTH / 2 - 2, 20)
                    .build());
        }

        private Component buildBiomeLabel(String displayName, boolean active) {
            if (active) {
                return Component.literal("[ON]  " + displayName).withStyle(style -> style.withColor(0xFF55FF55));
            } else {
                return Component.literal("[OFF] " + displayName).withStyle(style -> style.withColor(0xFFAAAAAA));
            }
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            super.render(graphics, mouseX, mouseY, partialTick);

            graphics.fill(0, 0, this.width, VIEW_TOP - 2, 0xCC000000);
            graphics.drawCenteredString(this.font, "Initial Chunk Biomes", this.width / 2, 10, 0xFFFFFFFF);
            int enabledCount = ChunkByChunkConfig.get().getGeneration().getInitialChunkBiomes().size();
            String sub = enabledCount == 0 ? "Any biome allowed" : enabledCount + " biome(s) selected";
            graphics.drawCenteredString(this.font, sub, this.width / 2, 22, 0xFFAAAAFF);

            graphics.fill(0, VIEW_BOTTOM + 2, this.width, this.height, 0xCC000000);

            graphics.fill(this.width / 2 - ENTRY_WIDTH / 2, VIEW_TOP - 2,
                    this.width / 2 + ENTRY_WIDTH / 2, VIEW_TOP - 1, 0xFF555555);
            graphics.fill(this.width / 2 - ENTRY_WIDTH / 2, VIEW_BOTTOM + 2,
                    this.width / 2 + ENTRY_WIDTH / 2, VIEW_BOTTOM + 3, 0xFF555555);

            drawScrollBar(graphics);
        }

        private void drawScrollBar(GuiGraphics graphics) {
            if (contentHeight <= viewHeight) return;
            int trackX = this.width / 2 + ENTRY_WIDTH / 2 + 4;
            int thumbHeight = Math.max(16, viewHeight * viewHeight / Math.max(1, contentHeight));
            int maxOffset = Math.max(0, contentHeight - viewHeight);
            int thumbRange = Math.max(1, viewHeight - thumbHeight);
            int thumbY = VIEW_TOP + (int) ((long) scrollOffset * thumbRange / Math.max(1, maxOffset));
            graphics.fill(trackX, VIEW_TOP, trackX + SCROLLBAR_WIDTH, VIEW_BOTTOM, 0xFF333333);
            graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, 0xFFAAAAAA);
        }

        private int getMaxOffset() {
            return Math.max(0, contentHeight - viewHeight);
        }

        @Override
        public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
            scrollOffset = Math.max(0, Math.min(getMaxOffset(), scrollOffset - (int) (scrollY * SCROLL_SPEED)));
            this.rebuildWidgets();
            return true;
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            double mouseX = event.x();
            double mouseY = event.y();
            int button = event.button();
            if (button == 0 && contentHeight > viewHeight) {
                int trackX = this.width / 2 + ENTRY_WIDTH / 2 + 4;
                int thumbHeight = Math.max(16, viewHeight * viewHeight / Math.max(1, contentHeight));
                int maxOffset = getMaxOffset();
                int thumbRange = Math.max(1, viewHeight - thumbHeight);
                int thumbY = VIEW_TOP + (int) ((long) scrollOffset * thumbRange / Math.max(1, maxOffset));
                if (mouseX >= trackX && mouseX <= trackX + SCROLLBAR_WIDTH) {
                    if (mouseY >= thumbY && mouseY <= thumbY + thumbHeight) {
                        isScrollbarDragging = true;
                        return true;
                    } else if (mouseY >= VIEW_TOP && mouseY <= VIEW_BOTTOM) {
                        int clickPos = (int) mouseY - VIEW_TOP - thumbHeight / 2;
                        scrollOffset = Math.max(0, Math.min(maxOffset,
                                (int) ((long) clickPos * maxOffset / Math.max(1, thumbRange))));
                        this.rebuildWidgets();
                        return true;
                    }
                }
            }
            return super.mouseClicked(event, doubleClick);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            double mouseX = event.x();
            double mouseY = event.y();
            int button = event.button();
            if (isScrollbarDragging && button == 0) {
                isScrollbarDragging = false;
                return true;
            }
            return super.mouseReleased(event);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
            double mouseX = event.x();
            double mouseY = event.y();
            int button = event.button();
            if (isScrollbarDragging && button == 0) {
                int thumbHeight = Math.max(16, viewHeight * viewHeight / Math.max(1, contentHeight));
                int maxOffset = getMaxOffset();
                int thumbRange = Math.max(1, viewHeight - thumbHeight);
                int clickPos = (int) mouseY - VIEW_TOP - thumbHeight / 2;
                scrollOffset = Math.max(0, Math.min(maxOffset,
                        (int) ((long) clickPos * maxOffset / Math.max(1, thumbRange))));
                this.rebuildWidgets();
                return true;
            }
            return super.mouseDragged(event, dragX, dragY);
        }

        @Override
        public void onClose() {
            Minecraft.getInstance().setScreen(parentScreen);
        }
    }
}