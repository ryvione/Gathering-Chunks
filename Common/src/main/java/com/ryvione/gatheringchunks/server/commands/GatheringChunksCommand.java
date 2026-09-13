/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.server.commands;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import com.ryvione.gatheringchunks.common.network.S2COpenConfigPacket;
import com.ryvione.gatheringchunks.common.network.S2CSyncConfigPacket;
import com.ryvione.gatheringchunks.common.util.ConfigUtil;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.config.system.ConfigMetadata;
import com.ryvione.gatheringchunks.config.system.FieldMetadata;
import com.ryvione.gatheringchunks.config.system.MetadataBuilder;
import com.ryvione.gatheringchunks.config.system.SectionMetadata;
import com.ryvione.gatheringchunks.interop.Services;
import com.ryvione.gatheringchunks.server.world.ChunkSpawnController;
import com.ryvione.gatheringchunks.server.world.SkyChunkGenerator;
import com.ryvione.gatheringchunks.server.world.SpawnChunkHelper;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.coordinates.Coordinates;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class GatheringChunksCommand {

    private static final Logger LOGGER = LogManager.getLogger(GatheringChunksConstants.MOD_ID);
    private static final Gson GSON = new GsonBuilder().create();

    private static final SimpleCommandExceptionType INVALID_POSITION = new SimpleCommandExceptionType(
            Component.translatable("commands.gatheringchunks.spawnchunk.invalidPosition"));
    private static final SimpleCommandExceptionType INVALID_LEVEL = new SimpleCommandExceptionType(
            Component.translatable("commands.gatheringchunks.spawnchunk.invalidlevel"));
    private static final SimpleCommandExceptionType INVALID_THEME = new SimpleCommandExceptionType(
            Component.translatable("commands.gatheringchunks.spawnchunk.invalidtheme"));
    private static final SimpleCommandExceptionType NON_EMPTY_CHUNK = new SimpleCommandExceptionType(
            Component.translatable("commands.gatheringchunks.spawnchunk.nonemptychunk"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("gatheringchunks")
                .then(Commands.literal("help")
                        .executes(GatheringChunksCommand::showHelp)
                        .then(Commands.argument("topic", StringArgumentType.word())
                                .suggests(new HelpTopicSuggestionProvider())
                                .executes(GatheringChunksCommand::showSpecificHelp)))

                .then(Commands.literal("spawnChunk")
                        .requires(x -> x.hasPermission(2))
                        .then(Commands.argument("location", Vec3Argument.vec3())
                                .executes((cmd) -> spawnChunk(cmd.getSource(), cmd.getSource().getLevel(),
                                        Vec3Argument.getCoordinates(cmd, "location"), false))))

                .then(Commands.literal("spawnRandomChunk")
                        .requires(x -> x.hasPermission(2))
                        .then(Commands.argument("location", Vec3Argument.vec3())
                                .executes((cmd) -> spawnChunk(cmd.getSource(), cmd.getSource().getLevel(),
                                        Vec3Argument.getCoordinates(cmd, "location"), true))))

                .then(Commands.literal("spawnThemedChunk")
                        .requires(x -> x.hasPermission(2))
                        .then(Commands.argument("theme", StringArgumentType.word())
                                .suggests(new BiomeThemeSuggestionProvider())
                                .then(Commands.argument("location", Vec3Argument.vec3())
                                        .executes((cmd) -> spawnThemedChunk(cmd.getSource(), cmd.getSource().getLevel(),
                                                StringArgumentType.getString(cmd, "theme"),
                                                Vec3Argument.getCoordinates(cmd, "location"))))))

                .then(Commands.literal("config")
                        .requires(x -> x.hasPermission(2))
                        .then(Commands.literal("reload")
                                .executes(GatheringChunksCommand::reloadConfig))
                        .then(Commands.literal("modify")
                                .executes(GatheringChunksCommand::modifyConfig))
                        .then(Commands.literal("set")
                                .then(Commands.argument("field", StringArgumentType.word())
                                        .then(Commands.argument("value", StringArgumentType.greedyString())
                                                .executes(GatheringChunksCommand::setConfigValue))))
                        .then(Commands.literal("list")
                                .executes(GatheringChunksCommand::listConfigFields)
                                .then(Commands.argument("search", StringArgumentType.word())
                                        .executes(GatheringChunksCommand::listConfigFields))))
                .then(Commands.literal("devmode")
                        .requires(x -> x.hasPermission(2))
                        .executes(GatheringChunksCommand::toggleDevMode)
                        .then(Commands.literal("on").executes(context -> setDevMode(context, true)))
                        .then(Commands.literal("off").executes(context -> setDevMode(context, false))))
                .then(Commands.literal("debug")
                        .requires(x -> x.hasPermission(2))
                        .then(Commands.literal("chunk")
                                .executes(GatheringChunksCommand::debugChunk)
                                .then(Commands.argument("x", IntegerArgumentType.integer())
                                        .then(Commands.argument("z", IntegerArgumentType.integer())
                                                .executes(GatheringChunksCommand::debugChunk))))));
    }

    private static class BiomeThemeSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context,
                                                             SuggestionsBuilder builder) {
            GatheringChunksConstants.BIOME_THEMES.forEach(builder::suggest);
            return builder.buildFuture();
        }
    }

    private static class HelpTopicSuggestionProvider implements SuggestionProvider<CommandSourceStack> {
        @Override
        public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSourceStack> context,
                                                             SuggestionsBuilder builder) {
            builder.suggest("commands");
            return builder.buildFuture();
        }
    }

    private static int showHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        source.sendSuccess(() -> Component.literal("§6===== Gathering Chunks Help ====="), false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunks help §7- Show this help"), false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunks spawnChunk <pos> §7- Spawn a chunk (OP)"),
                false);
        source.sendSuccess(
                () -> Component.literal("§e/gatheringchunks spawnRandomChunk <pos> §7- Spawn random chunk (OP)"),
                false);
        source.sendSuccess(
                () -> Component
                        .literal("§e/gatheringchunks spawnThemedChunk <theme> <pos> §7- Spawn themed chunk (OP)"),
                false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunks config reload §7- Reload config from disk (OP)"),
                false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunks config modify §7- Open config menu UI (OP)"),
                false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunks config list [search] §7- List config fields and current values (OP)"),
                false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunks config set <field> <value> §7- Set a config value and save it (OP)"),
                false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunks devmode [on|off] §7- Toggle verbose debug logging + /debug commands (OP)"),
                false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunks debug chunk [x] [z] §7- Compare visible vs generation dimension at a chunk (OP)"),
                false);
        source.sendSuccess(() -> Component.literal("§e/gatheringchunksdev §7- Client-only: toggle version watermark HUD"),
                false);
        source.sendSuccess(() -> Component.literal("§e/chests §7- List nearby chests"), false);
        source.sendSuccess(() -> Component.literal("§e/chests tracker enable/disable §7- Toggle chest notifications"),
                false);
        return 1;
    }

    private static int showSpecificHelp(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        String topic = StringArgumentType.getString(context, "topic").toLowerCase();

        switch (topic) {
            case "commands" -> {
                return showHelp(context);
            }
            default -> {
                source.sendFailure(Component.literal("§cUnknown help topic: " + topic));
                source.sendSuccess(() -> Component.literal("§7Available topics: commands"), false);
                return 0;
            }
        }
    }

    private static int spawnChunk(CommandSourceStack stack, ServerLevel level, Coordinates specifiedCoords,
                                  boolean random) throws CommandSyntaxException {
        Vec3 vec3 = specifiedCoords.getPosition(stack);
        BlockPos pos = new BlockPos((int) vec3.x, level.getMaxBuildHeight() - 1, (int) vec3.z);
        ChunkPos chunkPos = new ChunkPos(pos);

        if (!(level.getChunkSource().getGenerator() instanceof SkyChunkGenerator)) {
            throw INVALID_LEVEL.create();
        }

        if (!Level.isInSpawnableBounds(pos)) {
            throw INVALID_POSITION.create();
        }

        if (!SpawnChunkHelper.isEmptyChunk(level, chunkPos)) {
            throw NON_EMPTY_CHUNK.create();
        }

        ChunkSpawnController.get(level.getServer()).request(level, "", random, pos);
        return 1;
    }

    private static int spawnThemedChunk(CommandSourceStack stack, ServerLevel level, String biome,
                                        Coordinates specifiedCoords) throws CommandSyntaxException {
        Vec3 vec3 = specifiedCoords.getPosition(stack);
        BlockPos pos = new BlockPos((int) vec3.x, level.getMaxBuildHeight() - 1, (int) vec3.z);
        ChunkPos chunkPos = new ChunkPos(pos);

        if (level.getChunkSource().getGenerator() instanceof SkyChunkGenerator skyChunkGenerator) {
            ResourceKey<Level> biomeDimension = skyChunkGenerator.getBiomeDimension(biome);
            if (biomeDimension == null) {
                throw INVALID_THEME.create();
            }

            if (!Level.isInSpawnableBounds(pos)) {
                throw INVALID_POSITION.create();
            }

            if (!SpawnChunkHelper.isEmptyChunk(level, chunkPos)) {
                throw NON_EMPTY_CHUNK.create();
            }

            ServerLevel sourceLevel = level.getServer().getLevel(biomeDimension);
            if (sourceLevel == null) {
                throw INVALID_THEME.create();
            }

            ChunkSpawnController.get(level.getServer()).request(level, biome, false, pos);
            return 1;
        } else {
            throw INVALID_LEVEL.create();
        }
    }


    private static int listConfigFields(CommandContext<CommandSourceStack> context) {
        String search;
        try {
            search = StringArgumentType.getString(context, "search").toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException e) {
            search = "";
        }
        final String finalSearch = search;
        CommandSourceStack source = context.getSource();

        Object rootConfig = ChunkByChunkConfig.get();
        ConfigMetadata metadata = MetadataBuilder.build(rootConfig.getClass());

        List<String> lines = new ArrayList<>();
        collectFieldLines(metadata.getFields(), rootConfig, search, lines);
        for (SectionMetadata section : metadata.getSections().values()) {
            collectSectionLines(section, section.getSectionObject(rootConfig), search, lines);
        }

        if (lines.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§7No config fields match '" + finalSearch + "'"), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6===== Gathering Chunks Config (" + lines.size() + " fields) ====="), false);
        for (String line : lines) {
            source.sendSuccess(() -> Component.literal(line), false);
        }
        return lines.size();
    }

    private static void collectSectionLines(SectionMetadata section, Object sectionObject, String search, List<String> lines) {
        collectFieldLines(section.getFields(), sectionObject, search, lines);
        for (SectionMetadata sub : section.getSubsections().values()) {
            collectSectionLines(sub, sub.getSectionObject(sectionObject), search, lines);
        }
    }

    private static void collectFieldLines(Map<String, FieldMetadata<?>> fields, Object owner, String search, List<String> lines) {
        for (FieldMetadata<?> field : fields.values()) {
            String name = field.getName();
            if (!search.isEmpty() && !name.toLowerCase(java.util.Locale.ROOT).contains(search)) {
                continue;
            }
            String value = field.serializeValue(owner);
            String comment = field.getComments().isEmpty() ? "" : " §8- " + field.getComments().get(0);
            lines.add("§e" + name + " §f= §b" + value + comment);
        }
    }

    private static int toggleDevMode(CommandContext<CommandSourceStack> context) {
        boolean nowEnabled = com.ryvione.gatheringchunks.server.DevMode.toggle();
        return reportDevMode(context.getSource(), nowEnabled);
    }

    private static int setDevMode(CommandContext<CommandSourceStack> context, boolean value) {
        com.ryvione.gatheringchunks.server.DevMode.set(value);
        return reportDevMode(context.getSource(), value);
    }

    private static int reportDevMode(CommandSourceStack source, boolean enabled) {
        source.sendSuccess(() -> Component.literal("§e[Gathering Chunks] Server Dev Mode " + (enabled ? "§aenabled" : "§cdisabled")
                + "§e - verbose chunk-copy/biome/structure logging and /gatheringchunks debug are " + (enabled ? "on" : "off")), true);
        return 1;
    }

    private static int debugChunk(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        if (!(source.getEntity() instanceof net.minecraft.server.level.ServerPlayer player) &&
                !hasCoordArgs(context)) {
            source.sendFailure(Component.literal("§cMust be run by a player, or with explicit x/z coordinates"));
            return 0;
        }

        net.minecraft.server.level.ServerLevel visibleLevel = source.getLevel();
        ChunkPos chunkPos;
        try {
            int x = IntegerArgumentType.getInteger(context, "x");
            int z = IntegerArgumentType.getInteger(context, "z");
            chunkPos = new ChunkPos(x, z);
        } catch (IllegalArgumentException e) {
            chunkPos = new ChunkPos(net.minecraft.core.BlockPos.containing(source.getPosition()));
        }

        if (!(visibleLevel.getChunkSource().getGenerator() instanceof com.ryvione.gatheringchunks.server.world.SkyChunkGenerator skyGen)) {
            source.sendFailure(Component.literal("§cCurrent dimension isn't a Gathering Chunks sky dimension"));
            return 0;
        }
        net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> effectiveGenLevelKey =
                com.ryvione.gatheringchunks.server.world.ChunkSpawnController.get(source.getServer())
                        .getEffectiveGenerationLevel(visibleLevel.dimension().location().toString(), skyGen.getGenerationLevel());
        net.minecraft.server.level.ServerLevel genLevel = source.getServer().getLevel(effectiveGenLevelKey);
        if (genLevel == null) {
            source.sendFailure(Component.literal("§cGeneration dimension " + effectiveGenLevelKey.location() + " isn't loaded"));
            return 0;
        }
        if (!effectiveGenLevelKey.equals(skyGen.getGenerationLevel())) {
            source.sendSuccess(() -> Component.literal("§7(Using origin-locked generation dimension " + effectiveGenLevelKey.location()
                    + " instead of this generator's default " + skyGen.getGenerationLevel().location() + ")"), false);
        }

        net.minecraft.world.level.chunk.ChunkAccess visibleChunk =
                visibleLevel.getChunkSource().getChunkNow(chunkPos.x, chunkPos.z);
        net.minecraft.world.level.chunk.ChunkAccess genChunk =
                genLevel.getChunk(chunkPos.x, chunkPos.z, net.minecraft.world.level.chunk.status.ChunkStatus.FULL, true);

        final ChunkPos finalChunkPos = chunkPos;
        source.sendSuccess(() -> Component.literal("§6===== Chunk debug: " + finalChunkPos + " ====="), false);
        source.sendSuccess(() -> Component.literal("§7Visible: " + visibleLevel.dimension().location()
                + "  Generation: " + genLevel.dimension().location()), false);

        int centerX = chunkPos.getMinBlockX() >> 2;
        int centerZ = chunkPos.getMinBlockZ() >> 2;
        String visBiome = visibleChunk == null ? "(chunk not loaded)"
                : describeBiomeHolder(visibleChunk.getNoiseBiome(centerX, 8, centerZ));
        String genBiome = describeBiomeHolder(genChunk.getNoiseBiome(centerX, 8, centerZ));
        source.sendSuccess(() -> Component.literal("§eBiome (center column): §fvisible=" + visBiome + " §7| §fgeneration=" + genBiome
                + (visBiome.equals(genBiome) ? " §a(match)" : " §c(MISMATCH)")), false);

        boolean spawned = skyGen.isChunkSpawned(chunkPos.toLong());
        boolean persistedSpawned = com.ryvione.gatheringchunks.server.world.ChunkSpawnController.get(source.getServer())
                .isChunkSpawned(visibleLevel, finalChunkPos);
        source.sendSuccess(() -> Component.literal("§eGathered/spawned flag: §f" + spawned
                + " §7(persisted record: " + persistedSpawned + ")"), false);

        if (visibleChunk != null) {
            int barrierCount = countBoundaryBarriers(visibleLevel, chunkPos);
            source.sendSuccess(() -> Component.literal("§eBarrier blocks on chunk boundary: §f" + barrierCount
                    + (barrierCount > 0 ? " §7(non-zero after neighbors are gathered usually means a stuck barrier - "
                    + "removal is normally triggered by the neighbor's own spawn event, so this can be left behind "
                    + "if that chunk was spawned/overwritten through a path that skipped it, e.g. the origin-chunk guard)" : "")), false);
        }

        java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, net.minecraft.world.level.levelgen.structure.StructureStart> genStarts = genChunk.getAllStarts();
        source.sendSuccess(() -> Component.literal("§eStructure starts in generation chunk: §f" + genStarts.size()), false);
        for (var entry : genStarts.entrySet()) {
            if (!entry.getValue().isValid()) continue;
            source.sendSuccess(() -> Component.literal("  §7- " + entry.getKey() + " §7(" + entry.getValue().getPieces().size() + " pieces, bb=" + entry.getValue().getBoundingBox() + ")"), false);
        }
        if (visibleChunk != null) {
            java.util.Map<net.minecraft.world.level.levelgen.structure.Structure, net.minecraft.world.level.levelgen.structure.StructureStart> visStarts = visibleChunk.getAllStarts();
            source.sendSuccess(() -> Component.literal("§eStructure starts in visible chunk: §f" + visStarts.size()), false);
            for (var entry : visStarts.entrySet()) {
                if (!entry.getValue().isValid()) continue;
                source.sendSuccess(() -> Component.literal("  §7- " + entry.getKey() + " §7(" + entry.getValue().getPieces().size() + " pieces, bb=" + entry.getValue().getBoundingBox() + ")"), false);
            }
            if (!genStarts.isEmpty() && visStarts.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§c-> Generation dimension has structure data here but the visible chunk doesn't: copy failed or hasn't run yet (chunk not gathered)."), false);
            } else if (genStarts.isEmpty()) {
                source.sendSuccess(() -> Component.literal("§7-> Generation dimension has no structure start anchored in THIS chunk. If you expected one, it's likely centered in a neighboring chunk - try this command on adjacent chunk coordinates."), false);
            }
        } else {
            source.sendSuccess(() -> Component.literal("§7Visible chunk isn't loaded server-side right now (fly closer first)."), false);
        }

        return 1;
    }

    private static int countBoundaryBarriers(net.minecraft.server.level.ServerLevel level, ChunkPos chunkPos) {
        int count = 0;
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int minX = chunkPos.getMinBlockX();
        int maxX = chunkPos.getMaxBlockX();
        int minZ = chunkPos.getMinBlockZ();
        int maxZ = chunkPos.getMaxBlockZ();
        net.minecraft.core.BlockPos.MutableBlockPos pos = new net.minecraft.core.BlockPos.MutableBlockPos();
        for (int y = minY; y < maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                pos.set(x, y, minZ);
                if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BARRIER)) count++;
                pos.set(x, y, maxZ);
                if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BARRIER)) count++;
            }
            for (int z = minZ; z <= maxZ; z++) {
                pos.set(minX, y, z);
                if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BARRIER)) count++;
                pos.set(maxX, y, z);
                if (level.getBlockState(pos).is(net.minecraft.world.level.block.Blocks.BARRIER)) count++;
            }
        }
        return count;
    }

    private static boolean hasCoordArgs(CommandContext<CommandSourceStack> context) {
        try {
            IntegerArgumentType.getInteger(context, "x");
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static String describeBiomeHolder(net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome) {
        if (biome == null) return "?";
        return biome.unwrapKey().map(k -> k.location().toString()).orElse(biome.toString());
    }

    private record FoundField(FieldMetadata<?> field, Object owner) {
    }

    private static FoundField findField(String fieldName) {
        Object rootConfig = ChunkByChunkConfig.get();
        ConfigMetadata metadata = MetadataBuilder.build(rootConfig.getClass());
        String key = fieldName.toLowerCase(java.util.Locale.ROOT);

        FieldMetadata<?> direct = metadata.getFields().get(key);
        if (direct != null) {
            return new FoundField(direct, rootConfig);
        }
        for (SectionMetadata section : metadata.getSections().values()) {
            FoundField found = findFieldInSection(section, section.getSectionObject(rootConfig), key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static FoundField findFieldInSection(SectionMetadata section, Object sectionObject, String key) {
        FieldMetadata<?> field = section.getFields().get(key);
        if (field != null) {
            return new FoundField(field, sectionObject);
        }
        for (SectionMetadata sub : section.getSubsections().values()) {
            FoundField found = findFieldInSection(sub, sub.getSectionObject(sectionObject), key);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static int setConfigValue(CommandContext<CommandSourceStack> context) {
        String fieldName = StringArgumentType.getString(context, "field");
        String value = StringArgumentType.getString(context, "value");
        CommandSourceStack source = context.getSource();

        FoundField found = findField(fieldName);
        if (found == null) {
            source.sendFailure(Component.literal("§c[Gathering Chunks] Unknown config field '" + fieldName
                    + "'. Use /gatheringchunks config list [search] to see field names and current values."));
            return 0;
        }

        try {
            found.field().deserializeValue(found.owner(), value);
        } catch (RuntimeException e) {
            source.sendFailure(Component.literal(
                    "§c[Gathering Chunks] Invalid value '" + value + "' for '" + fieldName + "': " + e.getMessage()));
            return 0;
        }

        ConfigUtil.saveDefaultConfig(source.getServer());
        LOGGER.info("[Command] {} set config field '{}' to '{}'", source.getTextName(), fieldName, value);
        source.sendSuccess(() -> Component.literal(
                "§a[Gathering Chunks] Set '" + fieldName + "' to '" + found.field().serializeValue(found.owner()) + "' and saved to disk."), true);

        try {
            syncConfigToAllClients(source.getServer());
        } catch (Exception syncError) {
            LOGGER.error("[Command] Failed to sync config to clients after set", syncError);
            source.sendSuccess(() -> Component.literal("§6[Gathering Chunks] Value saved but client sync had errors. Check logs."), true);
        }
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> context) {
        LOGGER.info("[Command] Config reload requested by {}", context.getSource().getTextName());
        context.getSource().sendSuccess(() -> Component.literal("§e[Gathering Chunks] Reloading config..."), true);

        try {
            ConfigUtil.reloadConfig(context.getSource().getServer());
            LOGGER.info("[Command] Config successfully reloaded!");
            context.getSource().sendSuccess(() -> Component.literal("§a[Gathering Chunks] Config reloaded successfully!"), true);

            try {
                syncConfigToAllClients(context.getSource().getServer());
            } catch (Exception syncError) {
                LOGGER.error("[Command] Failed to sync config to clients, but config was reloaded on server", syncError);
                context.getSource().sendSuccess(() -> Component.literal("§6[Gathering Chunks] Config reloaded but client sync had errors. Check logs."), true);
                return 1;
            }

            return 1;
        } catch (Exception e) {
            LOGGER.error("[Command] Failed to reload config", e);
            context.getSource().sendFailure(Component.literal("§c[Gathering Chunks] Failed to reload config: " + e.getMessage()));
            return 0;
        }
    }

    private static void syncConfigToAllClients(net.minecraft.server.MinecraftServer server) throws Exception {
        try {
            String configJson = GSON.toJson(ChunkByChunkConfig.get().getGatheringChunksConfig());
            if (configJson == null || configJson.isEmpty()) {
                throw new RuntimeException("Failed to serialize config to JSON");
            }
            
            S2CSyncConfigPacket packet = new S2CSyncConfigPacket(configJson);

            int syncCount = 0;
            int failCount = 0;
            
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                try {
                    Services.PLATFORM.sendConfigSyncPacket(player, packet);
                    syncCount++;
                } catch (Exception playerSyncError) {
                    LOGGER.warn("[Command] Failed to sync config to player {}: {}", player.getName().getString(), playerSyncError.getMessage());
                    failCount++;
                }
            }

            LOGGER.info("[Command] Config synced to {}/{} online players (failed: {})", 
                    syncCount, server.getPlayerList().getPlayerCount(), failCount);
            
            if (failCount > 0) {
                LOGGER.warn("[Command] Some players failed to receive config sync, but config was reloaded on server");
            }
        } catch (Exception e) {
            LOGGER.error("[Command] Error during config sync: {}", e.getMessage(), e);
            throw e;
        }
    }


    private static int modifyConfig(CommandContext<CommandSourceStack> context) {
        if (context.getSource().getEntity() instanceof ServerPlayer player) {
            LOGGER.info("[Command] Config modify requested by player: {}", player.getName().getString());

            try {
                Services.PLATFORM.sendConfigOpenPacket(player, new S2COpenConfigPacket());

                context.getSource().sendSuccess(() -> Component.literal("§a[Gathering Chunks] Opening config menu..."), false);
                LOGGER.info("[Command] Config screen packet sent to player: {}", player.getName().getString());
                return 1;
            } catch (Exception e) {
                LOGGER.error("[Command] Failed to open config screen for player: {}", player.getName().getString(), e);
                context.getSource().sendFailure(Component.literal("§c[Gathering Chunks] Failed to open config menu"));
                return 0;
            }
        } else {
            context.getSource().sendFailure(Component.literal("§cThis command must be run by a player."));
            return 0;
        }
    }
}