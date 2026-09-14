/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.client.screens;

import com.ryvione.gatheringchunks.client.DevSnapshotWarning;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

public class DevSnapshotWarningScreen extends Screen {

    private static final long COUNTDOWN_MS = 3000;
    private static final int LINE_HEIGHT = 10;
    private static final int BOTTOM_AREA_HEIGHT = 44; 

    private final long startTimeMs = Util.getMillis();
    private boolean proceeded = false;
    private int lastShownSeconds = -1;

    private Checkbox doNotShowAgainCheckbox;
    private Button continueButton;

    public DevSnapshotWarningScreen() {
        super(Component.literal("Development Snapshot"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int widgetY = this.height - BOTTOM_AREA_HEIGHT;

        this.doNotShowAgainCheckbox = Checkbox.builder(Component.literal("Do not show again"), this.font)
                .pos(centerX - 155, widgetY)
                .maxWidth(150)
                .selected(false)
                .build();
        this.addRenderableWidget(this.doNotShowAgainCheckbox);

        this.continueButton = Button.builder(this.getContinueLabel(), button -> this.proceed())
                .bounds(centerX + 55, widgetY, 100, 20)
                .build();
        this.addRenderableWidget(this.continueButton);
        this.updateCountdown();
    }

    private boolean isCountdownDone() {
        return (Util.getMillis() - this.startTimeMs) >= COUNTDOWN_MS;
    }

    private int getRemainingSeconds() {
        if (this.isCountdownDone()) {
            return 0;
        }
        long elapsed = Util.getMillis() - this.startTimeMs;
        long remaining = (COUNTDOWN_MS - elapsed + 999) / 1000;
        return (int) Math.max(1, remaining);
    }

    private Component getContinueLabel() {
        int s = this.getRemainingSeconds();
        if (s <= 0) {
            return Component.literal("Continue");
        }
        return Component.literal("Continue in " + s + "...");
    }

    private void updateCountdown() {
        if (this.continueButton == null) {
            return;
        }
        int seconds = this.getRemainingSeconds();
        if (seconds != this.lastShownSeconds) {
            this.lastShownSeconds = seconds;
            this.continueButton.setMessage(this.getContinueLabel());
        }
        this.continueButton.setAlpha(seconds <= 0 ? 1.0F : 0.4F);
    }

    @Override
    public void tick() {
        super.tick();
        this.updateCountdown();
    }

    private void proceed() {
        if (!this.isCountdownDone() || this.proceeded) {
            return;
        }
        this.proceeded = true;
        if (this.doNotShowAgainCheckbox != null && this.doNotShowAgainCheckbox.selected()) {
            DevSnapshotWarning.setDoNotShowAgain();
        }
        this.onClose();
    }

    private List<Component> getLines() {
        return List.of(
                Component.literal("You are running a Development Snapshot of Gathering Chunks."),
                Component.empty(),
                Component.literal("This build is an unfinished work-in-progress snapshot and may:").withStyle(ChatFormatting.YELLOW),
                Component.literal(" \u2022 Crash unexpectedly or corrupt your worlds").withStyle(ChatFormatting.GRAY),
                Component.literal(" \u2022 Delete or overwrite world data without warning").withStyle(ChatFormatting.GRAY),
                Component.literal(" \u2022 Contain unfinished, unstable or broken features").withStyle(ChatFormatting.GRAY),
                Component.literal(" \u2022 Have settings that change between builds").withStyle(ChatFormatting.GRAY),
                Component.literal(" \u2022 Be replaced by newer snapshots at any time").withStyle(ChatFormatting.GRAY),
                Component.empty(),
                Component.literal("I STRONGLY recommend backing up your worlds/saves before playing!").withStyle(ChatFormatting.RED),
                Component.literal("By clicking Continue, you acknowledge these risks.").withStyle(ChatFormatting.RED)
        );
    }

    @Override
    public void renderBackground(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        guiGraphics.fillGradient(0, 0, this.width, this.height, 0xC0101010, 0xD0101010);
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        this.updateCountdown();
        this.renderBackground(guiGraphics, mouseX, mouseY, partialTick);

        int centerX = this.width / 2;
        int wrapWidth = Math.max(240, this.width - 80);

        guiGraphics.drawCenteredString(this.font,
                Component.literal("!!! DEVELOPMENT SNAPSHOT BUILD !!!").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
                centerX, 20, 0xFFFFFFFF);

        List<FormattedCharSequence> wrapped = new ArrayList<>();
        for (Component line : this.getLines()) {
            if (line.getString().isEmpty()) {
                wrapped.add(FormattedCharSequence.EMPTY);
            } else {
                wrapped.addAll(this.font.split(line, wrapWidth));
            }
        }

        int textTop = 40;
        int blockHeight = wrapped.size() * LINE_HEIGHT;
        int availableBottom = this.height - BOTTOM_AREA_HEIGHT;

        int freeSpace = availableBottom - textTop - blockHeight;
        int startY = freeSpace > 0 ? textTop + freeSpace / 2 : textTop;

        int y = startY;
        for (FormattedCharSequence seq : wrapped) {
            guiGraphics.drawCenteredString(this.font, seq, centerX, y, 0xFFFFFFFF);
            y += LINE_HEIGHT;
        }

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
