/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;

import java.util.function.Function;
import java.util.function.Supplier;

public final class SavedDataCompat {
    private SavedDataCompat() {
    }

    public static <T extends SavedData> SavedDataType<T> of(String id, Supplier<T> factory,
            Function<CompoundTag, T> loader, Function<T, CompoundTag> saver) {
        return new SavedDataType<>(id, factory, CompoundTag.CODEC.xmap(loader, saver), DataFixTypes.LEVEL);
    }
}
