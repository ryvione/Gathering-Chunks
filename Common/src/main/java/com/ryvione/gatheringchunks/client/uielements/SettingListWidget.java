/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.client.uielements;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.system.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
public class SettingListWidget extends ContainerObjectSelectionList<SettingListWidget.SettingEntry> {
    private final Minecraft minecraft;
    private EditBox lastFocused = null;
    private int rowWidth;
    public SettingListWidget(Minecraft minecraft, Screen parent, int width, int top, int bottom, int rowWidth) {
        super(minecraft, width, parent.height, top, bottom);
        this.minecraft = minecraft;
        this.rowWidth = rowWidth;
        ConfigMetadata metadata = MetadataBuilder.build(ChunkByChunkConfig.class);
        ChunkByChunkConfig defaultConfig = new ChunkByChunkConfig();
        for (SectionMetadata section : metadata.getSections().values()) {
            Object defaultSection = section.getSectionObject(defaultConfig);
            Object configSection = section.getSectionObject(ChunkByChunkConfig.get());
            this.addEntry(new SectionTitleEntry(section.getDisplayName()));
            for (FieldMetadata<?> field : section.getFields().values()) {
                if (field instanceof BooleanFieldMetadata boolField) {
                    Boolean defaultValue = boolField.getValue(defaultSection);
                    this.addEntry(new BooleanEntry(field.getDisplayName(),
                            () -> boolField.getValue(configSection),
                            (x) -> boolField.setValue(configSection, x), defaultValue));
                } else if (field instanceof EnumFieldMetadata enumField) {
                    Enum<?> defaultValue = enumField.getValue(defaultSection);
                    this.addEntry(new EnumEntry(enumField.getDisplayName(), enumField.enumType(),
                            () -> enumField.getValue(configSection),
                            (x) -> enumField.setValue(configSection, x), defaultValue));
                } else if (field instanceof IntFieldMetadata intField) {
                    Integer defaultValue = intField.getValue(defaultSection);
                    if (intField.getMaxValue() - intField.getMinValue() > 256) {
                        this.addEntry(new ExtendedIntegerEntry(field.getDisplayName(), intField.getMinValue(), intField.getMaxValue(),
                                () -> intField.getValue(configSection),
                                (x) -> intField.setValue(configSection, x), defaultValue));
                    } else {
                        this.addEntry(new IntegerEntry(field.getDisplayName(), intField.getMinValue(), intField.getMaxValue(),
                                () -> intField.getValue(configSection),
                                (x) -> intField.setValue(configSection, x), defaultValue));
                    }
                } else if (field instanceof StringFieldMetadata stringField) {
                   this.addEntry(new StringEntry(field.getDisplayName(), () -> stringField.getValue(configSection), (x) -> stringField.setValue(configSection, x), stringField.getValue(defaultSection)));
                } else {
                    GatheringChunksConstants.LOGGER.info("Skipping config option {} as type not supported", field.getName());
                }
            }
        }
    }
    public int getRowWidth() {
        return rowWidth;
    }
    protected int scrollBarX() {
        return this.width / 2 + getRowWidth() / 2 + 4;
    }
    public void tick() {
        this.children().forEach(SettingEntry::tick);
    }
    public void reset() {
        children().forEach(SettingEntry::reset);
    }
    public class SectionTitleEntry extends SettingEntry {
        private final Component displayName;
        public SectionTitleEntry(Component displayName) {
            this.displayName = displayName;
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int top = getContentY();
            int left = getContentX();
            guiGraphics.drawString(SettingListWidget.this.minecraft.font, this.displayName, left + 12, top + 8, 0xFFFFFFFF);        }
        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }
        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.emptyList();
        }
    }
    public class IntegerEntry extends AbstractWidgetEntry<IntegerSlider> {
        private final Integer defaultValue;
        private final Consumer<Integer> setter;
        public IntegerEntry(Component displayName, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter, Integer defaultValue) {
            super(new IntegerSlider(0, 0, getRowWidth(), 20, displayName, min, max, getter, setter));
            this.setter = setter;
            this.defaultValue = defaultValue;
        }
        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue);
        }
    }
    public class StringEntry extends AbstractWidgetEntry<EditBox> {
        private final Component displayName;
        private final String defaultValue;
        private final Consumer<String> setter;
        public StringEntry(Component displayName, Supplier<String> getter, Consumer<String> setter, String defaultValue) {
            super(new EditBox(SettingListWidget.this.minecraft.font, 0, 0, getRowWidth(), 20, displayName));
            this.displayName = displayName;
            this.defaultValue = defaultValue;
            this.setter = setter;
            widget.setValue(getter.get());
            widget.setEditable(true);
            widget.setResponder(setter);
        }
        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue);
        }
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) {
                lastFocused = widget;
                return true;
            }
            return false;
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int top = getContentY();
            int left = getContentX();
            int labelLength = SettingListWidget.this.minecraft.font.width(this.displayName);
            guiGraphics.drawString(SettingListWidget.this.minecraft.font, this.displayName, left, top + 6, 0xFFFFFFFF);
            widget.setX(left + labelLength + 6);
            widget.setWidth(getRowWidth() - labelLength - 6);
            widget.setY(top);
            widget.render(guiGraphics, mouseX, mouseY, delta);
        }
        @Override
        public boolean charTyped(CharacterEvent event) {
            return widget.charTyped(event);
        }
        @Override
        public void tick() {
        }
    }
    public class BooleanEntry extends AbstractWidgetEntry<CycleButton<Boolean>> {
        private final Boolean defaultValue;
        private final Consumer<Boolean> setter;
        public BooleanEntry(Component displayName, Supplier<Boolean> getter, Consumer<Boolean> setter, Boolean defaultValue) {
            super(new CycleButton.Builder<Boolean>((x) -> Component.translatable((x) ? "gui.yes" : "gui.no"), getter::get)
                    .withValues(Boolean.FALSE, Boolean.TRUE)
                    .create(0, 0, getRowWidth(), 20, displayName, (cycleButton, value) -> setter.accept(value)));
            this.defaultValue = defaultValue;
            this.setter = setter;
        }
        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue);
        }
    }
    public class ExtendedIntegerEntry extends AbstractWidgetEntry<EditBox> {
        private final Component displayName;
        private final Integer defaultValue;
        private final Consumer<Integer> setter;
        public ExtendedIntegerEntry(Component displayName, int min, int max, Supplier<Integer> getter, Consumer<Integer> setter, Integer defaultValue) {
            super(new EditBox(SettingListWidget.this.minecraft.font, 0, 0, getRowWidth(), 20, displayName));
            this.displayName = displayName;
            this.defaultValue = defaultValue;
            this.setter = setter;
            widget.setFilter(x -> {
                if (x.isEmpty() || "-".equals(x)) {
                    return true;
                }
                try {
                    int val = Integer.parseInt(x);
                    return val >= min && val <= max;
                } catch (NumberFormatException e) {
                    return false;
                }
            });
            widget.setValue(getter.get().toString());
            widget.setEditable(true);
            widget.setResponder(value -> {
                if (value.isEmpty() || "-".equals(value)) {
                    if (0 >= min && 0 <= max) {
                        setter.accept(0);
                    }
                } else {
                    setter.accept(Integer.parseInt(value));
                }
            });
        }
        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue.toString());
        }
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (super.mouseClicked(event, doubleClick)) {
                lastFocused = widget;
                return true;
            }
            return false;
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int top = getContentY();
            int left = getContentX();
            int labelLength = SettingListWidget.this.minecraft.font.width(this.displayName);
            guiGraphics.drawString(SettingListWidget.this.minecraft.font, this.displayName, left, top + 6, 0xFFFFFFFF);
            widget.setX(left + labelLength + 6);
            widget.setWidth(getRowWidth() - labelLength - 6);
            widget.setY(top);
            widget.render(guiGraphics, mouseX, mouseY, delta);
        }
        @Override
        public boolean charTyped(CharacterEvent event) {
            return widget.charTyped(event);
        }
        @Override
        public void tick() {
        }
    }
    public class EnumEntry extends AbstractWidgetEntry<CycleButton<Enum<?>>> {
        private final Enum<?> defaultValue;
        private final Consumer<Enum<?>> setter;
        public EnumEntry(Component displayName, Class<? extends Enum<?>> type, Supplier<Enum<?>> getter, Consumer<Enum<?>> setter, Enum<?> defaultValue) {
            super(new CycleButton.Builder<Enum<?>>((x) -> Component.translatable("enumvalue.chunkbychunk." + type.getSimpleName() + "." + x.name()), getter)
                    .withValues(type.getEnumConstants())
                    .create(0, 0, getRowWidth(), 20, displayName, (cycleButton, value) -> setter.accept(value)));
            this.defaultValue = defaultValue;
            this.setter = setter;
        }
        @Override
        public void reset() {
            setter.accept(defaultValue);
            widget.setValue(defaultValue);
        }
    }
    public abstract class AbstractWidgetEntry<T extends AbstractWidget> extends SettingEntry {
        protected final T widget;
        private boolean dragging;
        public AbstractWidgetEntry(T widget) {
            this.widget = widget;
        }
        @Override
        public void renderContent(GuiGraphics guiGraphics, int mouseX, int mouseY, boolean hovered, float delta) {
            int top = getContentY();
            int left = getContentX();
            widget.setX(left);
            widget.setY(top);
            widget.render(guiGraphics, mouseX, mouseY, delta);
        }
        @Override
        public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
            if (SettingListWidget.this.lastFocused != null && SettingListWidget.this.lastFocused != widget) {
                SettingListWidget.this.lastFocused.setFocused(false);
            }
            dragging = true;
            return this.widget.mouseClicked(event, doubleClick);
        }
        @Override
        public boolean mouseReleased(MouseButtonEvent event) {
            dragging = false;
            return this.widget.mouseReleased(event);
        }
        @Override
        public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
            if (dragging) {
                return this.widget.mouseDragged(event, deltaX, deltaY);
            }
            return false;
        }
        @Override
        public boolean isMouseOver(double p_93537_, double p_93538_) {
            return this.widget.isMouseOver(p_93537_, p_93538_);
        }
        @Override
        public boolean keyPressed(KeyEvent event) {
            return this.widget.keyPressed(event);
        }
        @Override
        public boolean keyReleased(KeyEvent event) {
            return this.widget.keyReleased(event);
        }
        @Override
        public List<? extends NarratableEntry> narratables() {
            return Collections.emptyList();
        }
        @Override
        public List<? extends GuiEventListener> children() {
            return Collections.singletonList(widget);
        }
    }
    public static abstract class SettingEntry extends Entry<SettingEntry> {
        public void tick() {
        }
        public void reset() {
        }
    }
}