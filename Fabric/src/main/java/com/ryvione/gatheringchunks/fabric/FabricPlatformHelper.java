/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.fabric;

import com.ryvione.gatheringchunks.common.CommonRegistry;
import com.ryvione.gatheringchunks.common.blockEntities.BedrockChestBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.WorldForgeBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.WorldMenderBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.WorldScannerBlockEntity;
import com.ryvione.gatheringchunks.common.blocks.SpawnChunkBlock;
import com.ryvione.gatheringchunks.common.menus.BedrockChestMenu;
import com.ryvione.gatheringchunks.common.menus.WorldForgeMenu;
import com.ryvione.gatheringchunks.common.menus.WorldMenderMenu;
import com.ryvione.gatheringchunks.common.menus.WorldScannerMenu;
import com.ryvione.gatheringchunks.common.blockEntities.ChunkEngineBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.CaveScannerBlockEntity;
import com.ryvione.gatheringchunks.common.menus.ChunkEngineMenu;
import com.ryvione.gatheringchunks.common.menus.CaveScannerMenu;
import com.ryvione.gatheringchunks.common.network.C2SSaveConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2COpenConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import com.ryvione.gatheringchunks.interop.CBCPlatformHelper;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
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

public final class FabricPlatformHelper implements CBCPlatformHelper {
    @Override
    public SpawnChunkBlock spawnChunkBlock() {
        return CommonRegistry.SPAWN_CHUNK_BLOCK;
    }

    @Override
    public Block unstableSpawnChunkBlock() {
        return CommonRegistry.UNSTABLE_SPAWN_CHUNK_BLOCK;
    }

    @Override
    public Block bedrockChestBlock() {
        return CommonRegistry.BEDROCK_CHEST_BLOCK;
    }

    @Override
    public Block worldCoreBlock() {
        return CommonRegistry.WORLD_CORE_BLOCK;
    }

    @Override
    public Block worldForgeBlock() {
        return CommonRegistry.WORLD_FORGE_BLOCK;
    }

    @Override
    public Block worldScannerBlock() {
        return CommonRegistry.WORLD_SCANNER_BLOCK;
    }

    @Override
    public Block worldMenderBlock() {
        return CommonRegistry.WORLD_MENDER_BLOCK;
    }

    @Override
    public Block chunkEngineBlock() {
        return CommonRegistry.CHUNK_ENGINE_BLOCK;
    }

    @Override
    public Block caveScannerBlock() {
        return CommonRegistry.CAVE_SCANNER_BLOCK;
    }

    @Override
    public Item spawnChunkBlockItem() {
        return CommonRegistry.SPAWN_CHUNK_BLOCK_ITEM;
    }

    @Override
    public Item unstableChunkSpawnBlockItem() {
        return CommonRegistry.UNSTABLE_SPAWN_CHUNK_BLOCK_ITEM;
    }

    @Override
    public Item bedrockChestItem() {
        return CommonRegistry.BEDROCK_CHEST_BLOCK_ITEM;
    }

    @Override
    public Item worldCoreBlockItem() {
        return CommonRegistry.WORLD_CORE_BLOCK_ITEM;
    }

    @Override
    public Item worldForgeBlockItem() {
        return CommonRegistry.WORLD_FORGE_BLOCK_ITEM;
    }

    @Override
    public Item worldScannerBlockItem() {
        return CommonRegistry.WORLD_SCANNER_BLOCK_ITEM;
    }

    @Override
    public Item worldMenderBlockItem() {
        return CommonRegistry.WORLD_MENDER_BLOCK_ITEM;
    }

    @Override
    public Item chunkEngineBlockItem() {
        return CommonRegistry.CHUNK_ENGINE_BLOCK_ITEM;
    }

    @Override
    public Item caveScannerBlockItem() {
        return CommonRegistry.CAVE_SCANNER_BLOCK_ITEM;
    }

    @Override
    public Item worldFragmentItem() {
        return CommonRegistry.WORLD_FRAGMENT_ITEM;
    }

