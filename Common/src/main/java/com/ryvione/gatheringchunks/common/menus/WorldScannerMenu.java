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

import com.google.common.base.Preconditions;
import com.ryvione.gatheringchunks.common.blockEntities.WorldScannerBlockEntity;
import com.ryvione.gatheringchunks.interop.Services;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.maps.MapId;

public class WorldScannerMenu extends BaseInventoryContainerMenu {

    private final ContainerData containerData;
    private BlockPos blockPos = BlockPos.ZERO;

    public WorldScannerMenu(int menuId, Inventory inventory) {
        this(menuId, inventory, new SimpleContainer(WorldScannerBlockEntity.NUM_ITEM_SLOTS), new SimpleContainerData(WorldScannerBlockEntity.NUM_DATA_ITEMS));
    }

    public WorldScannerMenu(int menuId, Inventory inventory, Container container, ContainerData containerData) {
        this(menuId, inventory, container, containerData, Services.PLATFORM.worldScannerMenu());
    }

    protected WorldScannerMenu(int menuId, Inventory inventory, Container container, ContainerData containerData, net.minecraft.world.inventory.MenuType<?> menuType) {
        super(menuType, menuId, container, inventory, 8, 84);
        this.containerData = containerData;
        if (container instanceof WorldScannerBlockEntity entity) {
            this.blockPos = entity.getBlockPos();
        } else if (container instanceof com.ryvione.gatheringchunks.common.blockEntities.CaveScannerBlockEntity caveEntity) {
            this.blockPos = caveEntity.getBlockPos();
        }
        addScannerSlots(container);
        addDataSlots(containerData);
    }

    protected void addScannerSlots(Container container) {
        Preconditions.checkArgument(container.getContainerSize() >= WorldScannerBlockEntity.NUM_ITEM_SLOTS, "Expected " + WorldScannerBlockEntity.NUM_ITEM_SLOTS + " item slots, but entity has " + container.getContainerSize());
        addSlot(new Slot(container, WorldScannerBlockEntity.SLOT_INPUT, 27, 21));
        addSlot(new FilteredSlot(container, WorldScannerBlockEntity.SLOT_FUEL, 27, 50, WorldScannerBlockEntity::isWorldScannerFuel));
    }

    public BlockPos getBlockPos() {
        return blockPos;
    }

    public int getEnergy() {
        return this.containerData.get(WorldScannerBlockEntity.DATA_ENERGY);
    }

    public int getMaxEnergy() {
        return this.containerData.get(WorldScannerBlockEntity.DATA_MAX_ENERGY);
    }

    public int getScannerChunkX() {
        int blockX = (this.containerData.get(WorldScannerBlockEntity.DATA_POS_X_HIGH) << 16)
                | (this.containerData.get(WorldScannerBlockEntity.DATA_POS_X_LOW) & 0xFFFF);
        return blockX >> 4;
    }

    public int getScannerChunkZ() {
        int blockZ = (this.containerData.get(WorldScannerBlockEntity.DATA_POS_Z_HIGH) << 16)
                | (this.containerData.get(WorldScannerBlockEntity.DATA_POS_Z_LOW) & 0xFFFF);
        return blockZ >> 4;
    }

    public int getCurrentChunkX() {
        return this.containerData.get(WorldScannerBlockEntity.DATA_SCANNING_X);
    }

    public int getCurrentChunkZ() {
        return this.containerData.get(WorldScannerBlockEntity.DATA_SCANNING_Z);
    }

    public boolean isMapAvailable() {
        return this.containerData.get(WorldScannerBlockEntity.DATA_MAP) != WorldScannerBlockEntity.NO_MAP;
    }

    public MapId getMapId() {
        int mapId = this.containerData.get(WorldScannerBlockEntity.DATA_MAP);
        return mapId == WorldScannerBlockEntity.NO_MAP ? null : new MapId(mapId);
    }

    public boolean isEspEnabled() {
        return this.containerData.get(WorldScannerBlockEntity.DATA_ESP_ENABLED) != 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == 0) {
            this.containerData.set(WorldScannerBlockEntity.DATA_ESP_ENABLED, isEspEnabled() ? 0 : 1);
            return true;
        }
        if (id == 1) {
            if (player.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                net.minecraft.world.level.block.entity.BlockEntity be = serverLevel.getBlockEntity(blockPos);
                if (be instanceof WorldScannerBlockEntity scanner) {
                    scanner.triggerManualScan();
                } else if (be instanceof com.ryvione.gatheringchunks.common.blockEntities.CaveScannerBlockEntity scanner) {
                    scanner.triggerManualScan();
                }
            }
            return true;
        }
        return super.clickMenuButton(player, id);
    }

    @Override
    protected boolean quickMoveToContainer(ItemStack stack) {
        if (WorldScannerBlockEntity.isWorldScannerFuel(stack)) {
            return this.moveItemStackToContainerSlot(stack, WorldScannerBlockEntity.SLOT_FUEL, WorldScannerBlockEntity.SLOT_FUEL + 1, false);
        }
        return this.moveItemStackToContainerSlot(stack, WorldScannerBlockEntity.SLOT_INPUT, WorldScannerBlockEntity.SLOT_INPUT + 1, false);
    }
}