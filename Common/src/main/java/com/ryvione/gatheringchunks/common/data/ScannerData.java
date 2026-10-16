/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.common.data;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.blockEntities.WorldScannerBlockEntity;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
public class ScannerData {
    private final Set<String> inputItems = new LinkedHashSet<>();
    private final Set<String> targetBlocks = new LinkedHashSet<>();
    public ScannerData(Collection<String> items, Collection<String> blocks) {
        this.inputItems.addAll(items);
        this.targetBlocks.addAll(blocks);
    }
    public void process(Identifier context, RegistryAccess registryAccess) {
        Set<Item> inputItems = getInputItems(context, registryAccess);
        Set<Block> targetBlocks = getTargetBlocks(context, registryAccess);
        if (!inputItems.isEmpty() && !targetBlocks.isEmpty()) {
            WorldScannerBlockEntity.addItemMappings(inputItems, targetBlocks);
        } else {
            GatheringChunksConstants.LOGGER.error("Invalid scanner data '{}', missing source items or target blocks", context);
        }
    }
    private Set<Block> getTargetBlocks(Identifier context, RegistryAccess registryAccess) {
        Registry<Block> blockRegistry = registryAccess != null ? registryAccess.lookupOrThrow(Registries.BLOCK) : BuiltInRegistries.BLOCK;
        return targetBlocks.stream()
                .map(x -> {
                    Identifier loc = Identifier.tryParse(x);
                    if (loc == null) {
                        GatheringChunksConstants.LOGGER.warn("Invalid block location {} in scanner data {}", x, context);
                        return Optional.<Block>empty();
                    }
                    Optional<Block> block = blockRegistry.getOptional(loc);
                    if (block.isEmpty()) {
                        GatheringChunksConstants.LOGGER.warn("Could not resolve block {} in scanner data {}", x, context);
                    }
                    return block;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }
    private Set<Item> getInputItems(Identifier context, RegistryAccess registryAccess) {
        Registry<Item> itemRegistry = registryAccess != null ? registryAccess.lookupOrThrow(Registries.ITEM) : BuiltInRegistries.ITEM;
        return inputItems.stream()
                .map(x -> {
                    Identifier loc = Identifier.tryParse(x);
                    if (loc == null) {
                        GatheringChunksConstants.LOGGER.warn("Invalid item location {} in scanner data {}", x, context);
                        return Optional.<Item>empty();
                    }
                    Optional<Item> item = itemRegistry.getOptional(loc);
                    if (item.isEmpty()) {
                        GatheringChunksConstants.LOGGER.warn("Could not resolve item {} in scanner data {}", x, context);
                    }
                    return item;
                })
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }
}