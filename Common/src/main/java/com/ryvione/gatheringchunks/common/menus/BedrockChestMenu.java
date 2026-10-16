/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.common.menus;

import com.ryvione.gatheringchunks.common.blockEntities.BedrockChestBlockEntity;
import com.ryvione.gatheringchunks.interop.Services;
import com.ryvione.gatheringchunks.server.world.ChestTracker;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
public class BedrockChestMenu extends BaseInventoryContainerMenu {
    public BedrockChestMenu(int menuId, Inventory inventory) {
        this(menuId, inventory, new SimpleContainer(BedrockChestBlockEntity.CONTAINER_SIZE));
    }
    public BedrockChestMenu(int menuId, Inventory inventory, Container container) {
        super(Services.PLATFORM.bedrockChestMenu(), menuId, container, inventory, 8, 84);
        checkContainerSize(container, BedrockChestBlockEntity.CONTAINER_SIZE);
        for(int k = 0; k < BedrockChestBlockEntity.ROWS; ++k) {
            for(int l = 0; l < BedrockChestBlockEntity.COLUMNS; ++l) {
                this.addSlot(new Slot(container, l + k * BedrockChestBlockEntity.COLUMNS, 41 + l * (INVENTORY_SLOT_PIXELS + 8), 22 + (INVENTORY_SLOT_PIXELS + 8) * k));
            }
        }
    }
    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide() && getContainer() instanceof BlockEntity blockEntity) {
            BlockPos pos = blockEntity.getBlockPos();
            if (player.level() instanceof ServerLevel serverLevel) {
                ChestTracker tracker = ChestTracker.get(serverLevel.getServer());
                tracker.checkAndRemoveIfEmpty(pos, serverLevel);
            }
        }
    }
}