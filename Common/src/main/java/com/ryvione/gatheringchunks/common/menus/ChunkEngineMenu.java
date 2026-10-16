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

import com.ryvione.gatheringchunks.common.blockEntities.ChunkEngineBlockEntity;
import com.ryvione.gatheringchunks.interop.Services;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class ChunkEngineMenu extends BaseInventoryContainerMenu {
    private final ContainerData data;

    public ChunkEngineMenu(int containerId, Inventory playerInventory) {
        this(containerId, playerInventory, new SimpleContainer(ChunkEngineBlockEntity.NUM_SLOTS), new SimpleContainerData(ChunkEngineBlockEntity.NUM_DATA));
    }

    public ChunkEngineMenu(int containerId, Inventory playerInventory, Container container, ContainerData data) {
        super(Services.PLATFORM.chunkEngineMenu(), containerId, container, playerInventory, 8, 84);
        checkContainerSize(container, ChunkEngineBlockEntity.NUM_SLOTS);
        checkContainerDataCount(data, ChunkEngineBlockEntity.NUM_DATA);
        this.data = data;
        this.addDataSlots(data);

        this.addSlot(new Slot(container, ChunkEngineBlockEntity.SLOT_FUEL, 58, 35) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                if (container instanceof ChunkEngineBlockEntity engine) {
                    return engine.isFuel(stack);
                }
                return true;
            }
        });
    }

    public int getFuelProgress() {
        int remainingFuel = this.data.get(ChunkEngineBlockEntity.DATA_REMAINING_FUEL);
        int chargedFuel = this.data.get(ChunkEngineBlockEntity.DATA_CHARGED_FUEL);
        if (chargedFuel != 0 && remainingFuel != 0) {
            return remainingFuel * 13 / chargedFuel;
        } else {
            return 0;
        }
    }

    public int getRemainingTicks() {
        return this.data.get(ChunkEngineBlockEntity.DATA_REMAINING_FUEL);
    }

    public ContainerData getData() {
        return this.data;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        return false;
    }
}
