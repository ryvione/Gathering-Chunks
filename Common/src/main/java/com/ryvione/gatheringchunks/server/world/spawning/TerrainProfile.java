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
import net.minecraft.nbt.ListTag;
import net.minecraft.world.level.ChunkPos;

public class TerrainProfile {
    public final int[] northEdge;
    public final int[] southEdge;
    public final int[] eastEdge;
    public final int[] westEdge;
    public final String[] northBiomes;
    public final String[] southBiomes;
    public final String[] eastBiomes;
    public final String[] westBiomes;
    public final int averageHeight;
    public final int heightVariance;
    public final String biomeTheme;
    public final ChunkPos sourcePos;

    public TerrainProfile(int[] north, int[] south, int[] east, int[] west,
                          String[] nBio, String[] sBio, String[] eBio, String[] wBio,
                          int averageHeight, int heightVariance,
                          String biomeTheme, ChunkPos sourcePos) {
        this.northEdge = north;
        this.southEdge = south;
        this.eastEdge = east;
        this.westEdge = west;
        this.northBiomes = nBio;
        this.southBiomes = sBio;
        this.eastBiomes = eBio;
        this.westBiomes = wBio;
        this.averageHeight = averageHeight;
        this.heightVariance = heightVariance;
        this.biomeTheme = biomeTheme;
        this.sourcePos = sourcePos;
    }

    public static TerrainProfile load(CompoundTag tag) {
        int avgHeight = tag.getIntOr("avgHeight", 0);
        int variance = tag.getIntOr("variance", 0);
        String theme = tag.getStringOr("theme", "");
        int[] north = tag.getIntArray("north").orElse(new int[0]);
        int[] south = tag.getIntArray("south").orElse(new int[0]);
        int[] east = tag.getIntArray("east").orElse(new int[0]);
        int[] west = tag.getIntArray("west").orElse(new int[0]);

        if (north.length == 0) north = new int[16];
        if (south.length == 0) south = new int[16];
        if (east.length == 0) east = new int[16];
        if (west.length == 0) west = new int[16];

        String[] nBio = loadStringArray(tag, "nBio");
        String[] sBio = loadStringArray(tag, "sBio");
        String[] eBio = loadStringArray(tag, "eBio");
        String[] wBio = loadStringArray(tag, "wBio");

        long sourcePosLong = tag.contains("sourcePos") ? tag.getLongOr("sourcePos", 0L) : ChunkPos.INVALID_CHUNK_POS;
        return new TerrainProfile(north, south, east, west, nBio, sBio, eBio, wBio, avgHeight, variance, theme, new ChunkPos(sourcePosLong));
    }

    private static String[] loadStringArray(CompoundTag tag, String key) {
        if (!tag.contains(key)) return new String[8];
        ListTag list = tag.getListOrEmpty(key);
        String[] arr = new String[list.size()];
        for (int i = 0; i < list.size(); i++) arr[i] = list.getStringOr(i, "");
        return arr;
    }

    private static ListTag saveStringArray(String[] arr) {
        ListTag list = new ListTag();
        if (arr != null) {
            for (String s : arr) list.add(net.minecraft.nbt.StringTag.valueOf(s != null ? s : ""));
        }
        return list;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("avgHeight", averageHeight);
        tag.putInt("variance", heightVariance);
        tag.putString("theme", biomeTheme);
        tag.putIntArray("north", northEdge);
        tag.putIntArray("south", southEdge);
        tag.putIntArray("east", eastEdge);
        tag.putIntArray("west", westEdge);
        tag.put("nBio", saveStringArray(northBiomes));
        tag.put("sBio", saveStringArray(southBiomes));
        tag.put("eBio", saveStringArray(eastBiomes));
        tag.put("wBio", saveStringArray(westBiomes));
        tag.putLong("sourcePos", sourcePos.toLong());
        return tag;
    }
}
