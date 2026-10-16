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

import com.ryvione.gatheringchunks.common.blocks.SpawnChunkBlock;
import com.ryvione.gatheringchunks.common.menus.WorldMenderMenu;
import com.ryvione.gatheringchunks.common.util.SpiralIterator;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.interop.Services;
import com.ryvione.gatheringchunks.server.world.ChunkSpawnController;
import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.StackedContents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.StackedContentsCompatible;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class WorldMenderBlockEntity extends BaseContainerBlockEntity implements WorldlyContainer, StackedContentsCompatible {
    public static final int SLOT_INPUT = 0;
    public static final int NUM_ITEM_SLOTS = 1;
    public static final int DATA_CHUNKS_SPAWNED = 0;
    public static final int NUM_DATA_ITEMS = 1;
    public static final int LEGACY_SLEEP_TICKS_WHEN_NOTHING_TO_GENERATE = 1200000;
    private static final int[] SLOTS = new int[]{SLOT_INPUT};
    private NonNullList<ItemStack> items;
    private int cooldown;
    private int chunksSpawned;

    protected final ContainerData dataAccess = new ContainerData() {
        public int get(int id) {
            return switch (id) {
                case DATA_CHUNKS_SPAWNED -> chunksSpawned;
                default -> 0;
            };
        }
        public void set(int id, int value) {
            switch (id) {
                case DATA_CHUNKS_SPAWNED -> chunksSpawned = value;
            }
        }
        public int getCount() {
            return NUM_DATA_ITEMS;
        }
    };

    public WorldMenderBlockEntity(BlockPos pos, BlockState state) {
        super(Services.PLATFORM.worldMenderEntity(), pos, state);
        items = NonNullList.withSize(NUM_ITEM_SLOTS, ItemStack.EMPTY);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.gatheringchunks.worldmender");
    }

    public static void serverTick(Level level, BlockPos blockPos, BlockState blockState, WorldMenderBlockEntity entity) {
        if (entity.cooldown > 0) {
            entity.cooldown--;
            return;
        }

        ServerLevel serverLevel = (ServerLevel) level;
        if (ChunkSpawnController.get(serverLevel.getServer()).isBusy()) {
            return;
        }

        int chunksSpawned = 0;
        boolean changed = false;

        if (entity.validInput()) {
            ChunkPos centerPos = new ChunkPos(blockPos);
            SpiralIterator spiralIterator = new SpiralIterator(centerPos.x, centerPos.z);
            while (spiralIterator.layerDistance() <= 8) {
                ChunkPos chunkPos = new ChunkPos(spiralIterator.getX(), spiralIterator.getY());
                if (SpawnChunkHelper.isEmptyChunk(level, chunkPos)) {
                    entity.chunksSpawned = chunksSpawned + 1;
                    BlockPos pos = chunkPos.getMiddleBlockPosition(level.getMaxY());
                    SpawnChunkBlock spawnChunkBlock = entity.getInputChunkBlock();
                    if (spawnChunkBlock != null && ChunkSpawnController.get(serverLevel.getServer()).request(serverLevel, spawnChunkBlock.getBiomeTheme(), spawnChunkBlock.isRandom(), pos)) {
                        entity.getItem(SLOT_INPUT).shrink(1);
                        changed = true;
                    }
                    entity.cooldown = ChunkByChunkConfig.get().getWorldMenderConfig().getCooldown();
                    if (changed) {
                        setChanged(level, blockPos, blockState);
                    }
                    return;
                }
                chunksSpawned++;
                spiralIterator.next();
            }
            int sleepTicks = ChunkByChunkConfig.get().getWorldMenderConfig().getEmptyRescanInterval();
            if (sleepTicks < 1 || sleepTicks > LEGACY_SLEEP_TICKS_WHEN_NOTHING_TO_GENERATE) {
                sleepTicks = LEGACY_SLEEP_TICKS_WHEN_NOTHING_TO_GENERATE;
            }
            entity.cooldown = sleepTicks;
            entity.chunksSpawned = chunksSpawned;
        }
    }

    private boolean validInput() {
        ItemStack targetItem = getItem(SLOT_INPUT);
        return targetItem.getItem() instanceof BlockItem bi && (bi.getBlock().equals(Services.PLATFORM.worldCoreBlock()) || bi.getBlock() instanceof SpawnChunkBlock);
    }

    private SpawnChunkBlock getInputChunkBlock() {
        ItemStack targetItem = getItem(SLOT_INPUT);
        if (targetItem.getItem() instanceof BlockItem bi) {
            if (bi.getBlock() instanceof SpawnChunkBlock scb) {
                return scb;
            } else if (bi.getBlock().equals(Services.PLATFORM.worldCoreBlock())) {
                return Services.PLATFORM.spawnChunkBlock();
            }
        }
        return null;
    }

    @Override
    public int getContainerSize() {
        return this.items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack itemstack : items) {
            if (!itemstack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return items.get(slot);
    }

    @Override
    public void setItem(int slot, ItemStack newItem) {
        items.set(slot, newItem);
        if (newItem.getCount() > this.getMaxStackSize()) {
            newItem.setCount(this.getMaxStackSize());
        }
    }

    @Override
    public ItemStack removeItem(int slot, int split) {
        return ContainerHelper.removeItem(items, slot, split);
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ContainerHelper.takeItem(this.items, slot);
    }

    @Override
    public void clearContent() {
        items.clear();
    }

    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack item) {
        if (Services.PLATFORM.worldCoreBlockItem().equals(item.getItem())) {
            return true;
        } else if (item.getItem() instanceof BlockItem blockItem) {
            return blockItem.getBlock() instanceof SpawnChunkBlock;
        } else {
            return false;
        }
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return canPlaceItem(slot, itemStack);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return false;
    }

    @Override
    public void fillStackedContents(StackedItemContents contents) {
        for (ItemStack itemstack : items) {
            contents.accountStack(itemstack);
        }
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        return SLOTS;
    }

    @Override
    protected AbstractContainerMenu createMenu(int menuId, Inventory inventory) {
        return new WorldMenderMenu(menuId, inventory, this, this.dataAccess);
    }

    @Override
    public boolean stillValid(Player player) {
        if (level.getBlockEntity(worldPosition) != this) {
            return false;
        } else {
            return player.distanceToSqr((double) this.worldPosition.getX() + 0.5D, (double) this.worldPosition.getY() + 0.5D, (double) this.worldPosition.getZ() + 0.5D) <= 64.0D;
        }
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            net.minecraft.world.Containers.dropContents(this.level, pos, this);
            this.level.updateNeighbourForOutputSignal(pos, state.getBlock());
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.cooldown = input.getIntOr("Cooldown", 0);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        this.chunksSpawned = input.getIntOr("ChunksSpawned", 0);
        ContainerHelper.loadAllItems(input, this.items);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        ContainerHelper.saveAllItems(output, this.items);
        output.putInt("Cooldown", cooldown);
        output.putInt("ChunksSpawned", chunksSpawned);
    }

    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }
}