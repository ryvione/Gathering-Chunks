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

import com.ryvione.gatheringchunks.common.menus.BedrockChestMenu;
import com.ryvione.gatheringchunks.interop.Services;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.RandomizableContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
public class BedrockChestBlockEntity extends RandomizableContainerBlockEntity {
    public static final int COLUMNS = 4;
    public static final int ROWS = 2;
    public static final int CONTAINER_SIZE = COLUMNS * ROWS;
    private NonNullList<ItemStack> items = NonNullList.withSize(8, ItemStack.EMPTY);
    public BedrockChestBlockEntity(BlockPos pos, BlockState state) {
        super(Services.PLATFORM.bedrockChestEntity(), pos, state);
    }
    @Override
    protected NonNullList<ItemStack> getItems() {
        return this.items;
    }
    @Override
    protected void setItems(NonNullList<ItemStack> items) {
        this.items = items;
    }
    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.gatheringchunks.bedrockchest");
    }
    @Override
    protected AbstractContainerMenu createMenu(int menuId, Inventory inventory) {
        return new BedrockChestMenu(menuId, inventory, this);
    }
    @Override
    public int getContainerSize() {
        return CONTAINER_SIZE;
    }
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (!this.trySaveLootTable(output)) {
            ContainerHelper.saveAllItems(output, this.items);
        }
    }
    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.items = NonNullList.withSize(this.getContainerSize(), ItemStack.EMPTY);
        if (!this.tryLoadLootTable(input)) {
            ContainerHelper.loadAllItems(input, this.items);
        }
    }
}