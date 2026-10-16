/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.mixins;

import com.ryvione.gatheringchunks.client.CreateWorldSettings;
import com.ryvione.gatheringchunks.client.screens.GatheringChunksConfigScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.tabs.GridLayoutTab;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$MoreTab")
public abstract class CreateWorldMoreTabMixin extends GridLayoutTab {

    private CreateWorldMoreTabMixin() {
        super(Component.empty());
    }

    @Inject(method = "<init>", at = @At("TAIL"))
    private void addGatheringChunksSettingsButton(CreateWorldScreen screen, CallbackInfo ci) {
        Button button = Button.builder(
                        Component.literal("GC settings"),
                        pressed -> Minecraft.getInstance().setScreen(
                                GatheringChunksConfigScreen.forWorldCreation(screen, CreateWorldSettings.draftFor(screen))))
                .width(210)
                .tooltip(Tooltip.create(Component.literal(
                        "Gathering Chunks settings for this world only. They are locked once the world is created.")))
                .build();
        this.layout.addChild(button, 3, 0);
    }
}
