/*
 * Original work Copyright (c) immortius
 * Modified work Copyright (c) 2026 Ryvione
 *
 * This file is part of Gathering Chunks (Ryvione's Fork).
 * Original: https://github.com/immortius/chunkbychunk
 *
 * Licensed under the MIT License. See LICENSE file in the project root for details.
 */
package com.ryvione.gatheringchunks.common.network;

import com.ryvione.gatheringchunks.common.GatheringChunksConstants;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

public record S2CSyncConfigPacket(String configJson) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<S2CSyncConfigPacket> TYPE = new CustomPacketPayload.Type<>(
            Identifier.fromNamespaceAndPath(GatheringChunksConstants.MOD_ID, "sync_config"));
    public static final StreamCodec<ByteBuf, S2CSyncConfigPacket> CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, S2CSyncConfigPacket::configJson,
            S2CSyncConfigPacket::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}