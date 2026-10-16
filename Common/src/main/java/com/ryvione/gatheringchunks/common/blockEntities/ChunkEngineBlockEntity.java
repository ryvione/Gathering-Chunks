/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.common.blockEntities;

import com.ryvione.gatheringchunks.common.blocks.ChunkEngineBlock;
import com.ryvione.gatheringchunks.common.menus.ChunkEngineMenu;
import com.ryvione.gatheringchunks.interop.Services;
import com.ryvione.gatheringchunks.server.world.ChunkEngineManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class ChunkEngineBlockEntity extends BaseFueledBlockEntity {
    public static final int SLOT_FUEL = 0;
    public static final int NUM_SLOTS = 1;

    public static final int DATA_REMAINING_FUEL = 0;
    public static final int DATA_CHARGED_FUEL = 1;
    public static final int DATA_MAX_CHUNKS = 2;
    public static final int DATA_SPAWNED_CHUNKS = 3;
    public static final int NUM_DATA = 4;

    private static final Map<Item, FuelValueSupplier> ITEM_FUEL;

    static {
        ITEM_FUEL = Map.of(
                Services.PLATFORM.worldShardItem(), () -> 1200,
                Services.PLATFORM.worldCrystalItem(), () -> 6000
        );
    }

    protected final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_REMAINING_FUEL -> getRemainingFuel();
                case DATA_CHARGED_FUEL -> getChargedFuel();
                case DATA_MAX_CHUNKS -> {
                    if (level instanceof ServerLevel serverLevel) {
                         yield com.ryvione.gatheringchunks.server.world.ChunkSpawnController.get(serverLevel.getServer()).getMaxChunks(serverLevel.dimension().identifier().toString());
                    }
                    yield 4;
                }
                case DATA_SPAWNED_CHUNKS -> {
                    if (level instanceof ServerLevel serverLevel) {
                         yield com.ryvione.gatheringchunks.server.world.ChunkSpawnController.get(serverLevel.getServer()).getSpawnedChunkCount(serverLevel.dimension().identifier().toString());
                    }
                    yield 0;
                }
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            if (index == DATA_REMAINING_FUEL) {
                setRemainingFuel(value);
            }
        }

        @Override
        public int getCount() {
            return NUM_DATA;
        }
    };

    public ChunkEngineBlockEntity(BlockPos pos, BlockState state) {
        this(Services.PLATFORM.chunkEngineEntity(), pos, state);
    }

    public ChunkEngineBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state, NUM_SLOTS, SLOT_FUEL, ITEM_FUEL, Map.of());
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.gatheringchunks.chunkengine");
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory playerInventory) {
        return new ChunkEngineMenu(id, playerInventory, this, dataAccess);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ChunkEngineBlockEntity engine) {
        boolean requiresFuel = com.ryvione.gatheringchunks.config.ChunkByChunkConfig.get().getDifficulty().isEngineRequiresFuel();
        boolean wasLit = state.getValue(ChunkEngineBlock.LIT);

        if (requiresFuel && engine.getRemainingFuel() == 0) {
            engine.checkConsumeFuelItem();
        }

        boolean isLit = !requiresFuel || engine.getRemainingFuel() > 0;

        if (isLit) {
            if (requiresFuel) {
                engine.consumeFuel(1);
            }
            if (level instanceof ServerLevel serverLevel) {
                ChunkEngineManager.get(serverLevel.getServer()).registerEngine(serverLevel, pos);
            }
        }

        if (wasLit != isLit) {
            level.setBlock(pos, state.setValue(ChunkEngineBlock.LIT, isLit), 3);
            engine.setChanged();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        return super.stillValid(player);
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return new int[]{SLOT_FUEL};
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return canPlaceItem(slot, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return true;
    }
}
