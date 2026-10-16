/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.server.world.spawning;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.core.UUIDUtil;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.UUID;

public record SpawnRequest(
        ChunkPos targetChunkPos,
        ResourceKey<Level> targetLevel,
        ChunkPos sourceChunkPos,
        ResourceKey<Level> sourceLevel,
        boolean immediate,
        boolean overwrite,
        boolean isMenderRequest,
        @Nullable UUID playerUUID
) {
    public SpawnRequest(ChunkPos targetChunkPos, ResourceKey<Level> targetLevel, ChunkPos sourceChunkPos,
            ResourceKey<Level> sourceLevel, boolean immediate, boolean overwrite, boolean isMenderRequest) {
        this(targetChunkPos, targetLevel, sourceChunkPos, sourceLevel, immediate, overwrite, isMenderRequest, null);
    }

    private static final String TARGET_POS = "targetPos";
    private static final String TARGET_LEVEL = "targetLevel";
    private static final String SOURCE_POS = "sourcePos";
    private static final String SOURCE_LEVEL = "sourceLevel";
    private static final String IMMEDIATE = "immediate";
    private static final String OVERWRITE = "overwrite";
    private static final String IS_INITIAL = "isInitial";

    public static SpawnRequest load(CompoundTag tag) {
        ChunkPos targetPos = new ChunkPos(tag.getLongOr(TARGET_POS, 0L));
        ResourceKey<Level> targetLevel = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse(tag.getStringOr(TARGET_LEVEL, "")));
        ChunkPos sourcePos = new ChunkPos(tag.getLongOr(SOURCE_POS, 0L));
        ResourceKey<Level> sourceLevel = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
                Identifier.parse(tag.getStringOr(SOURCE_LEVEL, "")));
        boolean immediate = tag.getBooleanOr(IMMEDIATE, false);
        boolean overwrite = tag.getBooleanOr(OVERWRITE, false);
        boolean isMender = tag.getBooleanOr(IS_INITIAL, false);
        UUID playerUUID = tag.getIntArray("playerUUID").map(UUIDUtil::uuidFromIntArray).orElse(null);
        return new SpawnRequest(targetPos, targetLevel, sourcePos, sourceLevel, immediate, overwrite, isMender, playerUUID);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putLong(TARGET_POS, targetChunkPos.toLong());
        tag.putString(TARGET_LEVEL, targetLevel.identifier().toString());
        tag.putLong(SOURCE_POS, sourceChunkPos.toLong());
        tag.putString(SOURCE_LEVEL, sourceLevel.identifier().toString());
        tag.putBoolean(IMMEDIATE, immediate);
        tag.putBoolean(OVERWRITE, overwrite);
        tag.putBoolean(IS_INITIAL, isMenderRequest);
        if (playerUUID != null) {
            tag.putIntArray("playerUUID", UUIDUtil.uuidToIntArray(playerUUID));
        }
        return tag;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpawnRequest that = (SpawnRequest) o;
        return targetChunkPos.equals(that.targetChunkPos)
                && targetLevel.equals(that.targetLevel)
                && sourceChunkPos.equals(that.sourceChunkPos);
    }

    @Override
    public int hashCode() {
        return Objects.hash(targetChunkPos, targetLevel, sourceChunkPos);
    }
}
