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

import com.google.common.collect.ImmutableMap;
import com.ryvione.gatheringchunks.common.menus.WorldForgeMenu;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.interop.Services;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Map;

public class WorldForgeBlockEntity extends BaseFueledBlockEntity {
    public static final int NUM_ITEM_SLOTS = 2;
    public static final int SLOT_INPUT = 0;
    public static final int SLOT_RESULT = 1;
    public static final int NUM_DATA_ITEMS = 2;
    public static final int DATA_PROGRESS = 0;
    public static final int DATA_GOAL = 1;
    public static final int GROW_CRYSTAL_AT = 4;
    public static final Map<Item, FuelValueSupplier> FUEL;
    public static final Map<TagKey<Item>, FuelValueSupplier> FUEL_TAGS;
    private static final Map<Item, FuelValueSupplier> CRYSTAL_COSTS;
    private static final Map<Item, Integer> ORE_TO_FRAGMENTS;
    private static final Item INITIAL_CRYSTAL = Services.PLATFORM.worldFragmentItem();
    public static final Map<Item, Item> CRYSTAL_STEPS;
    private static final int[] SLOTS_FOR_UP = new int[]{SLOT_INPUT};
    private static final int[] SLOTS_FOR_DOWN = new int[]{SLOT_RESULT};
    private static final int[] SLOTS_FOR_SIDE = new int[]{SLOT_INPUT};
    private static final TagKey<Item> SOIL_FUEL_TAG = TagKey.create(Registries.ITEM, Identifier.parse("gatheringchunks:weakworldforgefuel"));
    private static final TagKey<Item> STONE_FUEL_TAG = TagKey.create(Registries.ITEM, Identifier.parse("gatheringchunks:worldforgefuel"));
    private static final TagKey<Item> STRONG_FUEL_TAG = TagKey.create(Registries.ITEM, Identifier.parse("gatheringchunks:strongworldforgefuel"));
    private int progress;
    private int goal;
    private boolean processingOre = false;

    protected final ContainerData dataAccess = new ContainerData() {
        public int get(int id) {
            return switch (id) {
                case DATA_PROGRESS -> progress;
                case DATA_GOAL -> goal;
                default -> 0;
            };
        }
        public void set(int id, int value) {
            switch (id) {
                case DATA_PROGRESS -> progress = value;
                case DATA_GOAL -> goal = value;
            }
        }
        public int getCount() {
            return NUM_DATA_ITEMS;
        }
    };

    static {
        ImmutableMap.Builder<Item, FuelValueSupplier> fuelBuilder = ImmutableMap.builder();
        fuelBuilder.put(Services.PLATFORM.worldFragmentItem(), () -> ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost());
        fuelBuilder.put(Services.PLATFORM.worldShardItem(), () -> ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost() * 4);
        fuelBuilder.put(Services.PLATFORM.worldCrystalItem(), () -> ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost() * 16);
        FUEL = fuelBuilder.build();
        FUEL_TAGS = ImmutableMap.of(SOIL_FUEL_TAG, () -> ChunkByChunkConfig.get().getWorldForge().getSoilFuelValue(),
                STONE_FUEL_TAG, () -> ChunkByChunkConfig.get().getWorldForge().getStoneFuelValue(),
                STRONG_FUEL_TAG, () -> ChunkByChunkConfig.get().getWorldForge().getStrongFuelValue());
        CRYSTAL_COSTS = ImmutableMap.<Item, FuelValueSupplier>builder()
                .put(Services.PLATFORM.worldFragmentItem(),() -> ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost())
                .put(Services.PLATFORM.worldShardItem(), () -> ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost() * 4)
                .put(Services.PLATFORM.worldCrystalItem(), () -> ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost() * 16)
                .put(Services.PLATFORM.worldCoreBlockItem(), () -> ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost() * 64).build();
        CRYSTAL_STEPS = ImmutableMap.<Item, Item>builder()
                .put(Services.PLATFORM.worldFragmentItem(), Services.PLATFORM.worldShardItem())
                .put(Services.PLATFORM.worldShardItem(), Services.PLATFORM.worldCrystalItem())
                .put(Services.PLATFORM.worldCrystalItem(), Services.PLATFORM.worldCoreBlockItem()).build();
        ORE_TO_FRAGMENTS = ImmutableMap.<Item, Integer>builder()
                .put(Items.RAW_COPPER, 1)
                .put(Items.RAW_IRON, 4)
                .put(Items.RAW_GOLD, 8)
                .put(Items.DIAMOND, 16)
                .put(Items.NETHERITE_INGOT, 64)
                .build();
    }

    public WorldForgeBlockEntity(BlockPos pos, BlockState state) {
        super(Services.PLATFORM.worldForgeEntity(), pos, state, NUM_ITEM_SLOTS, SLOT_INPUT, FUEL, FUEL_TAGS);
    }

    @Override
    protected Component getDefaultName() {
        return Component.translatable("container.gatheringchunks.worldforge");
    }

    @Override
    protected AbstractContainerMenu createMenu(int menuId, Inventory inventory) {
        return new WorldForgeMenu(menuId, inventory, this, this.dataAccess);
    }

    @Override
    public boolean isFuel(ItemStack itemStack) {
        return isWorldForgeFuel(itemStack);
    }

