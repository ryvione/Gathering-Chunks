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

import com.ryvione.gatheringchunks.client.screens.GatheringChunksConfigScreen;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

    protected PauseScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void addGatheringChunksButton(CallbackInfo ci) {
        int x = this.width / 2 + 4 + 100 + 4;
        int y = this.height / 4 + 72 - 16;

        this.addRenderableWidget(Button.builder(
                        Component.literal("\u2699"),
                        button -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(GatheringChunksConfigScreen.forLoadedWorld(this));
                            }
                        })
                .bounds(x, y, 20, 20)
                .tooltip(Tooltip.create(Component.literal("Gathering Chunks Config")))
                .build());
    }
}