    @Override
    public Item worldShardItem() {
        return CommonRegistry.WORLD_SHARD_ITEM;
    }

    @Override
    public Item worldCrystalItem() {
        return CommonRegistry.WORLD_CRYSTAL_ITEM;
    }

    @Override
    public Item starterBookItem() {
        return CommonRegistry.STARTER_BOOK_ITEM;
    }

    @Override
    public List<ItemStack> biomeThemeBlockItems() {

        return CommonRegistry.biomeThemedBlockItems;
    }

    @Override
    public BlockEntityType<BedrockChestBlockEntity> bedrockChestEntity() {
        return CommonRegistry.BEDROCK_CHEST_BLOCK_ENTITY;
    }

    @Override
    public BlockEntityType<WorldForgeBlockEntity> worldForgeEntity() {
        return CommonRegistry.WORLD_FORGE_BLOCK_ENTITY;
    }

    @Override
    public BlockEntityType<WorldScannerBlockEntity> worldScannerEntity() {
        return CommonRegistry.WORLD_SCANNER_BLOCK_ENTITY;
    }

    @Override
    public BlockEntityType<WorldMenderBlockEntity> worldMenderEntity() {
        return CommonRegistry.WORLD_MENDER_BLOCK_ENTITY;
    }

    @Override
    public BlockEntityType<ChunkEngineBlockEntity> chunkEngineEntity() {
        return CommonRegistry.CHUNK_ENGINE_BLOCK_ENTITY;
    }

    @Override
    public BlockEntityType<CaveScannerBlockEntity> caveScannerEntity() {
        return CommonRegistry.CAVE_SCANNER_BLOCK_ENTITY;
    }

    @Override
    public SoundEvent spawnChunkSoundEffect() {
        return CommonRegistry.SPAWN_CHUNK_SOUND_EVENT;
    }

    @Override
    public MenuType<BedrockChestMenu> bedrockChestMenu() {
        return CommonRegistry.BEDROCK_CHEST_MENU;
    }

    @Override
    public MenuType<WorldForgeMenu> worldForgeMenu() {
        return CommonRegistry.WORLD_FORGE_MENU;
    }

    @Override
    public MenuType<WorldScannerMenu> worldScannerMenu() {
        return CommonRegistry.WORLD_SCANNER_MENU;
    }

    @Override
    public MenuType<WorldMenderMenu> worldMenderMenu() {
        return CommonRegistry.WORLD_MENDER_MENU;
    }

    @Override
    public MenuType<ChunkEngineMenu> chunkEngineMenu() {
        return CommonRegistry.CHUNK_ENGINE_MENU;
    }

    @Override
    public MenuType<com.ryvione.gatheringchunks.common.menus.CaveScannerMenu> caveScannerMenu() {
        return CommonRegistry.CAVE_SCANNER_MENU;
    }

    @Override
    public Fluid getFluidContent(BucketItem bucket) {
        return bucket.getContent();
    }

    @Override
    public <T> void unfreezeRegistry(MappedRegistry<T> registry) {
        if (registry instanceof com.ryvione.gatheringchunks.mixins.DefrostedRegistry defrosted) {
            defrosted.setFrozen(false);
        }
    }

    @Override
    public <T> void refreezeRegistry(MappedRegistry<T> registry) {
        if (registry instanceof com.ryvione.gatheringchunks.mixins.DefrostedRegistry defrosted) {
            defrosted.setFrozen(true);
        }
    }

    @Override
    public void openConfigScreen(ServerPlayer player) {
    }

    @Override
    public void sendConfigOpenPacket(ServerPlayer player, S2COpenConfigPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }

    @Override
    public void sendConfigSyncPacket(ServerPlayer player, S2CSyncConfigPacket packet) {
        ServerPlayNetworking.send(player, packet);
    }

    @Override
    public void sendConfigSavePacket(C2SSaveConfigPacket packet) {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            FabricClientHelper.sendConfigSavePacket(packet);
        }
    }

    @Override
    public void openStarterBook() {
        if (net.fabricmc.loader.api.FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            FabricClientHelper.openStarterBook();
        }
    }
}