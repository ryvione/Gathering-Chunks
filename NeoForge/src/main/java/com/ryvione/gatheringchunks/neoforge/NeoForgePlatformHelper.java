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
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.List;

public class NeoForgePlatformHelper implements CBCPlatformHelper {

    @Override
    public SpawnChunkBlock spawnChunkBlock() {
        return (SpawnChunkBlock) ModRegistry.SPAWN_CHUNK_BLOCK.get();
    }

    @Override
    public Block unstableSpawnChunkBlock() {
        return ModRegistry.UNSTABLE_SPAWN_CHUNK_BLOCK.get();
    }

    @Override
    public Block bedrockChestBlock() {
        return ModRegistry.BEDROCK_CHEST_BLOCK.get();
    }

    @Override
    public Block worldCoreBlock() {
        return ModRegistry.WORLD_CORE_BLOCK.get();
    }

    @Override
    public Block worldForgeBlock() {
        return ModRegistry.WORLD_FORGE_BLOCK.get();
    }

    @Override
    public Block worldScannerBlock() {
        return ModRegistry.WORLD_SCANNER_BLOCK.get();
    }

    @Override
    public Block worldMenderBlock() {
        return ModRegistry.WORLD_MENDER_BLOCK.get();
    }

    @Override
    public Block chunkEngineBlock() {
        return ModRegistry.CHUNK_ENGINE_BLOCK.get();
    }

    @Override
    public Block caveScannerBlock() {
        return ModRegistry.CAVE_SCANNER_BLOCK.get();
    }

    @Override
    public Item spawnChunkBlockItem() {
        return ModRegistry.SPAWN_CHUNK_BLOCK_ITEM.get();
    }

    @Override
    public Item unstableChunkSpawnBlockItem() {
        return ModRegistry.UNSTABLE_SPAWN_CHUNK_BLOCK_ITEM.get();
    }

    @Override
    public Item bedrockChestItem() {
        return ModRegistry.BEDROCK_CHEST_ITEM.get();
    }

    @Override
    public Item worldCoreBlockItem() {
        return ModRegistry.WORLD_CORE_BLOCK_ITEM.get();
    }

    @Override
    public Item worldForgeBlockItem() {
        return ModRegistry.WORLD_FORGE_BLOCK_ITEM.get();
    }

    @Override
    public Item worldScannerBlockItem() {
        return ModRegistry.WORLD_SCANNER_BLOCK_ITEM.get();
    }

    @Override
    public Item worldMenderBlockItem() {
        return ModRegistry.WORLD_MENDER_BLOCK_ITEM.get();
    }

    @Override
    public Item chunkEngineBlockItem() {
        return ModRegistry.CHUNK_ENGINE_BLOCK_ITEM.get();
    }

    @Override
    public Item caveScannerBlockItem() {
        return ModRegistry.CAVE_SCANNER_BLOCK_ITEM.get();
    }

    @Override
    public Item worldFragmentItem() {
        return ModRegistry.WORLD_FRAGMENT_ITEM.get();
    }

    @Override
    public Item worldShardItem() {
        return ModRegistry.WORLD_SHARD_ITEM.get();
    }

    @Override
    public Item worldCrystalItem() {
        return ModRegistry.WORLD_CRYSTAL_ITEM.get();
    }

    @Override
    public Item starterBookItem() {
        return ModRegistry.STARTER_BOOK_ITEM.get();
    }

    @Override
    public List<ItemStack> biomeThemeBlockItems() {

        return ModRegistry.getBiomeThemedBlockItems();
    }

    @Override
    public BlockEntityType<BedrockChestBlockEntity> bedrockChestEntity() {
        return ModRegistry.BEDROCK_CHEST_BLOCK_ENTITY.get();
    }

    @Override
    public BlockEntityType<WorldForgeBlockEntity> worldForgeEntity() {
        return ModRegistry.WORLD_FORGE_BLOCK_ENTITY.get();
    }

    @Override
    public BlockEntityType<WorldScannerBlockEntity> worldScannerEntity() {
        return ModRegistry.WORLD_SCANNER_BLOCK_ENTITY.get();
    }

    @Override
    public BlockEntityType<WorldMenderBlockEntity> worldMenderEntity() {
        return ModRegistry.WORLD_MENDER_BLOCK_ENTITY.get();
    }

    @Override
    public BlockEntityType<ChunkEngineBlockEntity> chunkEngineEntity() {
        return ModRegistry.CHUNK_ENGINE_BLOCK_ENTITY.get();
    }

    @Override
    public BlockEntityType<CaveScannerBlockEntity> caveScannerEntity() {
        return ModRegistry.CAVE_SCANNER_BLOCK_ENTITY.get();
    }

    @Override
    public SoundEvent spawnChunkSoundEffect() {
        return ModRegistry.SPAWN_CHUNK_SOUND_EVENT.get();
    }

    @Override
    public MenuType<BedrockChestMenu> bedrockChestMenu() {
        return ModRegistry.BEDROCK_CHEST_MENU.get();
    }

    @Override
    public MenuType<WorldForgeMenu> worldForgeMenu() {
        return ModRegistry.WORLD_FORGE_MENU.get();
    }

    @Override
    public MenuType<WorldScannerMenu> worldScannerMenu() {
        return ModRegistry.WORLD_SCANNER_MENU.get();
    }

    @Override
    public MenuType<WorldMenderMenu> worldMenderMenu() {
        return ModRegistry.WORLD_MENDER_MENU.get();
    }

    @Override
    public MenuType<ChunkEngineMenu> chunkEngineMenu() {
        return ModRegistry.CHUNK_ENGINE_MENU.get();
    }

    @Override
    public MenuType<com.ryvione.gatheringchunks.common.menus.CaveScannerMenu> caveScannerMenu() {
        return ModRegistry.CAVE_SCANNER_MENU.get();
    }

    @Override
    public Fluid getFluidContent(BucketItem bucket) {
        return bucket.getContent();
    }

    @Override
    public <T> void unfreezeRegistry(MappedRegistry<T> registry) {
        setFrozenFlag(registry, false);
    }

    @Override
    public <T> void refreezeRegistry(MappedRegistry<T> registry) {
        setFrozenFlag(registry, true);
    }

    private static <T> void setFrozenFlag(MappedRegistry<T> registry, boolean frozen) {
        try {
            java.lang.reflect.Field frozenField = null;
            try {
                frozenField = MappedRegistry.class.getDeclaredField("frozen");
            } catch (NoSuchFieldException e) {
                frozenField = MappedRegistry.class.getDeclaredField("l");
            }
            frozenField.setAccessible(true);
            frozenField.setBoolean(registry, frozen);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Failed to set registry frozen flag on NeoForge", e);
        }
    }

    @Override
    public void openConfigScreen(ServerPlayer player) {
    }

    @Override
    public void sendConfigOpenPacket(ServerPlayer player, S2COpenConfigPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @Override
    public void sendConfigSyncPacket(ServerPlayer player, S2CSyncConfigPacket packet) {
        PacketDistributor.sendToPlayer(player, packet);
    }

    @Override
    public void sendConfigSavePacket(C2SSaveConfigPacket packet) {
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            NeoForgeClientHelper.sendConfigSavePacket(packet);
        }
    }

    @Override
    public void openStarterBook() {
        if (net.neoforged.fml.loading.FMLEnvironment.getDist() == net.neoforged.api.distmarker.Dist.CLIENT) {
            NeoForgeClientHelper.openStarterBook();
        }
    }
}