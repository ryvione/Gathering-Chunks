/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.config;
import com.ryvione.gatheringchunks.config.system.Name;

public class ChunkByChunkConfig {
    private static final ChunkByChunkConfig instance = new ChunkByChunkConfig();
    private static volatile ChunkByChunkConfig activeWorld = null;

    public static ChunkByChunkConfig get() {
        ChunkByChunkConfig active = activeWorld;
        return active != null ? active : instance;
    }

    public static ChunkByChunkConfig global() {
        return instance;
    }

    public static void setActiveWorld(ChunkByChunkConfig worldView) {
        activeWorld = worldView;
    }

    public static void clearActiveWorld() {
        activeWorld = null;
    }

    @Name("ChunkGeneration")
    private GenerationConfig generation = new GenerationConfig();

    @Name("Difficulty")
    private DifficultyConfig difficulty = new DifficultyConfig();

    @Name("Gameplay")
    private GameplayConfig gameplayConfig = new GameplayConfig();

    @Name("GatheringChunks")
    private GatheringChunksConfig gatheringChunksConfig = new GatheringChunksConfig();

    @Name("WorldScanner")
    private WorldScannerConfig worldScannerConfig = new WorldScannerConfig();

    @Name("WorldForge")
    private WorldForgeConfig worldForge = new WorldForgeConfig();

    @Name("WorldMender")
    private WorldMenderConfig worldMenderConfig = new WorldMenderConfig();

    @Name("BedrockChest")
    private BedrockChestConfig bedrockChest = new BedrockChestConfig();

    @Name("Debug")
    private DebugConfig debug = new DebugConfig();

    @Name("Performance")
    private PerformanceConfig performance = new PerformanceConfig();

    public ChunkByChunkConfig worldView(ChunkByChunkConfig worldSettings) {
        ChunkByChunkConfig view = new ChunkByChunkConfig();
        view.generation = worldSettings.generation;
        view.difficulty = worldSettings.difficulty;
        view.gameplayConfig = worldSettings.gameplayConfig;
        view.gatheringChunksConfig = worldSettings.gatheringChunksConfig;
        view.worldForge = this.worldForge;
        view.worldScannerConfig = this.worldScannerConfig;
        view.worldMenderConfig = this.worldMenderConfig;
        view.bedrockChest = this.bedrockChest;
        view.debug = this.debug;
        view.performance = this.performance;
        return view;
    }

    public GenerationConfig getGeneration() {
        return generation;
    }

    public BedrockChestConfig getBedrockChest() {
        return bedrockChest;
    }

    public WorldForgeConfig getWorldForge() {
        return worldForge;
    }

    public GameplayConfig getGameplayConfig() {
        return gameplayConfig;
    }

    public GatheringChunksConfig getGatheringChunksConfig() {
        return gatheringChunksConfig;
    }

    public DebugConfig getDebug() {
        return debug;
    }

    public PerformanceConfig getPerformance() {
        return performance;
    }

    public WorldScannerConfig getWorldScannerConfig() {
        return worldScannerConfig;
    }

    public WorldMenderConfig getWorldMenderConfig() {
        return worldMenderConfig;
    }

    public DifficultyConfig getDifficulty() {
        return difficulty;
    }
}