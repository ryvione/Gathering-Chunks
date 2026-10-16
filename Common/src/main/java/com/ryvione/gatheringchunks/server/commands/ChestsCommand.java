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

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.ryvione.gatheringchunks.config.ChunkByChunkConfig;
import com.ryvione.gatheringchunks.server.world.ChestTracker;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.ChestBlock;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class ChestsCommand {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("chests")
                .requires(Commands.hasPermission(Commands.LEVEL_ALL))
                .executes(ChestsCommand::listChests)
                .then(Commands.literal("tracker")
                        .then(Commands.literal("enable")
                                .executes(context -> setTrackerState(context, true)))
                        .then(Commands.literal("disable")
                                .executes(context -> setTrackerState(context, false)))));
    }

    private static int listChests(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();

        if (ChunkByChunkConfig.get().getDifficulty().getHardMode().isDisableChestsCommand()) {
            source.sendFailure(Component.literal("§cThe /chests command is disabled"));
            return 0;
        }

        ServerLevel level = source.getLevel();

        if (level.getServer() == null) {
            source.sendFailure(Component.literal("Server not available"));
            return 0;
        }

        ChestTracker tracker = ChestTracker.get(level.getServer());
        Set<BlockPos> allChests = tracker.getChestPositions();

        List<BlockPos> validChests = new ArrayList<>();
        List<BlockPos> toRemove = new ArrayList<>();

        for (BlockPos pos : allChests) {
            if (!level.isLoaded(pos)) {
                continue;
            }

            net.minecraft.world.level.block.state.BlockState state = level.getBlockState(pos);
            boolean isChest = state.getBlock() instanceof ChestBlock ||
                    state.getBlock() == com.ryvione.gatheringchunks.interop.Services.PLATFORM.bedrockChestBlock();

            if (isChest) {
                validChests.add(pos);
            } else {
                toRemove.add(pos);
            }
        }

        for (BlockPos pos : toRemove) {
            tracker.removeChest(pos);
        }

        if (validChests.isEmpty()) {
            source.sendSuccess(() -> Component.literal("§eNo chests found in " + level.dimension().identifier()), false);
            return 0;
        }

        source.sendSuccess(() -> Component.literal("§6Available chests in " + level.dimension().identifier() + " §7(" + validChests.size() + ")§r:"), false);

        for (BlockPos pos : validChests) {
            final BlockPos finalPos = pos;
            Component message = Component.literal("  §bX=" + finalPos.getX() + " §bY=" + finalPos.getY() + " §bZ=" + finalPos.getZ());
            source.sendSuccess(() -> message, false);
        }

        return validChests.size();
    }

    private static int setTrackerState(CommandContext<CommandSourceStack> context, boolean enabled) {
        CommandSourceStack source = context.getSource();

        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("This command can only be used by players"));
            return 0;
        }

        ChestTracker tracker = ChestTracker.get(source.getServer());
        tracker.setTrackerEnabled(player.getUUID(), enabled);

        if (enabled) {
            player.sendSystemMessage(Component.literal("§aChest tracker notifications enabled"));
        } else {
            player.sendSystemMessage(Component.literal("§cChest tracker notifications disabled"));
        }

        return 1;
    }
}