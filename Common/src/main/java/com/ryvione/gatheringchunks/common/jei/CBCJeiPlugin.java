/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.common.jei;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.blockEntities.WorldForgeBlockEntity;
import com.ryvione.gatheringchunks.common.blockEntities.WorldScannerBlockEntity;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.interop.Services;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

@JeiPlugin
public class CBCJeiPlugin implements IModPlugin {

    public static final RecipeType<WorldForgeRecipe> WORLD_FORGE =
            RecipeType.create(GatheringChunksConstants.MOD_ID, "worldforge", WorldForgeRecipe.class);

    public static final RecipeType<WorldScannerRecipe> WORLD_SCANNER =
            RecipeType.create(GatheringChunksConstants.MOD_ID, "worldscanner", WorldScannerRecipe.class);

    public static final RecipeType<WorldMenderRecipe> WORLD_MENDER =
            RecipeType.create(GatheringChunksConstants.MOD_ID, "worldmender", WorldMenderRecipe.class);

    @Override
    public Identifier getPluginUid() {
        return Identifier.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new WorldForgeRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new WorldScannerRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new WorldMenderRecipeCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registerWorldForgeRecipes(registration);
        registerWorldScannerRecipes(registration);
        registerWorldMenderRecipes(registration);
    }

    private void registerWorldMenderRecipes(IRecipeRegistration registration) {
        registration.addRecipes(WORLD_MENDER, Arrays.asList(
                new WorldMenderRecipe(Services.PLATFORM.worldCoreBlockItem().getDefaultInstance()),
                new WorldMenderRecipe(Services.PLATFORM.unstableChunkSpawnBlockItem().getDefaultInstance()),
                new WorldMenderRecipe(Services.PLATFORM.spawnChunkBlockItem().getDefaultInstance())
        ));
        registration.addRecipes(
                WORLD_MENDER,
                Services.PLATFORM.biomeThemeBlockItems().stream().map(WorldMenderRecipe::new).toList()
        );
    }

    private void registerWorldScannerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
                WORLD_SCANNER,
                WorldScannerBlockEntity.FUEL.entrySet().stream()
                        .map(e -> new WorldScannerRecipe(
                                e.getKey().getDefaultInstance(),
                                e.getValue().get()
                        ))
                        .toList()
        );
    }

    private void registerWorldForgeRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
                WORLD_FORGE,
                WorldForgeBlockEntity.FUEL_TAGS.entrySet().stream()
                        .map(tagInfo -> {
                            int inputSize = determineForgeInput(tagInfo.getValue().get());
                            ItemStack output = determineForgeOutput(tagInfo.getValue().get());

                            List<ItemStack> inputs = registration.getIngredientManager()
                                    .getAllItemStacks().stream()
                                    .filter(stack -> stack.is(tagInfo.getKey()))
                                    .map(stack -> {
                                        ItemStack copy = stack.copy();
                                        copy.setCount(inputSize);
                                        return copy;
                                    })
                                    .toList();

                            return new WorldForgeRecipe(inputs, tagInfo.getValue().get(), output);
                        })
                        .filter(r -> !r.getInputItems().isEmpty())
                        .toList()
        );

        registration.addRecipes(
                WORLD_FORGE,
                WorldForgeBlockEntity.FUEL.entrySet().stream()
                        .map(fuelInfo -> {
                            ItemStack output = determineForgeOutput(fuelInfo.getValue().get());
                            return new WorldForgeRecipe(
                                    Collections.singletonList(fuelInfo.getKey().getDefaultInstance()),
                                    fuelInfo.getValue().get(),
                                    output
                            );
                        })
                        .toList()
        );

        registration.addRecipes(
                WORLD_FORGE,
                WorldForgeBlockEntity.CRYSTAL_STEPS.entrySet().stream()
                        .map(step -> {
                            ItemStack input = step.getKey().getDefaultInstance().copy();
                            input.setCount(WorldForgeBlockEntity.GROW_CRYSTAL_AT);
                            ItemStack output = step.getValue().getDefaultInstance();
                            return new WorldForgeRecipe(
                                    Collections.singletonList(input),
                                    ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost(),
                                    output
                            );
                        })
                        .toList()
        );

        registration.addRecipes(WORLD_FORGE, Arrays.asList(
                WorldForgeRecipe.oreToFragment(new ItemStack(Items.RAW_COPPER), 1),
                WorldForgeRecipe.oreToFragment(new ItemStack(Items.RAW_IRON), 4),
                WorldForgeRecipe.oreToFragment(new ItemStack(Items.RAW_GOLD), 8),
                WorldForgeRecipe.oreToFragment(new ItemStack(Items.DIAMOND), 16),
                WorldForgeRecipe.oreToFragment(new ItemStack(Items.NETHERITE_INGOT), 64)
        ));
    }

    private int determineForgeInput(int fuelValue) {
        return Math.max(
                1,
                ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost() / fuelValue
        );
    }

    @NotNull
    private ItemStack determineForgeOutput(int fuelValue) {
        int count = fuelValue / ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost();
        ItemStack output = Services.PLATFORM.worldFragmentItem().getDefaultInstance();
        if (count > 1) {
            output = output.copy();
            output.setCount(count);
        }
        return output;
    }
}