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

import com.ryvione.gatheringchunks.client.screens.WorldMenderScreen;
import com.ryvione.gatheringchunks.interop.Services;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
public class WorldMenderRecipeCategory implements IRecipeCategory<WorldMenderRecipe> {
    private final IDrawable icon;
    private final IDrawable background;
    public WorldMenderRecipeCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemStack(new ItemStack(Services.PLATFORM.worldMenderBlockItem()));
        background = guiHelper.drawableBuilder(WorldMenderScreen.CONTAINER_TEXTURE, 64, 53, 48, 48).setTextureSize(512, 512).build();
    }
    @Override
    public RecipeType<WorldMenderRecipe> getRecipeType() {
        return CBCJeiPlugin.WORLD_MENDER;
    }
    public Identifier getUid() {
        return getRecipeType().getUid();
    }
    public Class<? extends WorldMenderRecipe> getRecipeClass() {
        return getRecipeType().getRecipeClass();
    }
    @Override
    public Component getTitle() {
        return Component.translatable("block.chunkbychunk.worldmender");
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
    public void setRecipe(IRecipeLayoutBuilder builder, WorldMenderRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 16, 16).addItemStack(recipe.getInput());
        builder.addInvisibleIngredients(RecipeIngredientRole.CRAFTING_STATION).addItemStack(Services.PLATFORM.worldMenderBlockItem().getDefaultInstance());
    }
}