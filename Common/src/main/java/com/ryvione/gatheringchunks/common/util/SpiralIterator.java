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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
public class SpiralIterator {
    private static final int[][] SCAN_DIRECTION_OFFSET = {{1, 0}, {0, 1}, {-1, 0}, {0, -1}};
    private static final int[] SCAN_DISTANCE_INCREASE = {0, 1, 0, 1};
    private int startX;
    private int startY;
    private int currentX;
    private int currentY;
    private int direction = 0;
    private int lineLength = 1;
    private int lineRemaining = 1;
    public SpiralIterator() {
        this(0,0);
    }
    public SpiralIterator(int startX, int startY) {
        this.startX = startX;
        this.startY = startY;
        currentX = startX;
        currentY = startY;
    }
    public int getX() {
        return currentX;
    }
    public int getY() {
        return currentY;
    }
    public void reset() {
        reset(0,0);
    }
    public int layerDistance() {
        return Math.max(Mth.abs(currentX - startX), Mth.abs(currentY - startY));
    }
    public void reset(int x, int y) {
        currentX = x;
        currentY = y;
        direction = 0;
        lineLength = 1;
        lineRemaining = 1;
    }
    public void next() {
        currentX += SCAN_DIRECTION_OFFSET[direction][0];
        currentY += SCAN_DIRECTION_OFFSET[direction][1];
        lineRemaining--;
        if (lineRemaining == 0) {
            lineLength += SCAN_DISTANCE_INCREASE[direction];
            lineRemaining = lineLength;
            direction = (direction + 1) % SCAN_DIRECTION_OFFSET.length;
        }
    }
    public void load(CompoundTag tag) {
        currentX = tag.getIntOr("X", 0);
        currentY = tag.getIntOr("Y", 0);
        direction = tag.getIntOr("Direction", 0);
        lineLength = tag.getIntOr("LineLength", 0);
        lineRemaining = tag.getIntOr("LineRemaining", 0);
    }
    public CompoundTag createTag() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", currentX);
        tag.putInt("Y", currentY);
        tag.putInt("Direction", direction);
        tag.putInt("LineLength", lineLength);
        tag.putInt("LineRemaining", lineRemaining);
        return tag;
    }
}