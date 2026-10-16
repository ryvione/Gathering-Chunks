/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */

package com.ryvione.gatheringchunks.common.util;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;
public final class ChangeDimensionHelper {
    private static final ThreadLocal<Vec3> portalInfo = new ThreadLocal<>();
    private ChangeDimensionHelper() {
    }
    public static Vec3 getPortalInfo() {
        return portalInfo.get();
    }
    public static Entity changeDimension(Entity entity, ServerLevel level, Vec3 pos) {
        portalInfo.set(pos);
        TeleportTransition transition = new TeleportTransition(
                level,
                pos,
                Vec3.ZERO, 
                entity.getYRot(),
                entity.getXRot(),
                TeleportTransition.DO_NOTHING 
        );
        Entity result = entity.teleport(transition);
        portalInfo.remove();
        return result;
    }
}