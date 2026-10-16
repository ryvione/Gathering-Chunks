/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.interop;

import com.ryvione.gatheringchunks.common.blockEntities.BedrockChestBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.WorldForgeBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.WorldMenderBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.WorldScannerBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.ChunkEngineBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.CaveScannerBlockEntity;
import com.ryvione.gatheringchunks.common.blocks.SpawnChunkBlock;
import com.ryvione.gatheringchunks.common.menus.BedrockChestMenu;
import com.ryvione.gatheringchunks.common.menus.WorldForgeMenu;
import com.ryvione.gatheringchunks.common.menus.WorldMenderMenu;
import com.ryvione.gatheringchunks.common.menus.WorldScannerMenu;
import com.ryvione.gatheringchunks.common.menus.ChunkEngineMenu;
import com.ryvione.gatheringchunks.common.menus.CaveScannerMenu;
import com.ryvione.gatheringchunks.common.network.C2SSaveConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2COpenConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import net.minecraft.core.MappedRegistry;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;

public interface CBCPlatformHelper {
    SpawnChunkBlock spawnChunkBlock();
    Block unstableSpawnChunkBlock();
    Block bedrockChestBlock();
    Block worldCoreBlock();
    Block worldForgeBlock();
    Block worldScannerBlock();
    Block worldMenderBlock();
    Block chunkEngineBlock();
    Block caveScannerBlock();
    Item spawnChunkBlockItem();
    Item unstableChunkSpawnBlockItem();
    Item bedrockChestItem();
    Item worldCoreBlockItem();
    Item worldForgeBlockItem();
    Item worldScannerBlockItem();
    Item worldMenderBlockItem();
    Item chunkEngineBlockItem();
    Item caveScannerBlockItem();
    Item worldFragmentItem();
    Item worldShardItem();
    Item worldCrystalItem();
    Item starterBookItem();
    List<ItemStack> biomeThemeBlockItems();

    BlockEntityType<BedrockChestBlockEntity> bedrockChestEntity();
    BlockEntityType<WorldForgeBlockEntity> worldForgeEntity();
    BlockEntityType<WorldScannerBlockEntity> worldScannerEntity();
    BlockEntityType<WorldMenderBlockEntity> worldMenderEntity();
    BlockEntityType<ChunkEngineBlockEntity> chunkEngineEntity();
    BlockEntityType<CaveScannerBlockEntity> caveScannerEntity();
    SoundEvent spawnChunkSoundEffect();
    MenuType<BedrockChestMenu> bedrockChestMenu();
    MenuType<WorldForgeMenu> worldForgeMenu();
    MenuType<WorldScannerMenu> worldScannerMenu();
    MenuType<WorldMenderMenu> worldMenderMenu();
    MenuType<ChunkEngineMenu> chunkEngineMenu();
    MenuType<CaveScannerMenu> caveScannerMenu();
    Fluid getFluidContent(BucketItem bucketItem);
    <T> void unfreezeRegistry(MappedRegistry<T> registry);
    <T> void refreezeRegistry(MappedRegistry<T> registry);
    void openConfigScreen(ServerPlayer player);
    void sendConfigOpenPacket(ServerPlayer player, S2COpenConfigPacket packet);
    void sendConfigSyncPacket(ServerPlayer player, S2CSyncConfigPacket packet);
    void sendConfigSavePacket(C2SSaveConfigPacket packet);
    void openStarterBook();
}