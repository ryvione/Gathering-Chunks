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

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class ConfigSystem {
    private static final Logger LOGGER = LogManager.getLogger(GatheringChunksConstants.MOD_ID);
    private static final String NEWLINE = "\n";
    private static final String START_COMMENT = "#";
    private static final String EQUALS = " = ";
    private static final String START_SECTION = "[";
    private static final String END_SECTION = "]";
    private static final String INDENT = "\t";
    private static final String CONFIG_VERSION_KEY = "config_version";
    private static final int CURRENT_CONFIG_VERSION = 3;

    private final Map<Class<?>, ConfigMetadata> metadataMap = new HashMap<>();
    private static Path centralConfigDir = null;

    public static void initCentralConfigDir(Path gameDir) {
        centralConfigDir = gameDir.resolve("config").resolve("GatheringChunks");
        try {
            Files.createDirectories(centralConfigDir);
            LOGGER.info("[ConfigSystem] Initialized centralized config directory at: {}", centralConfigDir);
        } catch (IOException e) {
            LOGGER.error("[ConfigSystem] Failed to create centralized config directory", e);
        }
    }

    public static Path getCentralConfigPath(String filename) {
        if (centralConfigDir == null) {
            LOGGER.warn("[ConfigSystem] Central config dir not initialized, using fallback");
            return Path.of("config", "GatheringChunks", filename);
        }
        return centralConfigDir.resolve(filename);
    }

    public void synchConfig(Path configFile, Path defaultFile, Object object) {
        if (!createPathTo(configFile)) return;

        int existingVersion = 0;
        boolean needsMigration = false;
        boolean configExists = Files.exists(configFile);
        boolean loadedSuccessfully = false;

        if (configExists) {
            LOGGER.info("[ConfigSystem] Loading config from: {}", configFile);
            existingVersion = readConfigVersion(configFile);
            if (existingVersion < CURRENT_CONFIG_VERSION) {
                LOGGER.info("[ConfigSystem] Config version {} is outdated, current version is {}. Creating backup...",
                        existingVersion, CURRENT_CONFIG_VERSION);
                backupConfig(configFile);
                needsMigration = true;
            }
            try (BufferedReader reader = Files.newBufferedReader(configFile)) {
                readInto(reader, object);
                loadedSuccessfully = true;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[ConfigSystem] Failed to read config at '{}' - Error: {}. Attempting to use defaults...", configFile, e.getMessage());
            }
        }

        if (!loadedSuccessfully && defaultFile != null && Files.exists(defaultFile)) {
            LOGGER.info("[ConfigSystem] Loading config from default: {}", defaultFile);
            try (BufferedReader reader = Files.newBufferedReader(defaultFile)) {
                readInto(reader, object);
                loadedSuccessfully = true;
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[ConfigSystem] Failed to read default config at '{}' - Error: {}", defaultFile, e.getMessage(), e);
            }
        }

        if (!configExists) {
            LOGGER.info("[ConfigSystem] Creating new config file at: {}", configFile);
            try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
                writeWithVersion(writer, object);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[ConfigSystem] Failed to write config at {} - Error: {}", configFile, e.getMessage(), e);
            }
        } else if (!loadedSuccessfully) {
            LOGGER.warn("[ConfigSystem] Previous config was corrupted/unreadable. Creating new config file...");
            try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
                writeWithVersion(writer, object);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[ConfigSystem] Failed to write config at {} - Error: {}", configFile, e.getMessage(), e);
            }
        } else if (needsMigration) {
            LOGGER.info("[ConfigSystem] Migrating config from version {} to {}.", existingVersion, CURRENT_CONFIG_VERSION);
            try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
                writeWithVersion(writer, object);
            } catch (IOException | RuntimeException e) {
                LOGGER.error("[ConfigSystem] Failed to write migrated config at {} - Error: {}", configFile, e.getMessage(), e);
            }
        } else {
            LOGGER.info("[ConfigSystem] Config is up-to-date (version {}), no update needed", existingVersion);
        }
    }

    private int readConfigVersion(Path configFile) {
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.startsWith(CONFIG_VERSION_KEY)) {
                    String[] parts = line.split(EQUALS, 2);
                    if (parts.length == 2) {
                        try {
                            return Integer.parseInt(parts[1].trim());
                        } catch (NumberFormatException e) {
                            return 0;
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warn("[ConfigSystem] Could not read config version: {}", e.getMessage());
        }
        return 0;
    }

    private void backupConfig(Path configFile) {
        try {
            Path backupPath = configFile.getParent().resolve(configFile.getFileName() + ".backup");
            Files.copy(configFile, backupPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[ConfigSystem] Created config backup at: {}", backupPath);
        } catch (IOException e) {
            LOGGER.error("[ConfigSystem] Failed to create config backup", e);
        }
    }

    public void synchConfig(Path configFile, Object object) {
        synchConfig(configFile, null, object);
    }

    public void reloadConfig(Path configFile, Object object) {
        if (!Files.exists(configFile)) {
            LOGGER.warn("[ConfigSystem] Cannot reload - config file doesn't exist: {}", configFile);
            return;
        }
        LOGGER.info("[ConfigSystem] Reloading config from: {}", configFile);
        try (BufferedReader reader = Files.newBufferedReader(configFile)) {
            readInto(reader, object);
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[ConfigSystem] Failed to reload config - Error: {}", e.getMessage(), e);
        }
    }

    public void write(Path configFile, Object object) {
        if (!createPathTo(configFile)) return;
        try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
            writeWithVersion(writer, object);
            LOGGER.info("[ConfigSystem] Config written to: {}", configFile);
        } catch (IOException e) {
            LOGGER.error("[ConfigSystem] Failed to write config at {}", configFile, e);
        }
    }

    private boolean createPathTo(Path configFile) {
        if (configFile.getParent() != null && !Files.exists(configFile.getParent())) {
            try {
                Files.createDirectories(configFile.getParent());
                return true;
            } catch (IOException e) {
                LOGGER.error("[ConfigSystem] Failed to create config path '{}'", configFile.getParent(), e);
                return false;
            }
        }
        return true;
    }

    public void readInto(BufferedReader reader, Object into) {
        ConfigMetadata metadata = getMetadata(into);

        Map<String, SectionMetadata> allSections = new HashMap<>();
        Map<String, Object> sectionObjects = new HashMap<>();
        collectAllSections(metadata, into, allSections, sectionObjects, "");

        try {
            ObjectMetadata currentMetadata = metadata;
            Object currentObject = into;

            String line = reader.readLine();
            while (line != null) {
                line = line.trim();
                if (line.startsWith(START_SECTION)) {
                    int endIndex = line.indexOf(END_SECTION);
                    if (endIndex != -1) {
                        String sectionName = line.substring(START_SECTION.length(), endIndex);
                        String key = sectionName.toLowerCase(Locale.ROOT);

                        if (allSections.containsKey(key)) {
                            currentMetadata = allSections.get(key);
                            currentObject = sectionObjects.get(key);
                        } else {
                            LOGGER.warn("Encountered unexpected section {}", sectionName);
                            currentMetadata = metadata;
                            currentObject = into;
                        }
                    } else {
                        LOGGER.warn("Invalid section statement {}", line);
                    }
                } else if (!line.startsWith(START_COMMENT) && !line.isEmpty()) {
                    String[] parts = line.split(EQUALS, 2);
                    if (parts.length == 2) {
                        String fieldName = parts[0].trim();
                        String value = parts[1].trim();

                        if (fieldName.equals(CONFIG_VERSION_KEY)) {
                            line = reader.readLine();
                            continue;
                        }

                        FieldMetadata<?> fieldMetadata = currentMetadata.getFields().get(fieldName.toLowerCase(Locale.ROOT));
                        if (fieldMetadata != null) {
                            try {
                                fieldMetadata.deserializeValue(currentObject, value);
                            } catch (RuntimeException e) {
                                LOGGER.warn("[ConfigSystem] Ignoring invalid value '{}' for field '{}' ({}) - keeping previous/default value",
                                        value, fieldName, e.getMessage());
                            }
                        } else {
                            LOGGER.warn("Unexpected field {}", fieldName);
                        }
                    } else {
                        LOGGER.warn("Bad config line {}", line);
                    }
                }
                line = reader.readLine();
            }
        } catch (IOException e) {
            LOGGER.error("Failed to read config", e);
        }
    }

    private void collectAllSections(ObjectMetadata metadata, Object object,
            Map<String, SectionMetadata> allSections, Map<String, Object> sectionObjects, String prefix) {
        if (metadata instanceof ConfigMetadata configMetadata) {
            for (SectionMetadata section : configMetadata.getSections().values()) {
                String key = section.getName().toLowerCase(Locale.ROOT);
                Object sectionObj = section.getSectionObject(object);
                allSections.put(key, section);
                sectionObjects.put(key, sectionObj);
                collectAllSections(section, sectionObj, allSections, sectionObjects, key);
            }
        } else if (metadata instanceof SectionMetadata sectionMetadata) {
            for (SectionMetadata sub : sectionMetadata.getSubsections().values()) {
                String key = sub.getName().toLowerCase(Locale.ROOT);
                Object subObj = sub.getSectionObject(object);
                allSections.put(key, sub);
                sectionObjects.put(key, subObj);
                collectAllSections(sub, subObj, allSections, sectionObjects, key);
            }
        }
    }

    private void writeWithVersion(Writer writer, Object object) throws IOException {
        writer.write(START_COMMENT);
        writer.write(" GatheringChunks Config");
        writer.write(NEWLINE);
        writer.write(START_COMMENT);
        writer.write(" This config is automatically migrated between mod versions");
        writer.write(NEWLINE);
        writer.write(CONFIG_VERSION_KEY);
        writer.write(EQUALS);
        writer.write(String.valueOf(CURRENT_CONFIG_VERSION));
        writer.write(NEWLINE);
        writer.write(NEWLINE);
        write(writer, object);
    }

    public void write(Writer writer, Object object) {
        ConfigMetadata metadata = getMetadata(object);
        try {
            for (FieldMetadata<?> field : metadata.getFields().values()) {
                writeField(writer, object, field, "");
            }
            for (SectionMetadata section : metadata.getSections().values()) {
                writeSectionRecursive(writer, object, section, "");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to write config", e);
        }
    }

    private void writeSectionRecursive(Writer writer, Object parentObject, SectionMetadata section, String indentation) throws IOException {
        Object sectionObject = section.getSectionObject(parentObject);
        writer.write(NEWLINE);
        writer.write(START_SECTION);
        writer.write(section.getName());
        writer.write(END_SECTION);
        writer.write(NEWLINE);
        for (FieldMetadata<?> field : section.getFields().values()) {
            writeField(writer, sectionObject, field, INDENT);
        }
        for (SectionMetadata sub : section.getSubsections().values()) {
            writeSectionRecursive(writer, sectionObject, sub, INDENT);
        }
    }

    private void writeField(Writer writer, Object object, FieldMetadata<?> field, String indentation) throws IOException {
        for (String commentLine : field.getComments()) {
            for (String line : commentLine.split("[\n\r]+")) {
                writer.write(indentation);
                writer.write(START_COMMENT);
                writer.write(line);
                writer.write(NEWLINE);
            }
        }
        writer.write(indentation);
        writer.write(field.getName());
        writer.write(EQUALS);
        writer.write(field.serializeValue(object));
        writer.write(NEWLINE);
    }

    private ConfigMetadata getMetadata(Object o) {
        ConfigMetadata metadata = metadataMap.get(o.getClass());
        if (metadata == null) {
            metadata = MetadataBuilder.build(o.getClass());
            metadataMap.put(o.getClass(), metadata);
        }
        return metadata;
    }
}