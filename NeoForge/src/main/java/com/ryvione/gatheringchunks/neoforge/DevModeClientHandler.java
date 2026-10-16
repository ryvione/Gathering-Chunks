/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.neoforge;

import com.mojang.brigadier.CommandDispatcher;
import com.ryvione.gatheringchunks.client.DevModeState;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

@EventBusSubscriber(modid = GatheringChunksConstants.MOD_ID, value = Dist.CLIENT)
public class DevModeClientHandler {

    private static String cachedVersionString = null;

    private static String getVersionString() {
        if (cachedVersionString == null) {
            cachedVersionString = ModList.get().getModContainerById(GatheringChunksConstants.MOD_ID)
                    .map(container -> container.getModInfo().getVersion().toString())
                    .orElse("unknown");
        }
        return cachedVersionString;
    }

    @SubscribeEvent
    public static void onRegisterClientCommands(RegisterClientCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("gatheringchunksdev")
                .executes(context -> {
                    boolean nowEnabled = DevModeState.toggle();
                    context.getSource().sendSuccess(() -> Component.literal(
                            "§e[Gathering Chunks] Dev Mode HUD " + (nowEnabled ? "§aenabled" : "§cdisabled")), false);
                    return 1;
                }));
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                net.minecraft.resources.Identifier.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "dev_mode_hud"),
                (guiGraphics, deltaTracker) -> renderOverlay(guiGraphics));
    }

    private static void renderOverlay(GuiGraphics guiGraphics) {
        if (!DevModeState.isEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        String text = "Gathering Chunks - " + getVersionString() + " - Dev Mode";
        guiGraphics.drawString(mc.font, text, 2, 2, 0xE0E0E0, true);
    }
}