    @Override
    public int getFuelValue(ItemStack itemStack) {
        Item item = itemStack.getItem();
        String name = item.toString();
        if (itemStack.is(SOIL_FUEL_TAG) || name.contains("dirt") || name.contains("sand") || name.contains("gravel")) {
            return 2;
        }
        if (itemStack.is(STONE_FUEL_TAG) || name.contains("stone") || name.contains("cobblestone") || name.contains("andesite") || name.contains("diorite") || name.contains("granite") || name.contains("deepslate") || name.contains("tuff")) {
            return 4;
        }
        if (itemStack.is(STRONG_FUEL_TAG)) {
            return 128;
        }
        return super.getFuelValue(itemStack);
    }

    public static boolean isWorldForgeFuel(ItemStack itemStack) {
        Item item = itemStack.getItem();
        String name = item.toString();
        return FUEL.containsKey(item)
                || itemStack.is(SOIL_FUEL_TAG)
                || itemStack.is(STONE_FUEL_TAG)
                || itemStack.is(STRONG_FUEL_TAG)
                || name.contains("dirt")
                || name.contains("stone")
                || name.contains("cobblestone")
                || name.contains("sand")
                || name.contains("gravel")
                || name.contains("andesite")
                || name.contains("diorite")
                || name.contains("granite")
                || name.contains("deepslate")
                || name.contains("tuff");
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.progress = input.getIntOr("Progress", 0);
        this.goal = input.getIntOr("Goal", 0);
        this.processingOre = input.getBooleanOr("ProcessingOre", false);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Progress", this.progress);
        output.putInt("Goal", this.goal);
        output.putBoolean("ProcessingOre", this.processingOre);
    }

    public static void serverTick(Level level, BlockPos blockPos, BlockState blockState, WorldForgeBlockEntity entity) {
        boolean changed = false;
        ItemStack inputStack = entity.getItem(SLOT_INPUT);
        ItemStack outputItems = entity.getItem(SLOT_RESULT);

        if (!inputStack.isEmpty() && ORE_TO_FRAGMENTS.containsKey(inputStack.getItem())) {
            int fragmentCount = ORE_TO_FRAGMENTS.get(inputStack.getItem());
            ItemStack fragments = new ItemStack(Services.PLATFORM.worldFragmentItem(), fragmentCount);

            if (outputItems.isEmpty() || (outputItems.getItem() == Services.PLATFORM.worldFragmentItem() && outputItems.getCount() + fragmentCount <= outputItems.getMaxStackSize())) {
                inputStack.shrink(1);
                if (outputItems.isEmpty()) {
                    entity.setItem(SLOT_RESULT, fragments);
                } else {
                    outputItems.grow(fragmentCount);
                }
                changed = true;
            }
            if (changed) {
                setChanged(level, blockPos, blockState);
            }
            return;
        }

        if (entity.getRemainingFuel() > 0) {
            int consumeAmount = entity.consumeFuel(ChunkByChunkConfig.get().getWorldForge().getProductionRate());
            entity.progress += consumeAmount;
            changed = true;
        }

        Item producingItem;
        if (outputItems.isEmpty()) {
            producingItem = INITIAL_CRYSTAL;
        } else {
            producingItem = outputItems.getItem();
        }

        if (!CRYSTAL_COSTS.containsKey(producingItem)) {
            return;
        }

        if (outputItems.getCount() >= outputItems.getMaxStackSize()) {
            return;
        }

        if (entity.checkConsumeFuelItem()) {
            changed = true;
        }

        int itemCost = CRYSTAL_COSTS.get(producingItem).get();
        Item nextItem = CRYSTAL_STEPS.get(producingItem);
        entity.goal = itemCost;

        if (entity.progress >= itemCost) {
            entity.progress -= itemCost;
            changed = true;
            if (outputItems.isEmpty()) {
                entity.setItem(SLOT_RESULT, producingItem.getDefaultInstance());
            } else if (outputItems.getCount() == GROW_CRYSTAL_AT - 1 && nextItem != null) {
                entity.setItem(SLOT_RESULT, nextItem.getDefaultInstance());
                if (CRYSTAL_COSTS.containsKey(nextItem)) {
                    entity.goal = CRYSTAL_COSTS.get(nextItem).get();
                }
            } else {
                outputItems.grow(1);
            }
        }

        if (changed) {
            setChanged(level, blockPos, blockState);
        }
    }

    @Override
    public int[] getSlotsForFace(Direction direction) {
        if (direction == Direction.UP) {
            return SLOTS_FOR_UP;
        } else if (direction == Direction.DOWN) {
            return SLOTS_FOR_DOWN;
        }
        return SLOTS_FOR_SIDE;
    }

    @Override
    public boolean canPlaceItemThroughFace(int slot, ItemStack item, Direction direction) {
        return this.canPlaceItem(slot, item);
    }

    @Override
    public boolean canTakeItemThroughFace(int slot, ItemStack itemStack, Direction direction) {
        return slot == SLOT_RESULT;
    }

    @Override
    public boolean canPlaceItem(int slot, ItemStack item) {
        if (slot == SLOT_INPUT) {
            return isWorldForgeFuel(item) || ORE_TO_FRAGMENTS.containsKey(item.getItem());
        }
        if (slot == SLOT_RESULT) {
            return false;
        }
        return super.canPlaceItem(slot, item);
    }
}