/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.config.system;

import com.google.common.collect.ImmutableMap;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class SectionMetadata extends ObjectMetadata {
    private final Field sectionField;
    private final String name;
    private final Component displayName;
    private final Map<String, SectionMetadata> subsections;

    public SectionMetadata(String name, Collection<FieldMetadata<?>> fields, Field sectionField, List<SectionMetadata> subsections) {
        super(fields);
        this.name = name;
        this.sectionField = sectionField;
        this.sectionField.setAccessible(true);
        this.displayName = Component.translatable("config.gatheringchunks.section." + name.toLowerCase(Locale.ROOT));
        ImmutableMap.Builder<String, SectionMetadata> builder = ImmutableMap.builder();
        for (SectionMetadata sub : subsections) {
            builder.put(sub.getName().toLowerCase(Locale.ROOT), sub);
        }
        this.subsections = builder.build();
    }

    public SectionMetadata(String name, Collection<FieldMetadata<?>> fields, Field sectionField) {
        this(name, fields, sectionField, List.of());
    }

    public String getName() {
        return name;
    }

    public Component getDisplayName() {
        return displayName;
    }

    public Map<String, SectionMetadata> getSubsections() {
        return subsections;
    }

    public Object getSectionObject(Object source) {
        try {
            return sectionField.get(source);
        } catch (IllegalAccessException e) {
            throw new ConfigException("Failed to access section object " + name, e);
        }
    }
}