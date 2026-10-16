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

import com.ryvione.gatheringchunks.client.screens.WorldForgeScreen;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.interop.Services;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.ITickTimer;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;

public class WorldForgeRecipeCategory implements IRecipeCategory<WorldForgeRecipe> {
    private final IDrawable background;
    private final IDrawable icon;
    private final List<IDrawableStatic> frames;
    private final ITickTimer frameTimer;

    public WorldForgeRecipeCategory(IGuiHelper guiHelper) {
        background = guiHelper.createDrawable(WorldForgeScreen.CONTAINER_TEXTURE, 48, 25, 94, 36);
        icon = guiHelper.createDrawableItemStack(new ItemStack(Services.PLATFORM.worldForgeBlockItem()));
        frames = new ArrayList<>();
        for (int i = 0; i < WorldForgeScreen.NUM_FRAMES; i++) {
            frames.add(guiHelper.createDrawable(WorldForgeScreen.CONTAINER_TEXTURE, 176, i * 11, 30, 11));
        }
        frameTimer = guiHelper.createTickTimer((int) WorldForgeScreen.TICKS_PER_FRAME * frames.size(), frames.size() - 1, false);
    }

    @Override
    public RecipeType<WorldForgeRecipe> getRecipeType() {
        return CBCJeiPlugin.WORLD_FORGE;
    }

    public Identifier getUid() {
        return getRecipeType().getUid();
    }

    public Class<? extends WorldForgeRecipe> getRecipeClass() {
        return getRecipeType().getRecipeClass();
    }

    @Override
    public Component getTitle() {
        return Component.translatable("block.chunkbychunk.worldforge");
    }

    @Override
    public int getWidth() {
        return background.getWidth();
    }

    @Override
    public int getHeight() {
        return background.getHeight();
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void draw(WorldForgeRecipe recipe, IRecipeSlotsView recipeSlotsView, GuiGraphics guiGraphics, double mouseX, double mouseY) {
        background.draw(guiGraphics);
        frames.get(frameTimer.getValue()).draw(guiGraphics, 30, 12, 0, 0, 0, Math.max(0, 30 - 30 * recipe.getFuelValue() / ChunkByChunkConfig.get().getWorldForge().getFragmentFuelCost()));
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, WorldForgeRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 10, 10).setSlotName("Input").addIngredients(VanillaTypes.ITEM_STACK, recipe.getInputItems());
        builder.addSlot(RecipeIngredientRole.OUTPUT, 68, 10).setSlotName("Output").addIngredient(VanillaTypes.ITEM_STACK, recipe.getOutput());
        builder.addInvisibleIngredients(RecipeIngredientRole.CRAFTING_STATION).addItemStack(Services.PLATFORM.worldForgeBlockItem().getDefaultInstance());
    }

    public static List<WorldForgeRecipe> getOreRecipes() {
        List<WorldForgeRecipe> recipes = new ArrayList<>();
        recipes.add(WorldForgeRecipe.oreToFragment(new ItemStack(Items.RAW_COPPER), 1));
        recipes.add(WorldForgeRecipe.oreToFragment(new ItemStack(Items.RAW_IRON), 4));
        recipes.add(WorldForgeRecipe.oreToFragment(new ItemStack(Items.RAW_GOLD), 8));
        recipes.add(WorldForgeRecipe.oreToFragment(new ItemStack(Items.DIAMOND), 16));
        recipes.add(WorldForgeRecipe.oreToFragment(new ItemStack(Items.NETHERITE_INGOT), 64));
        return recipes;
    }
}