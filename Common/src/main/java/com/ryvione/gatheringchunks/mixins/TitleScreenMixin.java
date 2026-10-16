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

import com.ryvione.gatheringchunks.client.UpdateNotificationOverlay;
import com.ryvione.gatheringchunks.client.screens.GatheringChunksConfigScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void addGatheringChunksConfigButton(CallbackInfo ci) {
        int x = this.width / 2 + 104;
        int y = this.height / 4 + 48 + 24;

        this.addRenderableWidget(Button.builder(
                        Component.literal("\u2699 GC"),
                        button -> {
                            if (this.minecraft != null) {
                                this.minecraft.setScreen(new GatheringChunksConfigScreen(this));
                            }
                        })
                .bounds(x, y, 50, 20)
                .tooltip(Tooltip.create(Component.literal("Gathering Chunks Config")))
                .build());
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRender(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        UpdateNotificationOverlay.renderUpdateWarning(guiGraphics, (TitleScreen) (Object) this);
    }
}
