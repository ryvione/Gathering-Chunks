/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.server;

import net.minecraft.server.MinecraftServer;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class DelayedTasks {
    private static final List<Entry> PENDING = new ArrayList<>();

    private DelayedTasks() {
    }

    public static void runAfterTicks(MinecraftServer server, int ticks, Runnable task) {
        PENDING.add(new Entry(server.getTickCount() + ticks, task));
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        int now = server.getTickCount();
        List<Runnable> due = new ArrayList<>();
        for (Iterator<Entry> it = PENDING.iterator(); it.hasNext(); ) {
            Entry entry = it.next();
            if (entry.dueTick() <= now) {
                due.add(entry.task());
                it.remove();
            }
        }
        for (Runnable task : due) {
            task.run();
        }
    }

    private record Entry(int dueTick, Runnable task) {
    }
}
